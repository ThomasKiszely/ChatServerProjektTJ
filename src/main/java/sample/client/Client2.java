package sample.client;

import com.google.gson.Gson;
import sample.proto.MessageDTO;
import sample.domain.ChatType;

import java.net.*;
import java.io.*;
import java.util.Scanner;

public class Client2 {
    private static int port = 8888;
    private static String host = "localhost";
    private static Scanner input = new Scanner(System.in);
    private static final Gson gson = new Gson();
    private static String username;
    private static Socket socket;
    private static PrintWriter out;
    private static BufferedReader in;

    public static void main(String[] args) {
        try {
            socket = new Socket(host, port);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            System.out.println("Indtast brugernavn:");
            username = input.nextLine();
            out.println(username);

            System.out.println("Indtast kodeord:");
            String password = input.nextLine();
            out.println(password);

            Thread readerThread = new Thread(() -> {
                try {
                    String serverMessage;
                    while ((serverMessage = in.readLine()) != null) {
                        System.out.println(serverMessage);

                        // Hvis serveren siger ACCEPT, send filen
                        if (serverMessage.contains("har accepteret din fil")) {
                            String[] parts = serverMessage.split(" ");
                            String fileName = parts[parts.length - 1];
                            sendFile("files/" + fileName); // Justér sti efter behov
                        }

                        // Hvis serveren siger "Filoverførsel starter", modtag filen
                        if (serverMessage.contains("Filoverførsel starter")) {
                            receiveFile("received_files/modtaget_fil.dat", 102400); // Justér navn og størrelse
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
                        out.println(gson.toJson(message));

                    } else if (command == ChatType.FILE_OFFER) {
                        if (parts.length < 3) {
                            System.out.println("Brug: /FILE_OFFER <modtager> <sti_til_fil>");
                            continue;
                        }
                        String recipient = parts[1];
                        String filePath = parts[2];
                        File file = new File(filePath);
                        if (!file.exists()) {
                            System.out.println("Filen findes ikke: " + filePath);
                            continue;
                        }
                        String fileName = file.getName();
                        long fileSize = file.length();
                        String metadata = username + "|" + System.currentTimeMillis() + "|FILE_OFFER|" + fileName + "|" + fileSize;
                        MessageDTO offerMessage = new MessageDTO(username, ChatType.FILE_OFFER, metadata, null, recipient);
                        out.println(gson.toJson(offerMessage));

                    } else if (command == ChatType.FILE_ACCEPT || command == ChatType.FILE_REJECT) {
                        MessageDTO response = new MessageDTO(username, command, command.name(), null, null);
                        out.println(gson.toJson(response));

                    } else {
                        String payload = parts.length > 1 ? parts[1] : "";
                        MessageDTO message = new MessageDTO(username, command, payload, null, null);
                        out.println(gson.toJson(message));
                    }

                } else {
                    MessageDTO message = new MessageDTO(username, ChatType.TEXT, rawInput, null, null);
                    out.println(gson.toJson(message));
                }
            }

        } catch (IOException e) {
            System.out.println("Fejl. Kan ikke forbinde til server: " + e.getMessage());
        }
    }

    private static void sendFile(String filePath) {
        File file = new File(filePath);
        try (
                BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file));
                DataOutputStream dos = new DataOutputStream(socket.getOutputStream())
        ) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = bis.read(buffer)) != -1) {
                dos.write(buffer, 0, bytesRead);
            }
            dos.flush();
            System.out.println("Fil sendt: " + file.getName());
        } catch (IOException e) {
            System.out.println("Fejl ved afsendelse af fil: " + e.getMessage());
        }
    }

    private static void receiveFile(String savePath, long fileSize) {
        try (
                BufferedInputStream bis = new BufferedInputStream(socket.getInputStream());
                FileOutputStream fos = new FileOutputStream(savePath)
        ) {
            byte[] buffer = new byte[4096];
            long totalRead = 0;
            while (totalRead < fileSize) {
                int bytesToRead = (int) Math.min(buffer.length, fileSize - totalRead);
                int bytesRead = bis.read(buffer, 0, bytesToRead);
                if (bytesRead == -1) break;
                fos.write(buffer, 0, bytesRead);
                totalRead += bytesRead;
            }
            System.out.println("Fil modtaget og gemt som: " + savePath);
        } catch (IOException e) {
            System.out.println("Fejl ved modtagelse af fil: " + e.getMessage());
        }
    }
}
