package Server;

import Handler.ClientHandler;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {
    public final static int PORT = 12345;
    public final static String SERVER = "localhost";

    private ServerSocket serverSocket;

    public Server() throws IOException {
        this.serverSocket = new ServerSocket(PORT);
    }

    public void start() throws IOException {
        ExecutorService executorService = Executors.newWorkStealingPool();

        System.out.println("WELCOME TO MICROBLOGAMU");

        while (true) {
            Socket clientSocket = serverSocket.accept();
            executorService.submit(new ClientHandler(clientSocket));
        }
    }

    public static void main(String[] args) throws Exception {
        Server server = new Server();
        server.start();
    }
}