package sample.client;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import sample.proto.MessageDTO;
import sample.domain.ChatType;

import java.net.*;
import java.io.*;
import java.util.Scanner;

public class Client {
    private static int port = 8888;
    private static String host = "localhost"; //Eller ændr til routerens ip
    private static Scanner input = new Scanner(System.in);
    private static final Gson gson = new Gson();
    private static Socket socket;
    private static PrintWriter out;
    private static BufferedReader in;
    private static String pendingFileName;
    private static long pendingFileSize;

    public static void main(String[] args){
        try {
            new File("files").mkdirs();
            socket = new Socket(host, port);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
                System.out.println("Indtast brugernavn");
                String username = input.nextLine();
                out.println(username);
                System.out.println("Indtast kodeord");
                String password = input.nextLine();
                out.println(password);
                Thread readerThread = new Thread(() -> {
                    try {
                        // System.out.println("Skriv username og password");
                        String serverMessage;
                        while ((serverMessage = in.readLine()) != null) {
                            // --- 1) Prøv JSON-parse for FILE_PORT ---
                            try {
                                MessageDTO msg = gson.fromJson(serverMessage, MessageDTO.class);
                                if (msg.chatType() == ChatType.FILE_PORT) {
                                    int filePort = Integer.parseInt(msg.payload());
                                    System.out.println("Modtaget fil-port: " + filePort);
                                    new Thread(() ->
                                            sendFileOnSeparateSocket("files/" + pendingFileName, host, filePort)
                                    ).start();
                                    continue;
                                }
                            } catch (JsonSyntaxException ignored) {
                                // ikke JSON → fortsæt til tekst-flow
                            }

                            // --- 2) Almindelig tekst-flow ---
                            System.out.println(serverMessage);

                            if (serverMessage.contains("vil sende dig filen")) {
                                String[] parts = serverMessage.split("'");
                                if (parts.length >= 2) {
                                    pendingFileName = parts[1];
                                    String sizePart = serverMessage.split("Størrelse: ")[1].split(" ")[0];
                                    pendingFileSize = Long.parseLong(sizePart);
                                }
                            }

                            // Fjernet: sendFile(...) ved "har accepteret din fil"
                            // if (serverMessage.contains("har accepteret din fil")) {
                            //     sendFile("files/" + pendingFileName);
                            // }

                            if (serverMessage.startsWith("Filoverførsel starter")) {
                                receiveFile("received_files/" + pendingFileName, pendingFileSize);
                            }
                        }

                    } catch (IOException e) {
                        System.out.println("Fejl i besked: " + e.getMessage());
                    }
                });
                readerThread.start();
                while (true) {
                    String rawInput = input.nextLine();
                    if (rawInput.startsWith("/")) {
                        String[] parts = rawInput.trim().split("\\s+", 3);
                        ChatType command = ChatType.valueOf(parts[0].substring(1));
                        if (command == ChatType.PRIVATE) {
                            if (parts.length < 3) {
                                System.out.println("Brug: /PRIVATE <modtager> <besked>");
                                continue;
                            }
                            String recipient = parts[1];
                            String payload = parts[2];
                            MessageDTO message = new MessageDTO(username, command, payload, null, recipient);
                            String json = gson.toJson(message);
                            out.println(json);
                        } else if (command == ChatType.FILE_OFFER) {
                            if (parts.length < 3) {
                                System.out.println("Brug: /FILE_OFFER <modtager> <filen>");
                                continue;
                            }
                            String recipient = parts[1];
                            String filePath = "files/" + parts[2];
                            File file = new File(filePath);
                            if (!file.exists()) {
                                System.out.println("Filen eksisterer ikke: " + filePath);
                                continue;
                            }

                            pendingFileName = file.getName();
                            pendingFileSize = file.length();
                            String metadata = username + "|" + System.currentTimeMillis() + "|FILE_OFFER|" + pendingFileName + "|" + pendingFileSize;
                            MessageDTO offerMessage = new MessageDTO(username, ChatType.FILE_OFFER, metadata, null, recipient);
                            out.println(gson.toJson(offerMessage));
                        } else if (command == ChatType.FILE_ACCEPT || command == ChatType.FILE_REJECT) {
                            MessageDTO response = new MessageDTO(username, command, command.name(), null, null);
                            out.println(gson.toJson(response));
                        } else {
                            String payload = parts.length > 1 ? parts[1] : "";
                            MessageDTO message = new MessageDTO(username, command, payload, null, null);
                            String json = gson.toJson(message);
                            out.println(json);
                        }
                    } else {
                        MessageDTO message = new MessageDTO(username, ChatType.TEXT, rawInput, null, null);
                        out.println(gson.toJson(message));
                    }

                }

        } catch (IOException e){
                System.out.println("Fejl. Kan ikke forbinde til server: " + e.getMessage());
        }
    }
    private static void sendFile(String filePath) {
        File file = new File(filePath);
        try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file));
        DataOutputStream dataOutputStream = new DataOutputStream(socket.getOutputStream())
        ){
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = bis.read(buffer)) != -1) {
                dataOutputStream.write(buffer, 0, bytesRead);
            }
            dataOutputStream.flush();
            System.out.println("Fil sendt: " +  file.getName());
        } catch (IOException e){
            System.out.println("Fejl ved afsendelse af fil: " + e.getMessage());
        }
    }
    private static void receiveFile(String savePath, long fileSize){
        File file = new File(savePath);
        file.getParentFile().mkdirs();
        try (BufferedInputStream inputStream = new BufferedInputStream(socket.getInputStream());
        FileOutputStream fileOutputStream = new FileOutputStream(file)){
            byte[] buffer = new byte[1024];
            long totalRead = 0;
            while (totalRead < fileSize) {
                int bytesToRead = (int) Math.min(fileSize - totalRead, buffer.length);
                int bytesRead = inputStream.read(buffer, 0, bytesToRead);
                if (bytesRead == -1) {
                    break;
                }
                fileOutputStream.write(buffer, 0, bytesRead);
                totalRead += bytesRead;
            }
            System.out.println("Fil gemt som: " +  savePath);
        } catch (IOException e) {
            System.out.println("Fejl ved modtagelse af fil: " + e.getMessage());
        }
    }
    private static void sendFileOnSeparateSocket(String filePath, String host, int port) {
        File file = new File(filePath);
        System.out.println("Sender fil via separat socket " + port + ": " + file.getName());
        try (
                Socket fs = new Socket(host, port);
                BufferedOutputStream bos = new BufferedOutputStream(fs.getOutputStream());
                BufferedInputStream  bis = new BufferedInputStream(new FileInputStream(file))
        ) {
            byte[] buffer = new byte[4096];
            int    r;
            while ((r = bis.read(buffer)) != -1) {
                bos.write(buffer, 0, r);
            }
            bos.flush();
            System.out.println("Fil sendt: " + file.getName());
        } catch (IOException e) {
            System.out.println("Fejl ved filtransfer på port " + port + ": " + e.getMessage());
        }
    }


}
