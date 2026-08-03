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
package inetsoft.web.admin.general;

/*
 * Test strategy
 *
 * DataSpaceSettingsService.doBackup exports the storage contents to a temp file via
 * StorageTransfer, writes that file to external storage under a computed path, and returns a
 * BackupResult carrying both the status message and the path. The behaviors covered here:
 *
 * [G1] Success returns a non-null path, and that path is exactly what was written to external
 *      storage.
 * [G2] A failure during the write returns a BackupResult with path() == null, so a caller can
 *      detect failure without parsing the localized status string.
 * [G3] model.aiSnapshot() == true routes the backup under the "ai-snapshots" folder instead of
 *      "backup", so deleteRedundantBackupFiles (which only lists "backup") can never prune it.
 * [G4] A model with aiSnapshot absent (default false) preserves the pre-existing "backup" folder
 *      layout exactly.
 * [G5] deleteRedundantBackupFiles() prunes "backup/" to asset.backup.count on the assumption
 *      that the file it is about to write lands back in that same folder. An AI snapshot writes
 *      to "ai-snapshots/" instead, so pruning must be skipped for it - otherwise an AI snapshot
 *      would delete an operator's backup and replace it with nothing. A real (non-AI) backup
 *      must still prune as before.
 * [G6] Finding 6: nothing else prunes "ai-snapshots/", so a successful AI-snapshot write now
 *      prunes that folder to ai.snapshot.count (default 10; < 1 disables), keeping exactly that
 *      many files - unlike the pre-existing off-by-one in deleteRedundantBackupFiles, which is
 *      out of scope here. This never touches "backup/".
 */

import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.sree.security.SecurityProvider;
import inetsoft.storage.*;
import inetsoft.util.FileSystemService;
import inetsoft.util.Tool;
import inetsoft.util.config.InetsoftConfig;
import inetsoft.util.config.KeyValueConfig;
import inetsoft.web.admin.general.model.BackupDataModel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
@ExtendWith(MockitoExtension.class)
class DataSpaceSettingsServiceTest {
   @Mock private SecurityEngine securityEngine;
   @Mock private SecurityProvider securityProvider;
   @Mock private FileSystemService fileSystemService;
   @Mock private KeyValueEngine keyValueEngine;
   @Mock private BlobEngine blobEngine;
   @Mock private ExternalStorageService externalStorageService;
   @Mock private OrganizationManager organizationManager;

   private MockedStatic<SreeEnv> sreeEnvStatic;
   private MockedStatic<InetsoftConfig> configStatic;
   private MockedStatic<SecurityEngine> securityEngineStatic;
   private MockedStatic<OrganizationManager> organizationManagerStatic;
   private MockedStatic<Tool> toolStatic;
   private DataSpaceSettingsService service;

   @BeforeEach
   void setUp() throws IOException {
      sreeEnvStatic = mockStatic(SreeEnv.class, withSettings().lenient());
      configStatic = mockStatic(InetsoftConfig.class, withSettings().lenient());
      securityEngineStatic = mockStatic(SecurityEngine.class, withSettings().lenient());
      organizationManagerStatic = mockStatic(OrganizationManager.class, withSettings().lenient());
      toolStatic = mockStatic(Tool.class, withSettings().lenient());

      // ActionRecord's constructor (built by doBackup for auditing) reaches through these
      // statics; none of it is under test here, so just make it a harmless no-op path.
      securityEngineStatic.when(SecurityEngine::getSecurity).thenReturn(securityEngine);
      lenient().when(securityEngine.getSecurityProvider()).thenReturn(securityProvider);
      organizationManagerStatic.when(OrganizationManager::getInstance).thenReturn(organizationManager);
      lenient().when(organizationManager.getCurrentOrgID()).thenReturn("host-org");
      organizationManagerStatic.when(OrganizationManager::getCurrentOrgName).thenReturn("Host Org");
      toolStatic.when(Tool::getHost).thenReturn("test-host");

      // Force StorageTransfer.create() down the DirectStorageTransfer path so the mocked
      // key-value/blob engines are used instead of the cluster-routed mapdb implementation.
      KeyValueConfig keyValueConfig = mock(KeyValueConfig.class, withSettings().lenient());
      lenient().when(keyValueConfig.getType()).thenReturn("database");
      InetsoftConfig config = mock(InetsoftConfig.class, withSettings().lenient());
      lenient().when(config.getKeyValue()).thenReturn(keyValueConfig);
      configStatic.when(InetsoftConfig::getInstance).thenReturn(config);

      lenient().when(keyValueEngine.idStream()).thenReturn(Stream.empty());
      lenient().when(fileSystemService.getCacheTempFile("backup", ".zip"))
         .thenAnswer(inv -> Files.createTempFile("backup-test", ".zip").toFile());
      lenient().when(externalStorageService.getAvailableFile(anyString(), eq(1)))
         .thenAnswer(inv -> inv.getArgument(0));

      service = new DataSpaceSettingsService(securityEngine, fileSystemService, keyValueEngine,
                                              blobEngine, externalStorageService);
   }

