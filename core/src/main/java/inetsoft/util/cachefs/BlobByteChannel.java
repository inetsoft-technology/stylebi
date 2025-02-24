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

import inetsoft.storage.BlobChannel;
import inetsoft.storage.BlobStorage;

import java.io.IOException;
import java.lang.ref.Cleaner;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;

final class BlobByteChannel implements BlobChannel {
   public BlobByteChannel(CachePath path, CacheMetadata metadata,
                          BlobStorage<CacheMetadata> storage,
                          Cleaner cleaner)
      throws IOException
   {
      this.transaction = new TransactionSupport(storage, path, metadata, cleaner);
      this.channel = transaction.openChannel();
   }

   @Override
   public int read(ByteBuffer dst) throws IOException {
      return channel.read(dst);
   }

   @Override
   public int write(ByteBuffer src) throws IOException {
      return channel.write(src);
   }

   @Override
   public long position() throws IOException {
      return channel.position();
   }

   @Override
   public SeekableByteChannel position(long newPosition) throws IOException {
      return channel.position(newPosition);
   }

   @Override
   public long size() throws IOException {
      return channel.size();
   }

   @Override
   public SeekableByteChannel truncate(long size) throws IOException {
      return channel.truncate(size);
   }

   @Override
   public boolean isOpen() {
      return channel.isOpen();
   }

   @Override
   public void close() throws IOException {
      channel.close();
      transaction.commit();
   }

   @Override
   public ByteBuffer map(long pos, long size) throws IOException {
      return channel.map(pos, size);
   }

   @Override
   public void unmap(ByteBuffer buf) throws IOException {
      channel.unmap(buf);
   }

   private final TransactionSupport transaction;
   private final BlobChannel channel;
}
