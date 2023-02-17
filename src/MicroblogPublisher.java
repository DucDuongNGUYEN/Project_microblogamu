import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;

public class MicroblogPublisher {
    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 12345;

    public static void main(String[] args) {
        try (Socket socket = new Socket(SERVER_HOST, SERVER_PORT);
             BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
             PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true)) {

            System.out.print("Enter your username: ");
            String username = reader.readLine();

            while (true) {
                System.out.print("> ");
                String message = reader.readLine();

                writer.println("PUBLISH " + username + " " + message);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

