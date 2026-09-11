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

import inetsoft.web.admin.general.AiSnapshotInfo;
import inetsoft.web.admin.general.BackupResult;
import inetsoft.web.admin.general.DataSpaceSettingsService;
import inetsoft.web.admin.general.model.BackupDataModel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * {@code AdminBackupService} no longer touches {@code inetsoft.setup.StorageService} - that bean
 * only exists in the standalone setup-tool context and is never wired into the running web
 * application, which is exactly why the service could not be constructed on a live server (see
 * the class javadoc on {@link AdminBackupService}). The live-tested path is
 * {@link DataSpaceSettingsService#doBackup}, so these tests mock that dependency instead of
 * standing up a real storage engine round trip.
 */
@Tag("core")
@ExtendWith(MockitoExtension.class)
class AdminBackupServiceTest {
   @Mock private DataSpaceSettingsService dataSpaceSettingsService;

   private AdminBackupService service;

   @BeforeEach
   void setup() {
      service = new AdminBackupService(dataSpaceSettingsService);
   }

   @Test
   void backupReturnsThePathDoBackupReportedAndTagsTheModel() throws Exception {
      ArgumentCaptor<BackupDataModel> captor = ArgumentCaptor.forClass(BackupDataModel.class);
      when(dataSpaceSettingsService.doBackup(any()))
         .thenReturn(new BackupResult("Success", "ai-snapshots/admin-chg-1-123.zip"));

      String path = service.backup("chg-1");

      assertEquals("ai-snapshots/admin-chg-1-123.zip", path);
      verify(dataSpaceSettingsService).doBackup(captor.capture());
      BackupDataModel model = captor.getValue();
      assertTrue(model.aiSnapshot(), "aiSnapshot must be true so this is exempt from redundant-backup cleanup");
      assertEquals("admin-chg-1", model.dataspace());
   }

   @Test
   void backupThrowsWithTransactionIdAndStatusWhenDoBackupReportsNoPath() {
      when(dataSpaceSettingsService.doBackup(any()))
         .thenReturn(new BackupResult("Failed to back up storage: disk full", null));

      IOException ex = assertThrows(IOException.class, () -> service.backup("chg-1"));

      assertTrue(ex.getMessage().contains("chg-1"), ex.getMessage());
      assertTrue(ex.getMessage().contains("Failed to back up storage: disk full"), ex.getMessage());
      verify(dataSpaceSettingsService).doBackup(any());
   }

   @Test
   void backupRejectsTransactionIdWithPathTraversal() {
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                                                   () -> service.backup("../../etc/passwd"));
      assertTrue(ex.getMessage().contains("transactionId"), ex.getMessage());
      verifyNoInteractions(dataSpaceSettingsService);
   }

   @Test
   void backupRejectsTransactionIdWithSlash() {
      assertThrows(IllegalArgumentException.class, () -> service.backup("chg/1"));
      verifyNoInteractions(dataSpaceSettingsService);
   }

   @Test
   void backupRejectsNullOrBlankTransactionId() {
      assertThrows(IllegalArgumentException.class, () -> service.backup(null));
      assertThrows(IllegalArgumentException.class, () -> service.backup("   "));
      verifyNoInteractions(dataSpaceSettingsService);
   }

   // -------------------------------------------------------------------------
   // listSnapshots -- the read-only counterpart to backup()
   // -------------------------------------------------------------------------

   @Test
   void listSnapshotsDelegatesToDataSpaceSettingsService() {
      List<AiSnapshotInfo> expected = List.of(
         new AiSnapshotInfo("ai-snapshots/admin-chg-1-20260101120000.zip", 20260101120000L));
      when(dataSpaceSettingsService.listAiSnapshots("chg-1")).thenReturn(expected);

      List<AiSnapshotInfo> actual = service.listSnapshots("chg-1");

      assertEquals(expected, actual);
      verify(dataSpaceSettingsService).listAiSnapshots("chg-1");
   }

   @Test
   void listSnapshotsReturnsEmptyListWhenNoneSurvive() {
      when(dataSpaceSettingsService.listAiSnapshots("chg-1")).thenReturn(List.of());

      List<AiSnapshotInfo> actual = service.listSnapshots("chg-1");

      assertTrue(actual.isEmpty());
   }

   @Test
   void listSnapshotsRejectsTransactionIdWithPathTraversal() {
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                                                   () -> service.listSnapshots("../../etc/passwd"));
      assertTrue(ex.getMessage().contains("transactionId"), ex.getMessage());
      verifyNoInteractions(dataSpaceSettingsService);
   }

   @Test
   void listSnapshotsRejectsNullOrBlankTransactionId() {
      assertThrows(IllegalArgumentException.class, () -> service.listSnapshots(null));
      assertThrows(IllegalArgumentException.class, () -> service.listSnapshots("   "));
      verifyNoInteractions(dataSpaceSettingsService);
   }
}
