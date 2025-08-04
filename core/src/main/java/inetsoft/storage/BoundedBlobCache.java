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

import com.github.benmanes.caffeine.cache.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public class BoundedBlobCache extends BlobCache {
   BoundedBlobCache(Path baseDir, BlobEngine engine, long maxSize) {
      super(baseDir, engine);
      //noinspection DataFlowIssue
      cache = Caffeine.newBuilder()
         .maximumWeight(maxSize * 1024 * 1024)
         .weigher(BoundedBlobCache::getWeight)
         .evictionListener(BoundedBlobCache::onEvicted)
         .build();
      loadEntries(baseDir, cache);
   }
   private static int getWeight(CacheKey key, Path path) {
      return (int) (path.toFile().isFile() ? path.toFile().length() : 0L);
   }
   private static void onEvicted(CacheKey key, Path path, RemovalCause cause) {
      if(path.toFile().isFile()) {
         try {
            Files.delete(path);
         }
         catch(IOException e) {
            LOG.warn("Failed to delete cached blob file {}", path, e);
         }
      }
   }
   private static void loadEntries(Path base, Cache<CacheKey, Path> cache) {
      File[] storeDirs = base.toFile().listFiles();
      if(storeDirs != null) {
         for(File storeDir : storeDirs) {
            if(storeDir.isDirectory()) {
               String storeId = storeDir.getName();
               File[] blobDirs = storeDir.listFiles();
               if(blobDirs != null) {
                  for(File blobDir : blobDirs) {
                     String prefix = blobDir.getName();
                     if(!"temp".equals(prefix)) {
                        File[] blobs = blobDir.listFiles();
                        if(blobs != null) {
                           for(File blob : blobs) {
                              if(blob.isFile()) {
                                 String suffix = blob.getName();
                                 CacheKey key = new CacheKey(storeId, prefix + suffix);
                                 cache.put(key, blob.toPath());
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   @Override
   public Path get(String storeId, Blob<?> blob) throws IOException {
      return cache.get(new CacheKey(storeId, blob.getDigest()), this::copyToCache);
   }

   private Path copyToCache(CacheKey key) {
      try {
         return copyToCache(key.storeId, key.digest);
      }
      catch(IOException e) {
         throw new RuntimeException("Failed to copy blob to local cache", e);
      }
   }

   @Override
   protected void put(String storeId, String digest, Path file, Path tempFile) throws IOException {
      super.put(storeId, digest, file, tempFile);
      cache.put(new CacheKey(storeId, digest), file);
   }

   @Override
   protected void remove(String storeId, String digest, Path file) throws IOException {
      super.remove(storeId, digest, file);
      cache.invalidate(new CacheKey(storeId, digest));
   }

   private final Cache<CacheKey, Path> cache;
   private static final Logger LOG = LoggerFactory.getLogger(BoundedBlobCache.class);

   private static final class CacheKey {
      public CacheKey(String storeId, String digest) {
         this.storeId = storeId;
         this.digest = digest;
      }

      @Override
      public boolean equals(Object o) {
         if(o == null || getClass() != o.getClass()) {
            return false;
         }
         CacheKey cacheKey = (CacheKey) o;
         return Objects.equals(storeId, cacheKey.storeId) && Objects.equals(digest, cacheKey.digest);
      }

      @Override
      public int hashCode() {
         return Objects.hash(storeId, digest);
      }

      private final String storeId;
      private final String digest;
   }
}
