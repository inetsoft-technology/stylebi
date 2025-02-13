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
package inetsoft.mv.data;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.*;

public interface ChannelProvider {
   String getName();

   boolean exists();

   SeekableByteChannel newReadChannel() throws IOException;

   SeekableByteChannel newWriteChannel() throws IOException;

   static ChannelProvider file(Path file) {
      return new FileChannelProvider(file);
   }

   final class FileChannelProvider implements ChannelProvider {
      FileChannelProvider(Path file) {
         this.file = file;
      }

      @Override
      public String getName() {
         return file.toAbsolutePath().toString();
      }

      @Override
      public boolean exists() {
         return Files.exists(file);
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
         return Files.newByteChannel(file, StandardOpenOption.CREATE);
      }

      private final Path file;
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
