import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class TestServer {
    private static final int PORT = 12345;

    private List<Socket> clients = new ArrayList<>();

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server started on port " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Client connected: " + clientSocket.getInetAddress().getHostAddress());

                // Add the new client to the list
                clients.add(clientSocket);

                // Start a new thread to handle communication with the client
                Thread clientThread = new Thread(new ClientHandler(clientSocket));
                clientThread.start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private class ClientHandler implements Runnable {
        private Socket clientSocket;

        public ClientHandler(Socket clientSocket) {
            this.clientSocket = clientSocket;
        }

        @Override
        public void run() {
            try {
                InputStream input = clientSocket.getInputStream();
                OutputStream output = clientSocket.getOutputStream();

                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = input.read(buffer)) != -1) {
                    String message = new String(buffer, 0, bytesRead);
                    System.out.println("Received message from " + clientSocket.getInetAddress().getHostAddress() + ": " + message);

                    // Broadcast the message to all clients
                    for (Socket client : clients) {
                        if (client != clientSocket) {
                            output = client.getOutputStream();
                            output.write(("Broadcast from " + clientSocket.getInetAddress().getHostAddress() + ": " + message).getBytes());
                            output.flush();
                        }
                    }
                }

                // Remove the client from the list
                clients.remove(clientSocket);
                System.out.println("Client disconnected: " + clientSocket.getInetAddress().getHostAddress());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        TestServer server = new TestServer();
        server.start();
    }
}