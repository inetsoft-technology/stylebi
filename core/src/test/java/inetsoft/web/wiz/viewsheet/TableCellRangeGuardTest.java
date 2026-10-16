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

import inetsoft.report.TableLayout;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.VSTableLens;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.internal.table.RuntimeCalcTableLens;
import inetsoft.report.lens.CalcTableLens;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.util.script.graal.GraalJavaScriptEnv;
import inetsoft.web.composer.model.vs.HighlightDialogModel;
import inetsoft.web.composer.model.vs.HyperlinkDialogModel;
import inetsoft.web.composer.vs.dialog.HighlightDialogService;
import inetsoft.web.composer.vs.dialog.HyperlinkDialogService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78719 item 1: set_highlight / set_hyperlink (and list_highlights / get_hyperlink) with a
 * row/col outside the table were forwarded to the native dialog services unchecked. A plain
 * table's column past the end stored the link under a null path that every such column shared,
 * a row past the end hit the trailer path, a calc cell past the grid got a synthetic path, and a
 * negative index was a raw 500. A freehand table is addressed by LAYOUT row/col, so on a table
 * whose groups expand its cell must be translated to the runtime row the native service takes,
 * or the write lands on another layout cell.
 *
 * <p>The lenses are real: a {@link VSTableLens} over a {@link DefaultTableLens}, and over a
 * {@link RuntimeCalcTableLens} produced by {@link CalcTableLens#process0()} from a group that
 * expands.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class TableCellRangeGuardTest {
   // -- plain table and crosstab: runtime row/col ---------------------------------------------

   @ParameterizedTest(name = "({0},{1})")
   @CsvSource({ "1,99", "1,2", "99,1", "3,1", "-1,0", "1,-1" })
   void aPlainTableCellOutsideTheLensIsRefusedNamingTheRange(int row, int col) throws Exception {
      Fixture f = plain(mock(TableVSAssembly.class));

      IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
         () -> f.guard(row, col));

      String message = thrown.getMessage();
      assertTrue(message.contains("Cell [" + row + "," + col + "]"), message);
      assertTrue(message.contains("3 row(s) by 2 column(s)"), message);
   }

   /** Header row 0 and the trailer row (the last one) are real cells and stay addressable. */
   @ParameterizedTest(name = "({0},{1})")
   @CsvSource({ "0,0", "0,1", "1,1", "2,0", "2,1" })
   void aPlainTableCellInsideTheLensIsKept(int row, int col) throws Exception {
      Fixture f = plain(mock(TableVSAssembly.class));

      assertEquals(new TableLensGuard.Cell(row, col), f.guard(row, col));
   }

   @ParameterizedTest(name = "({0},{1})")
   @CsvSource({ "1,99", "99,1", "-1,0", "1,-1" })
   void aCrosstabCellOutsideTheLensIsRefused(int row, int col) throws Exception {
      Fixture f = plain(mock(CrosstabVSAssembly.class));

      assertThrows(IllegalArgumentException.class, () -> f.guard(row, col));
   }

   @Test
   void aCrosstabCellInsideTheLensIsKept() throws Exception {
      Fixture f = plain(mock(CrosstabVSAssembly.class));

      assertEquals(new TableLensGuard.Cell(2, 1), f.guard(2, 1));
   }

   // -- freehand table: layout row/col --------------------------------------------------------

   /** The mechanism the translation exists for: layout and runtime rows differ. */
   @Test
   void theExpandingCalcTableRendersMoreRowsThanItsLayout() throws Exception {
      Fixture f = expandingCalc(3);

      assertEquals(5, f.lens().getRowCount());
      assertEquals(2, ((RuntimeCalcTableLens) f.runtime()).getRow(4));
   }

   /**
    * Layout row 2 (below the group) renders at runtime row 4. Passing 2 through would write onto
    * layout row 1, the group row, which is what runtime row 2 is an instance of.
    */
   @ParameterizedTest(name = "layout ({0},{1}) -> runtime ({2},{3})")
   @CsvSource({ "0,0,0,0", "1,0,1,0", "1,1,1,1", "2,0,4,0", "2,1,4,1" })
   void anExpandingCalcCellIsTranslatedToItsFirstRuntimeRow(int row, int col, int runtimeRow,
                                                           int runtimeCol)
      throws Exception
   {
      Fixture f = expandingCalc(3);

      TableLensGuard.Cell cell = f.guard(row, col);

      assertEquals(new TableLensGuard.Cell(runtimeRow, runtimeCol), cell);
      // The path the native services store a highlight/link under.
      assertEquals("Cell [" + row + "," + col + "]",
                   f.lens().getTableDataPath(cell.row(), cell.col()).getPath()[0],
                   "the runtime cell must carry the layout cell's own path");
   }

   /** Runtime row 4 exists, but the layout has 3 rows: layout coordinates are what is checked. */
   @ParameterizedTest(name = "({0},{1})")
   @CsvSource({ "3,0", "4,0", "99,99", "-1,0", "0,-1", "0,2" })
   void anExpandingCalcCellOutsideTheLayoutIsRefused(int row, int col) throws Exception {
      Fixture f = expandingCalc(3);

      IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
         () -> f.guard(row, col));

      String message = thrown.getMessage();
      assertTrue(message.contains("outside the grid"), message);
      assertTrue(message.contains("3 row(s) by 2 column(s)"), message);
   }

   /** A group with no values renders no row, so its layout cell has nowhere to be written. */
   @Test
   void aCalcCellWhoseGroupExpandsToNothingIsRefusedByName() throws Exception {
      Fixture f = expandingCalc(0);

      IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
         () -> f.guard(1, 0));

      String message = thrown.getMessage();
      assertTrue(message.contains("Cell [1,0]"), message);
      assertTrue(message.contains("renders nowhere"), message);
      // The row below it still renders, one row up.
      assertEquals(new TableLensGuard.Cell(1, 0), f.guard(2, 0));
   }

   // -- the services: read and write ----------------------------------------------------------

   @Test
   void setHyperlinkOnAPlainTableColumnPastTheEndIsRefusedBeforeTheDialogService()
      throws Exception
   {
      Fixture f = plain(mock(TableVSAssembly.class));
      HyperlinkDialogService links = hyperlinkDialog();

      assertThrows(IllegalArgumentException.class, () -> hyperlinks(f, links).set(
         "tok", principal(), NAME, linkRegion(1, 99), webLink(), ""));
      assertThrows(IllegalArgumentException.class, () -> hyperlinks(f, links).read(
         "tok", principal(), NAME, linkRegion(-1, 0)));

      verifyNoInteractions(links);
   }

   @Test
   void setHyperlinkOnAnExpandingCalcCellWritesItsRuntimeCell() throws Exception {
      Fixture f = expandingCalc(3);
      HyperlinkDialogService links = hyperlinkDialog();

      hyperlinks(f, links).set("tok", principal(), NAME, linkRegion(2, 1), webLink(), "");

      verify(links).getHyperlinkDialogModel(eq("rt1"), eq(NAME), eq(4), eq(1), isNull(),
                                            eq(false), eq(false), eq(false), eq(false),
                                            any(Principal.class));
   }

   @Test
   void getHyperlinkOnAnExpandingCalcCellReadsItsRuntimeCellAndEchoesTheLayoutCell()
      throws Exception
   {
      Fixture f = expandingCalc(3);
      HyperlinkDialogService links = hyperlinkDialog();

      Map<String, Object> out =
         hyperlinks(f, links).read("tok", principal(), NAME, linkRegion(2, 1));

      verify(links).getHyperlinkDialogModel(eq("rt1"), eq(NAME), eq(4), eq(1), isNull(),
                                            eq(false), eq(false), eq(false), eq(false),
                                            any(Principal.class));
      assertEquals(2, out.get("row"));
      assertEquals(1, out.get("col"));
   }

   @Test
   void listHighlightsOutsideAPlainTableIsRefusedBeforeTheDialogService() throws Exception {
      Fixture f = plain(mock(TableVSAssembly.class));
      HighlightDialogService highlights = highlightDialog();

      for(int[] cell : new int[][] { { 1, 99 }, { 99, 1 }, { -1, 0 }, { 1, -1 } }) {
         assertThrows(IllegalArgumentException.class, () -> highlights(f, highlights).list(
            "tok", principal(), NAME, highlightRegion(cell[0], cell[1])));
      }

      verify(highlights, never()).getHighlightDialogModel(anyString(), anyString(), any(),
                                                          any(), any(), anyBoolean(),
                                                          anyBoolean(), any());
   }

   @Test
   void listHighlightsOnAnExpandingCalcCellReadsItsRuntimeCell() throws Exception {
      Fixture f = expandingCalc(3);
      HighlightDialogService highlights = highlightDialog();

      highlights(f, highlights).list("tok", principal(), NAME, highlightRegion(2, 0));

      verify(highlights).getHighlightDialogModel(eq("rt1"), eq(NAME), eq(4), eq(0), isNull(),
                                                 eq(false), eq(false), any());
   }

   /** No region is the assembly's default cell, which the fall-forward picks; not guarded. */
   @Test
   void listHighlightsWithNoRegionIsNotGuarded() throws Exception {
      Fixture f = expandingCalc(3);
      HighlightDialogService highlights = highlightDialog();

      highlights(f, highlights).list("tok", principal(), NAME, null);

      verify(highlights).getHighlightDialogModel(eq("rt1"), eq(NAME), eq(0), eq(0), isNull(),
                                                 eq(false), eq(false), any());
   }

   // -- fixtures ------------------------------------------------------------------------------

   private record Fixture(RuntimeViewsheet rvs, TableDataVSAssembly assembly, VSTableLens lens,
                          inetsoft.report.TableLens runtime)
   {
      TableLensGuard.Cell guard(int row, int col) throws Exception {
         return TableLensGuard.requireRenderedCell(rvs, assembly, NAME, row, col, "x");
      }
   }

   /** A 2-column table: a header row, a data row and a trailer row. */
   private static Fixture plain(TableDataVSAssembly assembly) throws Exception {
      DefaultTableLens data = new DefaultTableLens(new Object[][] {
         { "G", "Qty" },
         { "a", 10 },
         { "Total", 10 }
      });
      data.setTrailerRowCount(1);
      return fixture(assembly, new VSTableLens(data), data);
   }

   /**
    * Layout 3x2: row 0 static, row 1 a group of {@code values} ids expanding vertically, row 2
    * static. With 3 values the runtime rows map to layout rows [0, 1, 1, 1, 2].
    */
   private static Fixture expandingCalc(int values) throws Exception {
      Object[][] rows = new Object[values + 1][];
      rows[0] = new Object[] { "id" };

      for(int i = 1; i <= values; i++) {
         rows[i] = new Object[] { i };
      }

      DefaultTableLens data = new DefaultTableLens(rows);
      CalcTableVSAssembly element = new CalcTableVSAssembly();
      element.setScriptTable(data);
      element.setScriptEnv(new GraalJavaScriptEnv());

      CalcTableLens calc = new CalcTableLens(3, 2);
      calc.setElement(element);
      calc.setFormula(1, 0, "toList(data['id'])");
      calc.setExpansion(1, 0, CalcTableLens.EXPAND_VERTICAL);
      RuntimeCalcTableLens runtime = calc.process0();

      TableLayout layout = mock(TableLayout.class);
      when(layout.getRowCount()).thenReturn(3);
      when(layout.getColCount()).thenReturn(2);
      CalcTableVSAssembly assembly = mock(CalcTableVSAssembly.class);
      when(assembly.getTableLayout()).thenReturn(layout);

      return fixture(assembly, new VSTableLens(runtime), runtime);
   }

   private static Fixture fixture(TableDataVSAssembly assembly, VSTableLens lens,
                                  inetsoft.report.TableLens runtime)
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
      return new Fixture(rvs, assembly, lens, runtime);
   }

   private static ViewsheetSessionService sessions(Fixture f) throws Exception {
      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
      when(sessions.resolve(anyString(), any(Principal.class))).thenReturn(f.rvs());
      doAnswer(invocation -> {
         ViewsheetSessionService.Mutation mutation = invocation.getArgument(2);
         mutation.run(f.rvs(), "rt1", null);
         return null;
      }).when(sessions).mutate(anyString(), any(Principal.class), any());
      return sessions;
   }

   private static AssemblyHyperlinkService hyperlinks(Fixture f, HyperlinkDialogService links)
      throws Exception
   {
      return new AssemblyHyperlinkService(sessions(f), links);
   }

   private static HyperlinkDialogService hyperlinkDialog() throws Exception {
      HyperlinkDialogService links = mock(HyperlinkDialogService.class);
      when(links.getHyperlinkDialogModel(anyString(), anyString(), any(), any(), any(),
                                         anyBoolean(), anyBoolean(), anyBoolean(),
                                         anyBoolean(), any(Principal.class)))
         .thenAnswer(invocation -> {
            HyperlinkDialogModel model = new HyperlinkDialogModel();
            model.setRow(invocation.getArgument(2));
            model.setCol(invocation.getArgument(3));
            return model;
         });
      return links;
   }

   private static AssemblyHighlightService highlights(Fixture f,
                                                      HighlightDialogService highlights)
      throws Exception
   {
      return new AssemblyHighlightService(sessions(f), highlights, (text, sql, box) -> null);
   }

   private static HighlightDialogService highlightDialog() throws Exception {
      HighlightDialogService highlights = mock(HighlightDialogService.class);
      when(highlights.getHighlightDialogModel(anyString(), anyString(), any(), any(), any(),
                                              anyBoolean(), anyBoolean(), any()))
         .thenReturn(new HighlightDialogModel());
      when(highlights.getFirstDataCell(anyString(), anyString(), any()))
         .thenReturn(new int[] { 1, 0 });
      return highlights;
   }

   private static AssemblyHyperlinkService.Region linkRegion(int row, int col) {
      return new AssemblyHyperlinkService.Region(row, col, null, false, false, false, false);
   }

   private static AssemblyHighlightService.Region highlightRegion(int row, int col) {
      return new AssemblyHighlightService.Region(row, col, null, false, false);
   }

   private static Map<String, Object> webLink() {
      return Map.of("linkType", "web", "webLink", "https://example.com");
   }

   private static Principal principal() {
      return () -> "admin";
   }

   private static final String NAME = "Table1";
}
