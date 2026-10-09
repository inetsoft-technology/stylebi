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
import inetsoft.storage.BlobStorageManager;
import inetsoft.storage.BlobTransaction;
import inetsoft.util.ConfigurationContext;
import inetsoft.util.FileSystemService;
import jakarta.annotation.PreDestroy;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

public class EmbeddedTableStorage implements AutoCloseable {
   public EmbeddedTableStorage(BlobStorageManager blobStorageManager) {
      this.blobStorageManager = blobStorageManager;
   }

   private BlobStorage<Metadata> getStorage() {
      return getStorage(null);
   }

   private BlobStorage<Metadata> getStorage(String orgId) {
      orgId = orgId == null ? OrganizationManager.getInstance().getCurrentOrgID() : orgId;
      String storeID = orgId.toLowerCase() + "__pdata";
      return blobStorageManager.getStorage(storeID, true);
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

   public String listBlobs(String orgID) throws IOException {
      BlobStorage<Metadata> storage = getStorage(orgID);

      return storage != null ? storage.listBlobs() : null;
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

   /**
    * Mark a temporary table as not temporary, so that {@link #removeExpiredTempTables()} keeps
    * it. The stored data is copied through a local temp file, never from the in-memory table.
    *
    * @param path the table path.
    *
    * @return {@code true} if the table was temporary and is now marked as not temporary.
    *
    * @throws IOException if the table is missing or could not be written.
    */
   public boolean clearTempFlag(String path) throws IOException {
      if(!isTempTable(path)) {
         return false;
      }

      File file = FileSystemService.getInstance().getCacheTempFile("tdat", "tmp");

      try {
         // read it fully before the write, the write replaces the blob being read
         try(InputStream input = readTable(path)) {
            if(input == null) {
               throw new FileNotFoundException(path);
            }

            try(OutputStream output = new FileOutputStream(file)) {
               IOUtils.copy(input, output);
            }
         }

         try(InputStream input = new FileInputStream(file)) {
            writeTable(path, input, false);
         }
      }
      finally {
         if(!file.delete() && file.exists()) {
            LOG.debug("Failed to delete temp file: {}", file);
         }
      }

      return true;
   }

   /**
    * List the paths of every permanent (non-temp) table in an organization's store. Used by the
    * orphaned permanent snapshot file cleanup (bug #78035) to enumerate deletion candidates; the
    * temporary ones are already covered by {@link #removeExpiredTempTables()}.
    *
    * @param orgId the organization id.
    *
    * @return the data paths of the organization's permanent tables, without the {@code _s.tdat}
    * suffix.
    */
   public List<String> listPermanentTablePaths(String orgId) {
      return getStorage(orgId).paths()
         .filter(path -> !isTempTable(path, orgId))
         .map(path -> path.endsWith(DATA_SUFFIX) ?
            path.substring(0, path.length() - DATA_SUFFIX.length()) : path)
         .collect(Collectors.toList());
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
      return ConfigurationContext.getContext().getSpringBean(EmbeddedTableStorage.class);
   }

   @PreDestroy
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

   private final BlobStorageManager blobStorageManager;
   private static final String DATA_SUFFIX = "_s.tdat";
   private static final Logger LOG = LoggerFactory.getLogger(EmbeddedTableStorage.class);
}
