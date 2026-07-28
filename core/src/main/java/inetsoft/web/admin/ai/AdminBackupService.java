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
import inetsoft.util.FileSystemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;

/*
 * Spike findings (Task 6, Step 1):
 *
 * 1. `grep -rn "new StorageService\|StorageService(" community enterprise --include=*.java`
 *    shows `StorageService` is constructed in two contexts:
 *      - Offline/setup: `StorageInitializer.installPlugins(...)` does
 *        `try(StorageService service = new StorageService(configDirectory.getAbsolutePath()))`
 *        - a short-lived instance opened and closed around one operation, used before the
 *        application context (and its long-lived engines) exists.
 *      - Runtime/live: `DirectStorageConfig.storageService(InetsoftConfig, KeyValueEngine,
 *        BlobEngine)` (community/core/src/main/java/inetsoft/setup/DirectStorageConfig.java:101-104)
 *        is annotated `@Bean`, producing a single Spring-managed `StorageService` that wraps the
 *        *already-open* `KeyValueEngine`/`BlobEngine` beans used by the running server.
 *
 * 2. `DirectStorageConfig.java:103-104` confirms the live bean is built from the same
 *    `keyValueEngine`/`blobEngine` beans injected elsewhere in the app context - i.e. there is
 *    exactly one open storage backend per running server, and the bean is just a thin wrapper
 *    around it. `StorageInitializer.java:205-206` confirms the *other* construction path
 *    (`new StorageService(dir)`) is only ever used offline, opens its own engines against a
 *    directory, and is always closed (`try`-with-resources) before the server starts.
 *
 * 3. Because the live `StorageService` is a singleton Spring bean (not a fresh instance we would
 *    have to open ourselves), injecting it here does not open a second set of engines against the
 *    same directory - it reuses the one the running server already holds. Opening a *second*
 *    `StorageService(String)` against the live config directory while the server is running would
 *    risk contending with the live engines (e.g. file/db locks held by local or embedded backends);
 *    we avoid that entirely by depending on the bean instead of constructing our own.
 *
 * 4. Decision gate: BRANCH A - live `StorageService.backup(File)` / `restore(File)`, injected as
 *    the Spring bean, is safe to call directly from a running server. Implemented below.
 *
 * Backup file location: per `DataSpaceSettingsService.doBackup` (which uses
 * `this.fileSystemService.getCacheTempFile("backup", ".zip")`,
 * community/core/src/main/java/inetsoft/web/admin/general/DataSpaceSettingsService.java:104),
 * server-side backup artifacts belong under `FileSystemService.getInstance()`'s cache directory.
 * This service creates a dedicated, stable subdirectory there ("admin-ai-backups") rather than a
 * one-off cache temp file, since Tier-2 backups must remain resolvable by name for a later
 * `restore` call rather than being a fire-and-forget temp file.
 *
 * NOTE: Tier-2 backups produced here are stored server-side, on local disk, with no retention
 * policy. Hardening (retention/expiry, replication to the configured external storage provider,
 * access control on the backup directory) is a follow-up, not in scope for this task.
 */
@Service
public class AdminBackupService {
   /**
    * Production constructor. Wires the live, Spring-managed {@link StorageService} bean (see
    * spike findings above) and resolves a stable, writable server-side directory for Tier-2
    * backup artifacts.
    */
   @Autowired
   public AdminBackupService(StorageService storage) {
      this(storage, resolveBackupDir());
   }

   /**
    * Test seam: allows tests to point the service at an arbitrary {@link StorageService} and
    * directory (e.g. a JUnit {@code @TempDir}) without going through Spring or
    * {@link FileSystemService}.
    */
   AdminBackupService(StorageService storage, File backupDir) {
      this.storage = storage;
      this.backupDir = backupDir;
   }

   private static File resolveBackupDir() {
      File dir = FileSystemService.getInstance().getCacheFile("admin-ai-backups");

      if(dir == null) {
         throw new IllegalStateException(
            "Unable to resolve the Tier-2 backup directory under the cache directory");
      }

      if(!dir.exists() && !dir.mkdirs() && !dir.exists()) {
         throw new IllegalStateException(
            "Unable to create Tier-2 backup directory: " + dir.getAbsolutePath());
      }

      return dir;
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
      storage.restore(resolve(backupRef));
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

   private final StorageService storage;
   private final File backupDir;
}
