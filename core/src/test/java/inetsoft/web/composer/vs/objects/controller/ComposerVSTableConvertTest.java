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
package inetsoft.web.composer.vs.objects.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.TableCellBinding;
import inetsoft.report.TableLayout;
import inetsoft.report.TableLens;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.composition.ChangedAssemblyList;
import inetsoft.report.script.viewsheet.CalcTableVSAScriptable;
import inetsoft.report.script.viewsheet.CrosstabVSAScriptable;
import inetsoft.report.composition.graph.VSDataSet;
import inetsoft.test.*;
import inetsoft.uql.XCondition;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.CalcTableVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.CrosstabTree;
import inetsoft.uql.viewsheet.internal.TableVSAssemblyInfo;
import inetsoft.util.ThreadContext;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.composer.vs.VSObjectTreeService;
import inetsoft.web.composer.vs.objects.event.ConvertToFreehandTableEvent;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import inetsoft.web.viewsheet.handler.crosstab.CrosstabDrillHandler;
import inetsoft.web.viewsheet.model.VSObjectModelFactoryService;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.CoreLifecycleService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Runs {@code ComposerVSTableService.convertToFreehandTable} end to end on an opened runtime
 * viewsheet (EmbeddedVS1: the Hurricane worksheet table with the columns datetime, day,
 * latitude, longitude, name, type, wind, year). The crosstab is bound to the worksheet table,
 * converted to a freehand table, and the freehand data is compared with the data the crosstab
 * had before the conversion. Only the UI side (lifecycle service, object tree, drill and info
 * handlers) is mocked.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome(importResources = "/inetsoft/report/composition/EmbeddedVS1.zip")
@Tag("slow")
@Tag("integration")
class ComposerVSTableConvertTest {
   @Test
   void convertedFreehandTableHasTheCrosstabData() throws Exception {
      Viewsheet vs = box().getViewsheet();
      crosstab(vs, new String[] { "type" }, new String[0], "wind", "Sum");
      String before = cells(box().getData(CROSSTAB));

      convert(CROSSTAB);

      assertInstanceOf(CalcTableVSAssembly.class, vs.getAssembly(CROSSTAB));
      assertEquals(before, cells(box().getData(CROSSTAB)));
   }

   @Test
   void convertedFreehandTableKeepsTheGroupOrderOfTwoRowDimensions() throws Exception {
      Viewsheet vs = box().getViewsheet();
      crosstab(vs, new String[] { "year", "type" }, new String[0], "wind", "Count");
      String before = cells(box().getData(CROSSTAB));

      convert(CROSSTAB);

      assertEquals(before, cells(box().getData(CROSSTAB)));
   }

   @Test
   void convertedFreehandTableHasTheTopNRowsOfTheCrosstabWithoutOthers() throws Exception {
      Viewsheet vs = box().getViewsheet();
      CrosstabVSAssembly crosstab = crosstab(vs, new String[] { "name", "year" }, new String[0],
                                             "wind", "Sum");
      VSDimensionRef name = (VSDimensionRef) crosstab.getVSCrosstabInfo().getDesignRowHeaders()[0];
      name.setRankingOptionValue(XCondition.TOP_N + "");
      name.setRankingNValue("3");
      name.setRankingColValue("Sum(wind)");
      String before = cells(box().getData(CROSSTAB));

      // the Top 3 setting limits the first column of the crosstab to exactly 3 names; no Others
      // row because the crosstab supports Others only on the innermost dimension, and name is the
      // outer one
      Set<String> names = new HashSet<>();
      String[] lines = before.split("\n");

      for(int i = 1; i < lines.length; i++) {
         String first = lines[i].split("\\|", -1)[0];

         if(!"null".equals(first)) {
            names.add(first);
         }
      }

      assertEquals(3, names.size(), "Top 3 not applied on the crosstab: " + names);

      convert(CROSSTAB);

      assertEquals(before, cells(box().getData(CROSSTAB)));
   }

