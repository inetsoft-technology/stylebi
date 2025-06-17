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
package inetsoft.uql.asset;

import inetsoft.sree.security.OrganizationManager;
import inetsoft.storage.BlobStorage;
import inetsoft.storage.BlobTransaction;
import inetsoft.util.SingletonManager;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.time.Instant;

@SingletonManager.Singleton(EmbeddedTableStorage.Reference.class)
public class EmbeddedTableStorage implements AutoCloseable {
   public EmbeddedTableStorage() {
   }

   private BlobStorage<Metadata> getStorage() {
      String storeID = OrganizationManager.getInstance().getCurrentOrgID().toLowerCase() +  "__pdata";
      return SingletonManager.getInstance(BlobStorage.class, storeID, true);
   }

   private BlobStorage<Metadata> getStorage(String orgID) {
      String storeID = orgID.toLowerCase() +  "__pdata";
      return SingletonManager.getInstance(BlobStorage.class, storeID, true);
   }

   public boolean tableExists(String path) {
      try {
         return getStorage().exists(path);
      }
      catch(Exception ignore) {
         return false;
      }
   }

   public InputStream readTable(String path) throws IOException {
      try {
         return getStorage().getInputStream(path);
      }
      catch(FileNotFoundException ignore) {
         return null;
      }
   }

   public void writeTable(String path, InputStream input) throws IOException {
      try(BlobTransaction<Metadata> tx = getStorage().beginTransaction();
          OutputStream output = tx.newStream(path, new Metadata()))
      {
         IOUtils.copy(input, output);
         tx.commit();
      }
   }

   public void renameTable(String oldPath, String newPath) throws IOException {
      getStorage().rename(oldPath, newPath);
   }

   public void removeTable(String path) throws IOException {
      getStorage().delete(path);
   }

   public void listBlobs(String outputFile, String orgID) throws IOException {
      BlobStorage<Metadata> storage = getStorage(orgID);

      if(storage != null) {
         storage.listBlobs(outputFile);
      }
   }

   public Instant getLastModified(String path) throws FileNotFoundException {
      return getStorage().getLastModified(path);
   }

   public static EmbeddedTableStorage getInstance() {
      return SingletonManager.getInstance(EmbeddedTableStorage.class);
   }

   @Override
   public void close() throws Exception {
      getStorage().close();
   }

   public static final class Metadata implements Serializable {
   }

   public static final class Reference extends SingletonManager.Reference<EmbeddedTableStorage> {
      @Override
      public EmbeddedTableStorage get(Object... parameters) {
         if(instance == null) {
            instance = new EmbeddedTableStorage();
         }

         return instance;
      }

      @Override
      public void dispose() {

      }

      private EmbeddedTableStorage instance;
   }
}
