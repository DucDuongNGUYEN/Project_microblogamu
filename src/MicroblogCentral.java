import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MicroblogCentral {
    private Map<String, Set<SocketChannel>> keywordSubscribers = new HashMap<>();
    private Map<String, Set<SocketChannel>> userSubscribers = new HashMap<>();
    private Map<SocketChannel, BlockingQueue<Message>> messageQueues = new HashMap<>();
    private ExecutorService executor = Executors.newCachedThreadPool();

    public void start() {
        try (ServerSocketChannel server = ServerSocketChannel.open()) {
            server.bind(new InetSocketAddress(8888));
            while (true) {
                SocketChannel client = server.accept();
                executor.execute(() -> handleClient(client));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleClient(SocketChannel client) {
        try {
            ByteBuffer buffer = ByteBuffer.allocate(1024);
            while (client.read(buffer) > 0) {
                String request = new String(buffer.array()).trim();
                String[] tokens = request.split(" ");
                String command = tokens[0];
                switch (command) {
                    case "PUBLISH":
                        String userId = tokens[1];
                        String message = request.substring(request.indexOf(tokens[2]));
                        Message newMessage = new Message(userId, message);
                        // Ajouter le message à la liste des messages publiés
                        publishedMessages.add(newMessage);
                        // Ajouter le message à toutes les files d'attente des abonnés concernés
                        for (Queue<Message> queue : subscribers.values()) {
                            if (shouldAddToQueue(newMessage, queue)) {
                                queue.add(newMessage);
                            }
                        }
                        break;
                    case "RCV_IDS":
                        String[] userIds = Arrays.copyOfRange(tokens, 1, tokens.length);
                        List<String> messageIds = getMessageIds(userIds);
                        client.write(ByteBuffer.wrap(String.join(" ", messageIds).getBytes()));
                        break;
                    case "RCV_MSG":
                        List<String> messageContents = getMessageContents(tokens);
                        client.write(ByteBuffer.wrap(String.join("\n", messageContents).getBytes()));
                        break;
                    case "REPLY":
                        String originalMessageId = tokens[1];
                        String replyUserId = tokens[2];
                        String replyMessage = request.substring(request.indexOf(tokens[3]));
                        Message originalMessage = getMessageById(originalMessageId);
                        Message reply = new Message(replyUserId, replyMessage);
                        // Ajouter la réponse à la liste des messages publiés
                        publishedMessages.add(reply);
                        // Ajouter la réponse à la file d'attente de l'utilisateur d'origine
                        Queue<Message> originalUserQueue = subscribers.get(originalMessage.getUserId());
                        if (shouldAddToQueue(reply, originalUserQueue)) {
                            originalUserQueue.add(reply);
                        }
                        break;
                    case "REPUBLISH":
                        String republishUserId = tokens[1];
                        List<Message> userMessages = getMessagesByUserId(republishUserId);
                        for (Message userMessage : userMessages) {
                            // Ajouter le message à la liste des messages publiés
                            publishedMessages.add(userMessage);
                            // Ajouter le message à toutes les files d'attente des abonnés concernés
                            for (Queue<Message> queue : subscribers.values()) {
                                if (shouldAddToQueue(userMessage, queue)) {
                                    queue.add(userMessage);
                                }
                            }
                        }
                        break;
                    case "SUBSCRIBE":
                        String subscriberId = tokens[1];
                        List<String> keywords = Arrays.asList(Arrays.copyOfRange(tokens, 2, tokens.length));
                        Queue<Message> subscriberQueue = new ConcurrentLinkedQueue<>();
                        // Ajouter les mots-clés et l'ID de l'utilisateur à la liste des abonnés
                        subscribers.put(subscriberId, subscriberQueue);
                        keywordSubscribers.put(subscriberId, keywords);
                        // Ajouter tous les messages correspondant aux mots-clés à la file d'attente de l'abonné
                        for (Message message : publishedMessages) {
                            if (shouldAddToQueue(message, subscriberQueue)) {
                                subscriberQueue.add(message);
                            }
                        }
                        break;
                    case "UNSUBSCRIBE":
                        String[] unsubscribeTokens = payload.split(" ");
                        String clientUsername = unsubscribeTokens[0];
                        List<String> keywords = new ArrayList<>();
                        List<String> usernames = new ArrayList<>();
                        for (int i = 1; i < unsubscribeTokens.length; i++) {
                            String token = unsubscribeTokens[i];
                            if (token.startsWith("@")) {
                                usernames.add(token);
                            } else {
                                keywords.add(token);
                            }
                        }
                        if (subscriptions.containsKey(clientUsername)) {
                            List<String> existingKeywords = subscriptions.get(clientUsername).getKeywords();
                            existingKeywords.removeAll(keywords);
                            List<String> existingUsernames = subscriptions.get(clientUsername).getUsernames();
                            existingUsernames.removeAll(usernames);
                            if (existingKeywords.isEmpty() && existingUsernames.isEmpty()) {
                                subscriptions.remove(clientUsername);
                            }
                        }
                        break;
                    default:
                        System.out.println("Unknown command: " + command);
                }
            } catch(IOException e){
                System.out.println("Error reading from client socket: " + e.getMessage());
            }
        }
    }
    private void handleServerConnect(SocketChannel client, String[] args) throws IOException {
        String host = args[0];
        int port = Integer.parseInt(args[1]);

        InetSocketAddress serverAddress = new InetSocketAddress(host, port);

        try {
            // Création d'une socket pour se connecter au serveur
            SocketChannel server = SocketChannel.open(serverAddress);

            // Ajout de la socket à la liste des sockets des serveurs fédérés
            synchronized (servers) {
                servers.add(server);
            }

            // Envoi de la commande de connexion avec l'adresse et le port du serveur local
            String connectCmd = String.format("CONNECT %s %d", serverSocket.getLocalAddress().getHostAddress(), serverSocket.getLocalPort());
            server.write(encoder.encode(CharBuffer.wrap(connectCmd + "\n")));

            // Démarrage d'un thread pour recevoir les messages du serveur
            new Thread(() -> {
                ByteBuffer buffer = ByteBuffer.allocate(1024);
                while (true) {
                    try {
                        int numBytes = server.read(buffer);
                        if (numBytes == -1) {
                            // Le serveur a fermé la connexion
                            synchronized (servers) {
                                servers.remove(server);
                            }
                            server.close();
                            break;
                        }

                        buffer.flip();
                        String msg = decoder.decode(buffer).toString().trim();
                        buffer.clear();

                        handleCommand(server, msg);
                    } catch (IOException e) {
                        e.printStackTrace();
                        break;
                    }
                }
            }).start();

            // Envoi de la réponse au client
            String response = "OK";
            client.write(encoder.encode(CharBuffer.wrap(response + "\n")));
        } catch (IOException e) {
            // En cas d'erreur de connexion, envoi de l'erreur au client
            String errorMsg = String.format("ERROR %s", e.getMessage());
            client.write(encoder.encode(CharBuffer.wrap(errorMsg + "\n")));
        }
    }

}


