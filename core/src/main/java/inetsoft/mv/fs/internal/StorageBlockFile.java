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
package inetsoft.mv.fs.internal;

import inetsoft.mv.fs.BlockFile;
import inetsoft.mv.util.*;
import inetsoft.storage.BlobChannel;
import inetsoft.storage.BlobTransaction;

import java.io.IOException;
import java.nio.ByteBuffer;

public class StorageBlockFile implements BlockFile {
   public StorageBlockFile(String name) {
      this.name = name;
   }

   @Override
   public String getName() {
      return name;
   }

   @Override
   public long lastModified() {
      return BlockFileStorage.getInstance().lastModified(name);
   }

   @Override
   public long length() {
      return BlockFileStorage.getInstance().length(name);
   }

   @Override
   public boolean exists() {
      return BlockFileStorage.getInstance().exists(name);
   }

   @Override
   public boolean delete() {
      try {
         BlockFileStorage.getInstance().delete(name);
      }
      catch(IOException e) {
         return false;
      }

      return true;
   }

   @Override
   public SeekableInputStream openInputStream() throws IOException {
      return new StorageBlockFileStream(name);
   }

   @Override
   public TransactionChannel openWriteChannel() throws IOException {
      return new StorageBlockFileChannel(name);
   }

   @Override
   public String toString() {
      return name;
   }

   private final String name;

   private static final class StorageBlockFileStream implements SeekableInputStream {
      public StorageBlockFileStream(String name) throws IOException {
         this.name = name;
         this.delegate = BlockFileStorage.getInstance().openReadChannel(name);
      }

      @Override
      public int read(ByteBuffer buf) throws IOException {
         return delegate.read(buf);
      }

      @Override
      public ByteBuffer map(long pos, long size) throws IOException {
         return delegate.map(pos, size);
      }

      @Override
      public void unmap(ByteBuffer buf) throws IOException {
         delegate.unmap(buf);
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
         return BlockFileStorage.getInstance().lastModified(name);
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
            return new StorageBlockFileStream(name);
         }
         catch(IOException e) {
            throw new RuntimeException("Failed to reopen stream", e);
         }
      }

      @Override
      public Object getFilePath() {
         return name;
      }

      private final String name;
      private final BlobChannel delegate;
   }

   private static final class StorageBlockFileChannel implements TransactionChannel {
      public StorageBlockFileChannel(String name) throws IOException {
         this.name = name;
         this.tx = BlockFileStorage.getInstance().beginTransaction();
         this.delegate = tx.newChannel(name, new BlockFileStorage.Metadata());
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
         tx.commit();
      }

      @Override
      public void close() throws IOException {
         tx.close();
      }

      private final String name;
      private final BlobChannel delegate;
      private final BlobTransaction<BlockFileStorage.Metadata> tx;
   }
}