   @Test
   void freehandTableCopiesFillBlankWithZeroAndSortOthersLast() throws Exception {
      Viewsheet vs = box().getViewsheet();
      CrosstabVSAssembly crosstab = crosstab(vs, new String[] { "type" }, new String[] { "year" },
                                             "wind", "Sum");
      crosstab.getVSCrosstabInfo().setFillBlankWithZeroValue(true);
      crosstab.getVSCrosstabInfo().setSortOthersLastValue(false);
      box().getData(CROSSTAB);

      convert(CROSSTAB);

      CalcTableVSAssemblyInfo info =
         (CalcTableVSAssemblyInfo) vs.getAssembly(CROSSTAB).getInfo();
      assertTrue(info.isFillBlankWithZero());
      assertFalse(info.isSortOthersLast());
   }

   @Test
   void freehandGroupCellsDoNotMergeWhenTheCrosstabDoesNotMergeSpans() throws Exception {
      Viewsheet vs = box().getViewsheet();
      CrosstabVSAssembly crosstab = crosstab(vs, new String[] { "year", "type" }, new String[0],
                                             "wind", "Sum");
      crosstab.getVSCrosstabInfo().setMergeSpanValue(false);
      box().getData(CROSSTAB);

      convert(CROSSTAB);

      TableLayout layout = ((CalcTableVSAssembly) vs.getAssembly(CROSSTAB)).getTableLayout();
      int groups = 0;

      for(int r = 0; r < layout.getRowCount(); r++) {
         for(int c = 0; c < layout.getColCount(); c++) {
            TableCellBinding b = (TableCellBinding) layout.getCellBinding(r, c);

            if(b != null && b.getBType() == TableCellBinding.GROUP) {
               groups++;
               assertFalse(b.isMergeCells());
            }
         }
      }

      assertTrue(groups > 0, "no group cell in the freehand layout");
   }

   @Test
   void drilledCrosstabIsConvertedLikeTheNonDrilledCrosstab() throws Exception {
      Viewsheet vs = box().getViewsheet();
      CrosstabVSAssembly crosstab = crosstab(vs, new String[] { "year", "type" }, new String[0],
                                             "wind", "Sum");
      String before = cells(box().getData(CROSSTAB));
      CrosstabTree tree = crosstab.getCrosstabTree();
      tree.getExpandedPaths().put("year", new HashSet<>(Set.of("1851")));
      assertTrue(tree.isDrilled());

      convert(CROSSTAB);

      assertEquals(before, cells(box().getData(CROSSTAB)));
   }

   @Test
   void assemblyThatIsNotATableIsLeftUnchanged() throws Exception {
      Viewsheet vs = box().getViewsheet();
      GaugeVSAssembly gauge = new GaugeVSAssembly(vs, "Gauge1");
      vs.addAssembly(gauge);
      Assembly[] before = vs.getAssemblies();

      convert("Gauge1");

      assertArrayEquals(before, vs.getAssemblies());
      assertSame(gauge, vs.getAssembly("Gauge1"));
   }

   @Test
   void freehandTableKeepsTheNameAndTheCrosstabIsGone() throws Exception {
      Viewsheet vs = box().getViewsheet();
      crosstab(vs, new String[] { "type" }, new String[0], "wind", "Sum");
      int count = vs.getAssemblies().length;

      convert(CROSSTAB);

      assertEquals(count, vs.getAssemblies().length);
      assertInstanceOf(CalcTableVSAssembly.class, vs.getAssembly(CROSSTAB));
      assertFalse(vs.getAssembly(CROSSTAB) instanceof CrosstabVSAssembly);

      for(Assembly assembly : vs.getAssemblies()) {
         assertFalse(assembly instanceof CrosstabVSAssembly, "crosstab left: " + assembly);
      }
   }

