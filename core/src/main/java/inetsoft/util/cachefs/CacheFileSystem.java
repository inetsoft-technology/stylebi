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

import inetsoft.storage.BlobStorage;
import inetsoft.util.SingletonManager;

import java.io.IOException;
import java.io.Serializable;
import java.nio.file.*;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.spi.FileSystemProvider;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class CacheFileSystem extends FileSystem {
   @SuppressWarnings("unchecked")
   public CacheFileSystem(CacheFileSystemProvider provider, String storeId, Map<String, ?> env) {
      this.provider = provider;
      this.storeId = storeId;
      this.blobStorage = SingletonManager.getInstance(BlobStorage.class, storeId, true);
   }

   @Override
   public FileSystemProvider provider() {
      return provider;
   }

   @Override
   public void close() throws IOException {
      storageLock.lock();

      try {
         if(blobStorage != null) {
            blobStorage.close();
         }
      }
      catch(IOException e) {
         throw new IOException(e);
      }
      catch(Exception e) {
         throw new IOException(e.getMessage(), e);
      }
      finally {
         storageLock.unlock();
         provider.removeFileSystem(this);
      }
   }

   @Override
   public boolean isOpen() {
      storageLock.lock();

      try {
         return blobStorage != null;
      }
      finally {
         storageLock.unlock();
      }
   }

   @Override
   public boolean isReadOnly() {
      return false;
   }

   @Override
   public String getSeparator() {
      return "/";
   }

   @Override
   public Iterable<Path> getRootDirectories() {
      return null;
   }

   @Override
   public Iterable<FileStore> getFileStores() {
      return null;
   }

   @Override
   public Set<String> supportedFileAttributeViews() {
      return Set.of();
   }

   @Override
   public Path getPath(String first, String... more) {
      return null;
   }

   @Override
   public PathMatcher getPathMatcher(String syntaxAndPattern) {
      return null;
   }

   @Override
   public UserPrincipalLookupService getUserPrincipalLookupService() {
      return null;
   }

   @Override
   public WatchService newWatchService() throws IOException {
      return null;
   }

   String getStoreId() {
      return storeId;
   }

   private final CacheFileSystemProvider provider;
   private final String storeId;
   private final Lock storageLock = new ReentrantLock();
   private BlobStorage<Metadata> blobStorage;

   public static final class Metadata implements Serializable {
   }
}
