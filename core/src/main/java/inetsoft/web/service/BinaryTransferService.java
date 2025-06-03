/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.web.service;

import inetsoft.sree.SreeEnv;
import inetsoft.util.FileSystemService;
import inetsoft.util.Tool;
import inetsoft.util.cachefs.BinaryTransfer;
import inetsoft.util.cachefs.CacheFS;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.DeferredFileOutputStream;
import org.apache.commons.text.RandomStringGenerator;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;

@Service
public class BinaryTransferService {
   public BinaryTransfer createBinaryTransfer(String cacheFile) {
      RandomStringGenerator stringGenerator = new RandomStringGenerator.Builder().withinRange('a', 'z').build();
      return new BinaryTransfer(Tool.encodeUriPath(cacheFile) +"_" + stringGenerator.generate(32));
   }

   /**
    * Create a DeferredFileOutputStream to write the data to.
    * Use either this method or setData, but not both.
    */
   public DeferredFileOutputStream createOutputStream(BinaryTransfer dataTransfer) throws IOException {
      int threshold = Integer.parseInt(SreeEnv.getProperty("data.temp.file.threshold", "5242880"));
      Path path = CacheFS.getPath("tempStorage", dataTransfer.getCacheFile());

      return DeferredFileOutputStream.builder()
         .setBufferSize(4096)
         .setOutputFile(path)
         .setThreshold(threshold)
         .get();
   }

   /**
    * Finalize the output stream and either write the data to a file or
    * keep it in memory depending on the size threshold
    */
   public void closeOutputStream(BinaryTransfer dataTransfer, DeferredFileOutputStream outputStream) throws IOException {
      if(outputStream.isThresholdExceeded()) {
         int timeout = Integer.parseInt(SreeEnv.getProperty("data.temp.file.timeout", "600000"));
         outputStream.flush(); // Internally flushes data to Files.newInputStream(path)
         Path path = CacheFS.getPath("tempStorage", dataTransfer.getCacheFile());
         FileSystemService fileSystemService = FileSystemService.getInstance();
         fileSystemService.remove(path, timeout);// Auto-delete temp file after timeout
         outputStream.close();
         dataTransfer.setFileWritten(true);
      }
      else {
         dataTransfer.setData(outputStream.getData()); // Only gets data held in memory if not written to a file
         outputStream.close();
      }
   }

   /**
    * Directly set the byte data.
    * Use either this method or createOutputStream, but not both.
    */
   public void setData(BinaryTransfer dataTransfer, byte[] data) throws IOException {
      int threshold = Integer.parseInt(SreeEnv.getProperty("data.temp.file.threshold"));

      if(data != null && data.length > threshold) {
         DeferredFileOutputStream outputStream = createOutputStream(dataTransfer);
         outputStream.write(data);
         closeOutputStream(dataTransfer, outputStream);
      }
      else {
         dataTransfer.setData(data);
      }
   }

   /**
    * Reads the data stored in this object to a InputStream.
    */
   public InputStream getInputStream(BinaryTransfer dataTransfer) throws IOException {
      Path path = CacheFS.getPath("tempStorage", dataTransfer.getCacheFile());

      if(dataTransfer.getData() != null) {
         return new ByteArrayInputStream(dataTransfer.getData());
      }
      else if (dataTransfer.isFileWritten()) {
         if(Files.exists(path)) {
            return Files.newInputStream(path);
         }
         else {
            try {
               Thread.sleep(3000);
            }
            catch(Exception e) {

            }
            return Files.newInputStream(path);
         }
      }

      return new ByteArrayInputStream(new byte[0]);
   }

   /**
    * Reads the data stored directly to a byte array.
    * Only use for smaller file sizes.
    */
   public byte[] getData(BinaryTransfer dataTransfer) throws IOException {
      if(dataTransfer.getData() != null) {
         return dataTransfer.getData();
      }
      else {
         InputStream inputStream = getInputStream(dataTransfer);
         byte[] fullData = inputStream.readAllBytes();
         inputStream.close();
         clearCacheFile(dataTransfer);
         return fullData;
      }
   }

   /**
    * Deletes the temporary cache file if it was created.
    */
   public void clearCacheFile(BinaryTransfer dataTransfer) throws IOException {
      if(dataTransfer.getData() == null) {
         Path path = CacheFS.getPath("tempStorage", dataTransfer.getCacheFile());

         try {
            Files.delete(path);
         }
         catch (NoSuchFileException e) {
            // ignore
         }
      }
   }

   /**
    * Convenience method to open an input stream, write to an output stream, and clear cache afterward.
    */
   public void writeData(BinaryTransfer dataTransfer, OutputStream outputStream) throws IOException {
      InputStream inputStream = getInputStream(dataTransfer);
      IOUtils.copy(inputStream, outputStream);
      inputStream.close();
      clearCacheFile(dataTransfer);
   }

   /**
    * Check if data is stored in the object.
    */
   public boolean isEmpty(BinaryTransfer dataTransfer) {
      Path path = CacheFS.getPath("tempStorage", dataTransfer.getCacheFile());
      byte[] data = dataTransfer.getData();
      boolean fileWritten = dataTransfer.isFileWritten();
      return (data == null || data.length == 0) && !(fileWritten && Files.exists(path));
   }
}
