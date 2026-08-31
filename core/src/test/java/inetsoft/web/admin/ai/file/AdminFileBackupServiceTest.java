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
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("core")
@ExtendWith(MockitoExtension.class)
class AdminFileBackupServiceTest {
   @Mock private DataSpaceSettingsService dataSpaceSettingsService;

   private AdminFileBackupService service;

   @BeforeEach
   void setup() {
      service = new AdminFileBackupService(dataSpaceSettingsService);
   }

   @Test
   void backupReturnsThePathDoBackupReportedAndTagsTheModel() throws Exception {
      ArgumentCaptor<BackupDataModel> captor = ArgumentCaptor.forClass(BackupDataModel.class);
      when(dataSpaceSettingsService.doBackup(any()))
         .thenReturn(new BackupResult("Success", "backup/wiz-manual-nightly-export-123.zip"));

      String path = service.backup("nightly-export");

      assertEquals("backup/wiz-manual-nightly-export-123.zip", path);
      verify(dataSpaceSettingsService).doBackup(captor.capture());
      BackupDataModel model = captor.getValue();
      assertFalse(model.aiSnapshot(),
                  "aiSnapshot must be false so this lands in the counted/pruned backup folder, "
                  + "not the ai-snapshots folder");
      assertEquals("wiz-manual-nightly-export", model.dataspace());
   }

   @Test
   void backupThrowsWithTaskAndStatusWhenDoBackupReportsNoPath() {
      when(dataSpaceSettingsService.doBackup(any()))
         .thenReturn(new BackupResult("Failed to back up storage: disk full", null));

      IOException ex = assertThrows(IOException.class, () -> service.backup("nightly-export"));

      assertTrue(ex.getMessage().contains("nightly-export"), ex.getMessage());
      assertTrue(ex.getMessage().contains("Failed to back up storage: disk full"), ex.getMessage());
      verify(dataSpaceSettingsService).doBackup(any());
   }

   @Test
   void backupRejectsTaskWithPathTraversal() {
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                                                   () -> service.backup("../../etc/passwd"));
      assertTrue(ex.getMessage().contains("task"), ex.getMessage());
      verifyNoInteractions(dataSpaceSettingsService);
   }

   @Test
   void backupRejectsTaskWithSlash() {
      assertThrows(IllegalArgumentException.class, () -> service.backup("nightly/export"));
      verifyNoInteractions(dataSpaceSettingsService);
   }

   @Test
   void backupRejectsTaskWithBackslash() {
      assertThrows(IllegalArgumentException.class, () -> service.backup("nightly\\export"));
      verifyNoInteractions(dataSpaceSettingsService);
   }

   @Test
   void backupRejectsNullOrBlankTask() {
      assertThrows(IllegalArgumentException.class, () -> service.backup(null));
      assertThrows(IllegalArgumentException.class, () -> service.backup("   "));
      verifyNoInteractions(dataSpaceSettingsService);
   }
}
