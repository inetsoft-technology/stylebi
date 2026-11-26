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
package inetsoft.web.portal.data;

import inetsoft.mv.MVManager;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.*;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.sync.RenameTransformHandler;
import inetsoft.web.RecycleBin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/*
 * Bug #82430: the Data tab listing re-checked entry-independent permissions (WORKSHEET and
 * MATERIALIZATION ACCESS) for every worksheet and re-filtered READ on entries getEntries had
 * already filtered. Browse must evaluate the entry-independent checks once, not re-filter READ,
 * and keep per-entry editable/deletable/canMaterialize results; search must still gate on READ.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome()
@ExtendWith(MockitoExtension.class)
@Tag("core")
class DataSetServiceBrowsePermissionScopeTest {
   @Mock private SecurityProvider securityProvider;
   @Mock private SecurityEngine securityEngine;
   @Mock private AssetRepository assetRepository;
   @Mock private DataSetSearchService dataSetSearchService;
   @Mock private RecycleBin recycleBin;
   @Mock private DependencyHandler dependencyHandler;
   @Mock private RenameTransformHandler renameTransformHandler;
   @Mock private Principal principal;

   private DataSetService service;
   private String oldMv;
   private MockedStatic<MVManager> mvStatic;

   @BeforeEach
   void setup() throws Exception {
      service = new DataSetService(securityProvider, securityEngine, assetRepository,
                                   dataSetSearchService, recycleBin, dependencyHandler,
                                   renameTransformHandler);
      lenient().when(principal.getName()).thenReturn("admin");
      lenient().when(assetRepository.containsEntry(any())).thenReturn(true);
      mvStatic = mockStatic(MVManager.class);
      mvStatic.when(MVManager::getManager).thenReturn(mock(MVManager.class));
      oldMv = SreeEnv.getProperty("ws.mv.enabled");
      SreeEnv.setProperty("ws.mv.enabled", "true");
   }

   @AfterEach
   void restore() {
      mvStatic.close();
      SreeEnv.setProperty("ws.mv.enabled", oldMv);
   }

   private AssetEntry[] worksheets(int n) {
      AssetEntry[] entries = new AssetEntry[n];

      for(int i = 0; i < n; i++) {
         entries[i] = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET,
                                     "ws" + i, null);
      }

      return entries;
   }

   private int count(ResourceType type, ResourceAction action) {
      int[] c = { 0 };
      mockingDetails(securityProvider).getInvocations().forEach(inv -> {
         if("checkPermission".equals(inv.getMethod().getName()) && inv.getArguments().length == 4
            && inv.getArguments()[1] == type && inv.getArguments()[3] == action)
         {
            c[0]++;
         }
      });
      return c[0];
   }

   private PortalDataBrowserModel browse(int n, boolean allowWrite) throws Exception {
      AssetEntry[] entries = worksheets(n);
      when(assetRepository.getEntries(any(), any(), any(), any())).thenReturn(entries);
      lenient().when(securityProvider.checkPermission(
         any(), any(ResourceType.class), anyString(), any(ResourceAction.class)))
         .thenReturn(true);

      if(!allowWrite) {
         lenient().doThrow(new RuntimeException("denied")).when(assetRepository)
            .checkAssetPermission(any(), any(), eq(ResourceAction.WRITE));
      }

      return service.getDataBrowser("/", AssetRepository.GLOBAL_SCOPE, null, principal);
   }

   @Test
   void browse_entryIndependentChecksAreIndependentOfEntryCount() throws Exception {
      browse(10, true);
      int ws10 = count(ResourceType.WORKSHEET, ResourceAction.ACCESS);
      int mv10 = count(ResourceType.MATERIALIZATION, ResourceAction.ACCESS);
      clearInvocations(securityProvider, assetRepository);
      browse(200, true);

      assertEquals(ws10, count(ResourceType.WORKSHEET, ResourceAction.ACCESS));
      assertEquals(mv10, count(ResourceType.MATERIALIZATION, ResourceAction.ACCESS));
      assertEquals(1, ws10);
      assertEquals(1, mv10);
   }

   @Test
   void browse_doesNotRefilterReadAndKeepsPerEntryWriteDelete() throws Exception {
      browse(50, true);

      verify(assetRepository, never()).checkAssetPermission(
         any(), any(), eq(ResourceAction.READ));
      verify(assetRepository, times(50)).checkAssetPermission(
         any(), any(), eq(ResourceAction.WRITE));
   }

   @Test
   void browse_outputFlagsMatchGrantedUser() throws Exception {
      PortalDataBrowserModel model = browse(5, true);

      assertEquals(5, model.files().size());
      assertTrue(model.worksheetAccess());

      for(WorksheetBrowserInfo info : model.files()) {
         assertTrue(info.editable());
         assertTrue(info.canMaterialize());
         assertTrue(info.canWorksheet());
      }
   }

   @Test
   void browse_userDeniedWriteIsNotEditableNorMaterializable() throws Exception {
      PortalDataBrowserModel model = browse(5, false);

      for(WorksheetBrowserInfo info : model.files()) {
         assertFalse(info.editable());
         assertFalse(info.canMaterialize());
         assertTrue(info.canWorksheet());
      }
   }

   @Test
   void search_stillHidesWorksheetUserCannotRead() throws Exception {
      AssetEntry[] entries = worksheets(2);
      when(dataSetSearchService.findAssets(any(), anyString(), any()))
         .thenReturn(java.util.Arrays.asList(entries));
      lenient().when(securityProvider.checkPermission(
         any(), any(ResourceType.class), anyString(), any(ResourceAction.class)))
         .thenReturn(true);
      lenient().doThrow(new RuntimeException("denied")).when(assetRepository)
         .checkAssetPermission(any(), eq(entries[1]), eq(ResourceAction.READ));

      WorksheetBrowserInfo[] result = service.getSearchAssets(
         "/", "ws", AssetRepository.GLOBAL_SCOPE, principal);

      assertEquals(1, result.length);
      assertEquals("ws0", result[0].name());
   }
}
