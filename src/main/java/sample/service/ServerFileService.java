package sample.service;

import com.google.gson.Gson;
import sample.domain.ChatType;
import sample.domain.FileOffer;
import sample.domain.Message;
import sample.domain.User;
import sample.net.ChatClienthandler;
import sample.net.MessageSender;
import sample.proto.MessageDTO;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;



public class ServerFileService {
//    private Socket socket;
    private final MessageSender messageSender;
    private final Map<String, FileOffer> pendingFiles;

    public ServerFileService(MessageSender messageSender, Map<String, FileOffer> pendingFiles) {
        this.messageSender = messageSender;
        this.pendingFiles = pendingFiles;
    }

//    public void fileTransfer(MessageDTO msg, String pendingFileName, long pendingFileSize, String host) {
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
//    public void receiveFile(String savePath, long fileSize){
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
//    private void sendFileOnSeparateSocket(String filePath, String host, int port) {
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
//    private void receiveFileOnSeparateSocket(String savePath,
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
    public String[] offerFile(Message message, String[] parts) {
        String sender = parts[0];
        String timestamp = parts[1];
        String filename = parts[3];
        long fileSize = Long.parseLong(parts[4]);
        String recipient = message.recipient();

        pendingFiles.put(recipient, new FileOffer(sender, recipient, filename, fileSize));
        String returnMessage = (sender + " vil sende dig filen '" + filename + "' (Størrelse: " + fileSize + " bytes. Svar /FILE_ACCEPT eller /FILE_REJECT.");
        return new String[]{returnMessage, recipient};
    }

    public void acceptFile(User user, PrintWriter out) {
        FileOffer offer = pendingFiles.remove(user.getUsername());
        if (offer == null) {
            out.println("Ingen ventende filer");
            return;
        }

        int filePort;
        try (ServerSocket tmp = new ServerSocket(0)) {
            filePort = tmp.getLocalPort();
        } catch (IOException e) {
            out.println("Kunne ikke allokere port til filtransfer");
            e.printStackTrace();
            return;
        }


        // Nu er filePort garanteret initialiseret
        MessageDTO toSender = getDtoToSender(filePort, offer);
        MessageDTO toReceiver = getDtoToReceiver(filePort, offer);
        messageSender.unicast(new Gson().toJson(toSender), offer.sender);
        messageSender.unicast(new Gson().toJson(toReceiver), offer.recipient);

        new Thread(() -> {
            try (ServerSocket srv = new ServerSocket(filePort)) {
                // Upload fra afsender
                try (
                        Socket uploadSock = srv.accept();
                        BufferedInputStream bis = new BufferedInputStream(uploadSock.getInputStream());
                        FileOutputStream fos = new FileOutputStream("server_files/" + offer.fileName)
                ) {
                    byte[] buf = new byte[4096];
                    long total = 0;
                    int r;
                    while ((r = bis.read(buf)) != -1 && total < offer.fileSize) {
                        fos.write(buf, 0, r);
                        total += r;
                    }
                }

                // Download til modtager
                try (
                        Socket downloadSock = srv.accept();
                        BufferedOutputStream bos = new BufferedOutputStream(downloadSock.getOutputStream());
                        FileInputStream fis = new FileInputStream("server_files/" + offer.fileName)
                ) {
                    byte[] buf = new byte[4096];
                    int r;
                    while ((r = fis.read(buf)) != -1) {
                        bos.write(buf, 0, r);
                    }
                    bos.flush();
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }
    private MessageDTO getDtoToReceiver(int filePort, FileOffer offer) {
        MessageDTO toReceiver = new MessageDTO(
                "server",
                ChatType.FILE_PORT,
                filePort + "|DOWNLOAD|" + offer.fileName + "|" + offer.fileSize,
                null,
                offer.recipient
        );
        return toReceiver;
    }

    private MessageDTO getDtoToSender(int filePort, FileOffer offer) {
        MessageDTO toSender = new MessageDTO(
                "server",
                ChatType.FILE_PORT,
                filePort + "|UPLOAD",
                null,
                offer.sender
        );
        return toSender;
    }
}
