package Clients;

import Server.Server;

import java.io.*;
import java.net.Socket;
import java.sql.SQLException;

import java.util.Scanner;

public class Publisher extends Client {

    public Publisher() throws SQLException, ClassNotFoundException {}

    /*----------------------------------------------------------------------------------
     * M. REQUEST-RESPONSE
     * ----------------------------------------------------------------------------------*/
    public void publish() throws IOException, SQLException, ClassNotFoundException {
        System.out.println("Enter your messages (type '$' to stop) : ");
        String body = "", response = "";
        do {
            body = br.readLine();
            out.println(body);
            out.flush();
        } while (!body.equals("$"));
        response = in.readLine();
        System.out.println(">>" + response);
    }

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
            System.out.println("Enter PUBLISH to begin:");
            String cmd = br.readLine();
            if(cmd.equals("PUBLISH")) {
                out.println(cmd);
                header = "PUBLISH author: " + pseudo;
                out.println(header);
                new Publisher().publish();
            }
        }

        //closing client socket
        in.close();
        out.close();
        br.close();
        s.close();
    }
}
