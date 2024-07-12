/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.mv.fs.internal;

import inetsoft.mv.fs.BlockFile;
import inetsoft.mv.util.SeekableInputStream;
import inetsoft.mv.util.TransactionChannel;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.storage.*;
import inetsoft.util.*;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class BlockFileStorage implements AutoCloseable {
   public BlockFileStorage() {
   }

   public static BlockFileStorage getInstance() {
      return SingletonManager.getInstance(BlockFileStorage.class);
   }

   public BlobChannel openReadChannel(String name) throws IOException {
      return getStorage().getReadChannel(name);
   }

   public BlobTransaction<Metadata> beginTransaction() {
      return getStorage().beginTransaction();
   }

   public long lastModified(String name) {
      try {
         return getStorage().getLastModified(name).toEpochMilli();
      }
      catch(FileNotFoundException ignore) {
         return 0L;
      }
   }

   public long length(String name) {
      try {
         return getStorage().getLength(name);
      }
      catch(FileNotFoundException ignore) {
         return 0L;
      }
   }

   public boolean exists(String name) {
      return getStorage().exists(name);
   }

   public void delete(String name) throws IOException {
      getStorage().delete(name);
   }

   public void rename(BlockFile oldFile, BlockFile newFile) throws IOException {
      if((oldFile instanceof CacheBlockFile) && (newFile instanceof CacheBlockFile)) {
         FileSystemService fs = FileSystemService.getInstance();
         File source = fs.getCacheFile(oldFile.getName());
         File target = fs.getCacheFile(newFile.getName());
         Files.move(
            source.toPath(), target.toPath(),
            StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      }
      else if((oldFile instanceof CacheBlockFile) && (newFile instanceof StorageBlockFile)) {
         copy(oldFile, newFile);
      }
      else if((oldFile instanceof StorageBlockFile) && (newFile instanceof CacheBlockFile)) {
         copy(oldFile, newFile);
      }
      else if((oldFile instanceof StorageBlockFile) && (newFile instanceof StorageBlockFile)) {
         getStorage().rename(oldFile.getName(), newFile.getName());
      }
   }

   public void copy(BlockFile source, BlockFile target) throws IOException {
      try(SeekableInputStream inChannel = source.openInputStream();
          TransactionChannel outChannel = target.openWriteChannel())
      {
         Tool.copy(inChannel, outChannel);
         outChannel.commit();
      }
   }

   @Override
   public void close() throws Exception {
      getStorage().close();
   }

   private BlobStorage<Metadata> getStorage() {
      String storeID = OrganizationManager.getInstance().getCurrentOrgID() + "__" + "mvBlock";
      return SingletonManager.getInstance(BlobStorage.class, storeID, true);
   }

   public static final class Metadata implements Serializable {
   }
}