   @AfterEach
   void tearDown() {
      sreeEnvStatic.close();
      configStatic.close();
      securityEngineStatic.close();
      organizationManagerStatic.close();
      toolStatic.close();
   }

   // [G1] success returns the path that was written to external storage
   @Test
   void doBackup_returnsWrittenPath_onSuccess() throws Exception {
      BackupDataModel model = BackupDataModel.builder().dataspace("data").build();

      BackupResult result = service.doBackup(model);

      assertNotNull(result.path());
      verify(externalStorageService).write(eq(result.path()), any(Path.class), isNull());
      // Proves the export actually ran rather than doBackup merely writing an empty/untouched
      // temp file: StorageTransfer.create(keyValueEngine, blobEngine).exportContents(output)
      // reads the key-value store via idStream() to serialize its contents into the zip.
      verify(keyValueEngine, atLeastOnce()).idStream();
   }

   // [G2] failure during the external-storage write returns a null path
   @Test
   void doBackup_returnsNullPath_onFailure() throws Exception {
      doThrow(new IOException("disk full"))
         .when(externalStorageService).write(anyString(), any(Path.class), isNull());
      BackupDataModel model = BackupDataModel.builder().dataspace("data").build();

      BackupResult result = service.doBackup(model);

      assertNull(result.path());
      assertTrue(result.status().contains("Failed"));
   }

   // [G3] aiSnapshot(true) routes the backup under ai-snapshots/ instead of backup/
   @Test
   void doBackup_aiSnapshotTrue_writesUnderAiSnapshotsFolder() throws Exception {
      BackupDataModel model = BackupDataModel.builder().dataspace("data").aiSnapshot(true).build();

      BackupResult result = service.doBackup(model);

      assertNotNull(result.path());
      assertTrue(result.path().startsWith("ai-snapshots/"),
                 "expected path under ai-snapshots/ but was: " + result.path());
   }

   // [G4] aiSnapshot absent (default false) preserves the pre-existing backup/ layout
   @Test
   void doBackup_aiSnapshotAbsent_writesUnderBackupFolder() throws Exception {
      BackupDataModel model = BackupDataModel.builder().dataspace("data").build();

      assertFalse(model.aiSnapshot());

      BackupResult result = service.doBackup(model);

      assertNotNull(result.path());
      assertTrue(result.path().startsWith("backup/"),
                 "expected path under backup/ but was: " + result.path());
   }

