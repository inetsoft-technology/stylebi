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
package inetsoft.storage;

import inetsoft.mv.MVTool;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;

/**
 * {@code BlobReadChannel} wraps an {@link BlobChannel}, making it read-only.
 */
class BlobReadChannel implements BlobChannel {
   public BlobReadChannel(FileChannel delegate) {
      this.delegate = delegate;
   }

   @Override
   public int read(ByteBuffer dst) throws IOException {
      return delegate.read(dst);
   }

   @Override
   public int write(ByteBuffer src) throws IOException {
      throw new UnsupportedOperationException();
   }

   @Override
   public long position() throws IOException {
      return delegate.position();
   }

   @Override
   public SeekableByteChannel position(long newPosition) throws IOException {
      return delegate.position(newPosition);
   }

   @Override
   public long size() throws IOException {
      return delegate.size();
   }

   @Override
   public SeekableByteChannel truncate(long size) throws IOException {
      return delegate.truncate(size);
   }

   @Override
   public boolean isOpen() {
      return delegate.isOpen();
   }

   @Override
   public void close() throws IOException {
      delegate.close();
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

   private final FileChannel delegate;
}
