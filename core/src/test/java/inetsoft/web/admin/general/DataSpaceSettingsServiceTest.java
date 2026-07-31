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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
}
