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

import javax.swing.filechooser.FileSystemView;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.nio.file.spi.FileSystemProvider;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

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

         CacheFileSystem fileSystem = new CacheFileSystem(this, storeId, env);
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
      return getFileSystem(cachePath).newInputStream(cachePath, options);
   }

   @Override
   public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options,
                                             FileAttribute<?>... attrs) throws IOException
   {
      CachePath cachePath = checkPath(path);
      return getFileSystem(cachePath).newByteChannel(cachePath, options, attrs);
   }

   @Override
   public DirectoryStream<Path> newDirectoryStream(Path dir,
                                                   DirectoryStream.Filter<? super Path> filter)
      throws IOException
   {
      CachePath cachePath = checkPath(dir);
      return getFileSystem(cachePath).newDirectoryStream(cachePath, filter);
   }

   @Override
   public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
      CachePath cachePath = checkPath(dir);
      getFileSystem(cachePath).createDirectory(cachePath, attrs);
   }

   @Override
   public void delete(Path path) throws IOException {
      CachePath cachePath = checkPath(path);
      getFileSystem(cachePath).delete(cachePath);
   }

   @Override
   public void copy(Path source, Path target, CopyOption... options) throws IOException {
      CachePath sourcePath = checkPath(source);
      CachePath targetPath = checkPath(target);
      getFileSystem(sourcePath).copy(sourcePath, targetPath, options);
   }

   @Override
   public void move(Path source, Path target, CopyOption... options) throws IOException {
      CachePath sourcePath = checkPath(source);
      CachePath targetPath = checkPath(target);
      getFileSystem(sourcePath).move(sourcePath, targetPath, options);
   }

   @Override
   public boolean isSameFile(Path path, Path path2) {
      if(path.equals(path2)) {
         return true;
      }

      if(!(path instanceof CachePath && path2 instanceof CachePath)) {
         return false;
      }

      CachePath cachePath = (CachePath) path;
      CachePath cachePath2 = (CachePath) path2;

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
   public FileStore getFileStore(Path path) {
      throw new UnsupportedOperationException();
   }

   @Override
   public void checkAccess(Path path, AccessMode... modes) throws IOException {
      CachePath cachePath = checkPath(path);
      // todo check that file exists
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
      throw new UnsupportedOperationException();
   }

   @Override
   public Map<String, Object> readAttributes(Path path, String attributes,
                                             LinkOption... options) throws IOException
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
}
