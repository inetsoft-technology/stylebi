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
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.util.Tool;
import inetsoft.util.dep.*;
import inetsoft.web.admin.deploy.DeployUtil;
import org.junit.jupiter.api.Test;
import org.mockito.*;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class IndividualAssetBackupActionTest {
   @Test
   void testRunWithFilePath() {
      IndividualAssetBackupAction action = new IndividualAssetBackupAction();

      List<XAsset> assets = createXAssets();
      action.setAssets(assets);
      action.setPaths("/");
      action.setServerPaths(new ServerPathInfo("/filepath"));

      //check basic get methods
      assertEquals(assets, action.getAssets());
      assertEquals("/filepath", action.getPath());
      assertEquals("/filepath", action.getServerPath().getPath());

      // mock DeployUtil to deploy assets.
      MockedStatic<DeployUtil> mockDeployUtil = Mockito.mockStatic(DeployUtil.class);
      mockDeployUtil.when(() -> DeployUtil.getDependentAssetsList(anyList())).thenReturn(assets);
      mockDeployUtil.when(() -> DeployUtil.deploy("backupAssetFile_TEMP",
                                                  true, assets, assets)).thenReturn(new File("/backup"));

      // mock ExternalStorageService to write files.
      MockedStatic<ExternalStorageService> mockExternalStorageService = Mockito.mockStatic(ExternalStorageService.class);
      ExternalStorageService mockService = mock(ExternalStorageService.class);
      mockExternalStorageService.when(ExternalStorageService::getInstance).thenReturn(mockService);

      try{
         doNothing().when(mockService).write(anyString(), any(Path.class), any(Principal.class));
         action.run(admin);

         verify(mockService, atLeastOnce()).write(anyString(), any(Path.class), any(Principal.class));
      }catch(Throwable e) {
         e.printStackTrace();
      }finally {
         mockDeployUtil.close();
         mockExternalStorageService.close();
      }
   }

   @Test
   void testRunWithFTPPath() {
      IndividualAssetBackupAction action = new IndividualAssetBackupAction();

      List<XAsset> assets = createXAssets();
      action.setAssets(assets);
      action.setPaths("/");

      ServerPathInfo serverPathInfo = new ServerPathInfo("ftp://ftpuser:ftpuser@192.168.2.222/fpath?append=true");
      serverPathInfo.setPassword("123");
      serverPathInfo.setUsername("admin");
      action.setServerPaths(serverPathInfo);

      // mock DeployUtil to deploy assets.
      MockedStatic<DeployUtil> mockDeployUtil = Mockito.mockStatic(DeployUtil.class);
      mockDeployUtil.when(() -> DeployUtil.getDependentAssetsList(anyList())).thenReturn(assets);
      mockDeployUtil.when(() -> DeployUtil.deploy("backupAssetFile_TEMP",
                                                  true, assets, assets)).thenReturn(new File("/backup"));

      // mock ExternalStorageService to write files.
      MockedStatic<ExternalStorageService> mockExternalStorageService =
         Mockito.mockStatic(ExternalStorageService.class);
      ExternalStorageService mockService = mock(ExternalStorageService.class);
      mockExternalStorageService.when(ExternalStorageService::getInstance).thenReturn(mockService);

      // mock FTPUtil to upload files, do nothing
      MockedStatic<FTPUtil> mockFTPUtil = Mockito.mockStatic(FTPUtil.class);
      mockFTPUtil.when(() ->
                          FTPUtil.uploadToFTP(anyString(), any(File.class), any(ServerPathInfo.class), anyBoolean()))
         .thenAnswer(invocation -> null);

      try{
         doNothing().when(mockService).write(anyString(), any(Path.class), any(Principal.class));

         action.run(admin);
         verify(mockService, atLeastOnce()).write(anyString(), any(Path.class), any(Principal.class));
      }catch(Throwable e) {
         e.printStackTrace();
      }finally {
         mockDeployUtil.close();
         mockExternalStorageService.close();
         mockFTPUtil.close();
      }
   }

   @Test
   void testAssetsExist() {
      IndividualAssetBackupAction individualAssetBackupAction = new IndividualAssetBackupAction();

      // mock AssetRepository and Worksheet to get the Worksheet from AssetEntry
      MockedStatic<AssetUtil> mockedAssetUtil = Mockito.mockStatic(AssetUtil.class);
      AssetRepository mockAssetRepository = mock(AssetRepository.class);
      mockedAssetUtil.when(() -> AssetUtil.getAssetRepository(false)).thenReturn(mockAssetRepository);

      try {
         Method method = IndividualAssetBackupAction.class.getDeclaredMethod("testAssetsExist", List.class);
         method.setAccessible(true);

         // check AbstractSheetAsset
         AbstractSheetAsset mockAAsset = mock(AbstractSheetAsset.class);
         AssetEntry mockAssetEntry = mock(AssetEntry.class);
         when(mockAssetRepository.getAssetEntry(any(AssetEntry.class))).thenReturn(mockAssetEntry);

         Exception exception = assertThrows(InvocationTargetException.class, () -> {
            method.invoke(individualAssetBackupAction, List.of(mockAAsset));
         });
         verifyCausedByException(exception, "Failed to retrieve asset(s):[Mock for AbstractSheetAsset");

         //check TableStyleAsset
         TableStyleAsset mockTAsset = mock(TableStyleAsset.class);
         when(mockTAsset.getStyleID()).thenReturn("tableId");
         exception = assertThrows(InvocationTargetException.class, () -> {
            method.invoke(individualAssetBackupAction, List.of(mockTAsset));
         });
         verifyCausedByException(exception, "Failed to retrieve asset(s):[Mock for TableStyleAsset");

         //check ScriptAsset
         ScriptAsset mockSAsset = mock(ScriptAsset.class);
         when(mockSAsset.getPath()).thenReturn("/path1");
         exception = assertThrows(InvocationTargetException.class, () -> {
            method.invoke(individualAssetBackupAction, List.of(mockSAsset));
         });
         verifyCausedByException(exception, "Failed to retrieve asset(s):[Mock for ScriptAsset");

      }catch(Exception e) {
         e.printStackTrace();
      }
      finally {
         mockedAssetUtil.close();
      }
   }

   private void verifyCausedByException(Exception exception, String  message) {
      Throwable cause = exception.getCause();
      assertNotNull(cause);
      assertTrue(cause.getMessage().contains(message), "Expected message not found: " + cause.getMessage());
   }

   private  List<XAsset> createXAssets() {
      XAsset asset = mock(XAsset.class);
      when(asset.getPath()).thenReturn("/asset");

      return List.of(asset);
   }

   SRPrincipal admin = new SRPrincipal(new IdentityID("admin", Organization.getDefaultOrganizationID()),
                                       new IdentityID[] { new IdentityID("Administrator", null)},
                                       new String[] {"g0"}, "host-org",
                                       Tool.getSecureRandom().nextLong());
}
