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
import java.net.URI;
import java.nio.file.*;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.spi.FileSystemProvider;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class CacheFileSystem extends FileSystem {
   @SuppressWarnings("unchecked")
   CacheFileSystem(CacheFileSystemProvider provider, URI uri, PathService pathService,
                   String storeId)
   {
      this.provider = provider;
      this.uri = uri;
      this.pathService = pathService;
      this.storeId = storeId;
      this.storage = SingletonManager.getInstance(BlobStorage.class, storeId, true);

      if(!storage.exists("/")) {
         CacheMetadata metadata = new CacheMetadata(System.currentTimeMillis());
         metadata.setChildren(new String[0]);

         try {
            storage.createDirectory("/", metadata);
         }
         catch(IOException e) {
            throw new RuntimeException("Failed to create root directory", e);
         }
      }
   }

   @Override
   public FileSystemProvider provider() {
      return provider;
   }

   public URI getUri() {
      return uri;
   }

   @Override
   public void close() throws IOException {
      storageLock.lock();

      try {
         if(storage != null) {
            storage.close();
            storage = null;
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
         return storage != null;
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
      return pathService.getSeparator();
   }

   @Override
   public Iterable<Path> getRootDirectories() {
      return List.of(pathService.createRoot(Name.create("/", "/")));
   }

   public CachePath getWorkingDirectory() {
      return pathService.createRoot(Name.create("/", "/"));
   }

   @Override
   public Iterable<FileStore> getFileStores() {
      return List.of();
   }

   @Override
   public Set<String> supportedFileAttributeViews() {
      return Set.of();
   }

   @Override
   public Path getPath(String first, String... more) {
      return pathService.parsePath(first, more);
   }

   public URI toUri(CachePath path) {
      return pathService.toUri(uri, (CachePath) path.toAbsolutePath());
   }

   @Override
   public PathMatcher getPathMatcher(String syntaxAndPattern) {
      return pathService.createPathMatcher(syntaxAndPattern);
   }

   @Override
   public UserPrincipalLookupService getUserPrincipalLookupService() {
      throw new UnsupportedOperationException();
   }

   @Override
   public WatchService newWatchService() throws IOException {
      return new CacheWatchService(storage, pathService);
   }

   String getStoreId() {
      return storeId;
   }

   BlobStorage<CacheMetadata> getBlobStorage() {
      return storage;
   }

   private final CacheFileSystemProvider provider;
   private final URI uri;
   private final PathService pathService;
   private final String storeId;
   private final Lock storageLock = new ReentrantLock();
   private BlobStorage<CacheMetadata> storage;

}