   // [G5] an AI snapshot must not prune the operator's backup/ folder
   @Test
   void aiSnapshotDoesNotPruneTheOperatorsBackupFolder() throws Exception {
      // Without the call-site guard this deletes a real backup and puts nothing back, because the
      // snapshot lands in ai-snapshots/ rather than backup/. The guard means listFiles is never
      // even reached for an AI snapshot, so this stub is lenient - its point is to prove that
      // even though data is available to prune, nothing gets deleted.
      sreeEnvStatic.when(() -> SreeEnv.getProperty("asset.backup.count")).thenReturn("2");
      lenient().when(externalStorageService.listFiles("backup"))
         .thenReturn(List.of("data-20260101.zip", "data-20260102.zip", "data-20260103.zip"));

      service.doBackup(BackupDataModel.builder().dataspace("admin-chg-1").aiSnapshot(true).build());

      verify(externalStorageService, never()).delete(anyString());
   }

   // [G5] the counterpart: the guard must not disable pruning for ordinary backups
   @Test
   void aRealBackupStillPrunes() throws Exception {
      sreeEnvStatic.when(() -> SreeEnv.getProperty("asset.backup.count")).thenReturn("2");
      when(externalStorageService.listFiles("backup"))
         .thenReturn(List.of("data-20260101.zip", "data-20260102.zip", "data-20260103.zip"));

      service.doBackup(BackupDataModel.builder().dataspace("data").build());

      verify(externalStorageService, atLeastOnce()).delete(anyString());
   }

   // [G6] over the limit: prunes down to exactly ai.snapshot.count, oldest first
   @Test
   void aiSnapshotPruning_deletesDownToTheConfiguredCount() throws Exception {
      sreeEnvStatic.when(() -> SreeEnv.getProperty("ai.snapshot.count")).thenReturn("2");
      when(externalStorageService.listFiles("ai-snapshots")).thenReturn(List.of(
         "admin-1-20260101.zip", "admin-2-20260102.zip", "admin-3-20260103.zip"));

      service.doBackup(BackupDataModel.builder().dataspace("admin-chg-4").aiSnapshot(true).build());

      // 3 files, keep 2 -> delete exactly 1, the oldest.
      verify(externalStorageService, times(1)).delete(anyString());
      verify(externalStorageService).delete("ai-snapshots" + File.separator + "admin-1-20260101.zip");
   }

   // [G6] at or under the limit: nothing is deleted
   @Test
   void aiSnapshotPruning_doesNothingWhenAtOrUnderTheLimit() throws Exception {
      sreeEnvStatic.when(() -> SreeEnv.getProperty("ai.snapshot.count")).thenReturn("5");
      when(externalStorageService.listFiles("ai-snapshots"))
         .thenReturn(List.of("admin-1-20260101.zip", "admin-2-20260102.zip"));

      service.doBackup(BackupDataModel.builder().dataspace("admin-chg-5").aiSnapshot(true).build());

      verify(externalStorageService, never()).delete(anyString());
   }

   // [G6] a value below 1 disables pruning, matching deleteRedundantBackupFiles's convention
   @Test
   void aiSnapshotPruning_disabledWhenCountIsBelowOne() throws Exception {
      sreeEnvStatic.when(() -> SreeEnv.getProperty("ai.snapshot.count")).thenReturn("0");
      lenient().when(externalStorageService.listFiles("ai-snapshots")).thenReturn(List.of(
         "admin-1-20260101.zip", "admin-2-20260102.zip", "admin-3-20260103.zip"));

      service.doBackup(BackupDataModel.builder().dataspace("admin-chg-6").aiSnapshot(true).build());

      verify(externalStorageService, never()).delete(anyString());
   }

   // [G6] pruning ai-snapshots/ must never list or delete anything under backup/
   @Test
   void aiSnapshotPruning_neverTouchesTheBackupFolder() throws Exception {
      sreeEnvStatic.when(() -> SreeEnv.getProperty("ai.snapshot.count")).thenReturn("1");
      when(externalStorageService.listFiles("ai-snapshots"))
         .thenReturn(List.of("admin-1-20260101.zip", "admin-2-20260102.zip"));

      service.doBackup(BackupDataModel.builder().dataspace("admin-chg-7").aiSnapshot(true).build());

      verify(externalStorageService, never()).listFiles("backup");
      verify(externalStorageService, never()).delete(startsWith("backup"));
   }
}
