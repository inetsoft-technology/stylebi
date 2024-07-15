/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;

/**
 * BufferFileOutputStream is an OutputStream that uses an in-memory buffer
 * to store data, up until a specified size (limit), at which point the buffer
 * switches to using a File-based buffer in order to conserve memory.
 *
 * Temporary files are created with deleteOnExit, but it is preferable to
 * explicitly call the dispose method to immediately clean up this object.
 *
 * BufferFileOutputStream objects do not support re-use; meaning, once you have
 * used a BufferFileOutputStream object, then you should dispose of it and
 * create a new instance if you need another stream.
 *
 * @version 11.3, 12/6/2011
 * @author InetSoft Technology Corp
 */
public class BufferFileOutputStream extends OutputStream {
   /**
    * Constructor.
    * @param limit the byte limit under which we use in-memory storage,
    *        and above which we switch to using a file.
    */
   public BufferFileOutputStream(long limit) {
      this.limit = limit;
      memoryOutputStream = new ByteArrayOutputStream();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void write(int b) throws IOException {
      checkClosed();

      // If we are going to go over the limit, then switch to file buffering
      if(isBuffering && size + 1 > limit) {
         convertToFile();
      }

      if(isBuffering) {
         memoryOutputStream.write(b);
      }
      else {
         fileOutputStream.write(b);
      }

      size++;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void write(byte[] b, int off, int len) throws IOException {
      checkClosed();

      // If we are going to go over the limit, then switch to file buffering
      if(isBuffering && size + len > limit) {
         convertToFile();
      }

      if(isBuffering) {
         memoryOutputStream.write(b, off, len);
      }
      else {
         fileOutputStream.write(b, off, len);
      }

      size += len;
   }

   /**
    * Converts this stream from using an in-memory stream to use a file-stream.
    * @throws IOException
    */
   private void convertToFile() throws IOException {
      file = FileSystemService.getInstance().getCacheTempFile("BFOSbuffer", "tmp");
      file.deleteOnExit();
      fileOutputStream = new FileOutputStream(file);
      fileOutputStream.write(memoryOutputStream.toByteArray());
      memoryOutputStream.close();
      memoryOutputStream = null;
      isBuffering = false;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void flush() throws IOException {
      checkClosed();

      if(memoryOutputStream != null) {
         memoryOutputStream.flush();
      }
      
      if(fileOutputStream != null) {
         fileOutputStream.flush();
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void close() throws IOException {
      if(memoryOutputStream != null) {
         memoryOutputStream.flush();
         memoryOutputStream.close();
      }

      if(fileOutputStream != null) {
         fileOutputStream.flush();
         fileOutputStream.close();
         fileOutputStream = null;
      }

      isClosed = true;
   }

   /**
    * Throws an IOException if this stream has been closed.
    * @throws IOException if the stream has been closed
    */
   private void checkClosed() throws IOException {
      if(isClosed) {
         throw new IOException("Writer is already closed.");
      }
   }

   /**
    * Creates an InputStream for reading the contents written to this
    * output stream.
    *
    * Calling this will also close the stream, if it hasn't been closed yet.
    *
    * @return an InputStream that contains the contents written to this stream.
    * @throws java.io.IOException
    */
   public InputStream getInputStream() throws IOException {
      if(!isClosed) {
         close();
      }

      if(isBuffering) {
         return new ByteArrayInputStream(memoryOutputStream.toByteArray());
      }
      else {
         return new FileInputStream(file);
      }
   }

   /**
    * Returns the number of bytes written to this output stream.
    * @return the byte count
    */
   public long getSize() {
      return size;
   }

   /**
    * Perform clean up. This will remove the temporary file. No further method
    * invocations should be performed on this object.
    * @throws IOException
    */
   public void dispose() throws IOException {
      if(!isClosed) {
         close();
      }

      if(file != null) {
         try {
            if(file.exists()) {
               file.delete();
            }
         }
         catch(Throwable t) {
            LOG.warn(
                  "Failed to delete temp file [%s]", file.getAbsolutePath());
         }
         finally {
            file = null;
         }
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   protected void finalize() throws Throwable {
      try {
         dispose();
      }
      finally {
         super.finalize();
      }
   }

   private ByteArrayOutputStream memoryOutputStream;
   private FileOutputStream fileOutputStream = null;
   private File file = null;
   private long limit;
   private long size = 0;
   private boolean isBuffering = true;
   private boolean isClosed = false;

   private static final Logger LOG =
      LoggerFactory.getLogger(BufferFileOutputStream.class);
}
