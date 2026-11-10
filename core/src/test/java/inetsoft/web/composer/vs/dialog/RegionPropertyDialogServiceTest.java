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
package inetsoft.web.composer.vs.dialog;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.region.*;
import inetsoft.test.*;
import inetsoft.uql.CompositeValue;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.TitleDescriptor;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.web.adhoc.model.chart.ChartFormatConstants;
import inetsoft.web.graph.handler.ChartRegionHandler;
import inetsoft.web.graph.model.dialog.TitleFormatDialogModel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class RegionPropertyDialogServiceTest {
   private static final String RID = "CD1 Bar-1";
   private static final String CHART = "Chart1";

   private RegionPropertyDialogService service;
   private ChartVSAssemblyInfo info;
   private TitleDescriptor xTitle;
   private ChartArea chartArea;

   @BeforeEach
   void setUp() throws Exception {
      ViewsheetService engine = mock(ViewsheetService.class);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      Viewsheet vs = mock(Viewsheet.class);
      ChartVSAssembly chart = mock(ChartVSAssembly.class);
      info = new ChartVSAssemblyInfo();
      info.setVizMark(VizMark.MODERN_LIGHT);
      xTitle = info.getChartDescriptor().getTitlesDescriptor().getXTitleDescriptor();

      when(engine.getViewsheet(eq(RID), any())).thenReturn(rvs);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(vs.getAssembly(CHART)).thenReturn(chart);
      when(chart.getVSAssemblyInfo()).thenReturn(info);

      service = spy(new RegionPropertyDialogService(
         null, engine, null, null, new ChartRegionHandler()));
      chartArea = mock(ChartArea.class);
      doAnswer(inv -> chartArea).when(service).getChartArea(rvs, chart, null);
   }

   @Test
   void aDrawnTitleShowsItsDrawnLabel() throws Exception {
      TitleArea area = mock(TitleArea.class);
      ChartAreaInfo areaInfo = new ChartAreaInfo();
      areaInfo.setProperty("titlename", "Region");
      when(area.getChartAreaInfo()).thenReturn(areaInfo);
      when(chartArea.getXTitleArea()).thenReturn(area);

      TitleFormatDialogModel model = open();

      assertEquals("Region", model.getTitleFormatPaneModel().getCurrentTitle());
      assertEquals("Region", model.getTitleFormatPaneModel().getTitle());
   }

   @Test
   void aTitleItsSeedHidOpensWithNoText() throws Exception {
      seedHidden();

      TitleFormatDialogModel model = open();

      assertEquals("", model.getTitleFormatPaneModel().getCurrentTitle());
      assertEquals("", model.getTitleFormatPaneModel().getTitle());
   }

   @Test
   void aHiddenTypedTitleShowsItsText() throws Exception {
      xTitle.setAuthorTitleValue("Revenue");
      xTitle.setVisible(false);

      TitleFormatDialogModel model = open();

      assertEquals("Revenue", model.getTitleFormatPaneModel().getCurrentTitle());
      assertEquals("Revenue", model.getTitleFormatPaneModel().getTitle());
   }

   @Test
   void aChartWithNoAreasOpensWithNoText() throws Exception {
      chartArea = null;

      TitleFormatDialogModel model = open();

      assertEquals("", model.getTitleFormatPaneModel().getCurrentTitle());
   }

   @Test
   void submittingAnUntouchedHiddenTitleKeepsItHidden() throws Exception {
      seedHidden();
      TitleFormatDialogModel model = open();

      model.updateTitleProperties(xTitle, VizContext.of(info));

      assertFalse(xTitle.isVisible());
      assertFalse(xTitle.isUserTitle());
      assertTrue(xTitle.getTitleValue() == null || xTitle.getTitleValue().isEmpty());
   }

   @Test
   void typingOnATitleItsSeedHidShowsIt() throws Exception {
      seedHidden();
      TitleFormatDialogModel model = open();

      model.getTitleFormatPaneModel().setTitle("Revenue");
      model.updateTitleProperties(xTitle, VizContext.of(info));

      assertTrue(xTitle.isVisible());
      assertTrue(xTitle.isUserTitle());
      assertEquals("Revenue", xTitle.getTitleValue());
   }

   // per-type modern seed: hidden at the DEFAULT tier, nothing drawn
   private void seedHidden() {
      xTitle.setVisible(false, CompositeValue.Type.DEFAULT);
      when(chartArea.getXTitleArea()).thenReturn(null);
   }

   private TitleFormatDialogModel open() throws Exception {
      return service.getTitleFormatDialogModel(RID, CHART, ChartFormatConstants.X_TITLE, null, null);
   }
}
