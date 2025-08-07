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
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.*;
import java.security.*;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * {@code BlobTransaction} wraps one or more blob writes in a transaction.
 *
 * @param <T> the blob metadata type.
 */
public class BlobTransaction<T extends Serializable> implements Closeable {
   BlobTransaction(BlobStorage<T> storage) {
      this.storage = storage;
   }

   /**
    * Opens an output stream to the blob at the specified path.
    *
    * @param path     the path to the blob.
    * @param metadata the extended metadata for the blob or {@code null} if none.
    *
    * @return an output stream.
    *
    * @throws IOException if an I/O error occurs.
    */
   public OutputStream newStream(String path, T metadata) throws IOException {
      return newStream(path, metadata, null);
   }

   /**
    * Opens an output stream to the blob at the specified path.
    *
    * @param path         the path to the blob.
    * @param metadata     the extended metadata for the blob or {@code null} if none.
    * @param beforeCommit an optional callback that is invoked before the changes to the blob are
    *                     committed.
    *
    * @return an output stream.
    *
    * @throws IOException if an I/O error occurs.
    */
   public OutputStream newStream(String path, T metadata, BeforeCommit beforeCommit)
      throws IOException
   {
       return newStream(path, metadata, beforeCommit, 0L);
   }

   /**
    * Opens an output stream to the blob at the specified path.
    *
    * @param path         the path to the blob.
    * @param metadata     the extended metadata for the blob or {@code null} if none.
    * @param beforeCommit an optional callback that is invoked before the changes to the blob are
    *                     committed.
    *
    * @return an output stream.
    *
    * @throws IOException if an I/O error occurs.
    */
   public OutputStream newStream(String path, T metadata, BeforeCommit beforeCommit,
                                 long lastModified)
      throws IOException
   {
      Path tempFile = storage.createTempFile("blob", ".dat");
      StreamContext<T> context =
         new StreamContext<>(path, tempFile, metadata, beforeCommit, lastModified);
      operations.addLast(context);
      return context.getStream();
   }

   /**
    * Opens a channel for the blob at the specified path.
    *
    * @param path     the path to the blob.
    * @param metadata the extended metadata for the blob or {@code null} if none. If the blob is not
    *                 modified, the metadata will not be updated.
    *
    * @return a channel.
    *
    * @throws IOException if an I/O error occurs.
    */
   public BlobChannel newChannel(String path, T metadata) throws IOException {
      return newChannel(path, metadata, null);
   }

   /**
    * Opens a channel for the blob at the specified path.
    *
    * @param path         the path to the blob.
    * @param metadata     the extended metadata for the blob or {@code null} if none. If the blob is
    *                     not modified, the metadata will not be updated.
    * @param beforeCommit an optional callback that is invoked before the changes to the blob are
    *                     committed.
    *
    * @return a channel.
    *
    * @throws IOException if an I/O error occurs.
    */
   public BlobChannel newChannel(String path, T metadata, BeforeCommit beforeCommit)
      throws IOException
   {
      Blob<T> blob = storage.getStorage().get(path);
      Path tempFile;

      if(blob == null) {
         tempFile = storage.createTempFile("blob", ".dat");
      }
      else {
         tempFile = storage.copyToTemp(blob);
      }

      ChannelContext<T> context = new ChannelContext<>(path, tempFile, metadata, beforeCommit);
      operations.addLast(context);
      return context.getStream();
   }

   /**
    * Commits all the blobs modified by the output streams and channels created by this transaction
    * since the last commit.
    *
    * @throws IOException if an I/O error occurs.
    */
   public void commit() throws IOException {
      try {
         while(!operations.isEmpty()) {
            commit(operations.getLast());
            operations.removeLast();
         }
      }
      catch(Exception e) {
         rollback();

         if(e instanceof IOException) {
            throw (IOException) e;
         }

         throw new IOException("Failed to commit transaction", e);
      }
   }

   @Override
   public void close() throws IOException {
      rollback();
   }

   private void rollback() {
      while(!operations.isEmpty()) {
         rollback(operations.removeLast());
      }
   }

   private void commit(Context<T, ? extends Closeable> context) throws IOException {
      context.close();

      if(context.beforeCommit != null) {
         context.beforeCommit.call();
      }

      byte[] digest = context.getDigest();
      StringBuilder digestString = new StringBuilder();

      for(byte b : digest) {
         digestString.append(String.format("%02x", ((int) b) & 0xff));
      }

      long lastModified = context.getLastModified();
      Instant instant = lastModified > 0 ? Instant.ofEpochMilli(lastModified) : Instant.now();
      Blob<T> blob = new Blob<>(
         context.path, digestString.toString(), context.tempFile.toFile().length(),
         instant, context.metadata);

      storage.commit(blob, context.tempFile);
      BlobReference<T> oldBlob;
      BlobStorage.BlobLock lock;

      try {
         lock = storage.lock(blob.getPath(), false);
      }
      catch(Exception e) {
         throw new IOException("Failed to acquire lock for " + blob.getPath() + " in " + storage.getId(), e);
      }

      try {
         oldBlob = storage.putBlob(blob);
      }
      catch(Exception e) {
         throw new IOException("Failed to save metadata for blob " + context.path, e);
      }
      finally {
         lock.unlock();
      }

      if(oldBlob.getCount() == 0 && oldBlob.getBlob() != null) {
         lock = null;

         try {
            lock = storage.lock(oldBlob.getBlob().getPath(), false);
            storage.delete(oldBlob.getBlob());
         }
         catch(Exception e) {
            LOG.warn("Failed to delete old blob file", e);
         }
         finally {
            if(lock != null) {
               lock.unlock();
            }
         }
      }
   }

