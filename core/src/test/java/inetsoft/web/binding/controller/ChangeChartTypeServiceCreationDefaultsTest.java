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
package inetsoft.web.binding.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.mv.MVManager;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.AssetDataCache;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.composition.execution.DistributedTableCacheStore;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.composition.graph.GraphTypeUtil;
import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.LibManagerTestConfiguration;
import inetsoft.test.SreeHome;
import inetsoft.test.SwapperTestConfiguration;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.asset.EmbeddedTableAssembly;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.graph.aesthetic.CategoricalColorFrameWrapper;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VizMark;
import inetsoft.web.binding.event.ChangeChartTypeEvent;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.binding.handler.VSChartHandler;
import inetsoft.web.binding.model.BindingModel;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.binding.service.graph.ChartRefModelFactoryService;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.CoreLifecycleService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** The creation-time defaults the binding editor's type change applies to a marked chart. */
@SreeHome
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  LibManagerTestConfiguration.class,
                                  ChangeChartTypeServiceCreationDefaultsTest.TestConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@ExtendWith(MockitoExtension.class)
// LENIENT: shared @BeforeEach stubs are not all exercised by every test path
@MockitoSettings(strictness = Strictness.LENIENT)
@Tag("core")
class ChangeChartTypeServiceCreationDefaultsTest {
   // the waterfall's source read runs a real worksheet query
   @Configuration
   static class TestConfig {
      @Bean
      @SuppressWarnings("unchecked")
      public AssetDataCache assetDataCache() {
         ObjectProvider<DistributedTableCacheStore> provider = mock(ObjectProvider.class);
         when(provider.getObject()).thenReturn(mock(DistributedTableCacheStore.class));
         return new AssetDataCache(mock(DataSourceRegistry.class), provider);
      }

      @Bean
      public MVManager mvManager() {
         return mock(MVManager.class);
      }
   }

   private static final String[] ITEMS = {
      "Revenue", "Returns", "Cost of goods", "Gross profit adj", "Salaries", "Marketing",
      "Other income", "Taxes"
   };

