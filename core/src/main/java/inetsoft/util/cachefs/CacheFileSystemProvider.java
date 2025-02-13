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

import com.google.auto.service.AutoService;
import inetsoft.storage.BlobStorage;

import java.io.*;
import java.lang.ref.Cleaner;
import java.net.URI;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.FileSystem;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.nio.file.spi.FileSystemProvider;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@AutoService(FileSystemProvider.class)
public class CacheFileSystemProvider extends FileSystemProvider {
   @Override
   public String getScheme() {
      return URI_SCHEME;
   }

   @Override
   public FileSystem newFileSystem(URI uri, Map<String, ?> env) throws IOException {
      fileSystemsLock.lock();

      try {
         String storeId = uri.getAuthority();
         Objects.requireNonNull(storeId);

         if(fileSystems.containsKey(storeId)) {
            throw new FileSystemAlreadyExistsException(storeId);
         }

         PathService pathService = new PathService();
         CacheFileSystem fileSystem = new CacheFileSystem(this, uri, pathService, storeId);
         pathService.setFileSystem(fileSystem);

         fileSystems.put(storeId, fileSystem);
         return fileSystem;
      }
      finally {
         fileSystemsLock.unlock();
      }
   }

   @Override
   public FileSystem getFileSystem(URI uri) {
      return getFileSystem(uri, false);
   }

   public CacheFileSystem getFileSystem(URI uri, boolean create) {
      fileSystemsLock.lock();

      try {
         String storeId = uri.getAuthority();
         Objects.requireNonNull(storeId);
         CacheFileSystem fileSystem = fileSystems.get(storeId);

         if(fileSystem == null) {
            if(create) {
               try {
                  fileSystem = (CacheFileSystem) newFileSystem(uri, null);
               }
               catch(IOException e) {
                  throw (FileSystemNotFoundException)
                     new FileSystemNotFoundException(storeId).initCause(e);
               }
            }
            else {
               throw new FileSystemNotFoundException(storeId);
            }
         }

         return fileSystem;
      }
      finally {
         fileSystemsLock.unlock();
      }
   }

   @Override
   public Path getPath(URI uri) {
      String path = uri.getPath();
      return getFileSystem(uri, true).getPath(path);
   }

   @Override
   public InputStream newInputStream(Path path, OpenOption... options) throws IOException {
      CachePath cachePath = checkPath(path);
      return getStorage(cachePath).getInputStream(cachePath.toAbsolutePath().toString());
   }

   @Override
   public OutputStream newOutputStream(Path path, OpenOption... options) throws IOException {
      CachePath cachePath = checkPath(path);
      CacheMetadata metadata = getOrCreateMetadata(cachePath);
      return new BlobOutputStream(cachePath, metadata, getStorage(cachePath), cleaner);
   }

