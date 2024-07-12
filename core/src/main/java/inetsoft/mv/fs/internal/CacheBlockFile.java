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
package inetsoft.mv.fs.internal;

import inetsoft.mv.MVTool;
import inetsoft.mv.fs.BlockFile;
import inetsoft.mv.util.*;
import inetsoft.util.FileSystemService;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;

/**
 * CacheBlockFile is a BlockFile that pass through all calls to the underlying local
 * cache file. It is not transactional and replaces the direct random file access in
 * the MV classes pre BlockFile api. This is necessary since the same file may be modified
 * concurrently and staging changes in a temp file may cause data to be lost.
 */
public class CacheBlockFile implements BlockFile {
   public CacheBlockFile(String name) {
      this.name = name;
      this.cacheFile = FileSystemService.getInstance().getCacheFile(name);
   }

   public CacheBlockFile(String prefix, String suffix) {
      this.cacheFile = FileSystemService.getInstance().getCacheTempFile(prefix, suffix);
      this.name = cacheFile.getName();
   }

   @Override
   public String getName() {
      return name;
   }

   @Override
   public long lastModified() {
      return cacheFile.lastModified();
   }

   @Override
   public long length() {
      return cacheFile.length();
   }

   @Override
   public boolean exists() {
      return cacheFile.exists();
   }

   @Override
   public boolean delete() {
      return cacheFile.delete();
   }

   @Override
   public SeekableInputStream openInputStream() throws IOException {
      return new CacheBlockFileStream(cacheFile);
   }

   @Override
   public TransactionChannel openWriteChannel() throws IOException {
      return new CacheBlockFileChannel(cacheFile);
   }

   @Override
   public String toString() {
      return name;
   }

   private final String name;
   private final File cacheFile;

   private static final class CacheBlockFileStream extends FileSeekableInputStream {
      public CacheBlockFileStream(File file) throws IOException {
         super(null, file);
         this.file = file;
         this.delegate = FileChannel.open(file.toPath(), StandardOpenOption.READ,
                                          StandardOpenOption.WRITE,
                                          StandardOpenOption.CREATE);
         setFileChannel(this.delegate);
      }

      @Override
      public int read(ByteBuffer buf) throws IOException {
         return delegate.read(buf);
      }

      @Override
      public ByteBuffer map(long pos, long size) throws IOException {
         ByteBuffer buffer = delegate.map(FileChannel.MapMode.READ_ONLY, pos, size);
         delegate.position(pos + size);
         return buffer;
      }

      @Override
      public void unmap(ByteBuffer buf) throws IOException {
         MVTool.unmap((MappedByteBuffer) buf);
      }

      @Override
      public SeekableChannel position(long pos) throws IOException {
         return delegate.position(pos) == null ? null : this;
      }

      @Override
      public long position() throws IOException {
         return delegate.position();
      }

      @Override
      public long size() throws IOException {
         return delegate.size();
      }

      @Override
      public long getModificationTime() throws IOException {
         return file.lastModified();
      }

      @Override
      public void close() throws IOException {
         delegate.close();
      }

      @Override
      public boolean isOpen() {
         return delegate.isOpen();
      }

      @Override
      public SeekableInputStream reopen() {
         try {
            return new CacheBlockFileStream(file);
         }
         catch(IOException e) {
            throw new RuntimeException("Failed to reopen file stream", e);
         }
      }

      @Override
      public Object getFilePath() {
         return file;
      }

      @Override
      public String toString() {
         return super.toString() + "(" + file + ")";
      }

      private final File file;
      private final FileChannel delegate;
   }

   private static final class CacheBlockFileChannel extends FileSeekableInputStream
      implements TransactionChannel
   {
      public CacheBlockFileChannel(File file) throws IOException {
         super(null, file);
         this.file = file;

         this.delegate = FileChannel.open(
            file.toPath(), StandardOpenOption.READ, StandardOpenOption.WRITE,
            StandardOpenOption.CREATE);

         setFileChannel(this.delegate);
      }

      @Override
      public int read(ByteBuffer dst) throws IOException {
         return delegate.read(dst);
      }

      @Override
      public int write(ByteBuffer src) throws IOException {
         return delegate.write(src);
      }

      @Override
      public long position() throws IOException {
         return delegate.position();
      }

      @Override
      public SeekableChannel position(long newPosition) throws IOException {
         return delegate.position(newPosition) == null ? null : this;
      }

      @Override
      public long size() throws IOException {
         return delegate.size();
      }

      @Override
      public boolean isOpen() {
         return delegate.isOpen();
      }

      @Override
      public void commit() throws IOException {
      }

      @Override
      public void close() throws IOException {
         if(delegate.isOpen()) {
            delegate.close();
         }
      }

      @Override
      public SeekableInputStream reopen() throws IOException {
         return new CacheBlockFileChannel(file);
      }

      @Override
      public String toString() {
         return super.toString() + "(" + file + ")";
      }

      private final File file;
      private final FileChannel delegate;
   }
}
