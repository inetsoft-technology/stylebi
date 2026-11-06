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
package inetsoft.report.internal.graph;

import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.LibManagerTestConfiguration;
import inetsoft.test.SreeHome;
import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.VizContext;
import inetsoft.uql.viewsheet.internal.VizMark;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A change to or from a map writes the axis titles' visibility: the author's tier on a classic
 * chart, as before, and only the default tier on a modern one, where the per-type seed decides.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ChangeChartTypeProcessorMapTitleTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void aModernChartWritesTheMapTitlesAtTheDefaultTier() {
      ChartDescriptor desc = new ChartDescriptor();

      ChangeChartTypeProcessor.setMapTitlesVisible(desc, false, modern());

      for(TitleDescriptor title : titles(desc)) {
         assertFalse(title.isVisible());
         assertFalse(title.hasVisibleUserValue());
      }
   }

   @Test
   void aClassicChartWritesTheMapTitlesAsTheAuthorsAsBefore() {
      ChartDescriptor desc = new ChartDescriptor();

      ChangeChartTypeProcessor.setMapTitlesVisible(desc, false, VizContext.LEGACY);

      for(TitleDescriptor title : titles(desc)) {
         assertFalse(title.isVisible());
         assertTrue(title.hasVisibleUserValue());
      }
   }

   @Test
   void aModernTitleTheAuthorShowedStaysShownOnAMap() {
      ChartDescriptor desc = new ChartDescriptor();
      desc.getTitlesDescriptor().getXTitleDescriptor().setVisible(true);

      ChangeChartTypeProcessor.setMapTitlesVisible(desc, false, modern());

      assertTrue(desc.getTitlesDescriptor().getXTitleDescriptor().isVisible());
   }

   @Test
   void aModernChangeToAMapWritesNoAuthorValue() {
      ChartDescriptor desc = new ChartDescriptor();
      VSChartInfo cinfo = bar();

      ChartInfo map = new ChangeChartTypeProcessor(
         GraphTypes.CHART_BAR, GraphTypes.CHART_MAP, false, false, null, cinfo, false, desc,
         modern()).process();

      assertInstanceOf(MapInfo.class, map, "premise: the chart became a map");

      for(TitleDescriptor title : titles(desc)) {
         assertFalse(title.isVisible());
         assertFalse(title.hasVisibleUserValue());
      }

      new ChangeChartTypeProcessor(GraphTypes.CHART_MAP, GraphTypes.CHART_BAR, false, false, null,
                                   map, false, desc, modern()).process();

      for(TitleDescriptor title : titles(desc)) {
         assertTrue(title.isVisible());
         assertFalse(title.hasVisibleUserValue());
      }
   }

   @Test
   void aClassicChangeToAMapHidesTheTitlesAsBefore() {
      ChartDescriptor desc = new ChartDescriptor();

      new ChangeChartTypeProcessor(GraphTypes.CHART_BAR, GraphTypes.CHART_MAP, false, false, null,
                                   bar(), false, desc, VizContext.LEGACY).process();

      for(TitleDescriptor title : titles(desc)) {
         assertFalse(title.isVisible());
         assertTrue(title.hasVisibleUserValue());
      }
   }

   private static VizContext modern() {
      return VizContext.of(VizMark.MODERN_LIGHT);
   }

   private static TitleDescriptor[] titles(ChartDescriptor desc) {
      TitlesDescriptor titles = desc.getTitlesDescriptor();
      return new TitleDescriptor[] { titles.getXTitleDescriptor(), titles.getX2TitleDescriptor(),
                                     titles.getYTitleDescriptor(), titles.getY2TitleDescriptor() };
   }

   // state by Sum(v)
   private static VSChartInfo bar() {
      VSChartDimensionRef state = new VSChartDimensionRef(new AttributeRef(null, "state"));
      state.setDataType(XSchema.STRING);
      state.setGroupColumnValue("state");
      VSChartAggregateRef v = new VSChartAggregateRef();
      v.setDataRef(new AttributeRef(null, "v"));
      v.setColumnValue("v");
      v.setFormula(AggregateFormula.SUM);

      VSChartInfo cinfo = new DefaultVSChartInfo();
      cinfo.addXField(state);
      cinfo.addYField(v);
      cinfo.setChartType(GraphTypes.CHART_BAR);
      return cinfo;
   }
}
