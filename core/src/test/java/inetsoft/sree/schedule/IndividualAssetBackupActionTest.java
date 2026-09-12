/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.sree.schedule;

import inetsoft.sree.security.*;
import inetsoft.storage.ExternalStorageService;
import inetsoft.test.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.util.Tool;
import inetsoft.util.dep.*;
import inetsoft.web.admin.deploy.DeployUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/*
 * Tier: [mockStatic] — DeployUtil, ExternalStorageService, FTPUtil, AssetUtil.
 * Spring (@SreeHome + IntegrationTestConfiguration) is required for SRPrincipal and
 * LibManagerProvider when exercising TableStyleAsset / ScriptAsset branches in testAssetsExist.
 *
 * Intent vs implementation suspects: none confirmed for IndividualAssetBackupAction at this time.
 */

/*
 * Cases deferred - require integration context or covered elsewhere:
 *
 * [IndividualAssetBackupAction] writeXML / parseXML
 *             -> NOT yet covered; production path persists via ScheduleTask container XML
 * [IndividualAssetBackupAction] deploy() end-to-end with real DeployUtil jar output
 *             -> run() tests mock DeployUtil.deploy(); full deploy pipeline NOT duplicated here
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
@Tag("integration")
class IndividualAssetBackupActionTest {

   private SRPrincipal admin;

   @BeforeEach
   void setUpPrincipal() {
      admin = new SRPrincipal(
         new IdentityID("admin", Organization.getDefaultOrganizationID()),
         new IdentityID[] { new IdentityID("Administrator", null) },
         new String[] { "g0" },
         "host-org",
         Tool.getSecureRandom().nextLong());
   }

   // -------------------------------------------------------------------------
   // run(Principal) — Mode B boundary capture (ExternalStorageService / FTPUtil)
   // -------------------------------------------------------------------------

   @Nested
   class RunEntryPoint {

      @Test
      void run_emptyAssets_skipsStorageWrite() throws Throwable {
         IndividualAssetBackupAction action = new IndividualAssetBackupAction();
         action.setServerPaths(new ServerPathInfo("/filepath"));

         try(MockedStatic<ExternalStorageService> storage = mockStatic(ExternalStorageService.class)) {
            ExternalStorageService mockService = mock(ExternalStorageService.class);
            storage.when(ExternalStorageService::getInstance).thenReturn(mockService);

            action.run(admin);

            verifyNoInteractions(mockService);
         }
      }

      @Test
      void run_serverPath_writesZipBackupToExternalStorage() throws Throwable {
         IndividualAssetBackupAction action = new IndividualAssetBackupAction();
         action.setAssets(createXAssets());
         action.setServerPaths(new ServerPathInfo("/exports/backups"));

         try(MockedStatic<DeployUtil> deployUtil = mockStatic(DeployUtil.class);
             MockedStatic<ExternalStorageService> storage = mockStatic(ExternalStorageService.class))
         {
            List<XAsset> assets = action.getAssets();
            deployUtil.when(() -> DeployUtil.getDependentAssetsList(assets)).thenReturn(assets);
            deployUtil.when(() -> DeployUtil.deploy(eq("backupAssetFile_TEMP"), eq(true),
                  eq(assets), eq(assets))).thenReturn(new File("/backup"));

            ExternalStorageService mockService = mock(ExternalStorageService.class);
            storage.when(ExternalStorageService::getInstance).thenReturn(mockService);
            doNothing().when(mockService).write(anyString(), any(Path.class), any(Principal.class));

            action.run(admin);

            ArgumentCaptor<String> fileNameCaptor = ArgumentCaptor.forClass(String.class);
            verify(mockService).write(fileNameCaptor.capture(), any(Path.class), eq(admin));
            assertTrue(fileNameCaptor.getValue().endsWith(".zip"),
               "Non-FTP backup must use a .zip destination path");
            assertEquals("/exports/backups.zip", fileNameCaptor.getValue());
         }
      }

      @Test
      void run_ftpPath_writesToStorageAndUploadsWithAppendFlag() throws Throwable {
         IndividualAssetBackupAction action = new IndividualAssetBackupAction();
         action.setAssets(createXAssets());

         ServerPathInfo ftpPath = new ServerPathInfo(
            "ftp://ftpuser:ftpuser@192.168.2.222/fpath?append=true");
         ftpPath.setPassword("123");
         ftpPath.setUsername("admin");
         action.setServerPaths(ftpPath);

         try(MockedStatic<DeployUtil> deployUtil = mockStatic(DeployUtil.class);
             MockedStatic<ExternalStorageService> storage = mockStatic(ExternalStorageService.class);
             MockedStatic<FTPUtil> ftpUtil = mockStatic(FTPUtil.class))
         {
            List<XAsset> assets = action.getAssets();
            deployUtil.when(() -> DeployUtil.getDependentAssetsList(assets)).thenReturn(assets);
            deployUtil.when(() -> DeployUtil.deploy(eq("backupAssetFile_TEMP"), eq(true),
                  eq(assets), eq(assets))).thenReturn(new File("/backup"));

            ExternalStorageService mockService = mock(ExternalStorageService.class);
            storage.when(ExternalStorageService::getInstance).thenReturn(mockService);
            doNothing().when(mockService).write(anyString(), any(Path.class), any(Principal.class));

            action.run(admin);

            verify(mockService).write(startsWith("backup/"), any(Path.class), eq(admin));
            ftpUtil.verify(() -> FTPUtil.uploadToFTP(
               anyString(), any(File.class), eq(ftpPath), eq(true)));
         }
      }
   }

   // -------------------------------------------------------------------------
   // via: deploy() -> testAssetsExist(List)
   // -------------------------------------------------------------------------

   @Nested
   class AssetExistenceValidation {

      @Test
      void testAssetsExist_registeredAbstractSheetAsset_doesNotThrow() throws Exception {
         IndividualAssetBackupAction action = new IndividualAssetBackupAction();

         try(MockedStatic<AssetUtil> assetUtil = mockStatic(AssetUtil.class)) {
            AssetRepository repository = mock(AssetRepository.class);
            assetUtil.when(() -> AssetUtil.getAssetRepository(false)).thenReturn(repository);

            AbstractSheetAsset sheetAsset = mock(AbstractSheetAsset.class);
            AssetEntry entry = mock(AssetEntry.class);
            when(sheetAsset.getAssetEntry()).thenReturn(entry);
            when(repository.getAssetEntry(entry)).thenReturn(entry);

            assertDoesNotThrow(() -> invokeTestAssetsExist(action, List.of(sheetAsset)));
         }
      }

      @Test
      void testAssetsExist_missingAbstractSheetAsset_throws() throws Exception {
         IndividualAssetBackupAction action = new IndividualAssetBackupAction();

         try(MockedStatic<AssetUtil> assetUtil = mockStatic(AssetUtil.class)) {
            AssetRepository repository = mock(AssetRepository.class);
            assetUtil.when(() -> AssetUtil.getAssetRepository(false)).thenReturn(repository);

            AbstractSheetAsset sheetAsset = mock(AbstractSheetAsset.class);
            AssetEntry entry = mock(AssetEntry.class);
            when(sheetAsset.getAssetEntry()).thenReturn(entry);
            when(repository.getAssetEntry(entry)).thenReturn(null);

            InvocationTargetException ex = assertThrows(InvocationTargetException.class,
               () -> invokeTestAssetsExist(action, List.of(sheetAsset)));
            assertRuntimeMessageContains(ex, "Failed to retrieve asset(s):");
         }
      }

      @Test
      void testAssetsExist_missingTableStyleAsset_throws() throws Exception {
         IndividualAssetBackupAction action = new IndividualAssetBackupAction();

         try(MockedStatic<AssetUtil> assetUtil = mockStatic(AssetUtil.class)) {
            assetUtil.when(() -> AssetUtil.getAssetRepository(false)).thenReturn(mock(AssetRepository.class));

            TableStyleAsset styleAsset = mock(TableStyleAsset.class);
            when(styleAsset.getStyleID()).thenReturn("missing-style");

            InvocationTargetException ex = assertThrows(InvocationTargetException.class,
               () -> invokeTestAssetsExist(action, List.of(styleAsset)));
            assertRuntimeMessageContains(ex, "Failed to retrieve asset(s):");
         }
      }

      @Test
      void testAssetsExist_missingScriptAsset_throws() throws Exception {
         IndividualAssetBackupAction action = new IndividualAssetBackupAction();

         try(MockedStatic<AssetUtil> assetUtil = mockStatic(AssetUtil.class)) {
            assetUtil.when(() -> AssetUtil.getAssetRepository(false)).thenReturn(mock(AssetRepository.class));

            ScriptAsset scriptAsset = mock(ScriptAsset.class);
            when(scriptAsset.getPath()).thenReturn("/missing/script.js");

            InvocationTargetException ex = assertThrows(InvocationTargetException.class,
               () -> invokeTestAssetsExist(action, List.of(scriptAsset)));
            assertRuntimeMessageContains(ex, "Failed to retrieve asset(s):");
         }
      }
   }

   // -------------------------------------------------------------------------
   // Helpers
   // -------------------------------------------------------------------------

   private static List<XAsset> createXAssets() {
      XAsset asset = mock(XAsset.class);
      when(asset.getPath()).thenReturn("/asset");
      return List.of(asset);
   }

   // via: deploy() -> testAssetsExist
   private static void invokeTestAssetsExist(IndividualAssetBackupAction action, List<XAsset> assets)
      throws Exception
   {
      Method method = IndividualAssetBackupAction.class.getDeclaredMethod("testAssetsExist", List.class);
      method.setAccessible(true);

      try {
         method.invoke(action, assets);
      }
      catch(InvocationTargetException e) {
         throw e;
      }
   }

   private static void assertRuntimeMessageContains(InvocationTargetException ex, String fragment) {
      Throwable cause = ex.getCause();
      assertNotNull(cause);
      assertInstanceOf(RuntimeException.class, cause);
      assertTrue(cause.getMessage().contains(fragment),
         "Expected message fragment not found: " + cause.getMessage());
   }
}
