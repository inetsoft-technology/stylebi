/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.web.admin.general;

/*
 * Test strategy
 *
 * deleteRedundantBackupFiles() prunes operator backup zips in backup/ down to at most
 * asset.backup.count entries; doBackup() calls it once before writing a new backup and
 * once more after a successful write. Redmine #75898 covers two defects in the prune
 * method itself, plus a design gap in how doBackup() sequenced the single prune call it
 * used to make:
 *
 * [G1] At exactly the retention limit, the prune call deletes nothing. The loop used to
 *      run one extra iteration (i <= deleteCount instead of i < deleteCount).
 * [G2] Over the limit, exactly the surplus count is deleted, and it is the oldest files.
 * [G3] The oldest file is still deleted correctly when two backup timestamps are far enough
 *      apart (multiple years) that their difference as a long overflows int. The sort
 *      comparator used to narrow the long difference to an int, which can flip sign and
 *      invert the ordering -- deleting the newest backup instead of the oldest.
 * [G4] A non-numeric or unset asset.backup.count disables pruning entirely.
 * [G5] doBackup() used to prune only once, before writing. Combined with the now-correct
 *      prune logic in G1, that leaves one MORE backup than configured in steady-state
 *      operation (prune-to-N, then +1 for the new file, never trimmed back down). doBackup()
 *      now prunes again after a successful write, so the count converges on exactly N.
 *      (Separately, the original *unfixed* loop -- deleting one extra before the write was
 *      even attempted -- could drop asset.backup.count = 1 to zero backups on a failed
 *      write; fixing the loop bound in G1 already prevents that on its own, regardless of
 *      how many times doBackup() prunes. The two-call design here is about correcting the
 *      steady-state count, not about that failure case.)
 * [G6] The second prune must not run when the write fails, since that is precisely the
 *      case the extra prune call exists to protect.
 */

import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.sree.security.SecurityProvider;
import inetsoft.storage.BlobEngine;
import inetsoft.storage.ExternalStorageService;
import inetsoft.storage.KeyValueEngine;
import inetsoft.storage.StorageTransfer;
import inetsoft.util.FileSystemService;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
class DataSpaceSettingsServiceTest {
   private DataSpaceSettingsService service;
   private ExternalStorageService externalStorageService;
   private FileSystemService fileSystemService;
   private MockedStatic<SreeEnv> sreeEnvStatic;
   private MockedStatic<StorageTransfer> storageTransferStatic;
   private MockedStatic<SecurityEngine> securityEngineStatic;

   @BeforeEach
   void setUp() throws Exception {
      externalStorageService = mock(ExternalStorageService.class);
      fileSystemService = mock(FileSystemService.class);
      service = new DataSpaceSettingsService(
         mock(SecurityEngine.class), fileSystemService, mock(KeyValueEngine.class),
         mock(BlobEngine.class), externalStorageService);

      sreeEnvStatic = mockStatic(SreeEnv.class, withSettings().lenient());

      // doBackup() builds an ActionRecord for auditing, which looks up the security
      // provider through the static SecurityEngine.getSecurity() Spring accessor -- stub it
      // so the audit bookkeeping doesn't require a Spring context in this unit test
      securityEngineStatic = mockStatic(SecurityEngine.class, withSettings().lenient());
      SecurityEngine mockedSecurityEngine = mock(SecurityEngine.class);
      when(mockedSecurityEngine.getSecurityProvider()).thenReturn(mock(SecurityProvider.class));
      securityEngineStatic.when(SecurityEngine::getSecurity).thenReturn(mockedSecurityEngine);

      // doBackup() exports through a real, empty temp file; StorageTransfer's own export
      // logic is irrelevant to the prune-sequencing behaviour under test here
      File tempFile = File.createTempFile("backup-test", ".zip");
      tempFile.deleteOnExit();
      when(fileSystemService.getCacheTempFile("backup", ".zip")).thenReturn(tempFile);

      StorageTransfer storageTransfer = mock(StorageTransfer.class);
      storageTransferStatic = mockStatic(StorageTransfer.class, withSettings().lenient());
      storageTransferStatic.when(() -> StorageTransfer.create(any(), any()))
         .thenReturn(storageTransfer);
   }

