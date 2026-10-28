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
import inetsoft.report.composition.FormTableLens;
import inetsoft.report.composition.FormTableRow;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.test.*;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.TableVSAssemblyInfo;
import inetsoft.web.composer.vs.dialog.ViewsheetPropertyDialogService;
import inetsoft.web.viewsheet.controller.table.VSFormTableService;
import inetsoft.web.viewsheet.event.table.InsertTableRowEvent;
import inetsoft.web.wiz.dispatch.CapturingCommandDispatcher;
import inetsoft.web.viewsheet.service.VSBookmarkService;
import inetsoft.web.viewsheet.service.VSExportService;
import inetsoft.web.wiz.pairing.*;
import inetsoft.web.wiz.script.ScriptImageService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Redmine #78395, end to end below the plugin: the REST controller's {@code mode} handling feeds
 * the real {@link FormTableRowService}, whose native event is applied to a real
 * {@link FormTableLens} the way {@code VSFormTableService.addRow} applies it ({@code insert()}
 * -> {@code insertRow(row)}, else {@code appendRow(row)}). So "where did the new row land" is read
 * off the real lens, not off an argument captor. Also pins that a runtime table with no data rows,
 * or one whose data never loaded, is not caught by the no-runtime-form-state refusal.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class FormTableInsertRowModeEndToEnd78395Test {
   @ParameterizedTest
   @ValueSource(strings = { "append", "Append", "APPEND" })
   void appendInAnyCaseAtIndexZeroLandsAtDataRowOne(String mode) throws Exception {
      FormTableLens lens = threeRowLens();

      insert(runtimeWith(lens), mode, 0);

      assertEquals(5, lens.getRowCount());
      assertEquals(FormTableRow.ADDED, lens.rows()[2].getRowState()); // header + data row 1
      assertNotEquals(FormTableRow.ADDED, lens.rows()[1].getRowState());
   }

   @ParameterizedTest
   @ValueSource(strings = { "insert", "Insert", "", " " })
   void insertOrBlankAtIndexZeroLandsAtDataRowZero(String mode) throws Exception {
      FormTableLens lens = threeRowLens();

      insert(runtimeWith(lens), mode, 0);

      assertEquals(5, lens.getRowCount());
      assertEquals(FormTableRow.ADDED, lens.rows()[1].getRowState()); // header + data row 0
   }

   @Test
   void omittedModeAtIndexZeroLandsAtDataRowZero() throws Exception {
      FormTableLens lens = threeRowLens();

      insert(runtimeWith(lens), null, 0);

      assertEquals(FormTableRow.ADDED, lens.rows()[1].getRowState());
   }

   @Test
   void afterIsRefusedNamingModeAndTheLensIsUnchanged() {
      FormTableLens lens = threeRowLens();
      Fixture f = runtimeWith(lens);

      IllegalArgumentException e =
         assertThrows(IllegalArgumentException.class, () -> insert(f, "after", 0));

      assertTrue(e.getMessage().contains("'mode'"), e.getMessage());
      assertEquals(4, lens.getRowCount());
      verifyNoInteractions(f.forms);
   }

   /** A runtime table with a header and no data rows is a legitimate target, not "no runtime state". */
   @Test
   void runtimeTableWithNoDataRowsStillInserts() throws Exception {
      FormTableLens lens = new FormTableLens(new DefaultTableLens(new Object[][] { { "a", "b" } }));

      insert(runtimeWith(lens), "insert", 0);

      assertEquals(2, lens.getRowCount());
      assertEquals(FormTableRow.ADDED, lens.rows()[1].getRowState());
   }

   /**
    * A runtime table whose data never loaded still gets a non-null, 0-row lens
    * ({@code new FormTableLens(null)}); it must keep meeting the existing range checks rather than
    * the new no-runtime-state refusal.
    */
   @Test
   void runtimeTableWithUnloadedDataMeetsTheRangeChecksNotTheNoStateRefusal() {
      Fixture f = runtimeWith(new FormTableLens(null));

      for(org.junit.jupiter.api.function.Executable call : List.<org.junit.jupiter.api.function.Executable>of(
         () -> insert(f, "insert", 0),
         () -> f.service.deleteRows("tok", mock(Principal.class), "T", List.of(0), ""),
         () -> f.service.setCell("tok", mock(Principal.class), "T", 0, 0, "x", "")))
      {
         IllegalArgumentException e = assertThrows(IllegalArgumentException.class, call);

         assertFalse(e.getMessage().contains("Design tab"), e.getMessage());
         assertFalse(e.getMessage().contains("no runtime"), e.getMessage());
      }
   }

   private record Fixture(FormTableRowService service, VSFormTableService forms) {}

   private static void insert(Fixture f, String mode, int index) throws Exception {
      controllerWith(f.service).formTableInsertRow(
         "tok", new ViewsheetAssemblyAgentController.FormTableInsertRowRequest("T", index, mode),
         "", mock(Principal.class));
   }

   /** header + 3 data rows */
   private static FormTableLens threeRowLens() {
      return new FormTableLens(new DefaultTableLens(new Object[][] {
         { "a", "b" }, { "x1", "y1" }, { "x2", "y2" }, { "x3", "y3" } }));
   }

   private static Fixture runtimeWith(FormTableLens lens) {
      TableVSAssembly table = mock(TableVSAssembly.class);
      TableVSAssemblyInfo info = mock(TableVSAssemblyInfo.class);
      when(table.getVSAssemblyInfo()).thenReturn(info);
      when(info.isForm()).thenReturn(true);
      when(info.isInsert()).thenReturn(true);
      when(info.isDel()).thenReturn(true);
      when(info.isWriteBack()).thenReturn(true);

      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getAssembly(anyString())).thenReturn(table);

      ViewsheetSandbox box = mock(ViewsheetSandbox.class);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.isRuntime()).thenReturn(true);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(box));

      VSFormTableService forms = mock(VSFormTableService.class);
      CapturingCommandDispatcher dispatcher = mock(CapturingCommandDispatcher.class);
      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);

      try {
         when(box.getFormTableLens(anyString())).thenReturn(lens);

         // VSFormTableService.addRow's own branch on the native event
         doAnswer(inv -> {
            InsertTableRowEvent event = inv.getArgument(1);

            if(event.insert()) {
               lens.insertRow(event.row());
            }
            else {
               lens.appendRow(event.row());
            }

            return null;
         }).when(forms).addRow(anyString(), any(), anyString(), any(), any());

         doAnswer(inv -> {
            ViewsheetSessionService.Mutation mutation = inv.getArgument(2);
            mutation.run(rvs, "rt1", dispatcher);
            return List.of();
         }).when(sessions).mutate(anyString(), any(Principal.class), any());
      }
      catch(Exception e) {
         throw new IllegalStateException(e);
      }

      return new Fixture(new FormTableRowService(sessions, forms), forms);
   }

   private static ViewsheetAssemblyAgentController controllerWith(FormTableRowService rows) {
      SheetAgentFeature feature = mock(SheetAgentFeature.class);
      when(feature.isEnabled()).thenReturn(true);

      return new ViewsheetAssemblyAgentController(feature, mock(SheetJoinService.class),
                                          mock(SheetSessionService.class),
                                          mock(ViewsheetSessionService.class),
                                          mock(ViewsheetReadService.class),
                                          mock(ViewsheetEditService.class),
                                          mock(ViewsheetFormatService.class),
                                          mock(ScriptImageService.class),
                                          mock(AssemblyPropertyService.class),
                                          mock(SheetPropertyService.class),
                                          mock(AssemblyHyperlinkService.class),
                                          mock(ChartElementService.class),
                                          mock(ChartRegionPropertyService.class),
                                          mock(ChartTargetLineService.class),
                                          mock(HierarchyDimensionService.class),
                                          mock(AssemblyConditionService.class),
                                          mock(AssemblyHighlightService.class),
                                          mock(DateComparisonService.class),
                                          mock(AssemblyConvertService.class),
                                          mock(SelectionRuntimeService.class),
                                          mock(CalendarDisplayService.class),
                                          mock(InputValueService.class),
                                          mock(AssemblyMaxModeService.class),
                                          mock(SelectionLayoutService.class),
                                          rows,
                                          mock(ColumnOptionService.class),
                                          mock(ParameterCollectionService.class),
                                          mock(ParameterValueService.class),
                                          mock(ViewsheetService.class),
                                          mock(SheetAgentBroadcastService.class),
                                          mock(SheetOpenService.class),
                                          mock(LayoutSessionService.class),
                                          mock(LayoutReadService.class),
                                          mock(PrintDeviceLayoutPropertyService.class),
                                          mock(LayoutMutationService.class),
                                          mock(LayoutUndoService.class), mock(VSBookmarkService.class),
                                          mock(VSExportService.class), mock(SecurityEngine.class),
                                          mock(ViewsheetPropertyDialogService.class), null);
   }
}
