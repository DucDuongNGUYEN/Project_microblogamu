package Clients;

import Server.Server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.sql.SQLException;
import java.util.Scanner;

public class Follower extends Client{
    public Follower() throws SQLException, ClassNotFoundException {}

    public void recevoir() throws IOException {
        while(true) {
            String msg;
            msg = in.readLine();
            if(msg.equals("$")){
                break;
            }
            System.out.println(msg);
        }
    }

    public void subscribe() throws IOException {
        String msg;
        msg = in.readLine();
        System.out.println(">> " + msg);
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
            System.out.println("Enter RCV_IDS [author:@user] [tag:#tag] [since_id:id] [limit:n] || RCV_MSG msg_id:id " +
                    "|| (UN)SUBSCRIBE author:@user || (UN)SUBSCRIBE tag:#tag to begin:");
            String cmd = br.readLine();
            out.println(cmd);
            if(cmd.startsWith("SUBSCRIBE") || cmd.startsWith("UNSUBSCRIBE")){
                String msg = in.readLine();
                System.out.println(msg);
            } else {
                while (true) {
                    String msg;
                    msg = in.readLine();
                    if (msg.equals("$")) {
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
