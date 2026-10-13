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
package inetsoft.web.viewsheet.controller.chart;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.analytic.composition.event.ChartVSSelectionUtil;
import inetsoft.graph.data.DataSet;
import inetsoft.mv.MVManager;
import inetsoft.report.TableLens;
import inetsoft.report.composition.ChangedAssemblyList;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.*;
import inetsoft.report.composition.graph.BrushDataSet;
import inetsoft.report.composition.graph.VGraphPair;
import inetsoft.report.composition.graph.VSDataSet;
import inetsoft.report.composition.region.*;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.test.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.*;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.graph.aesthetic.CategoricalColorFrameWrapper;
import inetsoft.uql.viewsheet.graph.aesthetic.CategoricalSizeFrameWrapper;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.util.ThreadContext;
import inetsoft.util.Tool;
import inetsoft.web.graph.GraphBuilder;
import inetsoft.web.graph.model.LegendContainer;
import inetsoft.web.graph.model.RegionMeta;
import inetsoft.web.viewsheet.event.chart.VSChartBrushEvent;
import inetsoft.web.viewsheet.event.chart.VSChartZoomEvent;
import inetsoft.web.viewsheet.model.chart.VSChartModel;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.CoreLifecycleService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.lang.reflect.Method;
import java.security.Principal;
import java.util.List;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78481. Brush, zoom, exclude and show details on a relation chart's Node Color/Size
 * legend item, when the node field is a dimension that is neither Source nor Target. Runs
 * the real sandbox, query, render, VSChartBrushService and VSChartZoomService.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  LibManagerTestConfiguration.class,
                                  PluginsTestConfiguration.class,
                                  VSChartRelationNodeLegendTest.TestConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("slow")
class VSChartRelationNodeLegendTest {
   @Configuration
   static class TestConfig {
      @Bean
      public MVManager mvManager() {
         return mock(MVManager.class);
      }

      @Bean
      @SuppressWarnings("unchecked")
      public AssetDataCache assetDataCache() {
         ObjectProvider<DistributedTableCacheStore> provider = mock(ObjectProvider.class);
         when(provider.getObject()).thenReturn(mock(DistributedTableCacheStore.class));
         return new AssetDataCache(mock(DataSourceRegistry.class), provider);
      }
   }

   @BeforeEach
   void before() {
      oldPrincipal = ThreadContext.getPrincipal();
      principal = SUtil.getPrincipal(
         new IdentityID(XPrincipal.SYSTEM, OrganizationManager.getInstance().getCurrentOrgID()),
         null, false);
      ThreadContext.setPrincipal(principal);
   }

   @AfterEach
   void after() {
      if(box != null) {
         box.dispose();
      }

      ThreadContext.setPrincipal(oldPrincipal);
   }

   @Test
   void nodeColorStringLegend() throws Exception {
      build(GraphTypes.CHART_NETWORK, "nodeColor", false);
      checkLegendIsDimension("State");

      // brush: Chart2 (bar by Target) keeps the one TX row, C->D
      brush("Chart1", "State^VALUE:TX");
      assertEquals("[State] [is] [equal to] [TX]", chart1().getBrushConditionList(null, true)
         .toString());
      assertEquals(1, rows("Chart2"));
      brush("Chart1", null);
      assertEquals(3, rows("Chart2"));

      // zoom
      zoom("State^VALUE:TX", false);
      assertEquals("[State] [is] [equal to] [TX]", chart1().getZoomConditionList(null).toString());
      assertEquals(1, rows("Chart1"));
      zoom(null, false);

      // exclude: raw rows are filtered, so C->D is kept through its CA row
      zoom("State^VALUE:TX", true);
      assertTrue(chart1().getZoomConditionList(null).toString()
                    .startsWith("[State] [is not] [equal to] [TX]"),
                 chart1().getZoomConditionList(null).toString());
      assertEquals(4, rows("Chart1"));
      assertFalse(chart1Values("State").contains("TX"));
      zoom(null, false);

      // show details: the one TX row
      assertEquals(1, showDetails("State^VALUE:TX"));
   }

