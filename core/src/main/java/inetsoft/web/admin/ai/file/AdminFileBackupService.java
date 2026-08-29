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
package inetsoft.web.admin.ai.file;

import inetsoft.web.admin.general.BackupResult;
import inetsoft.web.admin.general.DataSpaceSettingsService;
import inetsoft.web.admin.general.model.BackupDataModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;

/*
 * A sibling of {@link inetsoft.web.admin.ai.AdminBackupService}, not a modification to it:
 * that service exists to protect a single pending admin-chat transaction (tagged
 * {@code aiSnapshot(true)}, landing in the small, separately-retained {@code ai-snapshots/}
 * folder). This service is a general-purpose, human-requested "give me a full backup right now"
 * export - functionally identical in intent to clicking the EM "Backup Now" button, just
 * triggered through chat - so it deliberately tags {@code aiSnapshot(false)}, landing the export
 * in the same counted/pruned {@code backup/} folder (bounded by {@code asset.backup.count}) the
 * EM button itself uses, rather than competing with genuine Tier-2 transaction snapshots for the
 * much smaller {@code ai.snapshot.count} retention window.
 */
@Service
public class AdminFileBackupService {
   @Autowired
   public AdminFileBackupService(DataSpaceSettingsService dataSpaceSettingsService) {
      this.dataSpaceSettingsService = dataSpaceSettingsService;
   }

   /**
    * Exports the live storage to external storage via {@link DataSpaceSettingsService#doBackup},
    * tagged with the given task description.
    *
    * @param task a short, human-readable description of why the backup was requested. Also used,
    *             literally, as a dataspace-name path segment, so it is validated as a single safe
    *             path segment.
    *
    * @return the external-storage path of the backup that was written.
    *
    * @throws IllegalArgumentException if {@code task} is null/blank or is not a safe, single path
    *         segment (see {@link #requireSafePathSegment}).
    * @throws IOException if the backup did not produce a usable artifact.
    */
   public String backup(String task) throws Exception {
      requireSafePathSegment(task, "task", "task description");

      BackupDataModel model = BackupDataModel.builder()
         .dataspace("wiz-manual-" + task)
         .aiSnapshot(false)
         .build();
      BackupResult result = dataSpaceSettingsService.doBackup(model);

      if(result.path() == null) {
         throw new IOException(
            "Failed to back up storage for '" + task + "': " + result.status());
      }

      return result.path();
   }

   /**
    * Rejects any null/blank value, or any value containing a path separator ({@code /} or
    * {@code \}) or {@code ..}, so that a caller-supplied {@code task} can never be used to
    * construct an unsafe dataspace/path segment. Fails loud with a field-named message rather
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
