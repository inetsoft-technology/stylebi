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
      return openReadChannel(name, null);
   }

   public BlobChannel openReadChannel(String name, String orgId) throws IOException {
      return getStorage(orgId).getReadChannel(name);
   }

   public BlobTransaction<Metadata> beginTransaction() {
      return beginTransaction(null);
   }

   public BlobTransaction<Metadata> beginTransaction(String orgId) {
      return getStorage(orgId).beginTransaction();
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
      return length(name, null);
   }

   public long length(String name, String orgId) {
      try {
         BlobStorage<Metadata> storage = orgId == null ? getStorage() : getStorage(orgId);
         return storage.getLength(name);
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
      copy(source, null, target, null);
   }

   public void copy(BlockFile source, String sourceOrgId, BlockFile target, String targetOrgId)
      throws IOException
   {
      try(SeekableInputStream inChannel = source.openInputStream(sourceOrgId);
          TransactionChannel outChannel = target.openWriteChannel(targetOrgId))
      {
         Tool.copy(inChannel, outChannel);
         outChannel.commit();
      }
   }

   @Override
   public void close() throws Exception {
      getStorage().close();
   }

   public BlobStorage<Metadata> getStorage(String orgID) {
      orgID = orgID == null ? OrganizationManager.getInstance().getCurrentOrgID() : orgID;
      String storeID = orgID.toLowerCase() + "__" + "mvBlock";
      return SingletonManager.getInstance(BlobStorage.class, storeID, true);
   }

   public BlobStorage<Metadata> getStorage() {
      return getStorage(OrganizationManager.getInstance().getCurrentOrgID());
   }

   public static final class Metadata implements Serializable {
   }
}
