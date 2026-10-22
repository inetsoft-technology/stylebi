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

import inetsoft.report.TableDataPath;
import inetsoft.report.TableLayout;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.VSTableLens;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.internal.table.RuntimeCalcTableLens;
import inetsoft.report.lens.CalcTableLens;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.*;
import inetsoft.uql.viewsheet.CalcTableVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.CalcTableVSAssemblyInfo;
import inetsoft.util.script.graal.GraalJavaScriptEnv;
import inetsoft.web.binding.controller.VSBindingModelService;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.composer.vs.dialog.HideColumnsDialogService;
import inetsoft.web.wiz.viewsheet.ViewsheetSessionService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #79029: calc-table column widths and row heights against a REAL calc table. The lens is a
 * {@link RuntimeCalcTableLens} produced by {@link CalcTableLens#process0()} with a horizontally
 * expanding cell, wrapped in a real {@link VSTableLens}; the assembly, its info and its layout are
 * real. Only the session plumbing (session service, runtime viewsheet, sandbox lookup) is mocked,
 * because those need a running server.
 *
 * <p>Layout (2 rows x 3 columns): (0,0) "Label", (0,1) {@code toList(data['id'])} expanded
 * horizontally over ids 1..3, (0,2) "Tail". It renders 5 columns: layout column 1 becomes rendered
 * columns 1, 2 and 3.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class TableBindingServiceCalcRealLensTest {
   private static final int EXPANDED_LAYOUT_COL = 1;

   @Test
   void everyExpandedCopyOfALayoutColumnSharesItsColumnPath() {
      Fixture f = new Fixture();

      assertEquals(5, f.lens.getColCount(), "layout column 1 should expand into 3 columns");
      assertEquals(List.of(0, 1, 1, 1, 2), f.layoutColumnOfEachRenderedColumn());
   }

   @Test
   void widthWrittenByLayoutIndexAppliesToEveryExpandedCopy() throws Exception {
      Fixture f = new Fixture();

      List<String> applied = f.service().setColumnWidths(
         "tok", principal(), "Calc1", Map.of(String.valueOf(EXPANDED_LAYOUT_COL), 200.0))
         .applied();

      assertEquals(List.of("layout column 1 -> 200px"), applied);

      for(int c = 0; c < f.lens.getColCount(); c++) {
         double width = f.info.getColumnWidth2(c, f.lens);

         if(c >= 1 && c <= 3) {
            assertEquals(200.0, width, "expanded copy at rendered column " + c);
         }
         else {
            assertTrue(Double.isNaN(width), "rendered column " + c + " must stay auto: " + width);
         }
      }

      // The lens recomputes its widths from the info on the next render.
      f.lens.initTableGrid(f.info);
      int[] widths = f.lens.getColumnWidths();
      assertEquals(200, widths[1]);
      assertEquals(200, widths[2]);
      assertEquals(200, widths[3]);
   }

   @Test
   void widthResetRestoresAutoOnEveryExpandedCopy() throws Exception {
      Fixture f = new Fixture();
      TableBindingService service = f.service();

      service.setColumnWidths("tok", principal(), "Calc1", Map.of("1", 200.0));
      service.setColumnWidths("tok", principal(), "Calc1", Collections.singletonMap("1", null));

      for(int c = 1; c <= 3; c++) {
         assertTrue(Double.isNaN(f.info.getColumnWidth2(c, f.lens)), "rendered column " + c);
      }
   }

   @Test
   void rowHeightsWrittenOnTheRealInfoRender() throws Exception {
      Fixture f = new Fixture();
      int headerRows = f.info.getHeaderRowCount();
      assertTrue(headerRows >= 1, "calc info header rows: " + headerRows);

      f.service().setRowHeights("tok", principal(), "Calc1", Map.of("0", 40.0), 30.0,
                                false, false);

      f.lens.initTableGrid(f.info);
      int[] rows = f.lens.getRowHeights();
      assertEquals(40, rows[0], "header row 0");
      assertEquals(30, rows[rows.length - 1], "last data row");
   }

   private static Principal principal() {
      Principal principal = mock(Principal.class);
      when(principal.getName()).thenReturn("tester");
      return principal;
   }

   private static final class Fixture {
      final Viewsheet vs = new Viewsheet();
      final CalcTableVSAssembly assembly = new CalcTableVSAssembly(vs, "Calc1");
      final CalcTableVSAssemblyInfo info = (CalcTableVSAssemblyInfo) assembly.getInfo();
      final VSTableLens lens;
      final ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);

      Fixture() {
         DefaultTableLens data = new DefaultTableLens(new Object[][] {
            { "id" },
            { 1 },
            { 2 },
            { 3 }
         });

         TableLayout layout = new TableLayout();
         layout.setColCount(3);
         assembly.setTableLayout(layout);
         assembly.setScriptTable(data);
         assembly.setScriptEnv(new GraalJavaScriptEnv());
         vs.addAssembly(assembly);

         CalcTableLens calc = new CalcTableLens(2, 3);
         calc.setElement(assembly);
         calc.setObject(0, 0, "Label");
         calc.setFormula(0, EXPANDED_LAYOUT_COL, "toList(data['id'])");
         calc.setExpansion(0, EXPANDED_LAYOUT_COL, CalcTableLens.EXPAND_HORIZONTAL);
         calc.setObject(0, 2, "Tail");
         RuntimeCalcTableLens runtime = calc.process0();
         lens = new VSTableLens(runtime);
      }

      /** The {@code n} of each rendered column's {@code "Column [n]"} data path. */
      List<Integer> layoutColumnOfEachRenderedColumn() {
         List<Integer> out = new ArrayList<>();

         for(int c = 0; c < lens.getColCount(); c++) {
            TableDataPath path = lens.getDescriptor().getColDataPath(c);
            String segment = path.getPath()[0];
            assertTrue(segment.matches("Column \\[\\d+]"), "rendered column " + c + ": " + segment);
            out.add(Integer.parseInt(segment.replaceAll("\\D", "")));
         }

         return out;
      }

      TableBindingService service() throws Exception {
         ViewsheetSandbox sandbox = mock(ViewsheetSandbox.class);
         when(sandbox.getVSTableLens("Calc1", false)).thenReturn(lens);
         RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
         when(rvs.getViewsheet()).thenReturn(vs);
         when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(sandbox));
         doAnswer(inv -> {
            ViewsheetSessionService.Mutation mutation = inv.getArgument(2);
            mutation.run(rvs, "rt1", null);
            return null;
         }).when(sessions).mutate(anyString(), any(Principal.class), any());

         return new TableBindingService(
            sessions, mock(VSBindingService.class), mock(VSBindingModelService.class),
            mock(DataRefModelFactoryService.class), mock(HideColumnsDialogService.class),
            mock(VSAssemblyInfoHandler.class));
      }
   }
}