   @BeforeEach
   void setup() throws Exception {
      service = new ChangeChartTypeService(
         bindingFactory, runtimeViewsheetRef, coreLifecycleService, chartRefService,
         changeSeparateController, assemblyInfoHandler, chartHandler, bindingTreeController,
         viewsheetService);

      when(runtimeViewsheetRef.getRuntimeId()).thenReturn("vs1");
      when(viewsheetService.getViewsheet(anyString(), nullable(Principal.class))).thenReturn(rvs);
      when(rvs.getViewsheet()).thenReturn(viewsheet);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(viewsheetSandbox));
      when(bindingFactory.createModel(any())).thenReturn(mock(BindingModel.class));
   }

   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void pickingAreaOnAMarkedBarGivesStackedArea() throws Exception {
      ChartVSAssembly chart = chart(barInfo(dim("Month")), true);

      changeType(GraphTypes.CHART_AREA);

      assertEquals(GraphTypes.CHART_AREA_STACK, chart.getVSChartInfo().getChartType());
   }

   @Test
   void turningStackOffOnAMarkedAreaSticks() throws Exception {
      VSChartInfo info = barInfo(dim("Month"));
      info.setChartType(GraphTypes.CHART_AREA_STACK);
      ChartVSAssembly chart = chart(info, true);

      changeType(GraphTypes.CHART_AREA);

      assertEquals(GraphTypes.CHART_AREA, chart.getVSChartInfo().getChartType());
   }

   @Test
   void aDeniedStackedStyleKeepsThePlainArea() throws Exception {
      ChartVSAssembly chart = chart(barInfo(dim("Month")), true);

      try(MockedStatic<GraphTypeUtil> mocked = Mockito.mockStatic(
         GraphTypeUtil.class, Mockito.CALLS_REAL_METHODS))
      {
         mocked.when(() -> GraphTypeUtil.checkChartStylePermission(anyInt())).thenReturn(true);
         mocked.when(() -> GraphTypeUtil.checkChartStylePermission(
            eq(GraphTypes.CHART_AREA_STACK))).thenReturn(false);
         service.changeChartType("vs1", event(GraphTypes.CHART_AREA), null, dispatcher, "/link");
      }

      assertEquals(GraphTypes.CHART_AREA, chart.getVSChartInfo().getChartType());
   }

   @Test
   void classicAreaStaysPlain() throws Exception {
      ChartVSAssembly chart = chart(barInfo(dim("Month")), false);

      changeType(GraphTypes.CHART_AREA);

      assertEquals(GraphTypes.CHART_AREA, chart.getVSChartInfo().getChartType());
   }

   @Test
   void switchingAMarkedBarToPieSortsItsSlice() throws Exception {
      ChartVSAssembly chart = chart(barInfo(dim("Segment")), true);

      changeType(GraphTypes.CHART_PIE);

      VSDimensionRef slice = colorDimension(chart);
      assertEquals("Segment", slice.getName());
      assertEquals(XConstants.SORT_VALUE_DESC, slice.getOrder());
      assertEquals("Sum(Sales)", slice.getSortByColValue());
   }

   @Test
   void switchingAMarkedBarToTreemapColoursByTheTopLevel() throws Exception {
      ChartVSAssembly chart = chart(barInfo(dim("Division"), dim("Department")), true);

      changeType(GraphTypes.CHART_TREEMAP);

      assertEquals("Division", colorDimension(chart).getName());
   }

   @Test
   void switchingAMarkedBarWithTickersToCandleMakesPanels() throws Exception {
      ChartVSAssembly chart = chart(tickerInfo(), true);

      changeType(GraphTypes.CHART_CANDLE);

      VSChartInfo info = chart.getVSChartInfo();
      assertTrue(Arrays.stream(info.getYFields()).anyMatch(f -> "Ticker".equals(f.getName())),
                 () -> "Y: " + Arrays.toString(info.getYFields()));
      assertNull(info.getColorField());
   }

   @Test
   void switchingToWaterfallSavesTheSourceOrder() throws Exception {
      VSChartInfo info = barInfo(dim("Item"));
      ChartVSAssembly chart = chart(info, true);
      chart.setSourceInfo(new SourceInfo(SourceInfo.ASSET, null, "Funnel"));
      AssetDataCache.getCache().clearCache();
      AssetQuerySandbox aq = new AssetQuerySandbox(funnel());
      when(viewsheetSandbox.getAssetQuerySandbox()).thenReturn(aq);

      try {
         changeType(GraphTypes.CHART_WATERFALL);
      }
      finally {
         aq.dispose();
      }

      VSDimensionRef item = (VSDimensionRef) chart.getVSChartInfo().getXField(0);
      assertEquals("Item", item.getName());
      assertEquals(XConstants.SORT_SPECIFIC, item.getOrder());
      assertEquals(Arrays.asList(ITEMS), item.getManualOrderList());
   }

   @Test
   void classicSwitchesApplyNoDefault() throws Exception {
      ChartVSAssembly pie = chart(barInfo(dim("Segment")), false);
      changeType(GraphTypes.CHART_PIE);
      VSDimensionRef slice = colorDimension(pie);
      assertEquals("Segment", slice.getName());
      assertEquals(XConstants.SORT_ASC, slice.getOrder());

      ChartVSAssembly treemap = chart(barInfo(dim("Division"), dim("Department")), false);
      changeType(GraphTypes.CHART_TREEMAP);
      assertNull(treemap.getVSChartInfo().getColorField());

      ChartVSAssembly candle = chart(tickerInfo(), false);
      changeType(GraphTypes.CHART_CANDLE);
      assertEquals("Ticker", colorDimension(candle).getName());
   }

   // a script's chartStyle runs the processor but none of the defaults
   @Test
   void aScriptChartStyleAppliesNoDefault() {
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.setVSChartInfo(barInfo(dim("Division"), dim("Department")));
      info.setChartDescriptor(new ChartDescriptor());
      info.setVizMark(VizMark.MODERN_LIGHT);

      info.setChartStyle(GraphTypes.CHART_TREEMAP);

      assertEquals(GraphTypes.CHART_TREEMAP, info.getVSChartInfo().getChartType());
      assertNull(info.getVSChartInfo().getColorField());
   }

   private ChartVSAssembly chart(VSChartInfo info, boolean marked) {
      ChartVSAssembly chart = new ChartVSAssembly(viewsheet, "Chart1");
      chart.setVSChartInfo(info);
      chart.setChartDescriptor(new ChartDescriptor());

      if(marked) {
         chart.getVSAssemblyInfo().setVizMark(VizMark.MODERN_LIGHT);
      }

      when(viewsheet.getAssembly("Chart1")).thenReturn(chart);
      return chart;
   }

   private void changeType(int type) throws Exception {
      try(MockedStatic<GraphTypeUtil> mocked = Mockito.mockStatic(
         GraphTypeUtil.class, Mockito.CALLS_REAL_METHODS))
      {
         mocked.when(() -> GraphTypeUtil.checkChartStylePermission(anyInt())).thenReturn(true);
         service.changeChartType("vs1", event(type), null, dispatcher, "/link");
      }
   }

   private static ChangeChartTypeEvent event(int type) {
      ChangeChartTypeEvent event = new ChangeChartTypeEvent();
      event.setName("Chart1");
      event.setType(type);
      return event;
   }

   private static VSDimensionRef colorDimension(ChartVSAssembly chart) {
      AestheticRef color = chart.getVSChartInfo().getColorField();
      assertNotNull(color, "Color is empty");
      DataRef ref = color.getDataRef();
      assertInstanceOf(VSDimensionRef.class, ref);
      return (VSDimensionRef) ref;
   }

   // the dimensions on X and Sum(Sales) on Y
   private static VSChartInfo barInfo(VSChartDimensionRef... dims) {
      VSChartInfo info = new DefaultVSChartInfo();
      info.setChartType(GraphTypes.CHART_BAR);

      for(VSChartDimensionRef dim : dims) {
         info.addXField(dim);
      }

      info.addYField(sum("Sales"));
      return info;
   }

   // a bar of daily closes with the tickers on Color
   private static VSChartInfo tickerInfo() {
      VSChartInfo info = barInfo(dim("Day"));
      VSAestheticRef color = new VSAestheticRef();
      color.setDataRef(dim("Ticker"));
      color.setVisualFrameWrapper(new CategoricalColorFrameWrapper());
      info.setColorField(color);
      return info;
   }

   // the worksheet table Funnel: Item, Amount
   private static Worksheet funnel() {
      double[] amounts = { 520, -35, -210, 40, -120, -45, 25, -38 };
      Object[][] data = new Object[ITEMS.length + 1][];
      data[0] = new Object[] { "Item", "Amount" };

      for(int i = 0; i < ITEMS.length; i++) {
         data[i + 1] = new Object[] { ITEMS[i], amounts[i] };
      }

      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly table = new EmbeddedTableAssembly(ws, "Funnel");
      table.setEmbeddedData(
         new XEmbeddedTable(new String[] { XSchema.STRING, XSchema.DOUBLE }, data));
      ws.addAssembly(table);
      return ws;
   }

   private static VSChartDimensionRef dim(String name) {
      VSChartDimensionRef ref = new VSChartDimensionRef(new AttributeRef(null, name));
      ref.setDataType(XSchema.STRING);
      ref.setGroupColumnValue(name);
      return ref;
   }

   private static VSChartAggregateRef sum(String name) {
      VSChartAggregateRef ref = new VSChartAggregateRef();
      ref.setDataRef(new AttributeRef(null, name));
      ref.setColumnValue(name);
      ref.setFormula(AggregateFormula.SUM);
      return ref;
   }

   private ChangeChartTypeService service;

   @Mock VSBindingService bindingFactory;
   @Mock RuntimeViewsheetRef runtimeViewsheetRef;
   @Mock CoreLifecycleService coreLifecycleService;
   @Mock ChartRefModelFactoryService chartRefService;
   @Mock ChangeSeparateStatusController changeSeparateController;
   @Mock VSAssemblyInfoHandler assemblyInfoHandler;
   @Mock VSChartHandler chartHandler;
   @Mock VSBindingTreeController bindingTreeController;
   @Mock ViewsheetService viewsheetService;
   @Mock RuntimeViewsheet rvs;
   @Mock Viewsheet viewsheet;
   @Mock ViewsheetSandbox viewsheetSandbox;
   @Mock CommandDispatcher dispatcher;
}
