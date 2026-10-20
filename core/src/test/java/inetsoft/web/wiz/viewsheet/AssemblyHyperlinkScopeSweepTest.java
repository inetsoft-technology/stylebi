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

import inetsoft.report.Hyperlink;
import inetsoft.report.TableDataPath;
import inetsoft.report.TableLens;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.VSTableLens;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.filter.CrossTabFilter;
import inetsoft.report.filter.Formula;
import inetsoft.report.filter.SumFormula;
import inetsoft.report.internal.table.TableHyperlinkAttr;
import inetsoft.report.internal.table.RuntimeCalcTableLens;
import inetsoft.report.lens.AttributeTableLens;
import inetsoft.report.lens.CalcTableLens;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.*;
import inetsoft.uql.viewsheet.CalcTableVSAssembly;
import inetsoft.uql.viewsheet.CrosstabVSAssembly;
import inetsoft.uql.viewsheet.TableDataVSAssembly;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.util.script.graal.GraalJavaScriptEnv;
import inetsoft.web.composer.model.vs.HyperlinkDialogModel;
import inetsoft.web.composer.vs.dialog.HyperlinkDialogService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.*;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78931: the reporter's two scenarios through the service's read/set, and a sweep over every
 * cell of every table kind -- plain table (one and two header rows, a single data row, a
 * duplicate header), crosstab (row/column headers, measure labels side by side and stacked,
 * subtotals, Total labels, grand totals on rows, columns and both, corners, aggregates, with and
 * without a column dimension) and freehand -- checking that {@code sharedByKind} is true exactly
 * when the Viewer's render filter fires the stored link on some cell other than the addressed one.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class AssemblyHyperlinkScopeSweepTest {
   /** vhl-015's disclosure regex. */
   private static final Pattern COLUMN_WIDE =
      Pattern.compile("whole column|entire column|every (data )?row|all (data )?rows|column-wide",
                      Pattern.CASE_INSENSITIVE);

   @Test
   void reporterPlainTableScenario() throws Exception {
      Fixture f = fixture(mock(TableVSAssembly.class), new VSTableLens(twelveRows()));
      AssemblyHyperlinkService service = service(f);

      List<String> warnings = service.set("tok", principal(), NAME, region(4, 0),
         new LinkedHashMap<>(Map.of("linkType", "web", "webLink", "https://x", "applyToRow", false)),
         "");
      assertEquals(1, warnings.size(), warnings.toString());
      assertTrue(COLUMN_WIDE.matcher(warnings.get(0)).find(), warnings.get(0));
      assertTrue(warnings.get(0).contains("column 0"), warnings.get(0));

      String own = (String) service.read("tok", principal(), NAME, region(4, 0)).get("cellLinkScope");

      for(int[] rc : new int[][] { {1, 0}, {2, 0}, {10, 0} }) {
         Map<String, Object> out = service.read("tok", principal(), NAME, region(rc[0], rc[1]));
         assertEquals(Boolean.TRUE, out.get("cellLinkSharedByKind"));
         assertEquals(own, out.get("cellLinkScope"));
         assertTrue(COLUMN_WIDE.matcher(Objects.toString(out)).find());
      }

      Map<String, Object> other = service.read("tok", principal(), NAME, region(4, 1));
      String otherScope = (String) other.get("cellLinkScope");
      assertTrue(otherScope.contains("column 1"), otherScope);
      assertFalse(otherScope.contains("column 0"), otherScope);

      Map<String, Object> header = service.read("tok", principal(), NAME, region(0, 0));
      assertEquals(Boolean.FALSE, header.get("cellLinkSharedByKind"));

      Set<String> expected = new HashSet<>();

      for(int r = 1; r <= 11; r++) {
         expected.add(r + ",0");
      }

      assertEquals(expected, fired(twelveRows(), 4, 0));
   }

   @Test
   void reporterCrosstabScenario() throws Exception {
      Fixture f = fixture(mock(CrosstabVSAssembly.class), new VSTableLens(regionCrosstab()));
      AssemblyHyperlinkService service = service(f);

      List<String> warnings = service.set("tok", principal(), NAME, region(1, 1),
         new LinkedHashMap<>(Map.of("linkType", "web", "webLink", "https://x")), "");
      assertEquals(1, warnings.size(), warnings.toString());
      assertTrue(COLUMN_WIDE.matcher(warnings.get(0)).find(), warnings.get(0));

      Map<String, Object> sibling = service.read("tok", principal(), NAME, region(2, 1));
      assertEquals(Boolean.TRUE, sibling.get("cellLinkSharedByKind"));
      assertTrue(COLUMN_WIDE.matcher(Objects.toString(sibling)).find());
      assertTrue(fired(regionCrosstab(), 1, 1).contains("2,1"));
   }

   /** The reporter's crosstab (one row dimension, one aggregate): its aggregate's header cell. */
   @Test
   void reporterCrosstabMeasureHeaderIsOneCell() throws Exception {
      Fixture f = fixture(mock(CrosstabVSAssembly.class), new VSTableLens(regionCrosstab()));
      Map<String, Object> header = service(f).read("tok", principal(), NAME, region(0, 1));
      assertEquals(Set.of("0,1"), fired(regionCrosstab(), 0, 1));
      assertEquals(Boolean.FALSE, header.get("cellLinkSharedByKind"),
                   String.valueOf(header.get("cellLinkScope")));
   }

   @Test
   void sweepSubtotalAndGrandTotalCrosstab() {
      sweep("subtotals+grand", AssemblyHyperlinkScopeSweepTest::withTotals);
   }

   @Test
   void sweepStackedMeasureLabels() {
      sweep("stacked", AssemblyHyperlinkScopeSweepTest::stackedMeasures);
   }

   @Test
   void sweepColumnDimensionWithTotals() {
      sweep("cols+grand", AssemblyHyperlinkScopeSweepTest::colsWithTotals);
   }

   @Test
   void sweepPlainTable() {
      sweep("plain", AssemblyHyperlinkScopeSweepTest::twelveRows);
   }

   @Test
   void sweepPlainTableEdgeShapes() {
      sweep("one data row", () -> new DefaultTableLens(new Object[][] { { "A", "B" }, { 1, 2 } }));
      sweep("duplicate header", () -> new DefaultTableLens(new Object[][] {
         { "A", "B", "A" }, { 1, 2, 3 }, { 4, 5, 6 } }));
      sweep("two header rows", () -> {
         DefaultTableLens table = new DefaultTableLens(new Object[][] {
            { "A", "B" }, { "a", "b" }, { 1, 2 }, { 3, 4 } });
         table.setHeaderRowCount(2);
         return table;
      });
   }

   @Test
   void sweepCrosstabWithoutTotals() {
      sweep("rows only", AssemblyHyperlinkScopeSweepTest::regionCrosstab);
      sweep("rows+cols", () -> new CrossTabFilter(data(), new int[] { 0 }, new int[] { 1 },
         new int[] { 2 }, new Formula[] { new SumFormula() }));
   }

   @Test
   void sweepSideBySideMeasureLabels() {
      sweep("side by side", () -> {
         CrossTabFilter crosstab = new CrossTabFilter(data(), new int[] { 0 }, new int[] { 1 },
            new int[] { 2, 3 }, new Formula[] { new SumFormula(), new SumFormula() });
         crosstab.setSummarySideBySide(true);
         crosstab.setShowSummaryHeaders(true);
         return crosstab;
      });
   }

   @Test
   void sweepGrandTotalsOnEachAxis() {
      sweep("row grand total, rows only", () -> {
         CrossTabFilter crosstab = regionCrosstab();
         crosstab.setSuppressRowGrandTotal(false);
         return crosstab;
      });
      sweep("column grand total only", () -> {
         CrossTabFilter crosstab = new CrossTabFilter(data(), new int[] { 0 }, new int[] { 1 },
            new int[] { 2 }, new Formula[] { new SumFormula() });
         crosstab.setSuppressColumnGrandTotal(false);
         return crosstab;
      });
      sweep("both grand totals, two measures", () -> {
         CrossTabFilter crosstab = new CrossTabFilter(data(), new int[] { 0 }, new int[] { 1 },
            new int[] { 2, 3 }, new Formula[] { new SumFormula(), new SumFormula() });
         crosstab.setSuppressRowGrandTotal(false);
         crosstab.setSuppressColumnGrandTotal(false);
         return crosstab;
      });
   }

   @Test
   void sweepSubtotalsWithAColumnDimension() {
      sweep("subtotals+cols", () -> {
         CrossTabFilter crosstab = new CrossTabFilter(data(), new int[] { 0, 1 },
            new int[] { 3 }, new int[] { 2 }, new Formula[] { new SumFormula() });
         crosstab.setSuppressRowGroupTotal(false, 0);
         crosstab.setSuppressRowGrandTotal(false);
         crosstab.setSuppressColumnGrandTotal(false);
         return crosstab;
      });
   }

   @Test
   void sweepFreehandTable() {
      sweep("freehand", () -> {
         try {
            return expandingCalc();
         }
         catch(Exception e) {
            throw new RuntimeException(e);
         }
      });
   }

   /**
    * For every cell: sharedByKind must be true exactly when the stored link fires on some cell
    * other than (or not on) the addressed one; a single-cell scope must not say "not just".
    */
   private static void sweep(String label, Supplier<TableLens> table) {
      TableLens t = table.get();
      List<String> problems = new ArrayList<>();

      for(int r = 0; t.moreRows(r); r++) {
         for(int c = 0; c < t.getColCount(); c++) {
            AssemblyHyperlinkService.CellLinkScope scope =
               AssemblyHyperlinkService.cellLinkScope(new VSTableLens(table.get()), r, c);
            TableDataPath path = t.getDescriptor().getCellDataPath(r, c);
            Set<String> fired = fired(table.get(), r, c);
            boolean single = fired.equals(Set.of(r + "," + c));
            if(scope == null) {
               problems.add("[" + r + "," + c + "] no scope");
            }
            else if(scope.sharedByKind() == single) {
               problems.add("[" + r + "," + c + "] " + path + " shared=" + scope.sharedByKind() +
                            " but fired=" + fired);
            }
            else if(single && scope.scope().contains("not just")) {
               problems.add("[" + r + "," + c + "] " + path + " is one cell, but: " +
                            scope.scope());
            }
         }
      }

      assertEquals(List.of(), problems, label);
   }

   // -- fixtures ------------------------------------------------------------------------------

   private static Set<String> fired(TableLens table, int row, int col) {
      TableHyperlinkAttr attr = new TableHyperlinkAttr();
      attr.setHyperlink(table.getDescriptor().getCellDataPath(row, col),
                        new Hyperlink("https://x", Hyperlink.WEB_LINK));
      TableLens render = attr.createFilter(table);
      Set<String> fired = new HashSet<>();

      for(int r = 0; render.moreRows(r); r++) {
         for(int c = 0; c < render.getColCount(); c++) {
            if(((AttributeTableLens) render).getHyperlink(r, c) != null) {
               fired.add(r + "," + c);
            }
         }
      }

      return fired;
   }

   private static DefaultTableLens twelveRows() {
      Object[][] rows = new Object[12][];
      rows[0] = new Object[] { "Company", "State" };

      for(int i = 1; i < rows.length; i++) {
         rows[i] = new Object[] { "Co" + i, "S" + (i % 3) };
      }

      return new DefaultTableLens(rows);
   }

   private static DefaultTableLens data() {
      return new DefaultTableLens(new Object[][] {
         { "State", "City", "Sales", "Qty" },
         { "NJ", "Newark", 10, 1 },
         { "NJ", "Trenton", 20, 2 },
         { "NY", "Albany", 30, 3 },
         { "NY", "Buffalo", 40, 4 },
         { "PA", "Erie", 50, 5 },
         { "PA", "York", 60, 6 },
      });
   }

   private static CrossTabFilter regionCrosstab() {
      return new CrossTabFilter(data(), new int[] { 0 }, new int[0], new int[] { 2 },
                                new Formula[] { new SumFormula() });
   }

   private static CrossTabFilter withTotals() {
      CrossTabFilter crosstab = new CrossTabFilter(data(), new int[] { 0, 1 }, new int[0],
         new int[] { 2 }, new Formula[] { new SumFormula() });
      crosstab.setSuppressRowGroupTotal(false, 0);
      crosstab.setSuppressRowGrandTotal(false);
      return crosstab;
   }

   private static CrossTabFilter colsWithTotals() {
      CrossTabFilter crosstab = new CrossTabFilter(data(), new int[] { 0 }, new int[] { 1 },
         new int[] { 2 }, new Formula[] { new SumFormula() });
      crosstab.setSuppressRowGrandTotal(false);
      crosstab.setSuppressColumnGrandTotal(false);
      return crosstab;
   }

   private static CrossTabFilter stackedMeasures() {
      CrossTabFilter crosstab = new CrossTabFilter(data(), new int[] { 0 }, new int[] { 1 },
         new int[] { 2, 3 }, new Formula[] { new SumFormula(), new SumFormula() });
      crosstab.setSummarySideBySide(false);
      crosstab.setShowSummaryHeaders(true);
      return crosstab;
   }

   /** Layout 3x2, row 1 a group of 3 ids expanding vertically: runtime rows [0, 1, 1, 1, 2]. */
   private static RuntimeCalcTableLens expandingCalc() throws Exception {
      DefaultTableLens data = new DefaultTableLens(new Object[][] { { "id" }, { 1 }, { 2 }, { 3 } });
      CalcTableVSAssembly element = new CalcTableVSAssembly();
      element.setScriptTable(data);
      element.setScriptEnv(new GraalJavaScriptEnv());

      CalcTableLens calc = new CalcTableLens(3, 2);
      calc.setElement(element);
      calc.setFormula(1, 0, "toList(data['id'])");
      calc.setExpansion(1, 0, CalcTableLens.EXPAND_VERTICAL);
      return calc.process0();
   }

   private record Fixture(RuntimeViewsheet rvs) {
   }

   private static Fixture fixture(TableDataVSAssembly assembly, VSTableLens lens) throws Exception {
      when(assembly.getAbsoluteName()).thenReturn(NAME);
      ViewsheetSandbox box = mock(ViewsheetSandbox.class);
      when(box.getVSTableLens(NAME, false)).thenReturn(lens);
      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getAssembly(NAME)).thenReturn(assembly);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(box));
      when(rvs.getID()).thenReturn("rt1");
      return new Fixture(rvs);
   }

   private static AssemblyHyperlinkService service(Fixture f) throws Exception {
      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
      when(sessions.resolve(anyString(), any(Principal.class))).thenReturn(f.rvs());
      doAnswer(invocation -> {
         ViewsheetSessionService.Mutation mutation = invocation.getArgument(2);
         mutation.run(f.rvs(), "rt1", null);
         return null;
      }).when(sessions).mutate(anyString(), any(Principal.class), any());

      HyperlinkDialogService links = mock(HyperlinkDialogService.class);
      when(links.getHyperlinkDialogModel(anyString(), anyString(), any(), any(), any(),
                                         anyBoolean(), anyBoolean(), anyBoolean(),
                                         anyBoolean(), any(Principal.class)))
         .thenAnswer(invocation -> {
            HyperlinkDialogModel model = new HyperlinkDialogModel();
            model.setRow(invocation.getArgument(2));
            model.setCol(invocation.getArgument(3));
            model.setTable(true);
            model.setShowRow(true);
            return model;
         });
      return new AssemblyHyperlinkService(sessions, links);
   }


   private static AssemblyHyperlinkService.Region region(int row, int col) {
      return new AssemblyHyperlinkService.Region(row, col, null, false, false, false, false);
   }

   private static Principal principal() {
      return () -> "admin";
   }

   private static final String NAME = "Table1";
}
