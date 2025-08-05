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

import inetsoft.sree.internal.cluster.MessageEvent;
import inetsoft.sree.internal.cluster.MessageListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.List;
import java.util.Objects;

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
    * @param cache    the blob storage cache.
    * @param preload  preload the local cache in a background thread on initialization.
    */
   public CachedBlobStorage(String id, Path cacheDir, KeyValueStorage<Blob<T>> storage,
                            BlobCache cache, boolean preload)
   {
      super(id, storage);
      Objects.requireNonNull(id, "The storage identifier cannot be null");
      Objects.requireNonNull(cacheDir, "The cache directory cannot be null");
      Objects.requireNonNull(cache, "The blob storage cache cannot be null");
      this.id = id;
      this.cache = cache;
      getCluster().addMessageListener(this);

      if(preload) {
         Thread thread = new Thread(this::preload);
         thread.setDaemon(true);
         thread.start();
      }
   }

   @Override
   protected InputStream getInputStream(Blob<T> blob) throws IOException {
      Path path = cache.get(id, blob);
      return Files.newInputStream(path);
   }

   @Override
   protected BlobChannel getReadChannel(Blob<T> blob) throws IOException {
      Path path = cache.get(id, blob);
      return new BlobReadChannel(FileChannel.open(path, StandardOpenOption.READ));
   }

   @Override
   protected void commit(Blob<T> blob, Path tempFile) throws IOException {
      cache.put(id, blob, tempFile);
   }

   @Override
   protected void delete(Blob<T> blob) throws IOException {
      cache.remove(id, blob);
   }

   @Override
   protected Path copyToTemp(Blob<T> blob) throws IOException {
      Path path = cache.get(id, blob);
      Path tempFile = createTempFile("blob", ".dat");
      Files.copy(path, tempFile, StandardCopyOption.REPLACE_EXISTING);
      return tempFile;
   }

   @Override
   protected Path createTempFile(String prefix, String suffix) throws IOException {
      return cache.createTempFile(id, prefix, suffix);
   }

   @Override
   protected boolean isLocal() {
      return false;
   }

   @Override
   public void deleteBlobStorage() throws Exception {
      List<String> files = stream()
         .map(Blob::getPath)
         .toList();

      for(String file : files) {
         delete(file);
      }

      close();
   }

   @Override
   public void close() throws Exception {
      getCluster().removeMessageListener(this);
      super.close();
   }

   @Override
   public void messageReceived(MessageEvent event) {
      if(event.getMessage() instanceof ClearBlobCacheMessage message) {

         if(getId().equals(message.getStoreId())) {
            String digest = message.getDigest();

            try {
               cache.remove(id, digest);
            }
            catch(IOException e) {
               logger.warn("Failed to delete local cache file {}", digest, e);
            }
         }
      }
   }

   private void preload() {
      getStorage().stream()
         .map(KeyValuePair::getValue)
         .filter(b -> b.getDigest() != null)
         .forEach(this::preload);
   }

   private void preload(Blob<T> blob) {
      try {
         cache.get(id, blob);
      }
      catch(Exception e) {
         logger.warn(
            "Failed to copy file {} in storage {} to the local cache", blob.getPath(), id, e);
      }
   }

   private final String id;
   private final BlobCache cache;
   private final Logger logger = LoggerFactory.getLogger(getClass());
}
