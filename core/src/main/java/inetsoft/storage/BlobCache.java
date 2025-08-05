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

package inetsoft.storage;

import inetsoft.util.SingletonManager;
import inetsoft.util.config.BlobConfig;
import inetsoft.util.config.InetsoftConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@SingletonManager.Singleton(BlobCache.Reference.class)
public class BlobCache {
   BlobCache(Path baseDir, BlobEngine engine) {
      this.baseDir = baseDir;
      this.engine = engine;
   }

   public static BlobCache getInstance() {
      return SingletonManager.getInstance(BlobCache.class);
   }

   public Path get(String storeId, Blob<?> blob) throws IOException {
      return copyToCache(storeId, blob.getDigest());
   }

   protected Path copyToCache(String storeId, String digest) throws IOException {
      Path path = getPath(storeId, digest, baseDir);
      if(!path.toFile().exists()) {
         Path lockDir = getLockDir(storeId);
         FileLock lock = new FileLock(lockDir, storeId + "-" + digest);
         try {
            if(lock.tryLock(5L, TimeUnit.MINUTES)) {
               try {
                  if(!path.toFile().exists()) {
                     if(engine.exists(storeId, digest)) {
                        Path tempFile = createTempFile(storeId, "blob", ".dat");
                        engine.read(storeId, digest, tempFile);
                        Files.createDirectories(path.getParent());
                        Files.move(tempFile, path, StandardCopyOption.ATOMIC_MOVE);
                     }
                     else {
                        LOG.warn("Blob file is missing: {}", digest);
                     }
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
            throw new IOException("Thread interrupted while waiting for file lock", e);
         }
      }
      return path;
   }

   public void put(String storeId, Blob<?> blob, Path tempFile) throws IOException {
      Path path = getPath(storeId, blob, baseDir);
      engine.write(storeId, blob.getDigest(), tempFile);
      if(path.toFile().exists()) {
         remove(storeId, blob.getDigest(), path);
      }
      else {
         put(storeId, blob.getDigest(), path, tempFile);
      }
   }

   protected void put(String storeId, String digest, Path file, Path tempFile) throws IOException {
      Files.createDirectories(file.getParent());
      Files.move(tempFile, file, StandardCopyOption.ATOMIC_MOVE);
   }

   public void remove(String storeId, Blob<?> blob) throws IOException {
      Path path = getPath(storeId, blob, baseDir);
      remove(storeId, blob.getDigest(), path);
   }

   public void remove(String storeId, String digest) throws IOException {
      String dir = digest.substring(0, 2);
      String file = digest.substring(2);
      Path path = baseDir.resolve(storeId).resolve(dir).resolve(file);
      remove(storeId, digest, path);
   }

   protected void remove(String storeId, String digest, Path file) throws IOException {
      if(file.toFile().exists()) {
         Files.delete(file);
      }
   }

   public Path createTempFile(String storeId, String prefix, String suffix) throws IOException {
      Path tempDir = getLockDir(storeId).getParent();
      return Files.createTempFile(tempDir, prefix, suffix);
   }

   private Path getPath(String storeId, Blob<?> blob, Path base) throws IOException {
      if(blob.getDigest() == null) {
         throw new IOException("The blob at " + blob.getPath() + " is a directory");
      }

      return getPath(storeId, blob.getDigest(), base);
   }

   private Path getPath(String storeId, String digest, Path base) throws IOException {
      String dir = digest.substring(0, 2);
      String name = digest.substring(2);
      return base.resolve(storeId).resolve(dir).resolve(name);
   }

   private Path getLockDir(String storeId) {
      Path path = baseDir.resolve(storeId).resolve("temp/locks");
      //noinspection ResultOfMethodCallIgnored
      path.toFile().mkdirs();
      return path;
   }

   private final Path baseDir;
   private final BlobEngine engine;
   private static final Logger LOG = LoggerFactory.getLogger(BlobCache.class);

   public static final class Reference extends SingletonManager.Reference<BlobCache> {
      @Override
      public BlobCache get(Object... parameters) {
         lock.lock();

         try {
            if(cache == null) {
               BlobConfig config = InetsoftConfig.getInstance().getBlob();
               Path baseDir = Paths.get(Objects.requireNonNull(config.getCacheDirectory()));
               BlobEngine engine = BlobEngine.getInstance();
               Long maxSize = config.getCacheMaxSize();

               if(maxSize != null && maxSize > 0) {
                  cache = new BoundedBlobCache(baseDir, engine, maxSize);
               }
               else {
                  cache = new BlobCache(baseDir, engine);
               }
            }

            return cache;
         }
         finally {
            lock.unlock();
         }
      }

      @Override
      public void dispose() {

         lock.lock();
         try {
            cache = null;
         }
         finally {
            lock.unlock();
         }
      }

      private BlobCache cache = null;
      private final Lock lock = new ReentrantLock();
   }
}
