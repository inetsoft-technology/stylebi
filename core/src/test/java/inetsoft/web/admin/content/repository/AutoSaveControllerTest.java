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
package inetsoft.web.admin.content.repository;

/*
 * Test strategy
 *
 * AutoSaveController has real in-controller logic in restoreAutoSaveAssets():
 *   - folder == "/" → assetName = fileName (no folder prefix)
 *   - folder != "/" → assetName = folder + "/" + fileName
 *   - ids.length == 1 → service called once with assetName (no numeric suffix)
 *   - ids.length > 1 → service called N times with assetName + i (0-based suffix)
 *   After each restore, AutoSaveUtils.deleteAutoSaveFile(id, user) is called.
 *
 * deleteAutoSaveAssets() calls Util.getObjectFullPath() which requires a Spring context;
 * covered by E2E tests.
 * getRepositoryTree() / getAssetFolder() logic is covered by E2E tests.
 *
 * AutoSaveUtils static methods are intercepted with MockedStatic (closed in try-with-resources).
 * Tool.split() is a pure string utility called for real.
 *
 * Coverage scope:
 *   [single id, root folder]   ids.length == 1, folder="/" → service called with fileName; deleteAutoSaveFile once
 *   [multiple ids, sub-folder] ids.length > 1, folder="Reports" → numbered suffix; deleteAutoSaveFile per id
 */

import inetsoft.util.IndexedStorage;
import inetsoft.web.AutoSaveServiceProxy;
import inetsoft.web.AutoSaveUtils;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.Principal;
import java.util.Map;

import static org.mockito.Mockito.*;

@Tag("core")
@ExtendWith(MockitoExtension.class)
class AutoSaveControllerTest {

   @Mock private AutoSaveServiceProxy autoSaveService;
   @Mock private IndexedStorage indexedStorage;
   @Mock private Principal principal;

   private AutoSaveController controller;

   @BeforeEach
   void setUp() {
      controller = new AutoSaveController(autoSaveService, indexedStorage);
   }

   // -------------------------------------------------------------------------
   // restoreAutoSaveAssets()
   // -------------------------------------------------------------------------

   // [single id, root folder] ids.length == 1, folder="/" → service called with fileName; deleteAutoSaveFile called once
   @Test
   void restoreAutoSaveAssets_singleId_rootFolder_callsServiceOnce() throws Exception {
      Map<String, String> body = Map.of(
         "ids", "id1", "name", "myFile", "folder", "/", "overwrite", "false");

      try(MockedStatic<AutoSaveUtils> autoSaveMock = mockStatic(AutoSaveUtils.class, withSettings().lenient())) {
         controller.restoreAutoSaveAssets(body, principal);

         verify(autoSaveService).restoreAutoSaveAssets("id1", "myFile", false, principal);
         autoSaveMock.verify(() -> AutoSaveUtils.deleteAutoSaveFile("id1", principal));
      }
   }

   // [multiple ids, sub-folder] ids.length > 1, folder prepended, 0-based suffix appended per id
   @Test
   void restoreAutoSaveAssets_multipleIds_appendsNumberedSuffix() throws Exception {
      Map<String, String> body = Map.of(
         "ids", "id1,id2", "name", "myFile", "folder", "Reports", "overwrite", "false");

      try(MockedStatic<AutoSaveUtils> autoSaveMock = mockStatic(AutoSaveUtils.class, withSettings().lenient())) {
         controller.restoreAutoSaveAssets(body, principal);

         verify(autoSaveService).restoreAutoSaveAssets("id1", "Reports/myFile0", false, principal);
         verify(autoSaveService).restoreAutoSaveAssets("id2", "Reports/myFile1", false, principal);
         autoSaveMock.verify(() -> AutoSaveUtils.deleteAutoSaveFile("id1", principal));
         autoSaveMock.verify(() -> AutoSaveUtils.deleteAutoSaveFile("id2", principal));
      }
   }
}
