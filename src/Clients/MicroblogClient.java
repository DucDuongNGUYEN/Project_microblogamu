package Clients;

import Database.MicroblogDatabase;
import Server.MicroblogCentral;

import java.io.*;
import java.net.Socket;
import java.sql.SQLException;
import java.util.concurrent.ConcurrentLinkedQueue;

public class MicroblogClient {
    private String pseudo;
    private final Socket socket;
    private Thread messageThread;
    private ConcurrentLinkedQueue<Integer> notifications = new ConcurrentLinkedQueue<>();
    private ConcurrentLinkedQueue<String> subscriptions = new ConcurrentLinkedQueue<>();

    private BufferedReader in;
    private PrintWriter out;
    private final BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

    public MicroblogClient(Socket socket) {
        this.socket = socket;
    }

    public void setPseudo(String pseudo) {
        this.pseudo = pseudo;
    }

    public void update() throws IOException, SQLException, ClassNotFoundException {
        int newMsg = Integer.parseInt(in.readLine());
        int newSub = Integer.parseInt(in.readLine());
        if(newMsg + newSub > 0) {
            getNotifications();
        }
        if(notifications.size() > 0) {
            System.out.print("You have " + notifications.size() + " new messages. Read your messages? Yes/No: ");
            if (br.readLine().equals("Yes")) {
                while (!notifications.isEmpty())
                    MicroblogDatabase.GET_MSG_BY_ID(notifications.poll());
            }
        }
        if(subscriptions.size() > 0) {
            System.out.print("You have " + subscriptions.size() + " new followers. Take a look? Yes/No: ");
            if (br.readLine().equals("Yes")) {
                while (!subscriptions.isEmpty())
                    System.out.println(subscriptions.poll());
            }
        }
    }

    public void getNotifications() throws IOException, SQLException, ClassNotFoundException {
        out.println("UPDATE");
        while (true) {
            String msg_id = in.readLine();
            if(msg_id.isEmpty()) break;
            notifications.add(Integer.parseInt(msg_id));
        }

        while (true) {
            String user = in.readLine();
            if(user.isEmpty()) break;
            subscriptions.add(user);
        }

    }


    public void start () throws IOException, SQLException, ClassNotFoundException {
        System.out.println("Socket: " + socket);

        System.out.print("Enter username: ");
        String pseudo = br.readLine();

        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out = new PrintWriter(socket.getOutputStream(), true);

        //sending request to server
        out.println(pseudo);

        //Wait for admission from the server
        String response = in.readLine();
        System.out.println(">> " + response);

        if (response.equals("OK")) {
            setPseudo(pseudo);
            // Start a new thread to handle incoming messages
            //messageThread = new Thread(new MessageHandler(socket.getInputStream()));
            //messageThread.start();
            while (true) {
                update();
                synchronized (socket.getInputStream()) {
                    System.out.println("Enter the operation you want to perform:");
                    String cmd = br.readLine();

                    // Send the chosen operation to the server and wait for the result
                    out.println(cmd);

                    if (cmd.equals("PUBLISH") || cmd.startsWith("REPLY")) {
                        String header;
                        if (cmd.equals("PUBLISH"))
                            header = "PUBLISH author: " + pseudo;
                        else
                            header = "REPLY author:" + pseudo + " reply_to_id:" + cmd.substring(cmd.indexOf(":") + 1);

                        out.println(header);
                        System.out.println("Enter your messages (type '$' to stop) : ");
                        String body = "";
                        do {
                            body = br.readLine();
                            out.println(body);
                            out.flush();
                        } while (!body.equals("$"));
                        response = in.readLine();
                        System.out.println(">>" + response);
                    } else if (cmd.startsWith("REPUBLISH")) {
                        String header = "REPUBLISH author:" + pseudo + " msg_id:" + cmd.substring(cmd.indexOf(":") + 1);
                        out.println(header);
                        while (true) {
                            String msg;
                            msg = in.readLine();
                            if (msg.equals("$")) {
                                break;
                            }
                            System.out.println(msg);
                        }
                    } else if ((cmd.startsWith("SUBSCRIBE") || cmd.startsWith("UNSUBSCRIBE"))) {
                        String msg = in.readLine();
                        System.out.println(msg);
                    } else if (cmd.equals("EXIT")) break;
                }
            }
        } else {
            MicroblogDatabase.SignUp();
        }
        close();
    }

    public void close () throws IOException {
        messageThread.interrupt();
        in.close();
        out.close();
        br.close();
        socket.close();
    }

    private class MessageHandler implements Runnable {
        private InputStream input;

        public MessageHandler(InputStream input) {
            this.input = input;
        }

        @Override
        public void run() {
            try {
                while (true){
                    String msg;
                    msg = in.readLine();
                    if (msg.equals("$")) {
                        break;
                    }
                    System.out.println(msg);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    public static void main (String[]args) throws IOException, SQLException, ClassNotFoundException {
        MicroblogClient microblogClient = new MicroblogClient(new Socket(MicroblogCentral.SERVER, MicroblogCentral.PORT));
        microblogClient.start();
    }
}

