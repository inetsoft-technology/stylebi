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
 * ImportAssetController has real in-controller logic in one method:
 *   setJarFile — generates a UUID import ID before delegating to the proxy
 *
 * All other endpoints (updateImportInfo, getJarFileInfo, getBookmarkConflicts, importAsset,
 * finishImport) are pure-delegation methods and are tested as such.
 *
 * @PostConstruct (cluster cache init) is not invoked without a Spring context.
 *
 * Coverage scope:
 *   [setJarFile: UUID generation]        proxy called with a generated UUID, file, and principal
 *   [updateImportInfo: delegation]       all parameters forwarded to proxy unchanged
 *   [getBookmarkConflicts: delegation]   all parameters forwarded to proxy unchanged
 *   [importAsset: delegation]            all parameters (including ignoreList, flags) forwarded
 *   [finishImport: delegation]           proxy.finishImport(importId) called
 */

import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.web.admin.content.repository.model.*;
import inetsoft.web.admin.model.FileData;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.Principal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
@ExtendWith(MockitoExtension.class)
class ImportAssetControllerTest {

   @Mock private ImportAssetServiceProxy importService;
   @Mock private Cluster cluster;
   @Mock private FileData file;
   @Mock private ExportedAssetsModel exportedAssetsModel;
   @Mock private ImportAssetResponse importAssetResponse;
   @Mock private Principal principal;

   private ImportAssetController controller;

   @BeforeEach
   void setUp() {
      controller = new ImportAssetController(importService, cluster);
   }

   // -------------------------------------------------------------------------
   // setJarFile()
   // -------------------------------------------------------------------------

   // [UUID generation] proxy called with a non-null UUID import ID; result returned unchanged
   @Test
   void setJarFile_generatesImportIdAndDelegatesToService() throws Exception {
      when(importService.setJarFile(anyString(), eq(file), eq(principal)))
         .thenReturn(exportedAssetsModel);

      ExportedAssetsModel result = controller.setJarFile(file, principal);

      assertSame(exportedAssetsModel, result);
      verify(importService).setJarFile(
         argThat(id -> id != null && !id.isEmpty()),
         eq(file), eq(principal));
   }

   // -------------------------------------------------------------------------
   // updateImportInfo()
   // -------------------------------------------------------------------------

   // [delegation] all parameters forwarded to proxy unchanged
   @Test
   void updateImportInfo_delegatesToService() throws Exception {
      when(importService.updateImportInfo("id1", "/target", 2, "alice", principal))
         .thenReturn(exportedAssetsModel);

      ExportedAssetsModel result =
         controller.updateImportInfo("id1", "/target", 2, "alice", principal);

      assertSame(exportedAssetsModel, result);
      verify(importService).updateImportInfo("id1", "/target", 2, "alice", principal);
   }

   // -------------------------------------------------------------------------
   // importAsset()
   // -------------------------------------------------------------------------

   // [delegation] ignoreList and empty resolutionMap forwarded to proxy when no keepCurrent
   // resolutions are present
   @Test
   void importAsset_delegatesToService() throws Exception {
      List<String> ignoreList = List.of("ignoredAsset");
      ImportAssetRequest request = new ImportAssetRequest();
      request.setIgnoreList(ignoreList);
      // All resolutions default to keepImported=true → resolutionMap stays empty

      when(importService.importAsset(
         "id1", "/target", 2, "alice", true, ignoreList, true, false, principal, Map.of()))
         .thenReturn(importAssetResponse);

      ImportAssetResponse result = controller.importAsset(
         "id1", "/target", 2, "alice", true, request, true, false, principal);

      assertSame(importAssetResponse, result);
      verify(importService).importAsset(
         "id1", "/target", 2, "alice", true, ignoreList, true, false, principal, Map.of());
   }

   // -------------------------------------------------------------------------
   // getBookmarkConflicts()
   // -------------------------------------------------------------------------

   // [delegation] all parameters forwarded to proxy unchanged
   @Test
   void getBookmarkConflicts_delegatesToService() throws Exception {
      List<BookmarkConflict> expected = List.of();
      when(importService.getBookmarkConflicts("id1", "/target", 2, "alice", true, null, principal))
         .thenReturn(expected);

      List<BookmarkConflict> result = controller.getBookmarkConflicts(
         "id1", "/target", 2, "alice", true, null, principal);

      assertSame(expected, result);
      verify(importService).getBookmarkConflicts(
         "id1", "/target", 2, "alice", true, null, principal);
   }

   // -------------------------------------------------------------------------
   // finishImport()
   // -------------------------------------------------------------------------

   // [delegation] proxy.finishImport(importId) called
   @Test
   void finishImport_delegatesToService() {
      controller.finishImport("id1");

      verify(importService).finishImport("id1");
   }
}
