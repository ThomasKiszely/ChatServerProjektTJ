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
    private final int port = 8888;
    private final List<PrintWriter> gameClients = Collections.synchronizedList(new ArrayList<>());
    private final List<PrintWriter> chattingClients = Collections.synchronizedList(new ArrayList<>());
    private final List<PrintWriter> musicClients = Collections.synchronizedList(new ArrayList<>());
    private final Map<User, PrintWriter> userMap = Collections.synchronizedMap(new HashMap<>());

    private final Map<String, FileOffer> pendingFiles = Collections.synchronizedMap(new HashMap<>());

    public int getPort() {
        return port;
    }
    public List<PrintWriter> getGameClients() {
        return gameClients;
    }
    public List<PrintWriter> getChattingClients() {
        return chattingClients;
    }
    public List<PrintWriter> getMusicClients() {
        return musicClients;
    }
    public Map<User, PrintWriter> getUserMap() {
        return userMap;
    }
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
                    pool.submit(new ChatClienthandler(clientSocket, this.getPendingFiles(), this.getUserMap(),this.getGameClients(), this.getChattingClients(), this.getMusicClients()));

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
