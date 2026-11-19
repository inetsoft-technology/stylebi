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
package inetsoft.web.graph;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.LibManagerTestConfiguration;
import inetsoft.test.SreeHome;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VizMark;
import inetsoft.web.vswizard.handler.VSWizardBindingHandler;
import inetsoft.web.vswizard.model.recommender.VSTemporaryInfo;
import inetsoft.web.vswizard.service.VSWizardTemporaryInfoService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A marked treemap's seeded-hidden size legend does not make the legends look hidden to the
 * Show Legend menu or the wizard.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class SizeLegendMenuTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      SreeEnv.setProperty("viewsheet.density", null);
   }

   private static ChartVSAssembly markedTreemap(boolean withColor, boolean withSize) {
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(vs, "Chart1");
      chart.getVSAssemblyInfo().initDefaultFormat();
      ChartVSAssemblyInfo info = (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
      info.setVizMark(VizMark.MODERN_LIGHT);
      VSChartInfo cinfo = info.getVSChartInfo();
      VSChartDimensionRef region = new VSChartDimensionRef(new AttributeRef(null, "Region"));
      region.setDataType(XSchema.STRING);
      region.setGroupColumnValue("Region");
      VSChartAggregateRef sales = new VSChartAggregateRef();
      sales.setColumnValue("Sales");
      cinfo.addXField(region);
      cinfo.addYField(sales);
      cinfo.setChartType(GraphTypes.CHART_TREEMAP);

      if(withColor) {
         VSChartDimensionRef group = new VSChartDimensionRef(new AttributeRef(null, "Brand"));
         group.setDataType(XSchema.STRING);
         group.setGroupColumnValue("Brand");
         VSAestheticRef color = new VSAestheticRef();
         color.setDataRef(group);
         cinfo.setColorField(color);
      }

      if(withSize) {
         VSChartAggregateRef units = new VSChartAggregateRef();
         units.setColumnValue("Units");
         VSAestheticRef sizeRef = new VSAestheticRef();
         sizeRef.setDataRef(units);
         cinfo.setSizeField(sizeRef);
      }

      info.reseedTypeDefaults(false);
      return chart;
   }

   private static VSWizardBindingHandler handler(VSWizardTemporaryInfoService service) {
      return new VSWizardBindingHandler(
         null, null, null, null, null, null, null, null, null, null, null, service, null, null);
   }

   @Test
   void noSizeFieldOffersNoShowLegend() {
      ChartVSAssembly chart = markedTreemap(false, false);
      LegendDescriptor size =
         chart.getChartDescriptor().getLegendsDescriptor().getSizeLegendDescriptor();
      assertFalse(size.isVisible(), "premise: seeded hidden");

      assertFalse(GraphBuilder.isLegendHidden(
         chart.getChartDescriptor(), chart.getVSChartInfo(), false));
   }

   @Test
   void aSeededHiddenSizeLegendStillOffersShowLegend() {
      ChartVSAssembly chart = markedTreemap(false, true);

      assertTrue(GraphBuilder.isLegendHidden(
         chart.getChartDescriptor(), chart.getVSChartInfo(), false));
   }

   @Test
   void theWizardKeepsTheColourLegend() {
      ChartVSAssembly chart = markedTreemap(true, true);
      VSTemporaryInfo temporaryInfo = new VSTemporaryInfo();
      temporaryInfo.setShowLegend(false);
      VSWizardTemporaryInfoService service = mock(VSWizardTemporaryInfoService.class);
      RuntimeViewsheet rtv = mock(RuntimeViewsheet.class);
      when(service.getVSTemporaryInfo(rtv)).thenReturn(temporaryInfo);

      handler(service).reloadLegendVisible(rtv, chart);

      assertTrue(temporaryInfo.isShowLegend(), "finishing does not hide all legends");
   }

   @Test
   void anAuthorsHiddenSizeLegendStillHidesInTheWizard() {
      ChartVSAssembly chart = markedTreemap(true, true);
      chart.getChartDescriptor().getLegendsDescriptor().getSizeLegendDescriptor()
         .setAuthorVisible(false);
      VSTemporaryInfo temporaryInfo = new VSTemporaryInfo();
      temporaryInfo.setShowLegend(true);
      VSWizardTemporaryInfoService service = mock(VSWizardTemporaryInfoService.class);
      RuntimeViewsheet rtv = mock(RuntimeViewsheet.class);
      when(service.getVSTemporaryInfo(rtv)).thenReturn(temporaryInfo);

      handler(service).reloadLegendVisible(rtv, chart);

      assertFalse(temporaryInfo.isShowLegend());
   }
}
