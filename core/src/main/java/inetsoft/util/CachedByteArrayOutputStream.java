/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.util;

import inetsoft.util.swap.XSwapper;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;

/**
 * CachedByteArrayOutputStream as a ByteArrayOutputStream, is swappable to
 * avoid out of memory. It's not thread safe.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public final class CachedByteArrayOutputStream extends OutputStream {
   /**
    * Constructor.
    */
   public CachedByteArrayOutputStream() {
      this.buf = new byte[4096];
   }

   private boolean isSwap(int nsize) {
      if(!swapped && nsize > lastSize + 1024) {
         // swap to file if exceeding max or memory is low.
         // this object is short lived so no need to make it a swappable.
         swapped = nsize > max || XSwapper.getMemoryState() < XSwapper.LOW_MEM;
         lastSize = size;
      }

      return swapped;
   }

   /**
    * Writes the specified byte to this byte array output stream.
    * @param b the byte to be written.
    */
   @Override
   public void write(int b) {
      int nsize = size + 1;

      if(isSwap(nsize)) {
         try {
            getOutputStream0().write(b);
         }
         catch(IOException ex) {
            LOG.error("Failed to get write buffer", ex);
            throw new RuntimeException(ex);
         }
      }
      else {
         if(nsize > buf.length) {
            byte[] nbuf = new byte[Math.max(buf.length << 1, nsize)];
            System.arraycopy(buf, 0, nbuf, 0, size);
            buf = nbuf;
         }

         buf[size] = (byte)b;
      }

      size = nsize;
   }

   /**
    * Writes <code>len</code> bytes from the specified byte array
    * starting at offset <code>off</code> to this byte array output stream.
    * @param b the data.
    * @param off the start offset in the data.
    * @param len the number of bytes to write.
    */
   @Override
   public void write(byte[] b, int off, int len) {
      if(off < 0 || len < 0 || off + len > b.length) {
         throw new IndexOutOfBoundsException();
      }
      else if(len == 0) {
         return;
      }

      final int nsize = size + len;

      if(isSwap(nsize)) {
         try {
            getOutputStream0().write(b, off, len);
         }
         catch(IOException ex) {
            LOG.error("Failed to write buffer: " +
                        off + " " + len, ex);
            throw new RuntimeException(ex);
         }
      }
      else {
         try {
            if(nsize > buf.length) {
               byte[] nbuf = new byte[Math.max(buf.length << 1, nsize)];
               System.arraycopy(buf, 0, nbuf, 0, size);
               buf = nbuf;
            }

            System.arraycopy(b, off, buf, size, len);
         }
         catch(IndexOutOfBoundsException ex) {
            LOG.error("Write buffer error: " + size + " " + len + " " + off + " " +
                         b.length + " " + buf.length, ex);
            throw ex;
         }
      }

      size = nsize;
   }

   /**
    * Writes the complete contents of this byte array output stream to
    * the specified output stream argument, as if by calling the output
    * stream's write method using <code>out.write(buf, 0, count)</code>.
    * @param out the output stream to which to write the data.
    * @exception IOException if an I/O error occurs.
    */
   public void writeTo(OutputStream out) throws IOException {
      if(!swapped) {
         out.write(buf, 0, size);
      }
      else {
         try(InputStream in = getInputStream0()) {
            Tool.copyTo(in, out);
         }
         catch(IOException ex) {
            LOG.error("Failed to copy buffer", ex);
            throw new RuntimeException(ex);
         }
      }
   }

   /**
    * Get output stream.
    */
   private OutputStream getOutputStream0() throws IOException {
      if(out == null) {
         boolean first = file == null;

         if(first) {
            file = FileSystemService.getInstance().getCacheTempFile("byteArray", "dat");
         }

         out = new BufferedOutputStream(new FileOutputStream(file, true), 8192);

         if(first) {
            out.write(buf, 0, size);
         }
      }

      return out;
   }

   /**
    * Get input stream.
    */
   private InputStream getInputStream0() throws IOException {
      close();

      if(file == null || !file.exists()) {
         throw new RuntimeException("Stream is finalized!");
      }

      return new FileInputStream(file);
   }

   /**
    * Get the input stream.
    * @return the input stream if any.
    */
   public InputStream getInputStream() throws IOException {
      InputStream in;

      if(!swapped) {
         in = new ByteArrayInputStream(buf, 0, size);
      }
      else {
         in = getInputStream0();
      }

      return new Input(in);
   }

   /**
    * Resets the <code>count</code> field of this byte array output
    * stream to zero, so that all currently accumulated output in the
    * ouput stream is discarded. The output stream can be used again,
    * reusing the already allocated buffer space.
    */
   public void reset() {
      try {
         dispose();
      }
      catch(Exception ex) {
         LOG.error("Failed to reset buffer", ex);
      }

      size = 0;
   }

   /**
    * Get the bytes.
    */
   public byte[] getBytes() {
      if(!swapped) {
         return buf;
      }
      else {
         byte[] nbuf = new byte[size];

         try(InputStream in = getInputStream0()) {
            IOUtils.readFully(in, nbuf);
         }
         catch(IOException ex) {
            LOG.error("Failed to read buffer", ex);
            throw new RuntimeException(ex);
         }

         return nbuf;
      }
   }

   /**
    * Creates a newly allocated byte array. Its size is the current
    * size of this output stream and the valid contents of the buffer
    * have been copied into it.
    * @return  the current contents of this output stream, as a byte array.
    */
   public byte[] toByteArray() {
      byte[] nbuf = new byte[size];

      if(!swapped) {
         System.arraycopy(buf, 0, nbuf, 0, size);
      }
      else {
         try(InputStream in = getInputStream0()) {
            IOUtils.readFully(in, nbuf);
         }
         catch(IOException ex) {
            LOG.error("Failed to read buffer: " + size, ex);
            throw new RuntimeException(ex);
         }
      }

      return nbuf;
   }

   /**
    * Returns the current size of the buffer.
    * @return the value of the <code>count</code> field, which is the number
    * of valid bytes in this output stream.
    */
   public int size() {
      return size;
   }

   /**
    * Converts the buffer's contents into a string, translating bytes into
    * characters according to the platform's default character encoding.
    * @return String translated from the buffer's contents.
    */
   public String toString() {
      byte[] nbuf = toByteArray();
      return new String(nbuf, 0, size);
   }

   /**
    * Converts the buffer's contents into a string, translating bytes into
    * characters according to the specified character encoding.
    * @param enc a character-encoding name.
    * @return String translated from the buffer's contents.
    */
   public String toString(String enc) throws UnsupportedEncodingException {
      byte[] nbuf = toByteArray();
      return new String(nbuf, 0, size, enc);
   }

   /**
    * Creates a newly allocated string. Its size is the current size of
    * the output stream and the valid contents of the buffer have been
    * copied into it.
    * @param hibyte the high byte of each resulting Unicode character.
    * @return the current contents of the output stream, as a string.
    */
   public String toString(int hibyte) {
      byte[] nbuf = toByteArray();
      return new String(nbuf, hibyte, 0, size);
   }

   /**
    * Close a <tt>ByteArrayOutputStream</tt>.
    */
   @Override
   public void close() throws IOException {
      if(out != null) {
         out.close();
         out = null;
      }
   }

   /**
    * Close a <tt>ByteArrayOutputStream</tt>.
    */
   public void dispose() throws IOException {
      close();

      if(file != null) {
         boolean removed = file.delete();

         if(!removed) {
            FileSystemService.getInstance().remove(file, 60000);
         }

         file = null;
      }

      this.buf = new byte[1];
      swapped = false;
   }

   /**
    * Finalize the object.
    */
   @Override
   protected void finalize() throws Throwable {
      dispose();
      super.finalize();
   }

   private static final int max = 10 * 1024 * 1024; // 10M
   private byte[] buf;
   private File file;
   private OutputStream out;
   private int size;
   private int lastSize; // size when last checked for swapping
   private boolean swapped = false;

   private static final Logger LOG =
      LoggerFactory.getLogger(CachedByteArrayOutputStream.class);

   /**
    * Input stream wrapper that holds a reference to the parent cached output
    * stream.
    *
    * @since 12.2
    */
   public final class Input extends FilterInputStream {
      /**
       * Creates a new instance of <tt>Input</tt>.
       *
       * @param in the wrapped input stream.
       */
      public Input(InputStream in) {
         super(in);
      }

      /**
       * Gets the parent cached output stream.
       *
       * @return the output.
       */
      public CachedByteArrayOutputStream getOutput() {
         return CachedByteArrayOutputStream.this;
      }
   }
}
