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
package inetsoft.storage;

import inetsoft.sree.internal.cluster.MessageEvent;
import inetsoft.sree.internal.cluster.MessageListener;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * {@code CachedBlobStorage} is an implementation of {@link BlobStorage} that stores blobs in some
 * external storage and keeps a local file cache.
 *
 * @param <T> the extended metadata type.
 */
public final class CachedBlobStorage<T extends Serializable>
   extends BlobStorage<T> implements MessageListener
{
   /**
    * Creates a new instance of {@code LocalBlobStorage}.
    *
    * @param id       the unique identifier of the blob storage.
    * @param cacheDir the base directory of the local blob cache.
    * @param storage  the key-value store for the blob metadata.
    * @param engine   the blob storage engine.
    * @param preload  preload the local cache in a background thread on initialization.
    *
    * @throws IOException if the local storage directories could not be created.
    */
   public CachedBlobStorage(String id, Path cacheDir, KeyValueStorage<Blob<T>> storage,
                            BlobEngine engine, boolean preload) throws IOException
   {
      super(id, storage);
      Objects.requireNonNull(id, "The storage identifier cannot be null");
      Objects.requireNonNull(cacheDir, "The cache directory cannot be null");
      Objects.requireNonNull(engine, "The blob storage engine cannot be null");
      this.id = id;
      this.base = cacheDir.resolve(id);
      this.tempDir = this.base.resolve("temp");
      this.lockDir = this.tempDir.resolve("locks");
      this.engine = engine;
      Files.createDirectories(this.lockDir);
      getCluster().addMessageListener(this);

      if(preload) {
         Thread thread = new Thread(this::preload);
         thread.setDaemon(true);
         thread.start();
      }
   }

   @Override
   protected InputStream getInputStream(Blob<T> blob) throws IOException {
      Path path = copyToCache(blob);
      return Files.newInputStream(path);
   }

   @Override
   protected BlobChannel getReadChannel(Blob<T> blob) throws IOException {
      Path path = copyToCache(blob);
      return new BlobReadChannel(FileChannel.open(path, StandardOpenOption.READ));
   }

   @Override
   protected void commit(Blob<T> blob, Path tempFile) throws IOException {
      Path path = getPath(blob, base);
      engine.write(id, blob.getDigest(), tempFile);

      if(path.toFile().exists()) {
         Tool.deleteFile(path.toFile());
      }
      else {
         Files.createDirectories(path.getParent());
         Files.move(tempFile, path, StandardCopyOption.ATOMIC_MOVE);
      }
   }

   @Override
   protected void delete(Blob<T> blob) throws IOException {
      Path path = getPath(blob, base);

      if(path.toFile().exists()) {
         Files.delete(path);
      }
   }

   @Override
   protected Path copyToTemp(Blob<T> blob) throws IOException {
      Path path = copyToCache(blob);
      Path tempFile = createTempFile("blob", ".dat");
      Files.copy(path, tempFile, StandardCopyOption.REPLACE_EXISTING);
      return tempFile;
   }

   @Override
   protected Path createTempFile(String prefix, String suffix) throws IOException {
      return Files.createTempFile(tempDir, prefix, suffix);
   }

   @Override
   protected boolean isLocal() {
      return false;
   }

   @Override
   public void close() throws Exception {
      getCluster().removeMessageListener(this);
      super.close();
   }

   @Override
   public void messageReceived(MessageEvent event) {
      if(event.getMessage() instanceof ClearBlobCacheMessage) {
         ClearBlobCacheMessage message = (ClearBlobCacheMessage) event.getMessage();

         if(getId().equals(message.getStoreId())) {
            String digest = message.getDigest();
            String dir = digest.substring(0, 2);
            String file = digest.substring(2);
            Path path = base.resolve(dir).resolve(file);

            if(path.toFile().exists()) {
               try {
                  Files.delete(path);
               }
               catch(IOException e) {
                  logger.warn("Failed to delete local cache file {}", path, e);
               }
            }
         }
      }
   }

   private Path copyToCache(Blob<T> blob) throws IOException {
      Path path = getPath(blob, base);

      if(!path.toFile().exists()) {
         FileLock lock = new FileLock(lockDir, id + "-" + blob.getDigest());

         try {
            if(lock.tryLock(5L, TimeUnit.MINUTES)) {
               try {
                  if(!path.toFile().exists()) {
                     Path tempFile = createTempFile("blob", ".dat");
                     engine.read(id, blob.getDigest(), tempFile);
                     Files.createDirectories(path.getParent());
                     Files.move(tempFile, path, StandardCopyOption.ATOMIC_MOVE);
                  }
               }
               finally {
                  lock.unlock();
               }
            }
            else {
               throw new IOException("Timeout while waiting for file lock");
            }
         }
         catch(InterruptedException e) {
            throw new IOException("Thread interrupted while waiting to lock file", e);
         }
      }

      return path;
   }

   private void preload() {
      getStorage().stream()
         .map(KeyValuePair::getValue)
         .filter(b -> b.getDigest() != null)
         .forEach(this::preload);
   }

   private void preload(Blob<T> blob) {
      try {
         copyToCache(blob);
      }
      catch(Exception e) {
         logger.warn(
            "Failed to copy file {} in storage {} to the local cache", blob.getPath(), id, e);
      }
   }

   private final String id;
   private final Path base;
   private final Path tempDir;
   private final Path lockDir;
   private final BlobEngine engine;
   private final Logger logger = LoggerFactory.getLogger(getClass());
}