   @Override
   public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options,
                                             FileAttribute<?>... attrs) throws IOException
   {
      CachePath cachePath = checkPath(path);
      CacheMetadata metadata = getOrCreateMetadata(cachePath);
      return new BlobByteChannel(cachePath, metadata, getStorage(cachePath), cleaner);
   }

   @Override
   public DirectoryStream<Path> newDirectoryStream(Path dir,
                                                   DirectoryStream.Filter<? super Path> filter)
      throws IOException
   {
      CachePath cachePath = checkPath(dir);
      BlobStorage<CacheMetadata> storage = getStorage(cachePath);

      if(!storage.exists(cachePath.toAbsolutePath().toString())) {
         throw new NoSuchFileException(dir.toString());
      }

      if(!storage.isDirectory(cachePath.toAbsolutePath().toString())) {
         throw new NotDirectoryException(dir.toString());
      }

      CacheMetadata metadata = storage.getMetadata(cachePath.toAbsolutePath().toString());
      return new CacheDirectoryStream(cachePath, metadata, filter);
   }

   @Override
   public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
      CachePath cachePath = checkPath(dir);
      CacheMetadata metadata = getOrCreateMetadata(cachePath);
      getStorage(cachePath).createDirectory(cachePath.toAbsolutePath().toString(), metadata);

      // update parent folder
      addToParent(cachePath);
   }

   @Override
   public void delete(Path path) throws IOException {
      CachePath cachePath = checkPath(path);

      try {
         getStorage(cachePath).delete(cachePath.toAbsolutePath().toString());
      }
      catch(FileNotFoundException e) {
         throw new NoSuchFileException(path.toString());
      }

      // update parent folder
      removeFromParent(cachePath);
   }

   @Override
   public void copy(Path source, Path target, CopyOption... options) throws IOException {
      CachePath sourcePath = checkPath(source);
      CachePath targetPath = checkPath(target);
      getStorage(sourcePath).copy(
         sourcePath.toAbsolutePath().toString(), targetPath.toAbsolutePath().toString());

      // update parent folder
      addToParent(targetPath);
   }

   @Override
   public void move(Path source, Path target, CopyOption... options) throws IOException {
      CachePath sourcePath = checkPath(source);
      CachePath targetPath = checkPath(target);
      getStorage(sourcePath).rename(
         sourcePath.toAbsolutePath().toString(), targetPath.toAbsolutePath().toString());

      // update parent folders
      removeFromParent(sourcePath);
      addToParent(targetPath);
   }

   private void addToParent(CachePath path) throws IOException {
      BlobStorage<CacheMetadata> storage = getStorage(path);
      CacheMetadata metadata = storage.getMetadata(path.toAbsolutePath().getParent().toString());
      Set<String> children = new TreeSet<>(Arrays.asList(metadata.getChildren()));
      children.add(path.toAbsolutePath().getFileName().toString());
      metadata.setChildren(children.toArray(new String[0]));
      storage.createDirectory(path.toAbsolutePath().getParent().toString(), metadata);
   }

   private void removeFromParent(CachePath path) throws IOException {
      BlobStorage<CacheMetadata> storage = getStorage(path);
      CacheMetadata metadata = storage.getMetadata(path.toAbsolutePath().getParent().toString());
      Set<String> children = new TreeSet<>(Arrays.asList(metadata.getChildren()));
      children.remove(path.toAbsolutePath().getFileName().toString());
      metadata.setChildren(children.toArray(new String[0]));
      storage.createDirectory(path.toAbsolutePath().getParent().toString(), metadata);
   }

   @Override
   public boolean isSameFile(Path path, Path path2) {
      if(path.equals(path2)) {
         return true;
      }

      if(!(path instanceof CachePath cachePath && path2 instanceof CachePath cachePath2)) {
         return false;
      }

      CacheFileSystem fs1 = getFileSystem(cachePath);
      CacheFileSystem fs2 = getFileSystem(cachePath2);

      if(!Objects.equals(fs1.getStoreId(), fs2.getStoreId())) {
         return false;
      }

      return Objects.equals(cachePath, cachePath2);
   }

   @Override
   public boolean isHidden(Path path) throws IOException {
      return false;
   }

   @Override
   public boolean exists(Path path, LinkOption... options) {
      CachePath cachePath = checkPath(path);
      return getStorage(cachePath).exists(cachePath.toAbsolutePath().toString());
   }

   @Override
   public FileStore getFileStore(Path path) {
      throw new UnsupportedOperationException();
   }

   @Override
   public void checkAccess(Path path, AccessMode... modes) throws IOException {
      if(!exists(path)) {
         throw new NoSuchFileException(path.toString());
      }
   }

   @Override
   public <V extends FileAttributeView> V getFileAttributeView(Path path, Class<V> type,
                                                               LinkOption... options)
   {
      return null;
   }

   @Override
   public <A extends BasicFileAttributes> A readAttributes(Path path, Class<A> type,
                                                           LinkOption... options) throws IOException
   {
      if(!type.isAssignableFrom(CacheFileAttributes.class)) {
         throw new UnsupportedOperationException();
      }

      CachePath cachePath = checkPath(path);
      String p = cachePath.toAbsolutePath().toString();
      BlobStorage<CacheMetadata> storage = getStorage(cachePath);

      boolean directory = storage.isDirectory(p);
      long creationTime = storage.getMetadata(p).getCreationTime();
      Instant lastModifiedTime = storage.getLastModified(p);
      long size = storage.getLength(p);

      CacheFileAttributes attrs =
         new CacheFileAttributes(p, directory, creationTime, lastModifiedTime, size);
      return type.cast(attrs);
   }

   @Override
   public Map<String, Object> readAttributes(Path path, String attributes,
                                             LinkOption... options)
   {
      throw new UnsupportedOperationException();
   }

   @Override
   public void setAttribute(Path path, String attribute, Object value, LinkOption... options)
      throws IOException
   {
      throw new UnsupportedOperationException();
   }

   private CachePath checkPath(Path path) {
      if(path instanceof CachePath p) {
         return p;
      }

      throw new ProviderMismatchException(
         "Path " + path + " is not associated with cache file system");
   }

   private CacheFileSystem getFileSystem(CachePath path) {
      return (CacheFileSystem) path.getFileSystem();
   }

   private BlobStorage<CacheMetadata> getStorage(CachePath path) {
      return getFileSystem(path).getBlobStorage();
   }

   private CacheMetadata getOrCreateMetadata(CachePath path) throws IOException {
      BlobStorage<CacheMetadata> storage = getStorage(path);
      String p = path.getFileName().toString();

      if(storage.exists(p)) {
         return storage.getMetadata(p);
      }

      return new CacheMetadata(System.currentTimeMillis());
   }

   @SuppressWarnings("resource")
   void removeFileSystem(CacheFileSystem fs) {
      fileSystemsLock.lock();

      try {
         fileSystems.remove(fs.getStoreId());
      }
      finally {
         fileSystemsLock.unlock();
      }
   }

   private final Lock fileSystemsLock = new ReentrantLock();
   private final Map<String, CacheFileSystem> fileSystems = new HashMap<>();
   static final String URI_SCHEME = "cachefs";
   private final Cleaner cleaner = Cleaner.create();
}