   // Bug #81262: the scriptable cached for the name must follow the new assembly type,
   // otherwise cancel/processChange throw ClassCastException (CrosstabVSAScriptable)
   @Test
   void convertedFreehandTableGetsAFreshScriptableSoCancelAndProcessChangeWork() throws Exception {
      Viewsheet vs = box().getViewsheet();
      crosstab(vs, new String[] { "type" }, new String[0], "wind", "Sum");
      box().getData(CROSSTAB);
      assertInstanceOf(CrosstabVSAScriptable.class, box().getScope().getVSAScriptable(CROSSTAB));

      convert(CROSSTAB);

      assertInstanceOf(CalcTableVSAScriptable.class, box().getScope().getVSAScriptable(CROSSTAB));
      assertDoesNotThrow(() -> box().cancel());
      assertDoesNotThrow(
         () -> box().processChange(CROSSTAB, 0xFFFF, new ChangedAssemblyList()));
   }

   // Bug #82418: Max Rows of a table must survive the conversion and clip the freehand output
   @Test
   void convertedFreehandTableCarriesTheMaxRowsOfTheTable() throws Exception {
      Viewsheet vs = box().getViewsheet();
      TableVSAssemblyInfo tinfo = (TableVSAssemblyInfo) vs.getAssembly(TABLE).getInfo();
      tinfo.setMaxRows(5);
      convert(TABLE);

      CalcTableVSAssemblyInfo info = (CalcTableVSAssemblyInfo) vs.getAssembly(TABLE).getInfo();
      assertEquals(5, info.getMaxRows());
      TableLens lens = (TableLens) box().getData(TABLE);
      lens.moreRows(Integer.MAX_VALUE);
      assertEquals(5, lens.getRowCount() - lens.getHeaderRowCount());
   }

   @Test
   void convertedFreehandTableKeepsDefaultMaxRowsForCrosstabSource() throws Exception {
      Viewsheet vs = box().getViewsheet();
      crosstab(vs, new String[] { "type" }, new String[0], "wind", "Sum");

      convert(CROSSTAB);

      assertEquals(0, ((CalcTableVSAssemblyInfo) vs.getAssembly(CROSSTAB).getInfo()).getMaxRows());
   }

   // ---- Top N / Others / sort by value / variables on the dimensions: the freehand table has the
   // data of the crosstab it came from (Bug #81258 and the convert probe T4 of #81234; the real
   // asset CroTable8 of the datatest storage gives other groups after the conversion, none of
   // these combinations reproduces it on the Hurricane table, see #82463).
   // The data cells are compared as the converter's own tests do; a group cell that is empty in
   // the freehand table where the crosstab merges it with the cell above or to the left is not
   // a difference.

   @Test
   void topNWithOthersOnRowsAndColumnsKeepsTheDataOfTwoAggregatesWithAPercent() throws Exception {
      VSAggregateRef sum = aggregate("wind", "Sum", null, "0");
      VSAggregateRef avgPercent = aggregate("wind", "Average", null, "2");

      for(boolean totals : new boolean[] { true, false }) {
         assertConvertKeepsTheData("rows and column top N + Others, totals " + totals,
            new DataRef[] { ranked("year", 2, "Sum(wind)", true, 0, null),
                            ranked("name", 2, "Sum(wind)", true, 0, null) },
            new DataRef[] { ranked("type", 1, "Sum(wind)", true, 0, null) }, totals,
            sum, avgPercent);
      }
   }

   @Test
   void topNRankedByAnAggregateThatIsNotTheFirstOneKeepsTheData() throws Exception {
      VSAggregateRef sum = aggregate("wind", "Sum", null, "0");
      VSAggregateRef avg = aggregate("wind", "Average", null, "0");

      assertConvertKeepsTheData("ranked by Average(wind), the second aggregate",
         new DataRef[] { ranked("year", 2, "Average(wind)", true, 0, null),
                         ranked("name", 2, "Sum(wind)", true, 0, null) },
         new DataRef[0], true, sum, avg);
      assertConvertKeepsTheData("inner dimension ranked by Average(wind)",
         new DataRef[] { ranked("year", 2, "Sum(wind)", true, 0, null),
                         ranked("name", 2, "Average(wind)", true, 0, null) },
         new DataRef[0], true, sum, avg);
   }