   private void rollback(Context<T, ? extends Closeable> context) {
      IOUtils.closeQuietly(context);

      try {
         Files.delete(context.tempFile);
      }
      catch(IOException e) {
         LOG.warn("Failed to delete temp file", e);
      }
   }

   private final BlobStorage<T> storage;
   private final Deque<Context<T, ? extends Closeable>> operations = new ArrayDeque<>();
   private static final Logger LOG = LoggerFactory.getLogger(BlobTransaction.class);

   /**
    * {@code BeforeCommit} is a function interface that allows callers to perform some action prior
    * to the changes to a blob being committed.
    */
   @FunctionalInterface
   public interface BeforeCommit {
      /**
       * Called before changes to a blob are committed.
       *
       * @throws IOException if an I/O error occurs.
       */
      void call() throws IOException;
   }

   private static abstract class Context<T extends Serializable, S extends Closeable>
      implements Closeable
   {
      Context(String path, Path tempFile, T metadata, BeforeCommit beforeCommit,
              long lastModified)
         throws IOException
      {
         this.path = path;
         this.tempFile = tempFile;
         this.metadata = metadata;
         this.stream = createStream(tempFile);
         this.beforeCommit = beforeCommit;
         this.lastModified = lastModified;
      }

      S getStream() {
         return stream;
      }

      @Override
      public void close() throws IOException {
         stream.close();
      }

      abstract S createStream(Path tempFile) throws IOException;

      abstract byte[] getDigest() throws IOException;

      public long getLastModified() {
         return lastModified;
      }

      final String path;
      final Path tempFile;
      final T metadata;
      final S stream;
      final long lastModified;
      final BeforeCommit beforeCommit;
   }

   private static final class StreamContext<T extends Serializable>
      extends Context<T, TransactionOutputStream>
   {
      public StreamContext(String path, Path tempFile, T metadata, BeforeCommit beforeCommit,
                           long lastModified)
         throws IOException
      {
         super(path, tempFile, metadata, beforeCommit, lastModified);
      }

      @Override
      TransactionOutputStream createStream(Path tempFile) throws IOException {
         OutputStream base = Files.newOutputStream(tempFile);

         try {
            return new TransactionOutputStream(base);
         }
         catch(NoSuchAlgorithmException e) {
            IOUtils.closeQuietly(base);
            throw new IOException("Failed to create output stream wrapper", e);
         }
      }

      @Override
      byte[] getDigest() {
         return getStream().getMessageDigest().digest();
      }
   }

   private static final class ChannelContext<T extends Serializable>
      extends Context<T, BlobChannel>
   {
      public ChannelContext(String path, Path tempFile, T metadata, BeforeCommit beforeCommit)
         throws IOException
      {
         super(path, tempFile, metadata, beforeCommit, 0L);
      }

      @Override
      public void close() throws IOException {
         if(getStream().isOpen()) {
            getStream().close();
         }
      }

      @Override
      BlobChannel createStream(Path tempFile) throws IOException {
         return new TransactionChannel(tempFile);
      }

      @Override
      byte[] getDigest() throws IOException {
         MessageDigest digest;

         try {
            digest = MessageDigest.getInstance("MD5");
         }
         catch(NoSuchAlgorithmException e) {
            throw new IOException("Failed to calculate digest of blob at " + path, e);
         }

         try(InputStream input = Files.newInputStream(tempFile)) {
            byte[] buffer = new byte[1024 * 1024];
            int len;

            while((len = input.read(buffer)) >= 0) {
               digest.update(buffer, 0, len);
            }
         }

         return digest.digest();
      }
   }

   private static final class TransactionOutputStream extends DigestOutputStream {
      public TransactionOutputStream(OutputStream out) throws NoSuchAlgorithmException {
         super(out, MessageDigest.getInstance("MD5"));
      }

      @Override
      public void close() throws IOException {
         if(!closed) {
            closed = true;
            super.close();
         }
      }

      boolean closed = false;
   }

   private static final class TransactionChannel implements BlobChannel {
      public TransactionChannel(Path path) throws IOException {
         delegate = FileChannel.open(
            path, StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE);
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
         return delegate.position(newPosition) == null ? null : this;
      }

      @Override
      public long size() throws IOException {
         return delegate.size();
      }

      @Override
      public SeekableByteChannel truncate(long size) throws IOException {
         return delegate.truncate(size) == null ? null : this;
      }

      @Override
      public boolean isOpen() {
         return delegate.isOpen();
      }

      @Override
      public void close() throws IOException {
         delegate.close();
      }

      private final FileChannel delegate;
   }
}
