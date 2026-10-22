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
package inetsoft.web.wiz.binding;

import inetsoft.report.TableDataDescriptor;
import inetsoft.report.TableDataPath;
import inetsoft.report.TableLayout;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.VSTableLens;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.viewsheet.CalcTableVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.CalcTableVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.TableVSAssemblyInfo;
import inetsoft.web.binding.controller.VSBindingModelService;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.composer.vs.dialog.HideColumnsDialogService;
import inetsoft.web.wiz.viewsheet.ViewsheetSessionService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #79029: column widths of a calc table by layout column index, and header/data row heights
 * of a Table, Crosstab or calc table.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class TableBindingServiceCalcSizingTest {
   @Test
   void calcWidthIsWrittenOnTheRenderedColumnOfTheLayoutColumn() throws Exception {
      Fixture f = new Fixture(3, 2);
      // Layout column 1 expands to rendered columns 1 and 2; either one carries its path.
      f.paths("Column [0]", "Column [1]", "Column [1]", "Column [2]");

      List<String> applied = f.service().setColumnWidths(
         "tok", principal(), "Calc1", Map.of("1", 200.0)).applied();

      verify(f.info).setColumnWidthValue2(1, 200.0, f.lens);
      verify(f.info).setExplicitTableWidthValue(true);
      verify(f.lens).setColumnWidths(null);
      verify(f.lens).setRowHeights(null);
      assertEquals(List.of("layout column 1 -> 200px"), applied);
   }

   @Test
   void widthSummaryReportsTheTruncatedPixelValue() throws Exception {
      Fixture f = new Fixture(1, 1);
      f.paths("Column [0]");

      List<String> applied = f.service().setColumnWidths(
         "tok", principal(), "Calc1", Map.of("0", 120.7)).applied();

      assertEquals(List.of("layout column 0 -> 120px (truncated from 120.7)"), applied);
   }

   @Test
   void calcWidthNullRemovesThePathKeyedWidth() throws Exception {
      Fixture f = new Fixture(2, 1);
      f.paths("Column [0]", "Column [1]");

      List<String> applied = f.service().setColumnWidths(
         "tok", principal(), "Calc1", Collections.singletonMap("0", null)).applied();

      verify(f.info).removeColumnWidthValue2(0, f.lens);
      verify(f.info, never()).setColumnWidthValue2(anyInt(), anyDouble(), any());
      assertEquals(List.of("layout column 0 -> auto"), applied);
   }

   @Test
   void calcWidthRefusesBadKeysAndWidthsWithoutWritingAnything() throws Exception {
      Fixture f = new Fixture(3, 1);
      f.paths("Column [0]", "Column [1]", "Column [2]");

      Map<String, Double> mixed = new LinkedHashMap<>();
      mixed.put("0", 100.0);
      mixed.put("9", 100.0);
      IllegalArgumentException range = assertThrows(IllegalArgumentException.class,
         () -> f.service().setColumnWidths("tok", principal(), "Calc1", mixed));
      assertTrue(range.getMessage().contains("0 to 2"), range.getMessage());

      IllegalArgumentException text = assertThrows(IllegalArgumentException.class,
         () -> f.service().setColumnWidths("tok", principal(), "Calc1", Map.of("Region", 100.0)));
      assertTrue(text.getMessage().contains("layout column index"), text.getMessage());

      for(double bad : new double[]{ 0.0, 0.5, -3.0, Double.NaN, 32768.0, 3.0e9, 1.0e20 }) {
         assertThrows(IllegalArgumentException.class,
            () -> f.service().setColumnWidths("tok", principal(), "Calc1", Map.of("1", bad)),
            "width " + bad + " must be refused");
      }

      verify(f.info, never()).setColumnWidthValue2(anyInt(), anyDouble(), any());
   }

   @Test
   void calcWidthNamesALayoutColumnThatRendersNothing() throws Exception {
      Fixture f = new Fixture(2, 1);
      f.paths("Column [0]");

      IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
         () -> f.service().setColumnWidths("tok", principal(), "Calc1", Map.of("1", 100.0)));
      assertTrue(thrown.getMessage().contains("renders no column"), thrown.getMessage());
   }

   @Test
   void headerAndDataRowHeightsAreWrittenAndRounded() throws Exception {
      Fixture f = new Fixture(1, 2);
      f.paths("Column [0]");

      List<String> applied = f.service().setRowHeights(
         "tok", principal(), "Calc1", Map.of("1", 40.0), 30.4, false, false).applied();

      InOrder order = inOrder(f.info);
      order.verify(f.info).updateHeaderRowHeights(false);
      order.verify(f.info).setHeaderRowHeight(1, 40);
      verify(f.info).setUserHeaderRowHeight(true);
      verify(f.info).setDataRowHeight(30);
      verify(f.info).setUserDataRowHeight(true);
      verify(f.lens).setRowHeights(null);
      assertEquals(List.of("header row 1 -> 40px", "data rows -> 30px (rounded from 30.4)"),
                   applied);
   }

   @Test
   void headerRowBeyondTheHeaderCountIsRefused() throws Exception {
      Fixture f = new Fixture(1, 2);
      f.paths("Column [0]");

      IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
         () -> f.service().setRowHeights("tok", principal(), "Calc1", Map.of("2", 40.0), null,
                                         false, false));
      assertTrue(thrown.getMessage().contains("0 to 1"), thrown.getMessage());
      verify(f.info, never()).setHeaderRowHeight(anyInt(), anyInt());
   }

   @Test
   void rowHeightResetsClearTheFlagsAndTheStoredHeights() throws Exception {
      Fixture f = new Fixture(1, 2);
      f.paths("Column [0]");

      List<String> applied = f.service().setRowHeights(
         "tok", principal(), "Calc1", null, null, true, true).applied();

      verify(f.info).setUserHeaderRowHeight(false);
      verify(f.info).setUserDataRowHeight(false);
      verify(f.info).setDataRowHeight(AssetUtil.defh);
      assertEquals(List.of("header rows -> default", "data rows -> default"), applied);
   }

   @Test
   void rowHeightsRefuseNothingToDoZeroAndContradictions() throws Exception {
      Fixture f = new Fixture(1, 1);
      f.paths("Column [0]");
      TableBindingService service = f.service();

      assertThrows(IllegalArgumentException.class,
         () -> service.setRowHeights("tok", principal(), "Calc1", null, null, false, false));
      assertThrows(IllegalArgumentException.class,
         () -> service.setRowHeights("tok", principal(), "Calc1", null, 0.0, false, false));
      assertThrows(IllegalArgumentException.class,
         () -> service.setRowHeights("tok", principal(), "Calc1", null, 0.4, false, false));
      assertThrows(IllegalArgumentException.class,
         () -> service.setRowHeights("tok", principal(), "Calc1", null, 20.0, false, true));
      for(double huge : new double[]{ 32768.0, 3.0e9, 1.0e20 }) {
         assertThrows(IllegalArgumentException.class,
            () -> service.setRowHeights("tok", principal(), "Calc1", null, huge, false, false),
            "height " + huge + " must be refused");
         assertThrows(IllegalArgumentException.class,
            () -> service.setRowHeights("tok", principal(), "Calc1", Map.of("0", huge), null,
                                        false, false));
      }
      verify(f.info, never()).setDataRowHeight(anyInt());
   }

   @Test
   void removingAPathKeyedWidthRestoresAutoFit() {
      TableVSAssemblyInfo info = new TableVSAssemblyInfo();
      VSTableLens lens = mock(VSTableLens.class);
      TableDataDescriptor desc = mock(TableDataDescriptor.class);
      when(lens.getDescriptor()).thenReturn(desc);
      when(lens.getColCount()).thenReturn(1);
      when(desc.getColDataPath(0)).thenReturn(new TableDataPath("Column [0]"));

      info.setColumnWidthValue2(0, 120.0, lens);
      assertEquals(120.0, info.getColumnWidth2(0, lens));

      info.removeColumnWidthValue2(0, lens);
      assertTrue(Double.isNaN(info.getColumnWidth2(0, lens)));
   }

   private static Principal principal() {
      Principal principal = mock(Principal.class);
      when(principal.getName()).thenReturn("tester");
      return principal;
   }

   /** A calc table whose lens renders the paths given to {@link #paths} and has header rows. */
   private static final class Fixture {
      final CalcTableVSAssembly assembly = mock(CalcTableVSAssembly.class);
      final CalcTableVSAssemblyInfo info = mock(CalcTableVSAssemblyInfo.class);
      final VSTableLens lens = mock(VSTableLens.class);
      final TableDataDescriptor desc = mock(TableDataDescriptor.class);
      final ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);

      Fixture(int layoutCols, int headerRows) throws Exception {
         TableLayout layout = mock(TableLayout.class);
         when(layout.getColCount()).thenReturn(layoutCols);
         when(info.getTableLayout()).thenReturn(layout);
         when(info.getHeaderRowCount()).thenReturn(headerRows);
         when(info.getHeaderRowHeightsLength()).thenReturn(headerRows);
         when(assembly.getInfo()).thenReturn(info);
         when(assembly.getAbsoluteName()).thenReturn("Calc1");
         when(lens.getDescriptor()).thenReturn(desc);

         ViewsheetSandbox sandbox = mock(ViewsheetSandbox.class);
         when(sandbox.getVSTableLens("Calc1", false)).thenReturn(lens);
         Viewsheet vs = mock(Viewsheet.class);
         when(vs.getAssembly(anyString())).thenReturn(assembly);
         RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
         when(rvs.getViewsheet()).thenReturn(vs);
         when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(sandbox));
         doAnswer(inv -> {
            ViewsheetSessionService.Mutation mutation = inv.getArgument(2);
            mutation.run(rvs, "rt1", null);
            return null;
         }).when(sessions).mutate(anyString(), any(Principal.class), any());
      }

      void paths(String... segments) {
         when(lens.getColCount()).thenReturn(segments.length);

         for(int i = 0; i < segments.length; i++) {
            when(desc.getColDataPath(i)).thenReturn(new TableDataPath(segments[i]));
         }
      }

      TableBindingService service() {
         return new TableBindingService(
            sessions, mock(VSBindingService.class), mock(VSBindingModelService.class),
            mock(DataRefModelFactoryService.class), mock(HideColumnsDialogService.class),
            mock(VSAssemblyInfoHandler.class));
      }
   }
}
