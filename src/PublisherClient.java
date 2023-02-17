import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class PublisherClient {
    private static final int BUFFER_SIZE = 1024;

    public static void main(String[] args) {
        String pseudo = null;
        while (pseudo == null) {
            System.out.print("Entrez votre pseudo : ");
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            try {
                pseudo = reader.readLine();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        try (SocketChannel client = SocketChannel.open(new InetSocketAddress("localhost", 8888))) {
            client.write(ByteBuffer.wrap(("CONNECT " + pseudo).getBytes()));
            ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
            client.read(buffer);
            String response = new String(buffer.array()).trim();
            System.out.println("Connexion établie : " + response);

            while (true) {
                System.out.print("Entrez votre message : ");
                BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
                String message = reader.readLine();
                client.write(ByteBuffer.wrap(("PUBLISH @" + pseudo + " " + message).getBytes()));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
