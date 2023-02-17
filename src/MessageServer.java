import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MessageServer {
    private Map<UUID, Message> messageMap = new HashMap<>();
    private Map<String, List<UUID>> authorMap = new HashMap<>();
    private Map<String, List<UUID>> tagMap = new HashMap<>();
    private int messageLimit = 5;

    public void start(int port) {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server started on port " + port);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New client connected");
                new ClientHandler(clientSocket).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private class ClientHandler extends Thread {
        private Socket clientSocket;
        private BufferedReader in;
        private PrintWriter out;

        public ClientHandler(Socket socket) {
            this.clientSocket = socket;
        }

        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                out = new PrintWriter(clientSocket.getOutputStream(), true);

                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    System.out.println("Received request: " + inputLine);
                    String[] parts = inputLine.split(" ", 2);
                    String header = parts[0];
                    String body = parts.length > 1 ? parts[1] : "";

                    String[] headers = header.split(" ");
                    switch (headers[0]) {
                        case "PUBLISH":
                            handlePublish(headers, body);
                            break;
                        case "RCV_IDS":
                            handleReceiveIds(headers);
                            break;
                        case "RCV_MSG":
                            handleReceiveMessage(headers);
                            break;
                        case "REPLY":
                            handleReply(headers, body);
                            break;
                        case "REPUBLISH":
                            handleRepublish(headers);
                            break;
                        default:
                            sendError("Unknown request");
                            break;
                    }
                }

                in.close();
                out.close();
                clientSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void handlePublish(String[] headers, String body) {
            String author = headers[1].substring(7);
            String content = body;

            UUID messageId = UUID.randomUUID();
            Message message = new Message(messageId, author, content, null, false);
            messageMap.put(messageId, message);

            List<UUID> messagesByAuthor = authorMap.computeIfAbsent(author, k -> new ArrayList<>());
            messagesByAuthor.add(messageId);

            for (String tag : content.split("\\s+")) {
                if (tag.startsWith("#")) {
                    List<UUID> messagesByTag = tagMap.computeIfAbsent(tag, k -> new ArrayList<>());
                    messagesByTag.add(messageId);
                }
            }

            sendOk();
        }

        private void handleReceiveIds(String[] requestHeaderParts) throws IOException {
            // Parse request parameters
            String author = null;
            String tag = null;
            String sinceId = null;
            int limit = 5; // Default limit

            for (int i = 1; i < requestHeaderParts.length; i++) {
                String[] paramParts = requestHeaderParts[i].split(":");
                String paramName = paramParts[0].trim().toLowerCase();
                String paramValue = paramParts[1].trim();

                switch (paramName) {
                    case "author":
                        author = paramValue;
                        break;
                    case "tag":
                        tag = paramValue;
                        break;
                    case "since_id":
                        sinceId = paramValue;
                        break;
                    case "limit":
                        limit = Integer.parseInt(paramValue);
                        break;
                    default:
                        throw new IllegalArgumentException("Invalid parameter in RCV_IDS request header: " + paramName);
                }
            }

            // Retrieve messages
            List<String> messageIds = messageStore.getMessageIds(author, tag, sinceId, limit);

            // Send response
            String responseHeader = "MSG_IDS\r\n";
            String responseBody = String.join("\r\n", messageIds) + "\r\n";
            sendResponse(responseHeader, responseBody);
        }
        private void handleReceiveMessage(String[] headers) {
            String id = headers[1];
            String user = headers[2];
            String keywords = headers[3];
            String content = headers[4];

            // Create a new message and add it to the list
            Message message = new Message(id, user, keywords, content);
            synchronized (messages) {
                messages.add(message);
            }

            // Notify all subscribers
            for (String keyword : keywords.split("\\s+")) {
                List<ClientHandler> subscribers = keywordSubscribers.get(keyword);
                if (subscribers != null) {
                    for (ClientHandler subscriber : subscribers) {
                        subscriber.queueMessage(message);
                    }
                }
            }

            // Send an acknowledgment back to the client
            String response = "ACK " + id;
            sendResponse(response);
        }

        private void handleReply(String[] headers, String body) {
            String id = headers[1];
            String user = headers[2];
            String content = body;

            // Find the message being replied to
            Message message = findMessageById(id);
            if (message == null) {
                sendError("Unknown message");
                return;
            }

            // Create a new reply and add it to the message
            Reply reply = new Reply(user, content);
            message.addReply(reply);

            // Notify the author of the original message
            ClientHandler author = userHandlers.get(message.getUser());
            if (author != null) {
                author.queueMessage(message);
            }

            // Send an acknowledgment back to the client
            String response = "ACK " + id;
            sendResponse(response);
        }

        private void handleRepublish(String[] headers) {
            String id = headers[1];

            // Find the message being republished
            Message message = findMessageById(id);
            if (message == null) {
                sendError("Unknown message");
                return;
            }

            // Notify all subscribers of the republished message
            for (String keyword : message.getKeywords()) {
                List<ClientHandler> subscribers = keywordSubscribers.get(keyword);
                if (subscribers != null) {
                    for (ClientHandler subscriber : subscribers) {
                        subscriber.queueMessage(message);
                    }
                }
            }

            // Send an acknowledgment back to the client
            String response = "ACK " + id;
            sendResponse(response);
        }

        private void sendError(String message) {
            String response = "ERR " + message;
            sendResponse(response);
        }

    }
}

