package sample.client;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import sample.proto.MessageDTO;
import sample.domain.ChatType;
import sample.service.ClientFileService;

import java.net.*;
import java.io.*;
import java.util.Scanner;

public class Client {
    private int port = 8888;
    private String host = "localhost"; //Eller ændr til serverens ip
    private Scanner input = new Scanner(System.in);
    private final Gson gson = new Gson();
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private String pendingFileName;
    private long pendingFileSize;
    private final ClientFileService fileService = new ClientFileService();

    public void start(){
        try {
            new File("files").mkdirs();
            socket = new Socket(host, port);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
            boolean loggedIn = false;
            String username = "";
            while (!loggedIn) {
                System.out.println("Indtast brugernavn");
                username = input.nextLine();
                out.println(username);
                System.out.println("Indtast kodeord");
                String password = input.nextLine();
                out.println(password);
                String response = "";
                try {
                    response = in.readLine();
                } catch (IOException e) {
                    System.out.println("Fejl ved login: " + e.getMessage());
                }
                if ("loggedIn".equals(response)){
                    loggedIn = true;
                    System.out.println("Brugernavn accepteret");
                }
                else {
                    System.out.println("Prøv igen");
                }
            }

            Thread readerThread = new Thread(() -> {
                try {
                    // System.out.println("Skriv username og password");
                    String serverMessage;
                    while ((serverMessage = in.readLine()) != null) {
                        // --- 1) Prøv JSON-parse for FILE_PORT ---
                        try {
                            MessageDTO msg = gson.fromJson(serverMessage, MessageDTO.class);
                            if (msg.chatType() == ChatType.FILE_PORT) {
                                // payload: "<port>|<rolle>" eller "<port>|DOWNLOAD|<fileName>|<fileSize>"
                                fileService.fileTransfer(msg, pendingFileName, pendingFileSize, host);
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
                            fileService.receiveFile("received_files/" + pendingFileName, pendingFileSize);
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

    public static void main(String[] args){
        Client client = new Client();
        client.start();
//        try {
//            new File("files").mkdirs();
//            socket = new Socket(host, port);
//            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
//            out = new PrintWriter(socket.getOutputStream(), true);
//            boolean loggedIn = false;
//            String username = "";
//            while (!loggedIn) {
//                System.out.println("Indtast brugernavn");
//                username = input.nextLine();
//                out.println(username);
//                System.out.println("Indtast kodeord");
//                String password = input.nextLine();
//                out.println(password);
//                String response = "";
//                try {
//                    response = in.readLine();
//                } catch (IOException e) {
//                    System.out.println("Fejl ved login: " + e.getMessage());
//                }
//                if ("loggedIn".equals(response)){
//                    loggedIn = true;
//                    System.out.println("Brugernavn accepteret");
//                }
//                else {
//                    System.out.println("Prøv igen");
//                }
//            }
//
//            Thread readerThread = new Thread(() -> {
//                try {
//                    // System.out.println("Skriv username og password");
//                    String serverMessage;
//                    while ((serverMessage = in.readLine()) != null) {
//                        // --- 1) Prøv JSON-parse for FILE_PORT ---
//                        try {
//                            MessageDTO msg = gson.fromJson(serverMessage, MessageDTO.class);
//                            if (msg.chatType() == ChatType.FILE_PORT) {
//                                // payload: "<port>|<rolle>" eller "<port>|DOWNLOAD|<fileName>|<fileSize>"
//                                fileService.fileTransfer(msg, pendingFileName, pendingFileSize, host);
//                                continue;
//                            }
//
//                        } catch (JsonSyntaxException ignored) {
//                            // ikke JSON → fortsæt til tekst-flow
//                        }
//
//                        // --- 2) Almindelig tekst-flow ---
//                        System.out.println(serverMessage);
//
//                        if (serverMessage.contains("vil sende dig filen")) {
//                            String[] parts = serverMessage.split("'");
//                            if (parts.length >= 2) {
//                                pendingFileName = parts[1];
//                                String sizePart = serverMessage.split("Størrelse: ")[1].split(" ")[0];
//                                pendingFileSize = Long.parseLong(sizePart);
//                            }
//                        }
//
//                        // Fjernet: sendFile(...) ved "har accepteret din fil"
//                        // if (serverMessage.contains("har accepteret din fil")) {
//                        //     sendFile("files/" + pendingFileName);
//                        // }
//
//                        if (serverMessage.startsWith("Filoverførsel starter")) {
//                            fileService.receiveFile("received_files/" + pendingFileName, pendingFileSize);
//                        }
//                    }
//
//                } catch (IOException e) {
//                    System.out.println("Fejl i besked: " + e.getMessage());
//                }
//            });
//            readerThread.start();
//            while (true) {
//                String rawInput = input.nextLine();
//                if (rawInput.startsWith("/")) {
//                    String[] parts = rawInput.trim().split("\\s+", 3);
//                    ChatType command = ChatType.valueOf(parts[0].substring(1));
//                    if (command == ChatType.PRIVATE) {
//                        if (parts.length < 3) {
//                            System.out.println("Brug: /PRIVATE <modtager> <besked>");
//                            continue;
//                        }
//                        String recipient = parts[1];
//                        String payload = parts[2];
//                        MessageDTO message = new MessageDTO(username, command, payload, null, recipient);
//                        String json = gson.toJson(message);
//                        out.println(json);
//                    } else if (command == ChatType.FILE_OFFER) {
//                        if (parts.length < 3) {
//                            System.out.println("Brug: /FILE_OFFER <modtager> <filen>");
//                            continue;
//                        }
//                        String recipient = parts[1];
//                        String filePath = "files/" + parts[2];
//                        File file = new File(filePath);
//                        if (!file.exists()) {
//                            System.out.println("Filen eksisterer ikke: " + filePath);
//                            continue;
//                        }
//
//                        pendingFileName = file.getName();
//                        pendingFileSize = file.length();
//                        String metadata = username + "|" + System.currentTimeMillis() + "|FILE_OFFER|" + pendingFileName + "|" + pendingFileSize;
//                        MessageDTO offerMessage = new MessageDTO(username, ChatType.FILE_OFFER, metadata, null, recipient);
//                        out.println(gson.toJson(offerMessage));
//                    } else if (command == ChatType.FILE_ACCEPT || command == ChatType.FILE_REJECT) {
//                        MessageDTO response = new MessageDTO(username, command, command.name(), null, null);
//                        out.println(gson.toJson(response));
//                    } else {
//                        String payload = parts.length > 1 ? parts[1] : "";
//                        MessageDTO message = new MessageDTO(username, command, payload, null, null);
//                        String json = gson.toJson(message);
//                        out.println(json);
//                    }
//                } else {
//                    MessageDTO message = new MessageDTO(username, ChatType.TEXT, rawInput, null, null);
//                    out.println(gson.toJson(message));
//                }
//
//            }
//
//        } catch (IOException e){
//            System.out.println("Fejl. Kan ikke forbinde til server: " + e.getMessage());
//        }
    }

//    private static void fileTransfer(MessageDTO msg) {
//        String[] parts = msg.payload().split("\\|");
//        int    filePort = Integer.parseInt(parts[0]);
//        String role     = parts[1];
//
//        if (role.equals("UPLOAD")) {
//            // Afsender skal uploade til serveren
//            new Thread(() ->
//                    sendFileOnSeparateSocket(
//                            "files/" + pendingFileName,
//                            host,
//                            filePort
//                    )
//            ).start();
//
//        } else if (role.equals("DOWNLOAD")) {
//            // Modtager skal downloade fra serveren
//            String fileName = parts[2];
//            long   fileSize = Long.parseLong(parts[3]);
//
//            new Thread(() ->
//                    receiveFileOnSeparateSocket(
//                            "received_files/" + fileName,
//                            host,
//                            filePort,
//                            fileSize
//                    )
//            ).start();
//        }
//    }
//
//    private static void receiveFile(String savePath, long fileSize){
//        File file = new File(savePath);
//        file.getParentFile().mkdirs();
//        try (BufferedInputStream inputStream = new BufferedInputStream(socket.getInputStream());
//             FileOutputStream fileOutputStream = new FileOutputStream(file)){
//            byte[] buffer = new byte[4096];
//            long totalRead = 0;
//            while (totalRead < fileSize) {
//                int bytesToRead = (int) Math.min(fileSize - totalRead, buffer.length);
//                int bytesRead = inputStream.read(buffer, 0, bytesToRead);
//                if (bytesRead == -1) {
//                    break;
//                }
//                fileOutputStream.write(buffer, 0, bytesRead);
//                totalRead += bytesRead;
//            }
//            System.out.println("Fil gemt som: " +  savePath);
//        } catch (IOException e) {
//            System.out.println("Fejl ved modtagelse af fil: " + e.getMessage());
//        }
//    }
//    private static void sendFileOnSeparateSocket(String filePath, String host, int port) {
//        File file = new File(filePath);
//        System.out.println("Sender fil via separat socket " + port + ": " + file.getName());
//        try (
//                Socket fs = new Socket(host, port);
//                BufferedOutputStream bos = new BufferedOutputStream(fs.getOutputStream());
//                BufferedInputStream  bis = new BufferedInputStream(new FileInputStream(file))
//        ) {
//            byte[] buffer = new byte[4096];
//            int    r;
//            while ((r = bis.read(buffer)) != -1) {
//                bos.write(buffer, 0, r);
//            }
//            bos.flush();
//            System.out.println("Fil sendt: " + file.getName());
//        } catch (IOException e) {
//            System.out.println("Fejl ved filtransfer på port " + port + ": " + e.getMessage());
//        }
//    }
//    private static void receiveFileOnSeparateSocket(String savePath,
//                                                    String host,
//                                                    int port,
//                                                    long fileSize) {
//        try {
//            Thread.sleep(1000); // 1 sekund forsinkelse
//        } catch (InterruptedException ignored) {}
//
//        try (
//                Socket sock = new Socket(host, port);
//                BufferedInputStream bis = new BufferedInputStream(sock.getInputStream());
//                FileOutputStream     fos = new FileOutputStream(savePath)
//        ) {
//            System.out.println("Downloader fil fra server…");
//            byte[] buffer = new byte[4096];
//            long   total  = 0;
//
//            while (total < fileSize) {
//                int toRead = (int) Math.min(buffer.length, fileSize - total);
//                int read   = bis.read(buffer, 0, toRead);
//                if (read < 0) break;
//                fos.write(buffer, 0, read);
//                total += read;
//            }
//            System.out.println("Fil gemt som: " + savePath);
//
//        } catch (IOException e) {
//            System.err.println("Fejl ved download: " + e.getMessage());
//        }
//    }

}