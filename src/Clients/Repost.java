package Clients;

import Server.Server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.sql.SQLException;
import java.util.Scanner;

public class Repost extends Client{
    public Repost() throws SQLException, ClassNotFoundException {}

    public void reply() throws IOException, SQLException, ClassNotFoundException {
        new Publisher().publish();
    }

    public void republish() throws SQLException, ClassNotFoundException, IOException {
        new Follower().recevoir();
    }

    //mode request-response
    public static void main(String args[]) throws IOException, SQLException, ClassNotFoundException {
        Socket s = new Socket(Server.SERVER, Server.PORT);
        System.out.print("Enter username: ");
        Scanner scanner = new Scanner(System.in);
        String pseudo = scanner.nextLine();

        BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
        PrintWriter out = new PrintWriter(s.getOutputStream(), true);
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        //sending request to server
        String header = pseudo;
        out.println(header);

        //Wait for a response from the server
        String response = in.readLine();
        System.out.println(">> " + response);

        if(response.equals("OK")){
            System.out.println("Enter REPLY reply_to_id:id || REPUBLISH msg_id:id to begin:");
            String cmd = br.readLine();
            if (cmd.startsWith("REPLY")) {
                header = "REPLY author:" + pseudo + " reply_to_id:" + cmd.substring(cmd.indexOf(":") + 1) ;
                out.println(header);
                System.out.println("Enter your messages (type '$' to stop) : ");
                String body = "", reponse = "";
                do {
                    body = br.readLine();
                    out.println(body);
                    out.flush();
                } while (!body.equals("$"));
                reponse = in.readLine();
                System.out.println(">>" + reponse);
            }
            if (cmd.startsWith("REPUBLISH")) {
                header = "REPUBLISH author:" + pseudo + " msg_id:" + cmd.substring(cmd.indexOf(":") + 1) ;
                out.println(header);
                while(true) {
                    String msg;
                    msg = in.readLine();
                    if(msg.equals("$")){
                        break;
                    }
                    System.out.println(msg);
                }
            }
        }

        //closing client socket
        in.close();
        out.close();
        br.close();
        s.close();
    }
}
