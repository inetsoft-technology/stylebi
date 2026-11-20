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

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
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
import org.mockito.MockedStatic;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #81758: an in-place {@code save_viewsheet} must refuse when another open runtime of the
 * same asset holds unsaved edits, unless {@code confirmOverwrite} is true.
 */
@WizAgentTestSupport
class ViewsheetAssemblyAgentControllerSaveOtherCopyTest {
   private final Principal agent = TestPrincipals.user("alice", "host-org");
   private final AssetRepository repository = mock(AssetRepository.class);
   private final ViewsheetService viewsheetService =
      mock(ViewsheetService.class);
   private final RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
   private final AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
      AssetEntry.Type.VIEWSHEET, "Sales/dash", null, "host-org");
   private ViewsheetAssemblyAgentController controller;

   ViewsheetAssemblyAgentControllerSaveOtherCopyTest() throws Exception {
      when(viewsheetService.getAssetRepository()).thenReturn(repository);
      when(rvs.getEntry()).thenReturn(entry);
      when(rvs.getID()).thenReturn("rt-agent");
      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
      when(sessions.resolve(eq("tok"), eq(agent))).thenReturn(rvs);
      controller = controller(sessions);
   }

   @Test
   void refusesWhenAnotherCopyHasUnsavedEdits() throws Exception {
      other("rt-tab", entry(entry.getPath()), 3, 1);

      PairingException e = assertThrows(PairingException.class, () -> save(null, null));

      assertTrue(e.getMessage().contains("other open cop"), e.getMessage());
      assertTrue(e.getMessage().contains("Sales/dash"), e.getMessage());
      verifySaved(false);
   }

   @Test
   void savesWhenConfirmOverwriteIsTrue() throws Exception {
      other("rt-tab", entry(entry.getPath()), 3, 1);

      save(null, true);

      verifySaved(true);
   }

   @Test
   void savesWhenThereIsNoOtherRuntime() throws Exception {
      when(viewsheetService.getRuntimeViewsheets(any())).thenReturn(new RuntimeViewsheet[] { rvs });

      save(null, null);

      verifySaved(true);
   }

   @Test
   void ignoresPreviewRuntimes() throws Exception {
      other("rt" + ViewsheetService.PREVIEW_VIEWSHEET + "1", entry(entry.getPath()), 3, 1);

      save(null, null);

      verifySaved(true);
   }

   @Test
   void ignoresADifferentPath() throws Exception {
      other("rt-tab", entry("Sales/other"), 3, 1);

      save(null, null);

      verifySaved(true);
   }

   @Test
   void ignoresACopyWithNoUnsavedEdits() throws Exception {
      other("rt-tab", entry(entry.getPath()), 2, 2);

      save(null, null);

      verifySaved(true);
   }

   @Test
   void namedSaveIsUnchanged() throws Exception {
      other("rt-tab", entry(entry.getPath()), 3, 1);

      save("newdash", null);

      verifySaved(true);
   }

   private AssetEntry entry(String path) {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET, path, null,
                            "host-org");
   }

   private void other(String id, AssetEntry otherEntry, int current, int savePoint) {
      RuntimeViewsheet rs = mock(RuntimeViewsheet.class);
      when(rs.getID()).thenReturn(id);
      when(rs.getEntry()).thenReturn(otherEntry);
      when(rs.getCurrent()).thenReturn(current);
      when(rs.getSavePoint()).thenReturn(savePoint);
      when(viewsheetService.getRuntimeViewsheets(any()))
         .thenReturn(new RuntimeViewsheet[] { rvs, rs });
   }

   private void verifySaved(boolean saved) throws Exception {
      verify(viewsheetService, saved ? times(1) : never())
         .setViewsheet(any(), any(), any(), eq(true), eq(true));
   }

   private void save(String name, Boolean confirm) throws Exception {
      try(MockedStatic<AutoSaveUtils> autoSave = mockStatic(AutoSaveUtils.class)) {
         controller.save("tok",
            new ViewsheetAssemblyAgentController.SaveRequest(name, null, confirm), agent);
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
         mock(FormTableRowService.class), mock(FormTableImportService.class),
         mock(EmbeddedTableCellService.class),
         mock(ColumnOptionService.class),
         mock(ParameterCollectionService.class), mock(ParameterValueService.class),
         viewsheetService, mock(SheetAgentBroadcastService.class), mock(SheetOpenService.class),
         mock(LayoutSessionService.class), mock(LayoutReadService.class),
         mock(PrintDeviceLayoutPropertyService.class), mock(LayoutMutationService.class),
         mock(LayoutUndoService.class), mock(VSBookmarkService.class), mock(VSExportService.class),
         mock(SecurityEngine.class), mock(ViewsheetPropertyDialogService.class),
         mock(RepletRegistryService.class), null);
   }
}
