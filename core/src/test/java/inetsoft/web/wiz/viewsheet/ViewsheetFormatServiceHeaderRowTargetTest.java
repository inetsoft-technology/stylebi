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

import inetsoft.report.TableDataDescriptor;
import inetsoft.report.TableDataPath;
import inetsoft.report.TableLens;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.VSTableLens;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.composition.execution.CrosstabVSAQuery;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.IntegrationTestConfiguration;
import inetsoft.test.SreeHome;
import inetsoft.test.SwapperTestConfiguration;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.AbstractSheet;
import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.EmbeddedTableAssembly;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.uql.viewsheet.CrosstabVSAssembly;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.VSAggregateRef;
import inetsoft.uql.viewsheet.VSCrosstabInfo;
import inetsoft.uql.viewsheet.VSDimensionRef;
import inetsoft.uql.viewsheet.Viewsheet;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Redmine #81983 BUG-50: set_format target {@code headerRow} formats only a Crosstab's header
 * band -- every cell in the rows above the data -- and leaves the row labels and the body alone.
 * The paths are computed over a real CrossTabFilter lens for each layout; the band is checked
 * against the lens's own header row count and against the partition of the rendered cells.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, IntegrationTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ViewsheetFormatServiceHeaderRowTargetTest {
   private static final String ENTITY = "SALES";

   @Test
   void bandIsOneRowForRowDimensionAndOneMeasure() throws Exception {
      assertBand(layout(new String[]{ "REGION" }, new String[0], new String[]{ "AMT" }, true,
                        false), 1);
   }

   @Test
   void bandIsOneRowForOneColumnDimensionAndOneMeasureWithTotals() throws Exception {
      assertBand(layout(new String[]{ "REGION" }, new String[]{ "YR" }, new String[]{ "AMT" },
                        true, true), 1);
   }

   @Test
   void bandIsTwoRowsForColumnDimensionAndTwoSideBySideMeasures() throws Exception {
      assertBand(layout(new String[0], new String[]{ "YR" }, new String[]{ "AMT", "QTY" }, true,
                        false), 2);
   }

   @Test
   void bandIsThreeRowsForTwoColumnDimensionsAndTwoMeasuresWithTotals() throws Exception {
      assertBand(layout(new String[]{ "REGION" }, new String[]{ "YR", "CAT" },
                        new String[]{ "AMT", "QTY" }, true, true), 3);
   }

   @Test
   void bandIsOneRowWithoutDimensions() throws Exception {
      assertBand(layout(new String[0], new String[0], new String[]{ "AMT" }, true, false), 1);
   }

   @Test
   void bandIsDisjointFromRowLabelsAndBodyWithTotalsOnTwoRowDimensions() throws Exception {
      assertBand(layout(new String[]{ "REGION", "CAT" }, new String[]{ "YR" },
                        new String[]{ "AMT", "QTY" }, true, true), -1);
   }

   /** Stacked measures: the measure-name labels sit under the band, in the row-label columns. */
   @Test
   void stackedMeasureNameLabelsStayOutOfTheBand() throws Exception {
      Layout l = layout(new String[]{ "REGION" }, new String[]{ "YR" },
                        new String[]{ "AMT", "QTY" }, false, true);
      assertBand(l, -1);

      Set<TableDataPath> band = Set.of(resolve(l, "headerrow"));
      int hrc = l.lens.getHeaderRowCount();
      boolean sawMeasureLabelBelowBand = false;

      for(int row = hrc; l.filter.moreRows(row); row++) {
         for(int col = 0; col < l.filter.getColCount(); col++) {
            String val = String.valueOf(l.filter.getObject(row, col));

            if(val.contains("AMT") || val.contains("QTY")) {
               sawMeasureLabelBelowBand = true;
               TableDataPath path = l.filter.getDescriptor().getCellDataPath(row, col);
               assertFalse(band.contains(path),
                           "measure-name label at [" + row + "," + col + "] is in the band");
            }
         }
      }

      assertTrue(sawMeasureLabelBelowBand, "expected measure-name labels below the band");
   }

   @Test
   void fieldScopingSharesTheHeaderTargetsBandFilter() throws Exception {
      Layout l = layout(new String[]{ "REGION" }, new String[0], new String[]{ "AMT" }, true,
                        false);
      assertEquals(List.of(resolve(l, "header", "AMT")), List.of(resolve(l, "headerrow", "AMT")));
      assertEquals(1, resolve(l, "headerrow", "AMT").length);
   }

   @Test
   void headerTargetStillCoversTheRowLabels() throws Exception {
      Layout l = layout(new String[]{ "REGION" }, new String[0], new String[]{ "AMT" }, true,
                        false);
      Set<TableDataPath> header = new HashSet<>(Arrays.asList(resolve(l, "header")));
      Set<TableDataPath> band = new HashSet<>(Arrays.asList(resolve(l, "headerrow")));
      assertTrue(header.containsAll(band));
      assertTrue(header.size() > band.size(), "header must still include the row labels");
   }

   @Test
   void plainTableHeaderRowEqualsHeader() throws Exception {
      TableDataPath h0 = new TableDataPath(-1, TableDataPath.HEADER, "string", new String[]{ "A" });
      TableDataPath h1 = new TableDataPath(-1, TableDataPath.HEADER, "string", new String[]{ "B" });
      TableDataPath d0 = new TableDataPath(-1, TableDataPath.DETAIL, "string", new String[]{ "A" });
      TableDataPath d1 = new TableDataPath(-1, TableDataPath.DETAIL, "string", new String[]{ "B" });
      TableDataDescriptor desc = mock(TableDataDescriptor.class);
      when(desc.getCellDataPath(0, 0)).thenReturn(h0);
      when(desc.getCellDataPath(0, 1)).thenReturn(h1);
      when(desc.getCellDataPath(1, 0)).thenReturn(d0);
      when(desc.getCellDataPath(1, 1)).thenReturn(d1);
      VSTableLens lens = mock(VSTableLens.class);
      when(lens.getDescriptor()).thenReturn(desc);
      when(lens.getColCount()).thenReturn(2);
      when(lens.getHeaderRowCount()).thenReturn(1);
      when(lens.moreRows(0)).thenReturn(true);
      when(lens.moreRows(1)).thenReturn(true);
      when(lens.moreRows(2)).thenReturn(false);

      ViewsheetSandbox box = mock(ViewsheetSandbox.class);
      when(box.getVSTableLens(eq("Table1"), anyBoolean())).thenReturn(lens);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      Viewsheet viewsheet = mock(Viewsheet.class);
      when(viewsheet.getAssembly("Table1")).thenReturn(mock(TableVSAssembly.class));
      when(rvs.getViewsheet()).thenReturn(viewsheet);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(box));

      Layout l = new Layout(rvs, null, null, "Table1");
      assertEquals(List.of(h0, h1), List.of(resolve(l, "header")));
      assertEquals(List.of(resolve(l, "header")), List.of(resolve(l, "headerrow")));
   }

   @Test
   void refusesAnAssemblyThatIsNotATableOrCrosstabNamingTheTarget() {
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      Viewsheet viewsheet = mock(Viewsheet.class);
      when(rvs.getViewsheet()).thenReturn(viewsheet);
      Layout l = new Layout(rvs, null, null, "Missing");
      Exception e = assertThrows(Exception.class, () -> resolve(l, "headerrow"));
      assertTrue(e.getMessage().contains("target 'headerRow' only applies to a Crosstab or Table"),
                 e.getMessage());
   }

   @Test
   void requireTargetAndRequireReadTargetAcceptTheLowerCasedName() throws Exception {
      for(String name : List.of("requireTarget", "requireReadTarget")) {
         Method m = ViewsheetFormatService.class.getDeclaredMethod(name, String.class);
         m.setAccessible(true);
         assertEquals("headerrow", m.invoke(null, "headerRow"), name);
         assertEquals("headerrow", m.invoke(null, "HEADERROW"), name);
      }
   }

   @Test
   void unknownTargetListIncludesHeaderRow() throws Exception {
      Method m = ViewsheetFormatService.class.getDeclaredMethod("requireTarget", String.class);
      m.setAccessible(true);
      Exception e = assertThrows(InvocationTargetException.class, () -> m.invoke(null, "bogus"));
      assertTrue(e.getCause().getMessage().contains("'headerRow'"), e.getCause().getMessage());
   }

   private void assertBand(Layout l, int expectedBandRows) throws Exception {
      VSTableLens lens = l.lens;
      int hrc = lens.getHeaderRowCount();

      if(expectedBandRows >= 0) {
         assertEquals(expectedBandRows, hrc, "header band rows");
      }

      TableDataDescriptor desc = l.filter.getDescriptor();
      Set<TableDataPath> expectedBand = new LinkedHashSet<>();
      Set<TableDataPath> rowLabels = new HashSet<>();
      Set<TableDataPath> body = new HashSet<>();

      for(int row = 0; lens.moreRows(row); row++) {
         for(int col = 0; col < lens.getColCount(); col++) {
            TableDataPath path = desc.getCellDataPath(row, col);

            if(path == null) {
               continue;
            }

            if(row < hrc) {
               expectedBand.add(path);
            }
            else {
               (col < lens.getHeaderColCount() ? rowLabels : body).add(path);
            }
         }
      }

      Set<TableDataPath> band = new LinkedHashSet<>(Arrays.asList(resolve(l, "headerrow")));
      assertEquals(expectedBand, band, "band = every cell of the rows above the data");

      Set<TableDataPath> spill = new HashSet<>(band);
      spill.retainAll(rowLabels);
      assertTrue(spill.isEmpty(), "band paths shared with the row labels: " + spill);

      spill = new HashSet<>(band);
      spill.retainAll(body);
      assertTrue(spill.isEmpty(), "band paths shared with the body: " + spill);

      Set<TableDataPath> header = new HashSet<>(Arrays.asList(resolve(l, "header")));
      assertTrue(header.containsAll(band), "headerRow must be a subset of header");
   }

   private TableDataPath[] resolve(Layout l, String target) throws Exception {
      return resolve(l, target, null);
   }

   private TableDataPath[] resolve(Layout l, String target, String field) throws Exception {
      Method m = ViewsheetFormatService.class.getDeclaredMethod(
         "computeHeaderRegionPaths", RuntimeViewsheet.class, String.class, String.class,
         boolean.class);
      m.setAccessible(true);

      try {
         return (TableDataPath[]) m.invoke(null, l.rvs, l.name, field, "headerrow".equals(target));
      }
      catch(InvocationTargetException ex) {
         throw (Exception) ex.getCause();
      }
   }

   private Layout layout(String[] rows, String[] cols, String[] aggs, boolean sideBySide,
                         boolean totals)
      throws Exception
   {
      Viewsheet viewsheet = new Viewsheet();
      CrosstabVSAssembly crosstab = new CrosstabVSAssembly(viewsheet, "Crosstab1");
      crosstab.setSourceInfo(new SourceInfo(SourceInfo.ASSET, null, "Query1"));
      viewsheet.addAssembly(crosstab);

      VSCrosstabInfo cinfo = new VSCrosstabInfo();
      cinfo.setDesignRowHeaders(dims(rows));
      cinfo.setDesignColHeaders(dims(cols));
      cinfo.setSummarySideBySide(sideBySide);
      cinfo.setRowTotalVisibleValue(String.valueOf(totals));
      cinfo.setColTotalVisibleValue(String.valueOf(totals));
      List<VSAggregateRef> aggRefs = new ArrayList<>();

      for(String agg : aggs) {
         VSAggregateRef ref = new VSAggregateRef();
         ref.setDataRef(new AttributeRef(ENTITY, agg));
         ref.setColumnValue(agg);
         ref.setFormulaValue(AggregateFormula.SUM.getFormulaName());
         aggRefs.add(ref);
      }

      cinfo.setDesignAggregates(aggRefs.toArray(new VSAggregateRef[0]));
      crosstab.setVSCrosstabInfo(cinfo);

      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly table = new EmbeddedTableAssembly(ws, "Query1");
      ColumnSelection cs = new ColumnSelection();

      for(String col : List.of("REGION", "YR", "CAT", "AMT", "QTY")) {
         cs.addAttribute(new ColumnRef(new AttributeRef(ENTITY, col)));
      }

      table.setColumnSelection(cs, false);
      Object[][] data = new Object[][]{
         { "REGION", "YR", "CAT", "AMT", "QTY" },
         { "East", "Y2023", "c1", 10.0, 1.0 }, { "East", "Y2024", "c2", -12.0, 1.0 },
         { "West", "Y2023", "c2", 20.0, 1.0 }, { "West", "Y2024", "c1", 22.0, 1.0 },
         { "North", "Y2023", "c1", -5.0, 1.0 }, { "North", "Y2024", "c2", 7.0, 1.0 },
      };
      table.setEmbeddedData(new XEmbeddedTable(
         new String[]{ "string", "string", "string", "double", "double" }, data));
      ws.addAssembly(table);

      Method setBaseWorksheet =
         Viewsheet.class.getDeclaredMethod("setBaseWorksheet", Worksheet.class);
      setBaseWorksheet.setAccessible(true);
      setBaseWorksheet.invoke(viewsheet, ws);

      ViewsheetSandbox queryBox =
         new ViewsheetSandbox(viewsheet, AbstractSheet.SHEET_RUNTIME_MODE, null, false, null);
      AssetQuerySandbox wbox = new AssetQuerySandbox(ws, null, new VariableTable());
      java.lang.reflect.Field wboxField = ViewsheetSandbox.class.getDeclaredField("wbox");
      wboxField.setAccessible(true);
      wboxField.set(queryBox, wbox);

      cinfo.update(viewsheet, cs, null, false, "Query1", null);

      TableLens rawLens = new CrosstabVSAQuery(queryBox, "Crosstab1", false).getTableLens(false);
      TableLens filter = rawLens;
      filter.moreRows(TableLens.EOT);
      VSTableLens lens = new VSTableLens(filter);

      ViewsheetSandbox writeBox = mock(ViewsheetSandbox.class);
      when(writeBox.getVSTableLens(eq("Crosstab1"), anyBoolean())).thenReturn(lens);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(viewsheet);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(writeBox));
      return new Layout(rvs, filter, lens, "Crosstab1");
   }

   private static VSDimensionRef[] dims(String[] names) {
      VSDimensionRef[] refs = new VSDimensionRef[names.length];

      for(int i = 0; i < names.length; i++) {
         refs[i] = new VSDimensionRef();
         refs[i].setDataRef(new AttributeRef(ENTITY, names[i]));
         refs[i].setGroupColumnValue(names[i]);
      }

      return refs;
   }

   private record Layout(RuntimeViewsheet rvs, TableLens filter, VSTableLens lens,
                         String name) {}
}
