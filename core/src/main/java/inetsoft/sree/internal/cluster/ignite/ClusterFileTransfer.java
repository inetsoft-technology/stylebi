/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package inetsoft.sree.internal.cluster.ignite;

import inetsoft.util.*;
import org.apache.commons.io.FileUtils;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class ClusterFileTransfer implements AutoCloseable {
   public ClusterFileTransfer(int port, InetAddress bindAddress) {
      try {
         localAddress = bindAddress;
         fileTransferSocket = new ServerSocket(port, 50, localAddress);
         fileTransferThread =
            new GroupedThread(this::serviceFileTransfers, "service-file-transfer");
         fileTransferThread.start();
      }
      catch(Exception e) {
         throw new RuntimeException("Failed to create file transfer socket", e);
      }
   }

   public String addTransferFile(File file) {
      String fileId = UUID.randomUUID().toString();
      String host = localAddress instanceof Inet6Address
         ? "[" + localAddress.getHostAddress() + "]"
         : localAddress.getHostAddress();
      String link = host + ":" + fileTransferSocket.getLocalPort() + "/" + fileId;
      transferFiles.put(fileId, file);
      return link;
   }

   public File getTransferFile(String link) throws IOException {
      int index2 = link.indexOf('/');

      if(index2 < 0) {
         throw new IllegalArgumentException("Invalid file transfer link: " + link);
      }

      String host;
      int port;

      if(link.startsWith("[")) {
         // IPv6: "[fe80::1]:8080/uuid"
         int closeBracket = link.indexOf(']');

         if(closeBracket < 0 || link.charAt(closeBracket + 1) != ':') {
            throw new IllegalArgumentException("Invalid file transfer link: " + link);
         }

         host = link.substring(1, closeBracket);
         port = Integer.parseInt(link.substring(closeBracket + 2, index2));
      }
      else {
         // IPv4: "1.2.3.4:8080/uuid"
         int index1 = link.indexOf(':');

         if(index1 < 0) {
            throw new IllegalArgumentException("Invalid file transfer link: " + link);
         }

         host = link.substring(0, index1);
         port = Integer.parseInt(link.substring(index1 + 1, index2));
      }
      String fileId = link.substring(index2 + 1);

      File localFile = transferFiles.remove(fileId);

      if(localFile != null) {
         return localFile;
      }

      File tempFile = FileSystemService.getInstance().getCacheTempFile("transfer", ".dat");
      InetAddress address = InetAddress.getByName(host);

      byte[] idBytes = new byte[16];
      ByteBuffer idBuffer = ByteBuffer.wrap(idBytes);
      UUID fileUuid = UUID.fromString(fileId);
      idBuffer.putLong(fileUuid.getMostSignificantBits());
      idBuffer.putLong(fileUuid.getLeastSignificantBits());

      Socket socket = new Socket();
      socket.connect(new InetSocketAddress(address, port), 30_000);
      socket.setSoTimeout(30_000);

      try(socket;
          InputStream input = socket.getInputStream();
          OutputStream output = socket.getOutputStream())
      {
         output.write(idBytes);
         output.flush();
         Files.copy(input, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
      }
      catch(IOException | RuntimeException e) {
         FileUtils.deleteQuietly(tempFile);
         throw e;
      }

      return tempFile;
   }

   public void cancelTransferFile(String link) {
      int index = link.lastIndexOf('/');

      if(index >= 0) {
         String fileId = link.substring(index + 1);
         File file = transferFiles.remove(fileId);
         FileUtils.deleteQuietly(file);
      }
   }

   private void serviceFileTransfers() {
      while(!((GroupedThread) Thread.currentThread()).isCancelled()) {
         try(Socket socket = fileTransferSocket.accept();
             InputStream input = socket.getInputStream();
             OutputStream output = socket.getOutputStream())
         {
            socket.setSoTimeout(30_000);
            byte[] idBytes = new byte[16];
            new DataInputStream(input).readFully(idBytes);

            ByteBuffer idBuffer = ByteBuffer.wrap(idBytes);
            String fileId = new UUID(idBuffer.getLong(), idBuffer.getLong()).toString();
            File file = transferFiles.remove(fileId);

            if(file != null && file.exists()) {
               try {
                  Files.copy(file.toPath(), output);
               }
               finally {
                  FileUtils.deleteQuietly(file);
               }
            }
         }
         catch(SocketException e) {
            if(!((GroupedThread) Thread.currentThread()).isCancelled()) {
               LoggerFactory.getLogger(getClass()).error("Failed to transfer file", e);
            }
         }
         catch(Exception e) {
            LoggerFactory.getLogger(getClass()).error("Failed to transfer file", e);
         }
      }
   }


   @Override
   public void close() {
      fileTransferThread.cancel();
      fileTransferThread.interrupt();

      try {
         fileTransferSocket.close();
      }
      catch(IOException ignore) {
      }
   }

   private final InetAddress localAddress;
   private final GroupedThread fileTransferThread;
   private final ServerSocket fileTransferSocket;
   private final ConcurrentMap<String, File> transferFiles = new ConcurrentHashMap<>();
}
