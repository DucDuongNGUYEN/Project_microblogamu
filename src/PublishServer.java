import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class PublishServer {

    private static final int BUFFER_SIZE = 1024;

    private static Map<String, String> messages = new HashMap<>();

    public static void main(String[] args) {
        try {
            Selector selector = Selector.open();
            ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.bind(new InetSocketAddress("localhost", 8888));
            serverSocketChannel.configureBlocking(false);
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
            System.out.println("Serveur démarré sur le port 8888");

            while (true) {
                selector.select();
                Set<SelectionKey> selectedKeys = selector.selectedKeys();
                Iterator<SelectionKey> iter = selectedKeys.iterator();

                while (iter.hasNext()) {
                    SelectionKey key = iter.next();

                    if (key.isAcceptable()) {
                        SocketChannel clientChannel = serverSocketChannel.accept();
                        clientChannel.configureBlocking(false);
                        clientChannel.register(selector, SelectionKey.OP_READ);
                        System.out.println("Nouvelle connexion de " + clientChannel.getRemoteAddress());
                    } else if (key.isReadable()) {
                        SocketChannel clientChannel = (SocketChannel) key.channel();
                        ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
                        clientChannel.read(buffer);
                        String request = new String(buffer.array()).trim();
                        System.out.println("Requête reçue de " + clientChannel.getRemoteAddress() + " : " + request);
                        String[] tokens = request.split(" ");

                        switch (tokens[0]) {
                            case "PUBLISH":
                                String message = request.substring(tokens[0].length() + 1);
                                String messageId = String.valueOf(System.currentTimeMillis());
                                messages.put(messageId, message);
                                System.out.println("Message publié : " + messageId + " - " + message);
                                break;
                            case "RCV_IDS":
                                String userIds = request.substring(tokens[0].length() + 1);
                                String[] userIdArray = userIds.split(",");
                                StringBuilder response = new StringBuilder();
                                for (String userId : userIdArray) {
                                    for (Map.Entry<String, String> entry : messages.entrySet()) {
                                        if (entry.getValue().startsWith("@" + userId)) {
                                            response.append(entry.getKey()).append(",");
                                        }
                                    }
                                }
                                if (response.length() > 0) {
                                    response.deleteCharAt(response.length() - 1);
                                }
                                clientChannel.write(ByteBuffer.wrap(response.toString().getBytes()));
                                break;
                            case "RCV_MSG":
                                String messageIds = request.substring(tokens[0].length() + 1);
                                String[] messageIdArray = messageIds.split(",");
                                StringBuilder messageResponse = new StringBuilder();
                                for (String messageId : messageIdArray) {
                                    String messageContent = messages.get(messageId);
                                    if (messageContent != null) {
                                        messageResponse.append(messageId).append(" - ").append(messageContent).append("\n");
                                    }
                                }
                                clientChannel.write(ByteBuffer.wrap(messageResponse.toString().getBytes()));
                                break;
                            case "REPLY":
                                String replyMessage = request.substring(tokens[0].length() + 1);
                                String originalMessageId = tokens[1];
                                String originalMessage = messages.get(originalMessageId);
                                if (originalMessage != null) {
                                    messages.put(originalMessageId, originalMessage + " - " + replyMessage);
                                }
                                break;
                            case "REPUBLISH":
                                String userId = tokens[1];
                                for (Map.Entry<String, String> entry : messages.entrySet()) {
                                    if (entry.getValue().startsWith("@" + userId)) {
                                        clientChannel.write(ByteBuffer.wrap(("PUBLISH " + entry.getValue()).getBytes()));
                                    }
                                }
                                break;
                        }
                    }
                    iter.remove();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
