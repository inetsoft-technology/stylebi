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

import inetsoft.sree.security.SecurityEngine;
import inetsoft.web.viewsheet.service.VSBookmarkService;
import inetsoft.web.viewsheet.service.VSExportService;
import inetsoft.web.wiz.pairing.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Redmine #78395 item 2: {@code form_table_insert_row}'s {@code mode} is the closed set
 * insert/append. Any other value was silently treated as insert; it is refused naming
 * {@code mode} now. Case and surrounding blanks are normalized, and an omitted mode is insert.
 */
@Tag("core")
class FormTableInsertRowModeTest {
   @ParameterizedTest
   @ValueSource(strings = { "append", "Append", "APPEND", " append " })
   void appendInAnyCaseAppends(String mode) throws Exception {
      FormTableRowService rows = mock(FormTableRowService.class);

      insert(rows, mode);

      verify(rows).insertRow(eq("tok"), any(), eq("T"), eq(0), eq(true), eq(""));
   }

   @ParameterizedTest
   @ValueSource(strings = { "insert", "Insert", "", "  " })
   void insertOrBlankInserts(String mode) throws Exception {
      FormTableRowService rows = mock(FormTableRowService.class);

      insert(rows, mode);

      verify(rows).insertRow(eq("tok"), any(), eq("T"), eq(0), eq(false), eq(""));
   }

   @Test
   void omittedModeInserts() throws Exception {
      FormTableRowService rows = mock(FormTableRowService.class);

      insert(rows, null);

      verify(rows).insertRow(eq("tok"), any(), eq("T"), eq(0), eq(false), eq(""));
   }

   @ParameterizedTest
   @ValueSource(strings = { "after", "before", "bogus", "appendRow" })
   void unknownModeIsRefusedNamingMode(String mode) {
      FormTableRowService rows = mock(FormTableRowService.class);

      IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                                                () -> insert(rows, mode));

      assertTrue(e.getMessage().contains("'mode'"), e.getMessage());
      assertTrue(e.getMessage().contains("insert, append"), e.getMessage());
      assertTrue(e.getMessage().contains("'" + mode + "'"), e.getMessage());
      verifyNoInteractions(rows);
   }

   private static void insert(FormTableRowService rows, String mode) throws Exception {
      controllerWith(rows).formTableInsertRow(
         "tok", new ViewsheetAssemblyAgentController.FormTableInsertRowRequest("T", 0, mode), "",
         mock(Principal.class));
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
                                          mock(inetsoft.web.wiz.script.ScriptImageService.class),
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
                                          mock(inetsoft.analytic.composition.ViewsheetService.class),
                                          mock(SheetAgentBroadcastService.class),
                                          mock(SheetOpenService.class),
                                          mock(LayoutSessionService.class),
                                          mock(LayoutReadService.class),
                                          mock(PrintDeviceLayoutPropertyService.class),
                                          mock(LayoutMutationService.class),
                                          mock(LayoutUndoService.class), mock(VSBookmarkService.class),
                                          mock(VSExportService.class), mock(SecurityEngine.class),
                                          mock(inetsoft.web.composer.vs.dialog.ViewsheetPropertyDialogService.class), null);
   }
}
