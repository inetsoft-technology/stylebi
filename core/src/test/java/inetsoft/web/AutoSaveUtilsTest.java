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
package inetsoft.web;

import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.storage.BlobStorage;
import inetsoft.storage.BlobStorageManager;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("core")
class AutoSaveUtilsTest {
   @Test
   void deleteUserAutoSaveFiles_nullUser_noStorageAccess() {
      try(MockedStatic<BlobStorageManager> bsm = mockStatic(BlobStorageManager.class)) {
         AutoSaveUtils.deleteUserAutoSaveFiles(null);
         bsm.verifyNoInteractions();
      }
   }

   @Test
   @SuppressWarnings("unchecked")
   void deleteUserAutoSaveFiles_deletesOnlyMatchingUserFiles() throws Exception {
      IdentityID alice = new IdentityID("alice", "org1");
      IdentityID bob = new IdentityID("bob", "org1");
      String aliceFile = "0^VIEWSHEET^" + alice.convertToKey() + "^dashboard1";
      String aliceRecycled = AutoSaveUtils.RECYCLE_PREFIX + "0^VIEWSHEET^" +
         alice.convertToKey() + "^dashboard2";
      String bobFile = "0^VIEWSHEET^" + bob.convertToKey() + "^dashboard3";
      String anonFile = "0^VIEWSHEET^anonymous^dashboard4";
      String nullFile = "0^VIEWSHEET^_NULL_^dashboard5";
      String shortFile = "0^VIEWSHEET^onlythree";

      BlobStorage<AutoSaveUtils.Metadata> blobStorage = mock(BlobStorage.class);
      when(blobStorage.paths()).thenReturn(Stream.of(
         aliceFile, aliceRecycled, bobFile, anonFile, nullFile, shortFile));

      try(MockedStatic<BlobStorageManager> bsm = mockStatic(BlobStorageManager.class)) {
         BlobStorageManager manager = mock(BlobStorageManager.class);
         bsm.when(BlobStorageManager::getInstance).thenReturn(manager);
         when(manager.<AutoSaveUtils.Metadata>getStorage(anyString(), anyBoolean()))
            .thenReturn(blobStorage);

         AutoSaveUtils.deleteUserAutoSaveFiles(alice);

         verify(blobStorage).delete(aliceFile);
         verify(blobStorage).delete(aliceRecycled);
         verify(blobStorage, times(2)).delete(anyString());
      }
   }

   @Test
   @SuppressWarnings("unchecked")
   void deleteUserAutoSaveFiles_oneFailedDelete_continuesWithOthers() throws Exception {
      IdentityID alice = new IdentityID("alice", "org1");
      String file1 = "0^VIEWSHEET^" + alice.convertToKey() + "^dashboard1";
      String file2 = "0^VIEWSHEET^" + alice.convertToKey() + "^dashboard2";

      BlobStorage<AutoSaveUtils.Metadata> blobStorage = mock(BlobStorage.class);
      when(blobStorage.paths()).thenReturn(Stream.of(file1, file2));
      doThrow(new java.io.IOException("boom")).doNothing().when(blobStorage).delete(anyString());

      try(MockedStatic<BlobStorageManager> bsm = mockStatic(BlobStorageManager.class)) {
         BlobStorageManager manager = mock(BlobStorageManager.class);
         bsm.when(BlobStorageManager::getInstance).thenReturn(manager);
         when(manager.<AutoSaveUtils.Metadata>getStorage(anyString(), anyBoolean()))
            .thenReturn(blobStorage);

         assertDoesNotThrow(() -> AutoSaveUtils.deleteUserAutoSaveFiles(alice));
         verify(blobStorage, times(2)).delete(anyString());
      }
   }

   // Bug #75777: the last field of an auto save file name is the ip address. For any scope other
   // than the temporary scope it used to be parsed as the organization id, which resolved the
   // entry against the wrong storage.
   @Test
   void createAssetEntry_savedSheet_usesCurrentOrgNotIpAddress() {
      IdentityID alice = new IdentityID("alice", "org1");
      String autoFile = AssetRepository.GLOBAL_SCOPE + "^VIEWSHEET^" + alice.convertToKey() +
         "^dashboard1^0_0_0_0_0_0_0_1~";

      try(MockedStatic<OrganizationManager> oms =
             mockStatic(OrganizationManager.class, withSettings().lenient()))
      {
         OrganizationManager orgManager = mock(OrganizationManager.class);
         oms.when(OrganizationManager::getInstance).thenReturn(orgManager);
         when(orgManager.getCurrentOrgID()).thenReturn("org1");

         AssetEntry entry = AutoSaveUtils.createAssetEntry(autoFile);

         assertNotNull(entry);
         assertEquals("org1", entry.getOrgID());
         assertEquals(AssetRepository.GLOBAL_SCOPE, entry.getScope());
         assertTrue(entry.isViewsheet());
         assertEquals(autoFile, entry.getProperty("autoFileName"));
      }
   }

   // Bug #75777: the file name of an auto save file embeds the user and ip address of the session
   // that created it, so an entry that carries the name must use it as is regardless of scope.
   // Rebuilding the name from the current user would resolve the wrong file, or none at all.
   @Test
   void getAutoSavedFile_savedSheetWithFileName_keepsRecycledFileName() {
      IdentityID alice = new IdentityID("alice", "org1");
      String autoFile = AssetRepository.GLOBAL_SCOPE + "^VIEWSHEET^" + alice.convertToKey() +
         "^dashboard1^0_0_0_0_0_0_0_1~";

      try(MockedStatic<OrganizationManager> oms =
             mockStatic(OrganizationManager.class, withSettings().lenient()))
      {
         OrganizationManager orgManager = mock(OrganizationManager.class);
         oms.when(OrganizationManager::getInstance).thenReturn(orgManager);
         when(orgManager.getCurrentOrgID()).thenReturn("org1");

         AssetEntry entry = AutoSaveUtils.createAssetEntry(autoFile);

         // createAssetEntry() flags the entry as a recycle bin entry, so the recycled copy of the
         // exact file name is resolved, not one rebuilt from the admin performing the restore
         assertEquals(AutoSaveUtils.RECYCLE_PREFIX + autoFile,
                      AutoSaveUtils.getAutoSavedFile(entry, null));
      }
   }

   // Bug #75777: an entry with no file name still resolves to the active area, which is what the
   // composer's "Autosaved file exists. Restore?" check relies on.
   @Test
   void getAutoSavedFile_noFileName_buildsActiveFileName() {
      AssetEntry entry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET, "dashboard1", null);

      String file = AutoSaveUtils.getAutoSavedFile(entry, null);

      assertFalse(file.startsWith(AutoSaveUtils.RECYCLE_PREFIX));
      assertTrue(file.startsWith(AssetRepository.GLOBAL_SCOPE + "^" + entry.getType() + "^"));
      assertTrue(file.contains("^dashboard1^"));
   }
}
