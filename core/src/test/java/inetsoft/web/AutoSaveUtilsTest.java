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
import inetsoft.storage.BlobStorage;
import inetsoft.storage.BlobStorageManager;
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
}
