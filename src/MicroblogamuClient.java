import java.io.*;
import java.net.Socket;

public class MicroblogamuClient {
    private static final int SERVER_PORT = 12345;

    public static void main(String[] args) {
        try {
            // Ouvre une connexion au serveur
            Socket socket = new Socket("localhost", SERVER_PORT);
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);

            // Publier un message
            writer.println("PUBLISH author:@user\r\nContenu du message\r\n");
            String response = reader.readLine();
            if (response.equals("OK")) {
                System.out.println("Message publié avec succès !");
            } else {
                System.out.println("Erreur lors de la publication du message : " + response);
            }

            // Recevoir des identifiants de messages
            writer.println("RCV_IDS [author:@user] [tag:#tag] [since_id:id] [limit:n]\r\n");
            response = reader.readLine();
            if (response.startsWith("MSG_IDS")) {
                String[] messageIds = response.split(" ");
                for (String messageId : messageIds) {
                    System.out.println("Identifiant du message : " + messageId);
                }
            } else {
                System.out.println("Erreur lors de la récupération des identifiants de messages : " + response);
            }

            // Recevoir un message
            writer.println("RCV_MSG msg_id:id\r\n");
            response = reader.readLine();
            if (response.startsWith("MSG")) {
                String message = response.substring(4);
                System.out.println("Contenu du message : " + message);
            } else if (response.equals("ERROR")) {
                System.out.println("Aucun message avec l'identifiant donné.");
            } else {
                System.out.println("Erreur lors de la récupération du message : " + response);
            }

            // Publier un message en réponse à un autre
            writer.println("REPLY author:@user reply_to_id:id\r\nContenu de la réponse\r\n");
            response = reader.readLine();
            if (response.equals("OK")) {
                System.out.println("Réponse publiée avec succès !");
            } else {
                System.out.println("Erreur lors de la publication de la réponse : " + response);
            }

            // Re-publier un message
            writer.println("REPUBLISH author:@user msg_id:id\r\n");
            response = reader.readLine();
            if (response.equals("OK")) {
                System.out.println("Message republié avec succès !");
            } else {
                System.out.println("Erreur lors de la republication du message : " + response);
            }

            // Ferme la connexion
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
