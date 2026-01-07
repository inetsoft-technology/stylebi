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

import inetsoft.sree.security.*;
import inetsoft.storage.BlobStorage;
import inetsoft.storage.BlobTransaction;
import inetsoft.util.SingletonManager;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@SingletonManager.Singleton(EmbeddedTableStorage.Reference.class)
public class EmbeddedTableStorage implements AutoCloseable {
   public EmbeddedTableStorage() {
   }

   private BlobStorage<Metadata> getStorage() {
      return getStorage(null);
   }

   private BlobStorage<Metadata> getStorage(String orgId) {
      orgId = orgId == null ? OrganizationManager.getInstance().getCurrentOrgID() : orgId;
      String storeID = orgId.toLowerCase() + "__pdata";
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

   public boolean tableExists(String path, String orgId) {
      try {
         return getStorage(orgId).exists(path);
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

   public InputStream readTable(String path, String orgId) throws IOException {
      try {
         return getStorage(orgId).getInputStream(path);
      }
      catch(FileNotFoundException ignore) {
         return null;
      }
   }

   public void writeTable(String path, InputStream input) throws IOException {
      writeTable(path, input, false);
   }

   public void writeTable(String path, InputStream input, boolean temp) throws IOException {
      try(BlobTransaction<Metadata> tx = getStorage().beginTransaction();
          OutputStream output = tx.newStream(path, new Metadata(temp)))
      {
         IOUtils.copy(input, output);
         tx.commit();
      }
   }

   public void renameTable(String oldPath, String newPath) throws IOException {
      getStorage().rename(oldPath, newPath);
   }

   public void removeTable(String path) throws IOException {
      removeTable(path, null);
   }

   public void removeTable(String path, String orgId) throws IOException {
      getStorage(orgId).delete(path);
   }

   public Instant getLastModified(String path) throws FileNotFoundException {
      return getLastModified(path, null);
   }

   public Instant getLastModified(String path, String orgId) throws FileNotFoundException {
      return getStorage(orgId).getLastModified(path);
   }

   public boolean isTempTable(String path) {
      return isTempTable(path, null);
   }

   public boolean isTempTable(String path, String orgId) {
      try {
         return getStorage(orgId).getMetadata(path).temp;
      }
      catch(FileNotFoundException e) {
         return false;
      }
   }

   public void removeExpiredTempTables() {
      Instant twoWeeksAgo = Instant.now().minus(14, ChronoUnit.DAYS);
      SecurityProvider provider = SecurityEngine.getSecurity().getSecurityProvider();
      String[] orgIds = provider.getOrganizationIDs();

      for(String orgId : orgIds) {
         getStorage(orgId).paths().filter(path -> {
            if(!isTempTable(path, orgId)) {
               return false;
            }

            try {
               Instant lastModified = getLastModified(path, orgId);
               return lastModified.isBefore(twoWeeksAgo);
            }
            catch(FileNotFoundException ignore) {
            }

            return false;
         }).forEach(path -> {
            try {
               LOG.debug("Removing expired table {}", path);
               removeTable(path, orgId);
            }
            catch(IOException e) {
               throw new RuntimeException(e);
            }
         });
      }
   }

   public static EmbeddedTableStorage getInstance() {
      return SingletonManager.getInstance(EmbeddedTableStorage.class);
   }

   @Override
   public void close() throws Exception {
      getStorage().close();
   }

   public static final class Metadata implements Serializable {
      public Metadata() {
         this.temp = false;
      }

      public Metadata(boolean temp) {
         this.temp = temp;
      }

      private final boolean temp;
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

   private static final Logger LOG = LoggerFactory.getLogger(EmbeddedTableStorage.class);
}
