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
package inetsoft.web.admin.deploy;

import inetsoft.sree.AnalyticRepository;
import inetsoft.sree.RepletEngine;
import inetsoft.sree.internal.DeployManagerService;
import inetsoft.sree.internal.DeploymentInfo;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.sree.security.SecurityProvider;
import inetsoft.web.admin.content.repository.model.ImportAssetResponse;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77628: secrets that could not be decrypted on import are reported as warnings. A warning
 * does not mark the import as failed, so the EM shows the import as successful with a warning.
 * Bug #78221: the file import (public REST API, shell, setup) logs the warnings and returns them
 * with the ignored user assets instead of throwing, so its callers can report them too.
 */
@Tag("core")
class DeployServiceImportWarningsTest {
   @BeforeEach
   void setUp() {
      OrganizationManager orgManager = mock(OrganizationManager.class);
      when(orgManager.getCurrentOrgID(any())).thenReturn("host-org");
      orgManagerStatic = mockStatic(OrganizationManager.class);
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);

      securityEngine = mock(SecurityEngine.class);
      when(securityEngine.getSecurityProvider()).thenReturn(mock(SecurityProvider.class));
      service = new DeployService(null, securityEngine, null, null, null, null, null, null, null);
      principal = mock(Principal.class);
   }

   @AfterEach
   void tearDown() {
      orgManagerStatic.close();
   }

   @Test
   void importAssetReturnsWarningsWithoutFailing() throws Exception {
      DeploymentInfo info = mock(DeploymentInfo.class);
      PartialDeploymentJarInfo jarInfo = mock(PartialDeploymentJarInfo.class);
      List<String> warnings = new ArrayList<>();
      when(info.getSelectedEntries()).thenReturn(new ArrayList<>());
      when(info.getDependentAssets()).thenReturn(new ArrayList<>());
      when(info.getJarInfo()).thenReturn(jarInfo);
      when(jarInfo.getDependeciesMap()).thenReturn(new HashMap<>());
      when(info.getProperties()).thenReturn(
         ImportJarProperties.builder().unzipFolderPath("unzip").build());
      when(info.getImportWarnings()).thenReturn(warnings);

      RepletEngine engine = mock(RepletEngine.class);
      when(engine.importAssets(anyBoolean(), any(), same(info), anyBoolean(), any(), any(), any(),
                               any(), any(), any()))
         .thenAnswer(inv -> {
            warnings.add(WARNING);
            return new ArrayList<>();
         });
      AnalyticRepository repository = mock(AnalyticRepository.class);
      when(repository.isWrapperFor(RepletEngine.class)).thenReturn(true);
      when(repository.unwrap(RepletEngine.class)).thenReturn(engine);

      try(MockedStatic<SUtil> sutil = mockStatic(SUtil.class)) {
         sutil.when(SUtil::getRepletRepository).thenReturn(repository);

         ImportAssetResponse response =
            service.importAsset(info, new ArrayList<>(), true, null, principal, null);

         assertFalse(response.failed());
         assertTrue(response.failedAssets().isEmpty(), response.failedAssets().toString());
         assertEquals(List.of(WARNING), response.warnings());
      }
   }

   @Test
   void fileImportWithWarningsDoesNotThrow() throws Exception {
      DeployService spy = fileImportService(ImportAssetResponse.builder()
         .addWarnings(WARNING).build());

      try(MockedStatic<DeployManagerService> dms = mockGetInfo();
          MockedConstruction<DeploymentInfo> ignored = mockConstruction(DeploymentInfo.class))
      {
         ImportAssetResponse result = assertDoesNotThrow(
            () -> spy.importAssets(zip(), new ArrayList<>(), true, principal));
         assertEquals(List.of(WARNING), result.warnings());
      }
   }

   @Test
   void fileImportReturnsWarningsAndIgnoredUserAssets() throws Exception {
      DeployService spy = fileImportService(ImportAssetResponse.builder()
         .addWarnings(WARNING, SCHEDULE_WARNING).addIgnoreUserAssets(USER_ASSET).build());

      try(MockedStatic<DeployManagerService> dms = mockGetInfo();
          MockedConstruction<DeploymentInfo> ignored = mockConstruction(DeploymentInfo.class))
      {
         ImportAssetResponse result =
            spy.importAssets(zip(), new ArrayList<>(), true, null, true, principal);
         assertEquals(List.of(WARNING, SCHEDULE_WARNING), result.warnings());
         assertEquals(List.of(USER_ASSET), result.ignoreUserAssets());
      }
   }

   @Test
   void fileImportWithoutWarningsReturnsEmptyLists() throws Exception {
      DeployService spy = fileImportService(ImportAssetResponse.builder().build());

      try(MockedStatic<DeployManagerService> dms = mockGetInfo();
          MockedConstruction<DeploymentInfo> ignored = mockConstruction(DeploymentInfo.class))
      {
         ImportAssetResponse result = spy.importAssets(zip(), new ArrayList<>(), true, principal);
         assertTrue(result.warnings().isEmpty(), result.warnings().toString());
         assertTrue(result.ignoreUserAssets().isEmpty(), result.ignoreUserAssets().toString());
      }
   }

   @Test
   void fileImportWithFailedAssetsStillThrows() throws Exception {
      DeployService spy = fileImportService(ImportAssetResponse.builder()
         .addFailedAssets("broken").addWarnings(WARNING).failed(true).build());

      try(MockedStatic<DeployManagerService> dms = mockGetInfo();
          MockedConstruction<DeploymentInfo> ignored = mockConstruction(DeploymentInfo.class))
      {
         Exception e = assertThrows(
            Exception.class, () -> spy.importAssets(zip(), new ArrayList<>(), true, principal));
         assertTrue(e.getMessage().contains("broken"), e.getMessage());
      }
   }

   private DeployService fileImportService(ImportAssetResponse response) throws Exception {
      DeployService spy = spy(service);
      doReturn(ImportJarProperties.builder().unzipFolderPath(tempDir.toString()).build())
         .when(spy).setJarFile(anyString(), eq(false));
      doReturn(null).when(spy).getJarFileInfo(any(DeploymentInfo.class), any());
      doReturn(response).when(spy)
         .importAsset(any(), any(), anyBoolean(), any(), any(), any());
      return spy;
   }

   private static MockedStatic<DeployManagerService> mockGetInfo() {
      PartialDeploymentJarInfo jarInfo = mock(PartialDeploymentJarInfo.class);
      when(jarInfo.getDependentAssets()).thenReturn(new ArrayList<>());
      MockedStatic<DeployManagerService> dms = mockStatic(DeployManagerService.class);
      dms.when(() -> DeployManagerService.getInfo(anyString(), anyBoolean())).thenReturn(jarInfo);
      return dms;
   }

   private File zip() throws Exception {
      return Files.createFile(tempDir.resolve("export" + (++count) + ".zip")).toFile();
   }

   private static final String WARNING = "secrets could not be decrypted";
   private static final String SCHEDULE_WARNING = "schedule task passwords were cleared";
   private static final String USER_ASSET = "other-org-user/Dashboard";

   @TempDir
   Path tempDir;
   private int count;
   private SecurityEngine securityEngine;
   private DeployService service;
   private Principal principal;
   private MockedStatic<OrganizationManager> orgManagerStatic;
}
