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
package inetsoft.web.wiz.viewsheet;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.web.AutoSaveUtils;
import inetsoft.web.admin.content.repository.RepletRegistryService;
import inetsoft.web.composer.vs.dialog.ViewsheetPropertyDialogService;
import inetsoft.web.viewsheet.service.VSBookmarkService;
import inetsoft.web.viewsheet.service.VSExportService;
import inetsoft.web.wiz.pairing.*;
import inetsoft.web.wiz.script.ScriptImageService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.MockedStatic;

import java.security.Principal;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #79359: {@code save_viewsheet} into a folder that does not exist must create the folder
 * (every level, through the registry route) before the dashboard is written, instead of failing
 * with the unrelated "Cannot move Dashboard to subfolder" message from {@code setSheet}.
 */
@WizAgentTestSupport
class ViewsheetAssemblyAgentControllerSaveFolderTest {
   private final Principal agent = TestPrincipals.user("alice", "host-org");
   private final IdentityID alice = IdentityID.getIdentityIDFromKey(agent.getName());

   private final Set<String> existing = new HashSet<>();
   private final AssetRepository repository = mock(AssetRepository.class);
   private final inetsoft.analytic.composition.ViewsheetService viewsheetService =
      mock(inetsoft.analytic.composition.ViewsheetService.class);
   private final RepletRegistryService registryService = mock(RepletRegistryService.class);
   private final RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
   private ViewsheetAssemblyAgentController controller;

   ViewsheetAssemblyAgentControllerSaveFolderTest() throws Exception {
      when(viewsheetService.getAssetRepository()).thenReturn(repository);
      when(repository.containsEntry(any()))
         .thenAnswer(inv -> existing.contains(((AssetEntry) inv.getArgument(0)).getPath()));

      // Creating through the registry also creates the storage half and every missing parent.
      doAnswer(inv -> {
         String path = inv.getArgument(0);
         path = path.startsWith("My Dashboards/") ? path.substring("My Dashboards/".length()) : path;

         for(int i = path.indexOf('/'); i >= 0; i = path.indexOf('/', i + 1)) {
            existing.add(path.substring(0, i));
         }

         existing.add(path);
         return null;
      }).when(registryService).addRepositoryFolder(anyString(), any(), any(), any());

      AssetEntry tempEntry = new AssetEntry(AssetRepository.TEMPORARY_SCOPE,
         AssetEntry.Type.VIEWSHEET, "__TEMPORARY__/vs-1", null);
      when(rvs.getEntry()).thenReturn(tempEntry);
      when(rvs.getID()).thenReturn("rt-vs-1");

      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
      when(sessions.resolve(eq("tok"), eq(agent))).thenReturn(rvs);
      controller = controller(sessions);
   }

   @Test
   void missingFolderIsCreatedThroughTheRegistryBeforeTheDashboardIsSaved() throws Exception {
      save("X/vs", null);

      InOrder order = inOrder(registryService, viewsheetService);
      order.verify(registryService).addRepositoryFolder("X", null, null, null);
      order.verify(viewsheetService).setViewsheet(any(), any(), any(), eq(true), eq(true));
      verify(repository).checkAssetPermission(any(), argThat(a -> a.isRoot()),
                                              eq(ResourceAction.WRITE));
   }

   @Test
   void nestedMissingFoldersAreCreatedByOneCallAndGatedOnTheNearestExistingAncestor()
      throws Exception
   {
      existing.add("A");
      save("A/B/C/vs", null);

      verify(registryService, times(1)).addRepositoryFolder(anyString(), any(), any(), any());
      verify(registryService).addRepositoryFolder("A/B/C", null, null, null);
      verify(repository).checkAssetPermission(any(), argThat(a -> "A".equals(a.getPath())),
                                              eq(ResourceAction.WRITE));
      assertTrue(existing.contains("A/B"));
   }

   @Test
   void existingFolderIsNotCreatedAgain() throws Exception {
      existing.add("X");
      save("X/vs", null);

      verifyNoInteractions(registryService);
      verify(repository, never()).checkAssetPermission(any(), any(), any());
      verify(viewsheetService).setViewsheet(any(), any(), any(), eq(true), eq(true));
   }

   @Test
   void topLevelNameNeverTouchesTheRegistry() throws Exception {
      save("vs", null);

      verifyNoInteractions(registryService);
      verify(repository, never()).checkAssetPermission(any(), any(), any());
   }

   @Test
   void userScopeCreatesUnderMyDashboardsForTheCaller() throws Exception {
      save("X/vs", "user");

      verify(registryService).addRepositoryFolder("My Dashboards/X", null, null, alice);
   }

