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
import inetsoft.report.internal.table.RuntimeCalcTableLens;
import inetsoft.report.internal.table.TableHyperlinkAttr;
import inetsoft.report.lens.AttributeTableLens;
import inetsoft.report.lens.CalcTableLens;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.*;
import inetsoft.uql.viewsheet.CalcTableVSAssembly;
import inetsoft.uql.viewsheet.CrosstabVSAssembly;
import inetsoft.uql.viewsheet.TableDataVSAssembly;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.TableVSAssemblyInfo;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78931: a table/crosstab/freehand cell's hyperlink is stored under the cell's
 * {@link TableDataPath}, which names its kind, never its row or value, so the Viewer fires it on
 * every cell of that kind. get_hyperlink/set_hyperlink said "this cell only". They now disclose
 * the real scope ({@code cellLinkScope}, {@code cellLinkSharedByKind}, and a set warning).
 *
 * <p>The lenses are real ({@link DefaultTableLens}, {@link CrossTabFilter},
 * {@link RuntimeCalcTableLens}, each under a {@link VSTableLens}), and every disclosed scope is
 * checked against what {@link TableHyperlinkAttr}'s render filter actually fires.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class AssemblyHyperlinkCellScopeTest {
   // -- plain table ---------------------------------------------------------------------------

   @Test
   void aPlainTableDataCellLinkCoversEveryDataRowOfItsColumn() {
      VSTableLens lens = new VSTableLens(plainData());

      AssemblyHyperlinkService.CellLinkScope scope =
         AssemblyHyperlinkService.cellLinkScope(lens, 4, 0);

      assertTrue(scope.sharedByKind());
      assertTrue(scope.scope().startsWith("every data row of column 0 ('State')"), scope.scope());
      assertEquals(Set.of("1,0", "2,0", "3,0", "4,0", "5,0", "6,0"), fired(plainData(), 4, 0));
   }

   @Test
   void aPlainTableHeaderCellLinkIsThatCellOnly() {
      AssemblyHyperlinkService.CellLinkScope scope =
         AssemblyHyperlinkService.cellLinkScope(new VSTableLens(plainData()), 0, 1);

      assertFalse(scope.sharedByKind());
      assertTrue(scope.scope().startsWith("this header cell only (column 1, 'City')"),
                 scope.scope());
      assertEquals(Set.of("0,1"), fired(plainData(), 0, 1));
   }

   /**
    * The Viewer resolves a stored header to the FIRST column with that text, so a link
    * addressed to a later duplicate column fires on the first one and never on itself.
    */
   @Test
   void aDuplicateHeaderColumnIsToldTheLinkFiresOnTheFirstSameHeaderColumn() {
      DefaultTableLens data = new DefaultTableLens(new Object[][] {
         { "A", "B", "A" },
         { 1, 2, 3 },
         { 4, 5, 6 },
      });

      AssemblyHyperlinkService.CellLinkScope scope =
         AssemblyHyperlinkService.cellLinkScope(new VSTableLens(data), 1, 2);

      assertTrue(scope.scope().startsWith("no cell of column 2"), scope.scope());
      assertTrue(scope.scope().contains("every data row of column 0"), scope.scope());
      assertEquals(Set.of("1,0", "2,0"), fired(data, 1, 2));
   }

   // -- crosstab ------------------------------------------------------------------------------

   /** Rows State, City; Sum(Sales). An aggregate cell's link covers every aggregate row. */
   @Test
   void aCrosstabAggregateCellLinkCoversEveryAggregateRow() {
      CrossTabFilter crosstab = rowsOnly();
      AssemblyHyperlinkService.CellLinkScope scope =
         AssemblyHyperlinkService.cellLinkScope(new VSTableLens(crosstab), 1, 2);

      assertTrue(scope.sharedByKind());
      assertTrue(scope.scope().startsWith("every aggregate cell of measure"), scope.scope());
      assertTrue(scope.scope().contains("every row of that kind"), scope.scope());
      assertEquals(Set.of("1,2", "2,2", "3,2", "4,2", "5,2", "6,2"), fired(rowsOnly(), 1, 2));
   }

   @Test
   void aCrosstabRowHeaderCellLinkCoversEveryValueOfThatDimension() {
      AssemblyHyperlinkService.CellLinkScope scope =
         AssemblyHyperlinkService.cellLinkScope(new VSTableLens(rowsOnly()), 1, 1);

      assertTrue(scope.sharedByKind());
      assertTrue(scope.scope().startsWith("every row-header cell of this row dimension"),
                 scope.scope());
      assertEquals(Set.of("1,1", "2,1", "3,1", "4,1", "5,1", "6,1"), fired(rowsOnly(), 1, 1));
   }

   @Test
   void aCrosstabCornerCellLinkIsThatCellOnly() {
      AssemblyHyperlinkService.CellLinkScope scope =
         AssemblyHyperlinkService.cellLinkScope(new VSTableLens(rowsOnly()), 0, 0);

      assertFalse(scope.sharedByKind());
      assertTrue(scope.scope().startsWith("this cell only"), scope.scope());
      assertEquals(Set.of("0,0"), fired(rowsOnly(), 0, 0));
   }

   /**
    * Rows State; columns City. One aggregate cell's link fires on every aggregate cell, in every
    * column: "the whole column" would be as wrong as "this cell only".
    */
   @Test
   void withAColumnDimensionAnAggregateCellLinkCoversEveryColumnGroup() {
      CrossTabFilter crosstab = rowsAndCols();
      AssemblyHyperlinkService.CellLinkScope scope =
         AssemblyHyperlinkService.cellLinkScope(new VSTableLens(crosstab), 1, 1);

      assertTrue(scope.scope().contains("every column group of that measure"), scope.scope());
      assertTrue(scope.scope().contains("not just this cell or this column"), scope.scope());

      Set<String> fired = fired(rowsAndCols(), 1, 1);
      Set<String> body = new HashSet<>();

      for(int r = crosstab.getHeaderRowCount(); crosstab.moreRows(r); r++) {
         for(int c = crosstab.getHeaderColCount(); c < crosstab.getColCount(); c++) {
            body.add(r + "," + c);
         }
      }

      assertEquals(body, fired);
      assertTrue(body.size() > crosstab.getRowCount(), "the fixture must span several columns");
   }

   @Test
   void aCrosstabColumnHeaderCellLinkCoversEveryColumnHeaderOfThatDimension() {
      CrossTabFilter crosstab = rowsAndCols();
      AssemblyHyperlinkService.CellLinkScope scope =
         AssemblyHyperlinkService.cellLinkScope(new VSTableLens(crosstab), 0, 1);

      assertTrue(scope.scope().startsWith("every column-header cell of this column dimension"),
                 scope.scope());
      assertEquals(crosstab.getColCount() - 1, fired(rowsAndCols(), 0, 1).size());
   }

   /**
    * Rows State; columns City; Sum(Sales) and Sum(Qty) side by side: the measure-label header
    * row repeats each measure's label under every City, and one label's link fires on each.
    */
   @Test
   void aCrosstabMeasureLabelHeaderLinkCoversEveryRepeatOfThatLabel() {
      CrossTabFilter crosstab = twoMeasures();
      int labelRow = crosstab.getHeaderRowCount() - 1;
      int col = crosstab.getHeaderColCount();
      TableDataPath path = crosstab.getDescriptor().getCellDataPath(labelRow, col);
      assertEquals(TableDataPath.HEADER, path.getType(), "the fixture must render a label row");

      AssemblyHyperlinkService.CellLinkScope scope =
         AssemblyHyperlinkService.cellLinkScope(new VSTableLens(crosstab), labelRow, col);

      assertTrue(scope.sharedByKind());
      assertTrue(scope.scope().startsWith("every label cell of measure"), scope.scope());

      Set<String> expected = new HashSet<>();

      for(int c = col; c < crosstab.getColCount(); c += 2) {
         expected.add(labelRow + "," + c);
      }

      assertEquals(expected, fired(twoMeasures(), labelRow, col));
   }

   // -- freehand table ------------------------------------------------------------------------

   /** Layout row 1 expands to 3 runtime rows: its link fires on each copy. */
   @Test
   void anExpandingFreehandCellLinkCoversEveryRenderedCopy() throws Exception {
      AssemblyHyperlinkService.CellLinkScope scope =
         AssemblyHyperlinkService.cellLinkScope(new VSTableLens(expandingCalc()), 1, 0);

      assertTrue(scope.sharedByKind());
      assertTrue(scope.scope().startsWith("every rendered copy of layout cell [1,0] -- 3 cells"),
                 scope.scope());
      assertEquals(Set.of("1,0", "2,0", "3,0"), fired(expandingCalc(), 1, 0));
   }

   @Test
   void aStaticFreehandCellLinkIsThatCellOnly() throws Exception {
      AssemblyHyperlinkService.CellLinkScope scope =
         AssemblyHyperlinkService.cellLinkScope(new VSTableLens(expandingCalc()), 4, 1);

      assertFalse(scope.sharedByKind());
      assertTrue(scope.scope().startsWith("this cell only, layout cell [2,1]"), scope.scope());
      assertEquals(Set.of("4,1"), fired(expandingCalc(), 4, 1));
   }

   // -- the agent-facing read and write -------------------------------------------------------

   @Test
   void getHyperlinkOnAPlainTableDataCellReportsTheColumnScope() throws Exception {
      Fixture f = fixture(mock(TableVSAssembly.class), new VSTableLens(plainData()));

      Map<String, Object> out = service(f, dialog(false))
         .read("tok", principal(), NAME, region(4, 0));

      assertEquals(Boolean.TRUE, out.get("cellLinkSharedByKind"));
      assertTrue(((String) out.get("cellLinkScope")).startsWith("every data row of column 0"),
                 String.valueOf(out.get("cellLinkScope")));
   }

   @Test
   void setHyperlinkOnACrosstabCellWarnsThatEveryCellOfItsKindGetsTheLink() throws Exception {
      Fixture f = fixture(mock(CrosstabVSAssembly.class), new VSTableLens(rowsOnly()));

      List<String> warnings = service(f, dialog(false)).set(
         "tok", principal(), NAME, region(1, 2),
         new LinkedHashMap<>(Map.of("linkType", "web", "webLink", "https://x")), "");

      assertEquals(1, warnings.size(), warnings.toString());
      assertTrue(warnings.get(0).startsWith(
         "The link on '" + NAME + "' was set for every aggregate cell of measure"),
         warnings.get(0));
   }

   @Test
   void setHyperlinkOnAHeaderCellDoesNotWarn() throws Exception {
      Fixture f = fixture(mock(TableVSAssembly.class), new VSTableLens(plainData()));

      List<String> warnings = service(f, dialog(false)).set(
         "tok", principal(), NAME, region(0, 0),
         new LinkedHashMap<>(Map.of("linkType", "web", "webLink", "https://x")), "");

      assertEquals(List.of(), warnings);
   }

   /** A row link is read back from a data cell; it is the table's, and a Form table never fires it. */
   @Test
   void aRowLinkReadBackSaysItIsTheTablesRowLinkAndAFormTableNeverFiresIt() throws Exception {
      TableVSAssembly table = mock(TableVSAssembly.class);
      TableVSAssemblyInfo info = mock(TableVSAssemblyInfo.class);
      when(table.getVSAssemblyInfo()).thenReturn(info);
      Fixture f = fixture(table, new VSTableLens(plainData()));

      Map<String, Object> out = service(f, dialog(true)).read("tok", principal(), NAME,
                                                              region(2, 1));
      assertTrue(((String) out.get("cellLinkScope")).contains("every data cell of every row"),
                 String.valueOf(out.get("cellLinkScope")));

      when(info.getFormValue()).thenReturn(true);
      out = service(f, dialog(true)).read("tok", principal(), NAME, region(2, 1));
      String scope = (String) out.get("cellLinkScope");
      assertTrue(scope.contains("which no cell fires: this is a Form table"), scope);
      assertFalse(scope.contains("every data cell of every row"), scope);
   }

   // -- fixtures ------------------------------------------------------------------------------

   /**
    * The cells the Viewer's render filter fires a link on, stored the way
    * {@code HyperlinkDialogService.setHyperlinkDialogModel} stores one set at (row, col).
    */
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

   private static DefaultTableLens plainData() {
      return new DefaultTableLens(new Object[][] {
         { "State", "City", "Sales" },
         { "NJ", "Newark", 10 },
         { "NJ", "Trenton", 20 },
         { "NY", "Albany", 30 },
         { "NY", "Buffalo", 40 },
         { "PA", "Erie", 50 },
         { "PA", "York", 60 },
      });
   }

   private static DefaultTableLens crosstabData() {
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

   private static CrossTabFilter rowsOnly() {
      return new CrossTabFilter(crosstabData(), new int[] { 0, 1 }, new int[0], new int[] { 2 },
                                new Formula[] { new SumFormula() });
   }

   private static CrossTabFilter rowsAndCols() {
      return new CrossTabFilter(crosstabData(), new int[] { 0 }, new int[] { 1 }, new int[] { 2 },
                                new Formula[] { new SumFormula() });
   }

   private static CrossTabFilter twoMeasures() {
      CrossTabFilter crosstab = new CrossTabFilter(
         crosstabData(), new int[] { 0 }, new int[] { 1 }, new int[] { 2, 3 },
         new Formula[] { new SumFormula(), new SumFormula() });
      crosstab.setSummarySideBySide(true);
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

   private static Fixture fixture(TableDataVSAssembly assembly, VSTableLens lens)
      throws Exception
   {
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

   private static AssemblyHyperlinkService service(Fixture f, HyperlinkDialogService links)
      throws Exception
   {
      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
      when(sessions.resolve(anyString(), any(Principal.class))).thenReturn(f.rvs());
      doAnswer(invocation -> {
         ViewsheetSessionService.Mutation mutation = invocation.getArgument(2);
         mutation.run(f.rvs(), "rt1", null);
         return null;
      }).when(sessions).mutate(anyString(), any(Principal.class), any());
      return new AssemblyHyperlinkService(sessions, links);
   }

   /** The dialog model a read returns; {@code rowLink} as for a data cell of a row-linked table. */
   private static HyperlinkDialogService dialog(boolean rowLink) throws Exception {
      HyperlinkDialogService links = mock(HyperlinkDialogService.class);
      when(links.getHyperlinkDialogModel(anyString(), anyString(), any(), any(), any(),
                                         anyBoolean(), anyBoolean(), anyBoolean(),
                                         anyBoolean(), any(Principal.class)))
         .thenAnswer(invocation -> {
            HyperlinkDialogModel model = new HyperlinkDialogModel();
            model.setRow(invocation.getArgument(2));
            model.setCol(invocation.getArgument(3));
            model.setTable(true);
            model.setApplyToRow(rowLink);
            return model;
         });
      return links;
   }

   private static AssemblyHyperlinkService.Region region(int row, int col) {
      return new AssemblyHyperlinkService.Region(row, col, null, false, false, false, false);
   }

   private static Principal principal() {
      return () -> "admin";
   }

   private static final String NAME = "Table1";
}
