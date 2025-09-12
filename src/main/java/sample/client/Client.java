package sample.client;

import sample.Config.DatabaseConfig;
import sample.domain.User;
import sample.proto.UserRepo;

import java.net.*;
import java.io.*;
import java.util.Scanner;

public class Client {
    private static int port = 8888;
    private static String host = "localhost"; //Eller ændr til routerens ip
    private static Scanner input = new Scanner(System.in);

    public static void main(String[] args){
        try (Socket socket = new Socket(host, port);
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        PrintWriter out = new PrintWriter(socket.getOutputStream(), true)){
            Thread readerThread = new Thread(() -> {
                try {
                   // System.out.println("Skriv username og password");
                    String serverMessage;
                    while ((serverMessage = in.readLine()) != null) {
                        System.out.println(serverMessage);
                    }
                } catch (IOException e) {
                    System.out.println("Fejl i besked: " + e.getMessage());
                }
            });
            readerThread.start();
            while (true){
                String chatMessage = input.nextLine();
                out.println(chatMessage);
            }

        } catch (IOException e) {
            System.out.println("Fejl. Kan ikke forbinde til server: " + e.getMessage());
        }
    }
}
