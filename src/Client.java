import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.HashSet;
import java.util.Set;

public class Client {
    private String username;
    private Set<String> tags = new HashSet<>();
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    // ...

    public void connect(String serverAddress, int serverPort) throws IOException {
        socket = new Socket(serverAddress, serverPort);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out = new PrintWriter(socket.getOutputStream(), true);
        out.println("CONNECT user:@" + username);
        String response = in.readLine();
        if (!response.equals("OK")) {
            throw new IOException("Failed to connect to server: " + response);
        }
        // Start a new thread to handle incoming messages in the background
        new Thread(this::handleIncomingMessages).start();
    }

    public void subscribeToUser(String user) throws IOException {
        out.println("SUBSCRIBE author:@" + user);
        String response = in.readLine();
        if (!response.equals("OK")) {
            throw new IOException("Failed to subscribe to user " + user + ": " + response);
        }
        tags.add("@" + user);
    }

    public void subscribeToTag(String tag) throws IOException {
        out.println("SUBSCRIBE tag:" + tag);
        String response = in.readLine();
        if (!response.equals("OK")) {
            throw new IOException("Failed to subscribe to tag " + tag + ": " + response);
        }
        tags.add(tag);
    }

    public void unsubscribeFromUser(String user) throws IOException {
        if (!tags.remove("@" + user)) {
            throw new IOException("Not subscribed to user " + user);
        }
        out.println("UNSUBSCRIBE author:@" + user);
        String response = in.readLine();
        if (!response.equals("OK")) {
            throw new IOException("Failed to unsubscribe from user " + user + ": " + response);
        }
    }

    public void unsubscribeFromTag(String tag) throws IOException {
        if (!tags.remove(tag)) {
            throw new IOException("Not subscribed to tag " + tag);
        }
        out.println("UNSUBSCRIBE tag:" + tag);
        String response = in.readLine();
        if (!response.equals("OK")) {
            throw new IOException("Failed to unsubscribe from tag " + tag + ": " + response);
        }
    }

    private void handleIncomingMessages() {
        try {
            String line;
            while ((line = in.readLine()) != null) {
                String[] parts = line.split(" ", 2);
                String header = parts[0];
                String body = parts.length > 1 ? parts[1] : "";
                if (header.equals("MSG")) {
                    handleIncomingMessage(body);
                } else {
                    System.out.println("Unexpected server response: " + line);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleIncomingMessage(String message) {
        // Handle incoming message here
        System.out.println("Received message: " + message);
    }
}
