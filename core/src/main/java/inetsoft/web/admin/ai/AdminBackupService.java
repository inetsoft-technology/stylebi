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
package inetsoft.web.admin.ai;

import inetsoft.setup.StorageService;
import inetsoft.storage.BlobStorage;
import inetsoft.storage.BlobStorageManager;
import inetsoft.storage.BlobTransaction;
import inetsoft.util.FileSystemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;

/*
 * This service depends on the live, Spring-managed `StorageService` bean
 * (see `DirectStorageConfig.storageService`) rather than constructing its own. That bean wraps
 * the `KeyValueEngine`/`BlobEngine` the running server already has open, so reusing it does not
 * open a second set of engines against the same directory; constructing a fresh
 * `StorageService(String)` against the live config directory while the server is running would
 * risk contending with those already-open engines (e.g. file/db locks held by local or embedded
 * backends).
 *
 * NOTE: Tier-2 backups produced here are stored server-side, on local disk, then published to
 * cluster-visible BlobStorage so a restore routed to a different node can still find them. There
 * is no retention policy yet. Hardening (retention/expiry, replication to the configured external
 * storage provider, access control on the backup directory) is a follow-up, not in scope for this
 * task.
 */
@Service
public class AdminBackupService {
   /**
    * Production constructor. {@code StorageService.backup(File)} necessarily writes a local file, so
    * the ZIP is published to cluster-visible {@link BlobStorage} afterwards: a backup that exists
    * only on the producing node is unusable once the load balancer routes {@code restore} elsewhere.
    */
   @Autowired
   public AdminBackupService(StorageService storage, BlobStorageManager blobStorageManager) {
      this(storage, blobStorageManager, resolveBackupDir());
   }

   /** Test seam: arbitrary {@link StorageService}, blob manager and staging directory. */
   AdminBackupService(StorageService storage, BlobStorageManager blobStorageManager,
                      File backupDir)
   {
      this.storage = storage;
      this.blobStorageManager = blobStorageManager;
      this.backupDir = backupDir;
      ensureBackupDir(backupDir);
   }

   private static File resolveBackupDir() {
      File dir = FileSystemService.getInstance().getCacheFile("admin-ai-backups");

      if(dir == null) {
         throw new IllegalStateException(
            "Unable to resolve the Tier-2 backup directory under the cache directory");
      }

      ensureBackupDir(dir);
      return dir;
   }

   /** Creates {@code dir} if it does not already exist, failing loud if it cannot be created. */
   private static void ensureBackupDir(File dir) {
      if(!dir.exists() && !dir.mkdirs() && !dir.exists()) {
         throw new IllegalStateException(
            "Unable to create Tier-2 backup directory: " + dir.getAbsolutePath());
      }
   }

   /**
    * Creates a Tier-2 backup ZIP of the live storage, named uniquely for the given transaction.
    *
    * @param transactionId the admin-chat transaction this backup protects.
    *
    * @return the backup reference (the ZIP file's name within the backup directory), suitable
    *         for a later {@link #restore(String)} call.
    *
    * @throws IllegalArgumentException if {@code transactionId} is null/blank or is not a safe,
    *         single path segment (see {@link #requireSafePathSegment}).
    */
   public String backup(String transactionId) throws Exception {
      requireSafePathSegment(transactionId, "transactionId", "transaction id");
      String name = "admin-" + transactionId + "-" + System.currentTimeMillis() + ".zip";
      // Defense in depth: re-validate the constructed name resolves inside backupDir even
      // though transactionId was already checked above.
      File file = resolveWithinBackupDir(name, "transactionId", "transaction id");
      storage.backup(file);

      // No write(path, InputStream) exists; publish through a transaction, per AutoSaveUtils.
      try(BlobTransaction<Serializable> tx = blobs().beginTransaction();
          OutputStream out = tx.newStream(blobPath(name), null);
          InputStream in = new FileInputStream(file))
      {
         in.transferTo(out);
         out.flush();
         tx.commit();
      }

      return name;
   }

   /**
    * Restores storage from a previously produced backup.
    *
    * @param backupRef a reference returned by {@link #backup(String)}.
    *
    * @throws IllegalArgumentException if {@code backupRef} is null/blank or is not a safe,
    *         single path segment (see {@link #requireSafePathSegment}).
    * @throws Exception if the backup file cannot be found or restore fails.
    */
   public void restore(String backupRef) throws Exception {
      requireSafePathSegment(backupRef, "backupRef", "backup reference");
      File file = resolve(backupRef);

      if(!file.exists()) {
         String path = blobPath(backupRef);
         BlobStorage<Serializable> blobs = blobs();

         if(!blobs.exists(path)) {
            throw new FileNotFoundException(
               "Admin backup not found locally or in shared storage: " + backupRef);
         }

         try(InputStream in = blobs.getInputStream(path)) {
            Files.copy(in, file.toPath());
         }
      }

      storage.restore(file);
   }

   /**
    * Resolves a backup reference to its file within the backup directory.
    *
    * @throws IllegalArgumentException if {@code backupRef} is null/blank, is not a safe, single
    *         path segment, or would resolve outside {@code backupDir}.
    */
   public File resolve(String backupRef) {
      return resolveWithinBackupDir(backupRef, "backupRef", "backup reference");
   }

   /**
    * Resolves {@code value} as a file directly inside {@code backupDir}, rejecting anything that
    * is not a safe, single path segment (see {@link #requireSafePathSegment}) and, as defense in
    * depth, anything whose canonical parent directory is not {@code backupDir} itself (e.g. via a
    * symlink) even if the raw string looked safe.
    */
   private File resolveWithinBackupDir(String value, String fieldName, String description) {
      requireSafePathSegment(value, fieldName, description);
      File file = new File(backupDir, value);

      try {
         File canonicalBackupDir = backupDir.getCanonicalFile();
         File canonicalParent = file.getCanonicalFile().getParentFile();

         if(canonicalParent == null || !canonicalParent.equals(canonicalBackupDir)) {
            throw new IllegalArgumentException(fieldName + ": invalid " + description);
         }
      }
      catch(IOException e) {
         throw new IllegalArgumentException(fieldName + ": invalid " + description, e);
      }

      return file;
   }

   /**
    * Rejects any null/blank value, or any value containing a path separator ({@code /} or
    * {@code \}) or {@code ..}, so that a caller-supplied {@code transactionId}/{@code backupRef}
    * can never escape {@code backupDir}. Fails loud with a field-named message rather than
    * silently truncating or sanitizing the input.
    */
   private static void requireSafePathSegment(String value, String fieldName, String description) {
      if(value == null || value.isBlank() ||
         value.contains("/") || value.contains("\\") || value.contains(".."))
      {
         throw new IllegalArgumentException(fieldName + ": invalid " + description);
      }
   }

   private BlobStorage<Serializable> blobs() {
      return blobStorageManager.getStorage(BLOB_STORE_ID, false);
   }

   /** Namespaced so admin backups cannot collide with other blob data. */
   private static String blobPath(String name) {
      return "admin-backups/" + name;
   }

   private static final String BLOB_STORE_ID = "adminChatBackups";

   private final StorageService storage;
   private final BlobStorageManager blobStorageManager;
   private final File backupDir;
}