   @Test
   void nodeSizeDateLegend() throws Exception {
      build(GraphTypes.CHART_NETWORK, "nodeSize", true);
      checkLegendIsDimension("Year(Date)");
      String selected = "Year(Date)^VALUE:2021-01-01 00:00:00";

      // brush: the 2021 rows are A->C and C->D
      brush("Chart1", selected);
      assertTrue(chart1().getBrushConditionList(null, true).toString()
                    .startsWith("[Year(Date)] [is] [equal to]"));
      assertEquals(2, rows("Chart2"));
      brush("Chart1", null);

      zoom(selected, false);
      assertNotNull(chart1().getZoomConditionList(null));
      assertEquals(2, rows("Chart1"));
      zoom(null, false);

      zoom(selected, true);
      assertTrue(chart1().getZoomConditionList(null).toString()
                    .startsWith("[Year(Date)] [is not] [equal to]"),
                 chart1().getZoomConditionList(null).toString());
      // A->C has only a 2021 row
      assertEquals(3, rows("Chart1"));
      zoom(null, false);

      assertEquals(2, showDetails(selected));
   }

   @Test
   void treeNodeColorDateLegend() throws Exception {
      build(GraphTypes.CHART_TREE, "nodeColor", true);
      checkLegendIsDimension("Year(Date)");

      brush("Chart1", "Year(Date)^VALUE:2020-01-01 00:00:00");
      assertTrue(chart1().getBrushConditionList(null, true).toString()
                    .startsWith("[Year(Date)] [is] [equal to]"));
      // the 2020 rows are A->B, B->C and C->D
      assertEquals(3, rows("Chart2"));
      assertEquals(1, rows("Chart3"));
   }

   /**
    * Show Details on a date-part node legend item (MonthOfYear, integer values). The detail
    * query adds the node's date range column hidden, so it filters the rows but never shows
    * up in the detail table, which keeps the base table's five columns.
    */
   @Test
   void nodeDatePartShowDetailsKeepsColumns() throws Exception {
      build(GraphTypes.CHART_NETWORK, "nodeColor", true, DateRangeRef.MONTH_OF_YEAR_PART);
      checkLegendIsDimension("MonthOfYear(Date)");

      assertEquals(1, showDetails("MonthOfYear(Date)^VALUE:7"));
      TableLens table = (TableLens) box.getData("Chart1", true, DataMap.DETAIL | DataMap.BRUSH);
      List<String> headers = new ArrayList<>();

      for(int c = 0; c < table.getColCount(); c++) {
         headers.add(Tool.getDataString(table.getObject(0, c)));
      }

      assertEquals(List.of("Source", "Target", "State", "Date", "Sales"), headers);
      assertEquals("2021-07-01", Tool.getDataString(table.getObject(1, 3)));
   }

   /**
    * Chart3 brushes the relation chart. The node column must stay a measure in the chart
    * data set, or BrushDataSet drops pair C->D, whose MAX(State) is TX for all data but CA
    * for the brushed 2020 rows.
    */
   @Test
   void relationChartAsBrushTarget() throws Exception {
      build(GraphTypes.CHART_NETWORK, "nodeColor", false);
      brush("Chart3", "Year(Date)^VALUE:2020-01-01 00:00:00");
      assertNotNull(((ChartVSAssembly) vs.getAssembly("Chart3")).getBrushSelection());

      DataSet data = render().getRealSizeVGraph().getCoordinate().getDataSet();
      assertInstanceOf(BrushDataSet.class, data);
      assertTrue(data.isMeasure("State"));

      List<String> pairs = new ArrayList<>();

      for(int r = 0; r < data.getRowCount(); r++) {
         pairs.add(data.getData("Source", r) + "->" + data.getData("Target", r) + ":" +
                      data.getData("State", r));
      }

      assertEquals(List.of("A->B:CA", "B->C:CA", "C->D:CA"), pairs);
   }

   private void checkLegendIsDimension(String field) throws Exception {
      VGraphPair pair = render();
      ChartVSAssembly chart = chart1();
      ChartArea area = new ChartArea(pair, null, chart.getVSChartInfo(), null, false);
      List<LegendItemArea> items = new ArrayList<>();

      for(LegendArea legend : area.getLegendsArea().getLegendAreas()) {
         collectItems(legend, items);
      }

      assertFalse(items.isEmpty());
      items.forEach(item -> assertTrue(item.isDimension(), "legend item " + item.getValue()));

      // the client model: each legend item region names the node field as its dimension
      VSChartModel model = new VSChartModel(chart, rvs());
      model = (VSChartModel) new GraphBuilder(chart, chart.getVSChartInfo(), area,
                                              chart.getChartDescriptor(), model).build();
      int contentRegions = 0;

      for(LegendContainer container : model.getLegends()) {
         for(inetsoft.web.graph.model.Legend legend : container.getLegendObjects()) {
            if(!"legend_content".equals(legend.areaName())) {
               continue;
            }

            for(inetsoft.web.graph.model.ChartRegion region :
               legend.regions().orElse(Collections.emptyList()))
            {
               RegionMeta meta = model.getRegionMetaDictionary()
                  .get(region.metaIdx() == null ? 0 : region.metaIdx());
               assertTrue(meta.getDimIdx() >= 0);
               assertEquals(field, model.getStringDictionary().get(meta.getDimIdx()));
               contentRegions++;
            }
         }
      }

      assertEquals(items.size(), contentRegions);
   }

