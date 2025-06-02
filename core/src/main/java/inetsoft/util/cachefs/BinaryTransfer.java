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

package inetsoft.util.cachefs;

import inetsoft.util.FileSystemService;
import inetsoft.util.Tool;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.DeferredFileOutputStream;
import org.apache.commons.text.RandomStringGenerator;

import java.io.*;
import java.nio.file.*;

public class BinaryTransfer implements Serializable {
   public BinaryTransfer(String cacheFile) {
      RandomStringGenerator stringGenerator = new RandomStringGenerator.Builder().withinRange('a', 'z').build();
      this.cacheFile = Tool.encodeUriPath(cacheFile) +"_" + stringGenerator.generate(32);
   }

   /**
    * Create a DeferredFileOutputStream to write the data to.
    * Use either this method or setData, but not both.
    */
   public OutputStream getOutputStream() throws IOException {
      if(outputStream == null) {
         Path path = CacheFS.getPath("tempStorage", cacheFile);
         outputStream = DeferredFileOutputStream.builder()
            .setBufferSize(4096)
            .setOutputFile(path)
            .setThreshold(SIZE_THRESHOLD)
            .get();
      }

      return outputStream;
   }

   /**
    * Finalize the output stream and either write the data to a file or
    * keep it in memory depending on the size threshold
    */
   public void closeOutputStream() throws IOException {
      if(outputStream.isThresholdExceeded()) {
         outputStream.flush(); // Internally flushes data to Files.newInputStream(path)
         Path path = CacheFS.getPath("tempStorage", cacheFile);
         FileSystemService fileSystemService = FileSystemService.getInstance();
         fileSystemService.remove(path, CACHE_TIMEOUT);// Auto-delete temp file after timeout
         outputStream.close();
         fileWritten = true;
      }
      else {
         data = outputStream.getData(); // Only gets data held in memory if not written to a file
         outputStream.close();
      }

      outputStream = null;
   }

   /**
    * Directly set the byte data.
    * Use either this method or getOutputStream, but not both.
    */
   public void setData(byte[] data) throws IOException {
      if(data != null && data.length > SIZE_THRESHOLD) {
         getOutputStream().write(data);
         closeOutputStream();
      }
      else {
         this.data = data;
      }
   }

   /**
    * Reads the data stored in this object to a InputStream.
    */
   public InputStream getInputStream() throws IOException {
      Path path = CacheFS.getPath("tempStorage", cacheFile);

      if(data != null) {
         return new ByteArrayInputStream(data);
      }
      else if (fileWritten) {
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
   public byte[] getData() throws IOException {
      if(data != null) {
         return data;
      }
      else {
         InputStream inputStream = getInputStream();
         byte[] fullData = inputStream.readAllBytes();
         inputStream.close();
         clearCacheFile();
         return fullData;
      }
   }

   /**
    * Deletes the temporary cache file if it was created.
    */
   public void clearCacheFile() throws IOException {
      if(data == null) {
         Path path = CacheFS.getPath("tempStorage", cacheFile);

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
   public void writeData(OutputStream outputStream) throws IOException {
      InputStream inputStream = getInputStream();
      IOUtils.copy(inputStream, outputStream);
      inputStream.close();
      clearCacheFile();
   }

   /**
    * Check if data is stored in the object.
    */
   public boolean isEmpty() {
      Path path = CacheFS.getPath("tempStorage", cacheFile);
      return (data == null || data.length == 0) && !(fileWritten && Files.exists(path));
   }

   byte[] data;
   String cacheFile;
   boolean fileWritten = false;
   transient DeferredFileOutputStream outputStream;
   final static int SIZE_THRESHOLD = 5 * 1024 * 1024; // 5 MB
   final static int CACHE_TIMEOUT = 600000; // 10 minutes
}
