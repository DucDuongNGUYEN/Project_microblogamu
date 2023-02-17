package Server;

import Database.MicroblogDatabase;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MicroblogCentral {
    public final static int PORT = 12345;
    public final static String SERVER = "localhost";

    private Map<String, ConcurrentLinkedQueue<String>> subscriptions = new ConcurrentHashMap<>();
    private  Map<String, ConcurrentLinkedQueue<Integer>> notifications = new ConcurrentHashMap<>();

    private  ConcurrentHashMap<String, Socket> ONLINE = new ConcurrentHashMap<>();
    private List<Socket> peers = new ArrayList<>();


    private ServerSocket serverSocket;

    private MicroblogCentral() throws IOException {
        this.serverSocket = new ServerSocket(PORT);
    }

    public void start() throws IOException {
        ExecutorService executorService = Executors.newWorkStealingPool();

        System.out.println("WELCOME TO MICROBLOGAMU - SERVER CENTRAL");
        while (true) {
            Socket clientSocket = serverSocket.accept();
            executorService.submit(new ClientHandlerMF(clientSocket,this));
        }

    }

    public static void main(String[] args) throws Exception {
        MicroblogCentral microblogCentral = new MicroblogCentral();
        microblogCentral.start();
    }

    private static class ClientHandlerMF implements Runnable {
        private final Socket clientSocket;
        private final MicroblogCentral microblogCentral;
        private BufferedReader in;
        private PrintWriter out;
        private String user;

        private Set<String> followers;

        public ClientHandlerMF(Socket clientSocket, MicroblogCentral microblogCentral) throws IOException {
            this.clientSocket = clientSocket;
            this.microblogCentral = microblogCentral;
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            out = new PrintWriter(clientSocket.getOutputStream(), true);
        }

        public void update() throws IOException {
            out.println(microblogCentral.notifications.get(user).size());
            out.println(microblogCentral.subscriptions.get(user).size());
        }

        public void pushNotifications() throws IOException {
            while (!microblogCentral.notifications.get(user).isEmpty())
                out.println(microblogCentral.notifications.get(user).poll());
            out.println();
            while (!microblogCentral.subscriptions.get(user).isEmpty())
                out.println(microblogCentral.subscriptions.get(user).poll());
            out.println();
        }

        public void sendNotification(String user, int msg_id) {
            microblogCentral.notifications.putIfAbsent(user, new ConcurrentLinkedQueue<>());
            microblogCentral.notifications.get(user).add(msg_id);
        }

        public void sendNotifications(int msg_id) throws SQLException, ClassNotFoundException {
            //Get all followers of tags included
            Set<String> tag_followers = new HashSet<>();
            for (String tag : MicroblogDatabase.GET_MSG_TAGS(msg_id)) {
                tag_followers.addAll(MicroblogDatabase.GET_TAG_FOLLOWERS(tag));
            }
            //Send notifications to every follower
            followers.addAll(tag_followers);
            for (String follower : followers) {
                sendNotification(follower, msg_id);
            }
        }

        @Override
        public void run() {
            try {
                //reading username and verify user's profile on database
                user = in.readLine();
                if (MicroblogDatabase.Authentification(user)) {

                    synchronized (microblogCentral.ONLINE) {
                        microblogCentral.ONLINE.putIfAbsent(user, clientSocket);
                        microblogCentral.notifications.putIfAbsent(user, new ConcurrentLinkedQueue<>());
                        microblogCentral.subscriptions.putIfAbsent(user, new ConcurrentLinkedQueue<>());
                    }

                    System.out.println("CONNECT TO USER:" + user);
                    out.println("OK");
                    //System.out.println(microblogCentral.ONLINE.values());

                    while (true) {
                        update();
                        String cmd = in.readLine();

                        //list of followers of this user
                        followers = MicroblogDatabase.GET_USER_FOLLOWERS(user);

                        if (cmd.equals("PUBLISH") || cmd.startsWith("REPLY")) {
                            PUBLISH_REPLY_MF();
                        } else if (cmd.startsWith("REPUBLISH")) {
                            REPUBLISH_MF(cmd);
                        } else if (cmd.startsWith("SUBSCRIBE") || cmd.startsWith("UNSUBSCRIBE")) {
                            SUBSCRIPTION(cmd);
                        } else if (cmd.equals("UPDATE")) {
                            pushNotifications();
                        } else if (cmd.equals("SERVERCONNECT")) {
                            //connectToServer();
                        } else if (cmd.equals("EXIT")) break;
                    }
                } else {
                    out.println("Cannot find your account/ Account doesn't exist.");
                }
                in.close();
                out.close();
                clientSocket.close();
            } catch (IOException | SQLException | ClassNotFoundException e) {

            } finally {
                // Remove the client from the map of connected clients
                synchronized (microblogCentral.ONLINE) {
                    microblogCentral.ONLINE.remove(user);
                }
                System.out.println(user + " DISCONNECTED");
                System.out.println();
                Thread.currentThread().interrupt();
            }
        }

        private void PUBLISH_REPLY_MF() throws IOException, SQLException, ClassNotFoundException {
            //read message header
            String header;
            header = in.readLine();
            //read message content from client's IO
            StringBuilder body = new StringBuilder();
            while (true) {
                String message;
                message = in.readLine();
                if (message.equals("$")) break;
                body.append(message);
            }

            String replyToId = null;
            if (header.startsWith("REPLY")) {
                Pattern pattern = Pattern.compile("reply_to_id:\\s*(\\d+)");
                Matcher matcher = pattern.matcher(header);
                if (matcher.find()) {
                    replyToId = matcher.group(1);
                } else {
                    out.println("ERREUR");
                    return;
                }
                MicroblogDatabase.REPLY(user, header, body.toString(), replyToId);
            } else {
                MicroblogDatabase.PUBLISH(user, header, body.toString());
            }

            //add message into database
            int msg_id = MicroblogDatabase.GET_LAST_MSG_ID();

            //print message on server
            System.out.println(header);
            System.out.println(body.toString());
            String formattedDateTime = LocalDateTime.now().
                    format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            System.out.println(formattedDateTime);

            if (replyToId != null)
                sendNotification(MicroblogDatabase.GET_MSG_AUTHOR(Integer.parseInt(replyToId)), msg_id);
            sendNotifications(msg_id);
            out.println("OK");
        }

        private void REPUBLISH_MF(String cmd) throws IOException, SQLException, ClassNotFoundException {
            String header = null;
            header = in.readLine();

            String republishId;
            Pattern pattern = Pattern.compile("msg_id:\\s*(\\d+)");
            Matcher matcher = pattern.matcher(cmd);
            if (matcher.find()) {
                republishId = matcher.group(1);

                String sql = "SELECT * FROM messages WHERE id = ?";
                PreparedStatement stmt = MicroblogDatabase.conn.prepareStatement(sql);
                stmt.setInt(1, Integer.parseInt(republishId));
                ResultSet rs = stmt.executeQuery();

                String body = null;
                while (rs.next()) {
                    body = rs.getString("content");
                    out.println(rs.getInt("id"));
                    out.println(rs.getString("header"));
                    out.println(rs.getString("content"));
                    out.println(rs.getString("timestamp"));
                    out.println();
                }
                MicroblogDatabase.PUBLISH(user, header, body);

                int msg_id = MicroblogDatabase.GET_LAST_MSG_ID();
                sendNotification(MicroblogDatabase.GET_MSG_AUTHOR(Integer.parseInt(republishId)), msg_id);
                sendNotifications(msg_id);
                out.flush();
                out.println(">> OK");
            } else {
                out.println("ERREUR");
            }
        }

        private void SUBSCRIPTION(String cmd) throws IOException, SQLException, ClassNotFoundException {
            Pattern pattern = Pattern.compile("(author|tag):([#@]\\w+)");
            Matcher matcher = pattern.matcher(cmd);

            if (matcher.find()) {
                String type = matcher.group(1);
                String value = matcher.group(2);

                if (cmd.startsWith("SUBSCRIBE")) {
                    if (type.equals("author")) {
                        MicroblogDatabase.SUBSCRIBE_USER(MicroblogDatabase.GET_USER_ID(user), MicroblogDatabase.GET_USER_ID(value));
                        microblogCentral.subscriptions.putIfAbsent(value, new ConcurrentLinkedQueue<>());
                        microblogCentral.subscriptions.get(value).add(user);
                        out.println(">> OK");
                    } else if (type.equals("tag")) {
                        MicroblogDatabase.SUBSCRIBE_TAG(MicroblogDatabase.GET_USER_ID(user), value);
                        out.println(">> OK");
                    } else {
                        out.println("ERREUR");
                    }
                } else if (cmd.startsWith("UNSUBSCRIBE")) {
                    if (type.equals("author")) {
                        MicroblogDatabase.UNSUBSCRIBE_USER(MicroblogDatabase.GET_USER_ID(user), MicroblogDatabase.GET_USER_ID(value));
                        out.println(">> OK");
                    } else if (type.equals("tag")) {
                        MicroblogDatabase.UNSUBSCRIBE_TAG(MicroblogDatabase.GET_USER_ID(user), value);
                        out.println(">> OK");
                    } else {
                        out.println("ERREUR");
                    }
                }

            } else {
                // Handle invalid subscription command
                out.println("Invalid subscription command: " + cmd);
            }

        }

        private void connectToServer(String hostName, int portNumber) {
            try {
                Socket socket = new Socket(hostName, portNumber);
                microblogCentral.peers.add(socket);
                new Thread(() -> {
                    try {
                        handlePeerConnection(socket);
                    } catch (SQLException | ClassNotFoundException throwables) {
                        throwables.printStackTrace();
                    }
                }).start();
            } catch (IOException e) {
                System.out.println("Error connecting to peer: " + e.getMessage());
            }
        }

        private void handlePeerConnection(Socket socket) throws SQLException, ClassNotFoundException {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
                out.println("SERVERCONNECT " + InetAddress.getLocalHost().getHostAddress() + " " + PORT);
                String line;
                while ((line = in.readLine()) != null) {
                    update();
                    String cmd = in.readLine();

                    //list of followers of this user
                    followers = MicroblogDatabase.GET_USER_FOLLOWERS(user);

                    if (cmd.equals("PUBLISH") || cmd.startsWith("REPLY")) {
                        PUBLISH_REPLY_MF();
                    } else if (cmd.startsWith("REPUBLISH")) {
                        REPUBLISH_MF(cmd);
                    } else if (cmd.startsWith("SUBSCRIBE") || cmd.startsWith("UNSUBSCRIBE")) {
                        SUBSCRIPTION(cmd);
                    } else if (cmd.equals("UPDATE")) {
                        pushNotifications();
                    } else if (cmd.equals("SERVERCONNECT")) {
                        //connectToServer();
                    } else if (cmd.equals("EXIT")) break;
                }
            } catch (IOException e) {
                System.out.println("Error handling peer connection: " + e.getMessage());
            } finally {
                try {
                    socket.close();
                    microblogCentral.peers.remove(socket);
                } catch (IOException e) {
                    System.out.println("Error closing socket: " + e.getMessage());
                }
            }
        }

        private void connectToPeers() {
            try (BufferedReader reader = new BufferedReader(new FileReader("pairs.cfg"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] tokens = line.split(" ");
                    if (tokens[0].equals("master")) {
                        connectToServer(tokens[1], Integer.parseInt(tokens[2]));
                    } else if (tokens[0].equals("peer")) {
                        connectToServer(tokens[1], Integer.parseInt(tokens[2]));
                    }
                }
            } catch (IOException e) {
                System.out.println("Error reading pairs.cfg file: " + e.getMessage());
            }
        }
    }
}