   private static void collectItems(Object area, List<LegendItemArea> items) {
      if(area instanceof LegendItemArea) {
         items.add((LegendItemArea) area);
      }
      else if(area instanceof LegendArea) {
         for(DefaultArea child : ((LegendArea) area).getAllAreas()) {
            collectItems(child, items);
         }
      }
      else if(area instanceof ListLegendContentArea) {
         for(DefaultArea child : ((ListLegendContentArea) area).getAllAreas()) {
            collectItems(child, items);
         }
      }
   }

   /**
    * The selection and data steps of VSChartShowDetailsService, without the preview
    * worksheet it opens to display the rows.
    */
   private int showDetails(String selected) throws Exception {
      ChartVSAssembly chart = chart1();
      VGraphPair pair = box.getVGraphPair("Chart1");
      DataSet vdset = pair.getRealSizeVGraph().getCoordinate().getDataSet();
      VSDataSet lens = vdset instanceof VSDataSet ? (VSDataSet) vdset :
         (VSDataSet) box.getData("Chart1");
      VSSelection selection = ChartVSSelectionUtil.getVSSelection(
         selected, lens, (VSDataSet) box.getData("Chart1", true, DataMap.ZOOM), vdset, false,
         chart.getVSChartInfo(), false, null, true, false, false);
      assertNotNull(selection);
      assertFalse(selection.isEmpty());

      chart.setDetailSelection(selection);
      box.processChange("Chart1", VSAssembly.DETAIL_INPUT_DATA_CHANGED,
                        new ChangedAssemblyList(false));
      assertNotNull(chart.getDetailConditionList(null));
      TableLens table = (TableLens) box.getData("Chart1", true, DataMap.DETAIL | DataMap.BRUSH);
      assertNotNull(table);
      table.moreRows(Integer.MAX_VALUE);
      return table.getRowCount() - table.getHeaderRowCount();
   }

   private void build(int chartType, String slot, boolean date) throws Exception {
      build(chartType, slot, date, DateRangeRef.YEAR_INTERVAL);
   }