   // order=18 in the datatest asset is SORT_VALUE_DESC: sorted by the value of another aggregate
   @Test
   void sortByValueTogetherWithTopNByAnotherAggregateKeepsTheOrderAndTheGroups() throws Exception {
      VSAggregateRef sum = aggregate("wind", "Sum", null, "0");
      VSAggregateRef avg = aggregate("wind", "Average", null, "0");
      int asc = inetsoft.uql.XConstants.SORT_VALUE_ASC;
      int desc = inetsoft.uql.XConstants.SORT_VALUE_DESC;
      int[][] orders = { { asc, asc }, { asc, desc }, { desc, desc } };
      String[] sortBys = { "Average(wind)", "Sum(wind)" };

      for(int[] order : orders) {
         for(String sortBy : sortBys) {
            for(boolean others : new boolean[] { true, false }) {
               assertConvertKeepsTheData("order " + order[0] + "/" + order[1] + " by " + sortBy +
                                         ", others " + others,
                  new DataRef[] { ranked("year", 2, "Sum(wind)", others, order[0], sortBy),
                                  ranked("name", 2, "Sum(wind)", others, order[1], sortBy) },
                  new DataRef[0], true, sum, avg);
            }
         }
      }
   }

   // the datatest asset names the column dimension with a RadioButton variable ($(RadioButton3))
   @Test
   void columnDimensionBoundToARadioButtonKeepsTopNWithOthers() throws Exception {
      VSAggregateRef sum = aggregate("wind", "Sum", null, "0");

      for(String selected : new String[] { "type", "year" }) {
         for(boolean others : new boolean[] { true, false }) {
            Viewsheet vs = box().getViewsheet();
            RadioButtonVSAssembly radio = new RadioButtonVSAssembly(vs, "RadioButton1");
            ListData data = new ListData();
            data.setValues(new Object[] { "type", "year" });
            data.setLabels(new String[] { "type", "year" });
            radio.setListData(data);
            radio.setSourceType(ListInputVSAssembly.EMBEDDED_SOURCE);
            radio.setSelectedObject(selected);
            vs.addAssembly(radio);
            box().processChange("RadioButton1",
                                VSAssembly.INPUT_DATA_CHANGED | VSAssembly.OUTPUT_DATA_CHANGED,
                                new ChangedAssemblyList());
            VSDimensionRef col = ranked("type", 1, "Sum(wind)", others, 0, null);
            col.setGroupColumnValue("$(RadioButton1)");

            try {
               assertConvertKeepsTheData("column dimension of RadioButton1 = " + selected +
                                         ", others " + others,
                  new DataRef[] { ranked("name", 2, "Sum(wind)", true, 0, null) },
                  new DataRef[] { col }, true, sum);
            }
            finally {
               vs.removeAssembly("RadioButton1");
            }
         }
      }
   }

   // First(a, b) is the ranking aggregate and the displayed aggregate of the datatest asset
   @Test
   void topNRankedByATwoColumnAggregateKeepsTheData() throws Exception {
      VSAggregateRef first = aggregate("wind", "First", "day", "0");
      VSAggregateRef avgPercent = aggregate("wind", "Average", null, "2");

      for(String rank : new String[] { "First(wind, day)", "Average(wind)" }) {
         assertConvertKeepsTheData("ranked by " + rank,
            new DataRef[] { ranked("year", 2, rank, true, 0, null),
                            ranked("name", 2, rank, true, 0, null) },
            new DataRef[] { ranked("type", 1, rank, true, 0, null) }, true, first, avgPercent);
      }
   }

