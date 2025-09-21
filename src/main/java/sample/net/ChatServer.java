package sample.net;

import sample.domain.FileOffer;
import sample.domain.User;

import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ChatServer {
    private static final int port = 8888;
    public static final List<PrintWriter> gameClients = Collections.synchronizedList(new ArrayList<>());
    public static final List<PrintWriter> chattingClients = Collections.synchronizedList(new ArrayList<>());
    public static final List<PrintWriter> musicClients = Collections.synchronizedList(new ArrayList<>());
    public static final Map<User, PrintWriter> userMap = Collections.synchronizedMap(new HashMap<>());

    private final Map<String, FileOffer> pendingFiles = Collections.synchronizedMap(new HashMap<>());

    public Map<String, FileOffer> getPendingFiles() {
        return pendingFiles;
    }

    public void start() {
        BlockingQueue<Runnable> queue = new ArrayBlockingQueue<>(100);
        ThreadPoolExecutor pool = new ThreadPoolExecutor(5, 5, 0, TimeUnit.SECONDS, queue);

        try (ServerSocket serverSocket = new ServerSocket(port)){
            System.out.println("Server listening on port " + port);

            while(true){
                try {
                    Socket clientSocket = serverSocket.accept();
                    pool.submit(new ChatClienthandler(clientSocket, this.getPendingFiles()));

                } catch (IOException e) {
                    System.out.println("Fejl med klientforbindelsen" + e.getMessage());
                }
            }

        } catch (IOException e){
            System.out.println("Fejl på serveren");
        }

    }


    public static void main(String[] args) {
        ChatServer chatServer = new ChatServer();
        chatServer.start();
    }
}