   private void build(int chartType, String slot, boolean date, int dateLevel)
      throws Exception
   {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly table = new EmbeddedTableAssembly(ws, "D");
      table.setEmbeddedData(new XEmbeddedTable(
         new String[] { XSchema.STRING, XSchema.STRING, XSchema.STRING, XSchema.DATE,
                        XSchema.DOUBLE },
         new Object[][] { { "Source", "Target", "State", "Date", "Sales" },
                          { "A", "B", "CA", date(2020, 1, 15), 10.0 },
                          { "A", "C", "NY", date(2021, 3, 1), 20.0 },
                          { "B", "C", "CA", date(2020, 6, 1), 30.0 },
                          { "C", "D", "TX", date(2021, 7, 1), 40.0 },
                          { "C", "D", "CA", date(2020, 8, 1), 5.0 } }));
      ws.addAssembly(table);
      vs = new Viewsheet();
      Method setBase = Viewsheet.class.getDeclaredMethod("setBaseWorksheet", Worksheet.class);
      setBase.setAccessible(true);
      setBase.invoke(vs, ws);

      RelationVSChartInfo info = new RelationVSChartInfo();
      info.setChartType(chartType);
      info.setSourceField(dim("Source", XSchema.STRING));
      info.setTargetField(dim("Target", XSchema.STRING));
      VSAestheticRef aes = new VSAestheticRef();
      ChartRef nodeRef = date ? dim("Date", XSchema.DATE) : dim("State", XSchema.STRING);

      if(date) {
         ((VSChartDimensionRef) nodeRef).setDateLevelValue(String.valueOf(dateLevel));
      }

      aes.setDataRef(nodeRef);

      if("nodeSize".equals(slot)) {
         aes.setVisualFrameWrapper(new CategoricalSizeFrameWrapper());
         info.setNodeSizeField(aes);
      }
      else {
         aes.setVisualFrameWrapper(new CategoricalColorFrameWrapper());
         info.setNodeColorField(aes);
      }

      vs.addAssembly(chart("Chart1", info));

      // Chart2 is brushed by the legend; Chart3 brushes the relation chart
      vs.addAssembly(chart("Chart2", bar(dim("Target", XSchema.STRING))));
      vs.addAssembly(chart("Chart3", bar(dim("Date", XSchema.DATE))));

      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
                                        AssetEntry.Type.VIEWSHEET, "test/Bug78481", null);
      vs.setEntry(entry);
      box = new ViewsheetSandbox(vs, AbstractSheet.SHEET_RUNTIME_MODE, principal, false, entry);
      box.reset(null, vs.getAssemblies(), new ChangedAssemblyList(), true, true, null);
   }

   private ChartVSAssembly chart(String name, VSChartInfo info) {
      ChartVSAssembly chart = new ChartVSAssembly(vs, name);
      chart.setSourceInfo(new SourceInfo(SourceInfo.ASSET, null, "D"));
      chart.getVSAssemblyInfo().setPixelSize(new Dimension(500, 400));
      ((ChartVSAssemblyInfo) chart.getVSAssemblyInfo()).setVSChartInfo(info);
      return chart;
   }

   private static VSChartInfo bar(ChartRef x) {
      VSChartInfo bar = new DefaultVSChartInfo();
      bar.setChartType(GraphTypes.CHART_BAR);
      bar.addXField(x);
      VSChartAggregateRef sales = new VSChartAggregateRef();
      sales.setColumnValue("Sales");
      sales.setFormulaValue("Sum");
      bar.addYField(sales);
      return bar;
   }

   private static ChartRef dim(String col, String dtype) {
      VSChartDimensionRef dim = new VSChartDimensionRef();
      dim.setGroupColumnValue(col);
      dim.setDataType(dtype);

      if(XSchema.isDateType(dtype)) {
         dim.setDateLevelValue(String.valueOf(DateRangeRef.YEAR_INTERVAL));
      }

      return dim;
   }

   private static java.sql.Date date(int y, int m, int d) {
      return java.sql.Date.valueOf(String.format("%04d-%02d-%02d", y, m, d));
   }

   private ChartVSAssembly chart1() {
      return (ChartVSAssembly) vs.getAssembly("Chart1");
   }

   private VGraphPair render() throws Exception {
      box.resetDataMap("Chart1");
      box.clearGraph("Chart1");
      return box.getVGraphPair("Chart1", true, null);
   }

   private int rows(String name) throws Exception {
      box.resetDataMap(name);
      Object data = box.getData(name, true, DataMap.NORMAL);
      assertInstanceOf(VSDataSet.class, data);
      return ((VSDataSet) data).getRowCount();
   }

   private Set<String> chart1Values(String col) throws Exception {
      box.resetDataMap("Chart1");
      VSDataSet data = (VSDataSet) box.getData("Chart1", true, DataMap.NORMAL);
      Set<String> values = new TreeSet<>();

      for(int r = 0; r < data.getRowCount(); r++) {
         values.add(Tool.getDataString(data.getData(col, r)));
      }

      return values;
   }

   private RuntimeViewsheet rvs() {
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(box));
      when(rvs.getEntry()).thenReturn(vs.getEntry());
      when(rvs.getID()).thenReturn(RID);
      return rvs;
   }

   private CoreLifecycleService lifecycle() {
      CoreLifecycleService lifecycle = mock(CoreLifecycleService.class);
      when(lifecycle.createList(anyBoolean(), any(), any(), any()))
         .thenAnswer(inv -> new ChangedAssemblyList(false));
      return lifecycle;
   }

   private ViewsheetService viewsheetService() throws Exception {
      ViewsheetService service = mock(ViewsheetService.class);
      RuntimeViewsheet rvs = rvs();
      when(service.getViewsheet(eq(RID), any())).thenReturn(rvs);
      return service;
   }

   private void brush(String chart, String selected) throws Exception {
      VSChartBrushService service = new VSChartBrushService(lifecycle(), viewsheetService(), null);
      VSChartBrushEvent event = new VSChartBrushEvent();
      event.setChartName(chart);
      event.setSelected(selected);
      service.eventHandler(RID, event, "", principal, mock(CommandDispatcher.class));
   }

   private void zoom(String selected, boolean exclude) throws Exception {
      VSChartZoomService service = new VSChartZoomService(lifecycle(), viewsheetService(), null);
      VSChartZoomEvent event = new VSChartZoomEvent();
      event.setChartName("Chart1");
      event.setSelected(selected);
      event.setExclude(exclude);
      service.eventHandler(RID, event, "", principal, mock(CommandDispatcher.class));
   }

   private static final String RID = "rid";
   private Principal oldPrincipal;
   private Principal principal;
   private Viewsheet vs;
   private ViewsheetSandbox box;
}
