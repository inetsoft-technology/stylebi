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
   public ClusterFileTransfer() {
      try {
         fileTransferSocket = new ServerSocket(0, 50, Tool.getLocalIP());
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
      String link = fileTransferSocket.getInetAddress().getHostAddress() + ":" +
         fileTransferSocket.getLocalPort() + "/" + fileId;
      transferFiles.put(fileId, file);
      return link;
   }

   public File getTransferFile(String link) throws IOException {
      int index1 = link.indexOf(':');
      int index2 = link.indexOf('/');

      if(index1 < 0 || index2 < 0) {
         throw new IllegalArgumentException("Invalid file transfer link: " + link);
      }

      String host = link.substring(0, index1);
      int port = Integer.parseInt(link.substring(index1 + 1, index2));
      String fileId = link.substring(index2 + 1);

      if(transferFiles.containsKey(fileId)) {
         return transferFiles.remove(fileId);
      }

      File tempFile = FileSystemService.getInstance().getCacheTempFile("transfer", ".dat");
      InetAddress address = InetAddress.getByName(host);

      byte[] idBytes = new byte[16];
      ByteBuffer idBuffer = ByteBuffer.wrap(idBytes);
      UUID fileUuid = UUID.fromString(fileId);
      idBuffer.putLong(fileUuid.getMostSignificantBits());
      idBuffer.putLong(fileUuid.getLeastSignificantBits());

      try(Socket socket = new Socket(address, port);
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

   private void serviceFileTransfers() {
      while(!((GroupedThread) Thread.currentThread()).isCancelled()) {
         try(Socket socket = fileTransferSocket.accept();
             InputStream input = socket.getInputStream();
             OutputStream output = socket.getOutputStream())
         {
            byte[] idBytes = new byte[16];

            for(int i = 0; i < 16; i++) {
               idBytes[i] = (byte) input.read();
            }

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

   private final GroupedThread fileTransferThread;
   private final ServerSocket fileTransferSocket;
   private final ConcurrentMap<String, File> transferFiles = new ConcurrentHashMap<>();
}