   @Test
   void deniedGateCreatesNothingAndSavesNothing() throws Exception {
      doThrow(new inetsoft.sree.security.SecurityException("no write"))
         .when(repository).checkAssetPermission(any(), any(), eq(ResourceAction.WRITE));

      PairingException e = assertThrows(PairingException.class, () -> save("X/vs", null));

      assertTrue(e.getMessage().contains("'X'"), e.getMessage());
      verify(registryService, never()).addRepositoryFolder(any(), any(), any(), any());
      verify(viewsheetService, never()).setViewsheet(any(), any(), any(), anyBoolean(),
                                                     anyBoolean());
   }

   @Test
   void creationFailureNamesTheFolderAndSavesNothing() throws Exception {
      doThrow(new Exception("Failed to add folder X"))
         .when(registryService).addRepositoryFolder(any(), any(), any(), any());

      PairingException e = assertThrows(PairingException.class, () -> save("X/vs", null));

      assertTrue(e.getMessage().contains("Cannot create folder 'X'"), e.getMessage());
      verify(viewsheetService, never()).setViewsheet(any(), any(), any(), anyBoolean(),
                                                     anyBoolean());
   }

   @Test
   void registryFolderMissingFromStorageIsReportedWithoutClaimingACause() throws Exception {
      // the registry accepts the folder but the asset-store half never appears
      doNothing().when(registryService).addRepositoryFolder(any(), any(), any(), any());

      PairingException e = assertThrows(PairingException.class, () -> save("X/vs", null));

      assertTrue(e.getMessage().contains("'X'"), e.getMessage());
      assertTrue(e.getMessage().contains("missing from storage"), e.getMessage());
      assertTrue(e.getMessage().contains("repair_repository_folders"), e.getMessage());
      verify(viewsheetService, never()).setViewsheet(any(), any(), any(), anyBoolean(),
                                                     anyBoolean());
   }

   @Test
   void folderIsKeptWhenTheDashboardWriteIsRefusedAfterwards() throws Exception {
      // Accepted limit: the gate on the ancestor passes but setSheet's own check on the new path
      // rejects the write; the folder was already created and is not rolled back.
      doThrow(new Exception("no write on X"))
         .when(viewsheetService).setViewsheet(any(), any(), any(), anyBoolean(), anyBoolean());

      PairingException e = assertThrows(PairingException.class, () -> save("X/vs", null));

      assertTrue(e.getMessage().startsWith("Failed to save viewsheet"), e.getMessage());
      verify(registryService).addRepositoryFolder("X", null, null, null);
      assertTrue(existing.contains("X"));
   }

   @Test
   void emptyFolderSegmentsAreRefusedBeforeAnythingIsCreated() {
      for(String name : new String[] { "/lead", "trail/", "a//b" }) {
         PairingException e = assertThrows(PairingException.class, () -> save(name, null));

         assertTrue(e.getMessage().contains("must not start or end with '/'"), e.getMessage());
      }

      verifyNoInteractions(registryService);
   }

   private void save(String name, String scope) throws Exception {
      try(MockedStatic<AutoSaveUtils> autoSave = mockStatic(AutoSaveUtils.class)) {
         controller.save("tok", new ViewsheetAssemblyAgentController.SaveRequest(name, scope),
                         agent);
      }
   }

   private ViewsheetAssemblyAgentController controller(ViewsheetSessionService sessions) {
      SheetAgentFeature feature = mock(SheetAgentFeature.class);
      when(feature.isEnabled()).thenReturn(true);

      return new ViewsheetAssemblyAgentController(feature, mock(SheetJoinService.class),
         mock(SheetSessionService.class), sessions, mock(ViewsheetReadService.class),
         mock(ViewsheetEditService.class), mock(ViewsheetFormatService.class),
         mock(ScriptImageService.class),
         mock(AssemblyPropertyService.class), mock(SheetPropertyService.class),
         mock(AssemblyHyperlinkService.class), mock(ChartElementService.class),
         mock(ChartRegionPropertyService.class), mock(ChartTargetLineService.class),
         mock(HierarchyDimensionService.class), mock(AssemblyConditionService.class),
         mock(AssemblyHighlightService.class), mock(DateComparisonService.class),
         mock(AssemblyConvertService.class), mock(SelectionRuntimeService.class),
         mock(CalendarDisplayService.class), mock(InputValueService.class),
         mock(AssemblyMaxModeService.class), mock(SelectionLayoutService.class),
         mock(FormTableRowService.class), mock(ColumnOptionService.class),
         mock(ParameterCollectionService.class), mock(ParameterValueService.class),
         viewsheetService, mock(SheetAgentBroadcastService.class), mock(SheetOpenService.class),
         mock(LayoutSessionService.class), mock(LayoutReadService.class),
         mock(PrintDeviceLayoutPropertyService.class), mock(LayoutMutationService.class),
         mock(LayoutUndoService.class), mock(VSBookmarkService.class), mock(VSExportService.class),
         mock(SecurityEngine.class), mock(ViewsheetPropertyDialogService.class),
         registryService);
   }
}