   // many groups share the ranking value (Min(wind) is 30 or 35 for most storms)
   @Test
   void topNOverTiedRankingValuesKeepsTheGroups() throws Exception {
      VSAggregateRef sum = aggregate("wind", "Sum", null, "0");
      VSAggregateRef min = aggregate("wind", "Min", null, "0");

      assertConvertKeepsTheData("year, name by Min(wind) with ties",
         new DataRef[] { ranked("year", 2, "Min(wind)", true, 0, null),
                         ranked("name", 3, "Min(wind)", true, 0, null) },
         new DataRef[0], true, sum, min);
      assertConvertKeepsTheData("type by Min(wind) with ties",
         new DataRef[] { ranked("type", 2, "Min(wind)", true, 0, null) },
         new DataRef[0], true, sum, min);
   }

   private void assertConvertKeepsTheData(String config, DataRef[] rows, DataRef[] cols,
                                          boolean totals, VSAggregateRef... aggregates)
      throws Exception
   {
      Viewsheet vs = box().getViewsheet();

      if(vs.getAssembly(CROSSTAB) != null) {
         vs.removeAssembly(CROSSTAB);
      }

      SourceInfo source =
         (SourceInfo) ((TableVSAssembly) vs.getAssembly(TABLE)).getSourceInfo().clone();
      CrosstabVSAssembly crosstab = new CrosstabVSAssembly(vs, CROSSTAB);
      crosstab.setSourceInfo(source);
      VSCrosstabInfo cinfo = crosstab.getVSCrosstabInfo();
      cinfo.setDesignRowHeaders(rows);
      cinfo.setDesignColHeaders(cols);
      cinfo.setDesignAggregates(aggregates);
      cinfo.setRowTotalVisibleValue(Boolean.toString(totals));
      cinfo.setColTotalVisibleValue(Boolean.toString(totals));
      vs.addAssembly(crosstab);

      String before = cells(box().getData(CROSSTAB));
      assertTrue(before.split("\n").length > 2, config + ": the crosstab has no data\n" + before);
      convert(CROSSTAB);
      assertInstanceOf(CalcTableVSAssembly.class, vs.getAssembly(CROSSTAB), config);
      String after = cells(box().getData(CROSSTAB));
      assertTrue(sameModuloSpans(before, after),
                 config + "\n-- crosstab\n" + before + "-- freehand table\n" + after);
   }

   /** A dimension with Top N (and Others) by an aggregate, optionally sorted by another value. */
   private static VSDimensionRef ranked(String column, int n, String rankingColumn,
                                        boolean others, int order, String sortBy)
   {
      VSDimensionRef dim = (VSDimensionRef) dimensions(new String[] { column })[0];
      dim.setRankingOptionValue(XCondition.TOP_N + "");
      dim.setRankingNValue(n + "");
      dim.setRankingColValue(rankingColumn);

      if(others) {
         dim.setGroupOthersValue("true");
      }

      if(order != 0) {
         dim.setOrder(order);
         dim.setSortByColValue(sortBy);
      }

      return dim;
   }

   private static VSAggregateRef aggregate(String column, String formula, String second,
                                           String percentage)
   {
      VSAggregateRef agg = new VSAggregateRef();
      agg.setColumnValue(column);
      agg.setFormulaValue(formula);

      if(second != null) {
         agg.setSecondaryColumnValue(second);
      }

      agg.setPercentageOptionValue(percentage);
      return agg;
   }

   /**
    * The cells are equal as text or as numbers; a freehand cell that is empty where the crosstab
    * repeats the cell above or to the left (a merged span) is not a difference.
    */
   private static boolean sameModuloSpans(String before, String after) {
      String[] a = before.split("\n");
      String[] b = after.split("\n");

      if(a.length != b.length) {
         return false;
      }

      for(int i = 0; i < a.length; i++) {
         String[] x = a[i].split("\\|", -1);
         String[] y = b[i].split("\\|", -1);

         if(x.length != y.length) {
            return false;
         }

         String[] prev = i > 0 ? a[i - 1].split("\\|", -1) : null;

         for(int j = 0; j < x.length; j++) {
            if(x[j].equals(y[j])) {
               continue;
            }

            try {
               if(Double.parseDouble(x[j]) == Double.parseDouble(y[j])) {
                  continue;
               }
            }
            catch(NumberFormatException ignore) {
            }

            boolean span = y[j].equals("null") && !x[j].equals("null") &&
               ((j > 0 && x[j - 1].equals(x[j])) || (prev != null && prev[j].equals(x[j])));

            if(!span) {
               return false;
            }
         }
      }

      return true;
   }

