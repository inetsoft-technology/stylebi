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
package inetsoft.mv.data;

import org.apache.commons.io.IOUtils;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;

public interface ChannelProvider {
   String getName();

   boolean exists();

   SeekableByteChannel newReadChannel() throws IOException;

   SeekableByteChannel newWriteChannel() throws IOException;

   static ChannelProvider file(File file) {
      return new FileChannelProvider(file);
   }

   final class FileChannelProvider implements ChannelProvider {
      FileChannelProvider(File file) {
         this.file = file;
      }

      @Override
      public String getName() {
         return file.getAbsolutePath();
      }

      @Override
      public boolean exists() {
         return file.exists();
      }

      @Override
      public SeekableByteChannel newReadChannel() throws IOException {
         return newChannel("r");
      }

      @Override
      public SeekableByteChannel newWriteChannel() throws IOException {
         return newChannel("rw");
      }

      private SeekableByteChannel newChannel(String mode) throws IOException {
         RandomAccessFile raf = new RandomAccessFile(file, mode);
         return new DelegatingChannel(raf.getChannel()) {
            @Override
            public void close() throws IOException {
               try {
                  super.close();
               }
               finally {
                  IOUtils.closeQuietly(raf);
               }
            }
         };
      }

      private final File file;
   }

   abstract class DelegatingChannel implements SeekableByteChannel {
      DelegatingChannel(SeekableByteChannel delegate) {
         this.delegate = delegate;
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

      private final SeekableByteChannel delegate;
   }
}
