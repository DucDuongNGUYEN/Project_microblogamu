import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class RepostClient {
    private static final int BUFFER_SIZE = 1024;
    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Usage : java RepostClient user1 [user2 ...]");
            System.exit(0);
        }

        try (SocketChannel client = SocketChannel.open(new InetSocketAddress("localhost", 8888))) {
            StringBuilder userIds = new StringBuilder();
            for (String userId : args) {
                userIds.append(userId).append(",");
            }
            if (userIds.length() > 0) {
                userIds.deleteCharAt(userIds.length() - 1);
            }
            client.write(ByteBuffer.wrap(("RCV_IDS " + userIds.toString()).getBytes()));
            ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
            client.read(buffer);
            String response = new String(buffer.array()).trim();
            String[] messageIdArray = response.split(",");
            for (String messageId : messageIdArray) {
                client.write(ByteBuffer.wrap(("REPUBLISH " + messageId).getBytes()));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