   @AfterEach
   void tearDown() {
      securityEngineStatic.close();
      storageTransferStatic.close();
      sreeEnvStatic.close();
   }

   // [G1] at exactly the retention limit, no file may be deleted
   @Test
   void retainsAllFilesWhenAtTheLimit() throws Exception {
      stubBackupCount(3);
      stubZips("data-20260101000000.zip", "data-20260102000000.zip", "data-20260103000000.zip");

      service.deleteRedundantBackupFiles();

      verify(externalStorageService, never()).delete(anyString());
   }

   // [G2] over the limit, only the surplus is deleted, and it is the oldest files
   @Test
   void deletesOnlyTheOldestSurplusFiles() throws Exception {
      stubBackupCount(2);
      stubZips("data-20260103000000.zip", "data-20260101000000.zip", "data-20260102000000.zip");

      service.deleteRedundantBackupFiles();

      verify(externalStorageService, times(1)).delete("backup" + java.io.File.separator +
                                                        "data-20260101000000.zip");
      verify(externalStorageService, never())
         .delete("backup" + java.io.File.separator + "data-20260102000000.zip");
      verify(externalStorageService, never())
         .delete("backup" + java.io.File.separator + "data-20260103000000.zip");
   }

   // [G3] a pair of timestamps whose long difference overflows int must not invert the sort
   @Test
   void deletesTheOldestEvenWhenTimestampsAreYearsApart() throws Exception {
      stubBackupCount(1);
      // difference between these two timestamps overflows int and flips sign under the
      // narrowing (int) cast, which used to sort the newer file first
      stubZips("data-20260101000000.zip", "data-20230101000000.zip");

      service.deleteRedundantBackupFiles();

      verify(externalStorageService, times(1))
         .delete("backup" + java.io.File.separator + "data-20230101000000.zip");
      verify(externalStorageService, never())
         .delete("backup" + java.io.File.separator + "data-20260101000000.zip");
   }

   // [G4] pruning is disabled unless asset.backup.count is a positive integer
   @Test
   void doesNotDeleteWhenBackupCountIsNotConfigured() throws Exception {
      sreeEnvStatic.when(() -> SreeEnv.getProperty("asset.backup.count")).thenReturn(null);

      service.deleteRedundantBackupFiles();

      verify(externalStorageService, never()).listFiles(anyString());
      verify(externalStorageService, never()).delete(anyString());
   }

   // [G5] a successful backup converges on exactly the configured count: the prune before
   // the write clears only genuine surplus, and the prune after the write trims the file
   // that was just added back down
   @Test
   void doBackupPrunesAgainAfterASuccessfulWrite() throws Exception {
      DataSpaceSettingsService spyService = spy(service);
      doNothing().when(spyService).deleteRedundantBackupFiles();

      spyService.doBackup(null);

      verify(spyService, times(2)).deleteRedundantBackupFiles();
   }

   // [G6] a failed write must not trigger the second prune -- that is exactly the failure
   // the extra call exists to protect against
   @Test
   void doBackupDoesNotPruneAgainAfterAFailedWrite() throws Exception {
      DataSpaceSettingsService spyService = spy(service);
      doNothing().when(spyService).deleteRedundantBackupFiles();
      // getAvailableFile is unstubbed and so returns null for the destination path here --
      // use any() rather than anyString(), which does not match a null argument
      doThrow(new IOException("simulated write failure"))
         .when(externalStorageService).write(any(), any(), any());

      String status = spyService.doBackup(null);

      assertTrue(status.contains("Failed"));
      verify(spyService, times(1)).deleteRedundantBackupFiles();
   }

   private void stubBackupCount(int count) {
      sreeEnvStatic.when(() -> SreeEnv.getProperty("asset.backup.count"))
         .thenReturn(String.valueOf(count));
   }

   private void stubZips(String... names) {
      when(externalStorageService.listFiles("backup")).thenReturn(List.of(names));
   }
}