   private ViewsheetSandbox box() {
      return vsResource.getRuntimeViewsheet().getViewsheetSandbox().orElseThrow();
   }

   private void convert(String name) throws Exception {
      RuntimeViewsheet rvs = vsResource.getRuntimeViewsheet();
      ComposerVSTableService service = new ComposerVSTableService(
         Mockito.mock(CoreLifecycleService.class), Mockito.mock(VSObjectTreeService.class),
         Mockito.mock(VSObjectModelFactoryService.class), Mockito.mock(VSBindingService.class),
         assetRepository, viewsheetService, Mockito.mock(CrosstabDrillHandler.class),
         Mockito.mock(VSAssemblyInfoHandler.class));
      ConvertToFreehandTableEvent event = new ConvertToFreehandTableEvent();
      event.setName(name);
      Principal principal = ThreadContext.getPrincipal();
      service.convertToFreehandTable(rvs.getID(), event, principal, "http://localhost:8080/sree",
                                     Mockito.mock(CommandDispatcher.class));
   }

   private CrosstabVSAssembly crosstab(Viewsheet vs, String[] rows, String[] cols,
                                       String column, String formula)
   {
      SourceInfo source =
         (SourceInfo) ((TableVSAssembly) vs.getAssembly(TABLE)).getSourceInfo().clone();
      CrosstabVSAssembly crosstab = new CrosstabVSAssembly(vs, CROSSTAB);
      crosstab.setSourceInfo(source);
      VSCrosstabInfo cinfo = crosstab.getVSCrosstabInfo();
      cinfo.setDesignRowHeaders(dimensions(rows));
      cinfo.setDesignColHeaders(dimensions(cols));
      VSAggregateRef agg = new VSAggregateRef();
      agg.setColumnValue(column);
      agg.setFormulaValue(formula);
      cinfo.setDesignAggregates(new DataRef[] { agg });
      vs.addAssembly(crosstab);
      return crosstab;
   }

   private static DataRef[] dimensions(String[] names) {
      DataRef[] refs = new DataRef[names.length];

      for(int i = 0; i < names.length; i++) {
         VSDimensionRef dim = new VSDimensionRef(new ColumnRef(new AttributeRef(null, names[i])));
         dim.setGroupColumnValue(names[i]);
         refs[i] = dim;
      }

      return refs;
   }

   private static String cells(Object data) {
      TableLens lens = data instanceof VSDataSet ? ((VSDataSet) data).getTable() : (TableLens) data;
      assertNotNull(lens, "no data");
      StringBuilder sb = new StringBuilder();
      lens.moreRows(Integer.MAX_VALUE);

      for(int r = 0; r < lens.getRowCount(); r++) {
         for(int c = 0; c < lens.getColCount(); c++) {
            sb.append(lens.getObject(r, c)).append(c < lens.getColCount() - 1 ? "|" : "\n");
         }
      }

      return sb.toString();
   }

   private static OpenViewsheetEvent openViewsheetEvent() {
      OpenViewsheetEvent event = new OpenViewsheetEvent();
      event.setEntryId("1^128^__NULL__^EmbeddedVS1^host-org");
      event.setViewer(true);
      return event;
   }

   @RegisterExtension
   RuntimeViewsheetExtension vsResource = new RuntimeViewsheetExtension(openViewsheetEvent());
   @Autowired
   private ViewsheetService viewsheetService;
   @Autowired
   private AssetRepository assetRepository;

   private static final String TABLE = "TableView1";
   private static final String CROSSTAB = "Crosstab1";
}
