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
package inetsoft.mv.util;

import inetsoft.mv.MVTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class FileSeekableInputStream implements SeekableInputStream {
   public FileSeekableInputStream(FileChannel fc, File file) {
      this.fc = fc;
      this.file = file;
   }

   @Override
   public int read(ByteBuffer buf) throws IOException {
      return fc.read(buf);
   }

   /**
    * Map the file region into a byte buffer in memory.
    */
   @Override
   public ByteBuffer map(long pos, long size) throws IOException {
      ByteBuffer buf = fc.map(FileChannel.MapMode.READ_ONLY, pos, size);
      fc.position(pos + size);

      return buf;
   }

   /**
    * Unmap the buffer created by map().
    */
   @Override
   public void unmap(ByteBuffer buf) throws IOException {
      MVTool.unmap((MappedByteBuffer) buf);
   }

   @Override
   public SeekableChannel position(long pos) throws IOException {
      return fc.position(pos) == null ? null : this;
   }

   @Override
   public long position() throws IOException {
      return fc.position();
   }

   @Override
   public long size() throws IOException {
      return fc.size();
   }

   @Override
   public long getModificationTime() throws IOException {
      return file.lastModified();
   }

   @Override
   public void close() throws IOException {
      fc.close();

      if(rfile != null) {
         rfile.close();
      }
   }

   @Override
   public boolean isOpen() {
      return fc.isOpen();
   }

   @Override
   public SeekableInputStream reopen() throws IOException {
      try {
         RandomAccessFile rfile = new RandomAccessFile(file, "r");
         FileSeekableInputStream input = 
            new FileSeekableInputStream(rfile.getChannel(), file);
         input.rfile = rfile;

         return input;
      }
      catch(IOException ex) {
         LOG.error("Failed to open file: " + file, ex);
      }

      return null;
   }

   public FileChannel getFileChannel() {
      return fc;
   }

   public File getFile() {
      return file;
   }

   protected void setFileChannel(FileChannel fc) {
      this.fc = fc;
   }

   protected void setFile(File file) {
      this.file = file;
   }

   @Override
   public Object getFilePath() {
      return getFile();
   }

   private FileChannel fc = null;
   private File file;
   private RandomAccessFile rfile = null;

   private static final Logger LOG = 
      LoggerFactory.getLogger(FileSeekableInputStream.class);
}
