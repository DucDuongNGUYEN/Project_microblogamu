package Clients;

import Database.MicroblogDatabase;
import Server.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.sql.SQLException;

public class Client {
    protected String pseudo;

    protected BufferedReader in;
    protected PrintWriter out;
    protected BufferedReader br  = new BufferedReader(new InputStreamReader(System.in));

    public Client() throws SQLException, ClassNotFoundException {}

    public void start () throws IOException, SQLException, ClassNotFoundException{
        Socket s = new Socket(Server.SERVER, Server.PORT);
        System.out.print("Enter username: ");
        pseudo = br.readLine();

        in = new BufferedReader(new InputStreamReader(s.getInputStream()));
        out = new PrintWriter(s.getOutputStream(), true);

        //sending request to server
        String header = pseudo;
        out.println(header);

        //Wait for a response from the server
        String reponse = in.readLine();
        System.out.println(">> " + reponse);

        // If the authentication was successful, display the available operations and prompt the user to choose one
        if(reponse.equals("OK")) {
            while (true) {
                //System.out.println(in.readLine());
                System.out.println("Enter the operation you want to perform:");
                // Prompt the user to choose an operation
                String choice = br.readLine();

                // Send the chosen operation to the server and wait for the result
                out.println(choice);

                if (choice.equals("PUBLISH")) {
                    Publisher publisher = new Publisher();
                    header = "PUBLISH author: " + pseudo;
                    out.println(header);
                    publisher.publish();
                }
                if (choice.startsWith("RCV_IDS") ||choice.startsWith("RCV_MSG")) {
                    new Follower().recevoir();
                }
                if (choice.startsWith("REPLY")) {
                    Repost repost = new Repost();
                    header = "REPLY author:" + pseudo + " reply_to_id:" + choice.substring(choice.indexOf(":") + 1) ;
                    out.println(header);
                    repost.reply();
                }
                if (choice.startsWith("REPUBLISH")) {
                    Repost repost = new Repost();
                    header = "REPUBLISH author:" + pseudo + " msg_id:" + choice.substring(choice.indexOf(":") + 1) ;
                    out.println(header);
                    repost.republish();
                }
                if (choice.startsWith("SUBSCRIBE") || choice.startsWith("UNSUBSCRIBE")){
                    new Follower().subscribe();
                }
            }
        } else { MicroblogDatabase.SignUp();}
        in.close();
        out.close();
        br.close();
        s.close();
    }

    public static void main(String[] args) throws IOException, SQLException, ClassNotFoundException {
        Client client = new Client();
        client.start();
    }

}
