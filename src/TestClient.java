import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Scanner;

public class TestClient {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int PORT = 12345;

    public void start() {
        try (Socket socket = new Socket(SERVER_ADDRESS, PORT)) {
            System.out.println("Connected to server at " + SERVER_ADDRESS + ":" + PORT);

            InputStream input = socket.getInputStream();
            OutputStream output = socket.getOutputStream();

            // Start a new thread to handle incoming messages
            Thread messageThread = new Thread(new MessageHandler(input));
            messageThread.start();

            // Read messages from the console and send them to the server
            Scanner scanner = new Scanner(System.in);
            while (true) {
                String message = scanner.nextLine();
                output.write(message.getBytes());
                output.flush();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private class MessageHandler implements Runnable {
        private InputStream input;

        public MessageHandler(InputStream input) {
            this.input = input;
        }

        @Override
        public void run() {
            byte[] buffer = new byte[1024];
            int bytesRead;
            try {
                while ((bytesRead = input.read(buffer)) != -1){
                    String message = new String(buffer, 0, bytesRead);
                    System.out.println(message);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        TestClient client = new TestClient();
        client.start();
    }
}
