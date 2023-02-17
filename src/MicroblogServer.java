import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class MicroblogServer {
    private static final int PORT = 12345;

    private ConcurrentHashMap<Long, String> messages = new ConcurrentHashMap<>();
    private AtomicLong idGenerator = new AtomicLong(0);

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server started on port " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                new Thread(new ClientHandler(clientSocket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private synchronized long publishMessage(String message) {
        long id = idGenerator.getAndIncrement();
        messages.put(id, message);
        System.out.println("New message published: " + id + " - " + message);
        return id;
    }

    private class ClientHandler implements Runnable {
        private Socket clientSocket;

        public ClientHandler(Socket clientSocket) {
            this.clientSocket = clientSocket;
        }

        @Override
        public void run() {
            try {
                // TODO: handle client requests
            } finally {
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static void main(String[] args) {
        MicroblogServer server = new MicroblogServer();
        server.start();
    }
}
