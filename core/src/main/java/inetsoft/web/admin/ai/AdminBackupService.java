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

import inetsoft.web.admin.general.BackupResult;
import inetsoft.web.admin.general.DataSpaceSettingsService;
import inetsoft.web.admin.general.model.BackupDataModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;

/*
 * The snapshot here is taken through the live-tested {@link DataSpaceSettingsService#doBackup}
 * path - the same one the EM "Backup" action uses - rather than `inetsoft.setup.StorageService`.
 * `StorageService` is a setup-context API: it is only ever wired up as a bean inside
 * `DirectStorageConfig`, which `StorageContext` instantiates as its own standalone
 * `AnnotationConfigApplicationContext` for the offline setup tool. That bean is never imported
 * into the running web application context, so injecting `StorageService` here made this service
 * - and, transitively, `AdminAiController`, whose constructor takes it - impossible for Spring to
 * construct on a live server (`NoSuchBeanDefinitionException`). `DataSpaceSettingsService` is a
 * normal `@Service` in the web application context and its `doBackup` is exercised by real traffic,
 * so it is the correct dependency.
 *
 * There is deliberately no restore method. Restoring storage on a running cluster is untested and
 * will not work - recovering from a snapshot is an offline operation that requires a restart. An
 * administrator who needs to recover from a snapshot taken here must either restore it manually
 * (offline) or revert the individual changes recorded in the admin-chat audit records.
 */
@Service
public class AdminBackupService {
   @Autowired
   public AdminBackupService(DataSpaceSettingsService dataSpaceSettingsService) {
      this.dataSpaceSettingsService = dataSpaceSettingsService;
   }

   /**
    * Creates a Tier-2 snapshot of the live storage, named for the given transaction, via
    * {@link DataSpaceSettingsService#doBackup}.
    *
    * @param transactionId the admin-chat transaction this snapshot protects.
    *
    * @return the external-storage path of the snapshot that was written.
    *
    * @throws IllegalArgumentException if {@code transactionId} is null/blank or is not a safe,
    *         single path segment (see {@link #requireSafePathSegment}).
    * @throws IOException if the snapshot did not produce a usable artifact.
    */
   public String backup(String transactionId) throws Exception {
      requireSafePathSegment(transactionId, "transactionId", "transaction id");

      BackupDataModel model = BackupDataModel.builder()
         .dataspace("admin-" + transactionId)
         .aiSnapshot(true)
         .build();
      BackupResult result = dataSpaceSettingsService.doBackup(model);

      if(result.path() == null) {
         throw new IOException(
            "Failed to snapshot storage for transaction '" + transactionId + "': " +
            result.status());
      }

      return result.path();
   }

   /**
    * Rejects any null/blank value, or any value containing a path separator ({@code /} or
    * {@code \}) or {@code ..}, so that a caller-supplied {@code transactionId} can never be used
    * to construct an unsafe dataspace/path segment. Fails loud with a field-named message rather
    * than silently truncating or sanitizing the input.
    */
   private static void requireSafePathSegment(String value, String fieldName, String description) {
      if(value == null || value.isBlank() ||
         value.contains("/") || value.contains("\\") || value.contains(".."))
      {
         throw new IllegalArgumentException(fieldName + ": invalid " + description);
      }
   }

   private final DataSpaceSettingsService dataSpaceSettingsService;
}
