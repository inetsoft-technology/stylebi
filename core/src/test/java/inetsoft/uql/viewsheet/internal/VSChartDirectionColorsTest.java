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
package inetsoft.uql.viewsheet.internal;

import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.LibManagerTestConfiguration;
import inetsoft.test.SreeHome;
import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.graph.aesthetic.CategoricalColorFrameWrapper;
import inetsoft.uql.viewsheet.graph.aesthetic.StaticColorFrameWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Direction colours draw only on a marked candle, stock or waterfall with both stored colours,
 * no colour field, no author or stylesheet single colour, and (for stock) an Open field.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class VSChartDirectionColorsTest {
   private static final VizContext MODERN = VizContext.of(VizMark.MODERN_LIGHT);

   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      SreeEnv.setProperty("viewsheet.density", null);
      VSChartPaletteDefaults.clearMemo();
   }

   @Test
   void aModernCandleDraws() {
      assertTrue(VSChartDirectionColors.draws(MODERN, candle(new CandleVSChartInfo())));
      assertTrue(VSChartDirectionColors.draws(VizContext.of(VizMark.MODERN_DARK),
                                              candle(new CandleVSChartInfo())));
   }

   @Test
   void aModernWaterfallDraws() {
      assertTrue(VSChartDirectionColors.draws(MODERN, waterfall()));
   }

   @Test
   void aModernStockWithOpenDraws() {
      assertTrue(VSChartDirectionColors.draws(MODERN, candle(new StockVSChartInfo())));
   }

   @Test
   void aMarkedCandleWithoutStoredColoursDoesNot() {
      CandleVSChartInfo candle = candle(new CandleVSChartInfo());
      assertTrue(VSChartDirectionColors.draws(MODERN, candle), "premise: both stored colours");

      candle.setDownColorFrameWrapper(null);
      assertFalse(VSChartDirectionColors.draws(MODERN, candle), "no down colour");

      candle.setUpColorFrameWrapper(null);
      assertFalse(VSChartDirectionColors.draws(MODERN, candle), "neither colour");

      DefaultVSChartInfo waterfall = waterfall();
      waterfall.setUpColorFrameWrapper(null);
      assertFalse(VSChartDirectionColors.draws(MODERN, waterfall), "a waterfall with no up colour");
   }

   @Test
   void aStockWithoutOpenDoesNot() {
      StockVSChartInfo stock = new StockVSChartInfo();
      candle(stock);
      stock.setOpenField(null);
      assertFalse(VSChartDirectionColors.draws(MODERN, stock));
   }

   @Test
   void aClassicCandleDoesNot() {
      CandleVSChartInfo candle = candle(new CandleVSChartInfo());
      assertFalse(VSChartDirectionColors.draws(VizContext.LEGACY, candle));
      assertFalse(VSChartDirectionColors.draws(null, candle));
   }

   @Test
   void aBarDoesNot() {
      DefaultVSChartInfo bar = new DefaultVSChartInfo();
      bar.addXField(dim("state"));
      bar.addYField(agg("v"));
      bar.setChartType(GraphTypes.CHART_BAR);
      assertFalse(VSChartDirectionColors.draws(MODERN, stored(bar)));
   }

   @Test
   void aColourFieldStopsIt() {
      CandleVSChartInfo candle = candle(new CandleVSChartInfo());
      candle.setColorField(regionColour());
      assertFalse(VSChartDirectionColors.draws(MODERN, candle));
   }

   @Test
   void anAuthorSingleColourStopsIt() {
      CandleVSChartInfo candle = candle(new CandleVSChartInfo());
      ((StaticColorFrameWrapper) candle.getColorFrameWrapper()).setUserColor(Color.RED);
      assertFalse(VSChartDirectionColors.draws(MODERN, candle));
   }

   @Test
   void aStylesheetSingleColourStopsIt() {
      CandleVSChartInfo candle = candle(new CandleVSChartInfo());
      StaticColorFrameWrapper wrapper = (StaticColorFrameWrapper) candle.getColorFrameWrapper();
      ((inetsoft.graph.aesthetic.StaticColorFrame) wrapper.getVisualFrame())
         .setCssColor(Color.BLUE);
      assertFalse(VSChartDirectionColors.draws(MODERN, candle));
   }

   @Test
   void aWaterfallMeasureColourStopsIt() {
      DefaultVSChartInfo waterfall = waterfall();
      VSChartAggregateRef measure = (VSChartAggregateRef) waterfall.getYField(0);
      ((StaticColorFrameWrapper) measure.getColorFrameWrapper()).setUserColor(Color.RED);
      assertFalse(VSChartDirectionColors.draws(MODERN, waterfall));

      DefaultVSChartInfo other = waterfall();
      ((VSChartAggregateRef) other.getYField(0)).setColorField(regionColour());
      assertFalse(VSChartDirectionColors.draws(MODERN, other));
   }

   @Test
   void aWaterfallWithASecondMeasureDoesNot() {
      DefaultVSChartInfo info = new DefaultVSChartInfo();
      info.setMultiStyles(true);
      info.addXField(dim("state"));
      info.addYField(styled(agg("v"), GraphTypes.CHART_WATERFALL));
      stored(info);
      assertEquals(GraphTypes.CHART_WATERFALL, ResolvedChartType.of(info).type(),
                   "premise: resolves as a waterfall");
      assertTrue(VSChartDirectionColors.draws(MODERN, info),
                 "premise: the waterfall measure alone draws");

      info.addYField(styled(agg("w"), GraphTypes.CHART_LINE));
      assertFalse(VSChartDirectionColors.draws(MODERN, info));
   }

   private static VSChartAggregateRef styled(VSChartAggregateRef agg, int type) {
      agg.setChartType(type);
      agg.setRTChartType(type);
      return agg;
   }

   private static <T extends CandleVSChartInfo> T candle(T info) {
      info.addXField(dim("date"));
      info.setOpenField(agg("open"));
      info.setCloseField(agg("close"));
      info.setHighField(agg("high"));
      info.setLowField(agg("low"));
      info.setChartType(info instanceof StockVSChartInfo ? GraphTypes.CHART_STOCK :
                           GraphTypes.CHART_CANDLE);
      return stored(info);
   }

   private static DefaultVSChartInfo waterfall() {
      DefaultVSChartInfo info = new DefaultVSChartInfo();
      info.addXField(dim("state"));
      info.addYField(agg("v"));
      info.setChartType(GraphTypes.CHART_WATERFALL);
      return stored(info);
   }

   // the up and down colours a marked chart's seed stores
   private static <T extends AbstractChartInfo> T stored(T info) {
      info.setUpColorFrameWrapper(new StaticColorFrameWrapper());
      info.setDownColorFrameWrapper(new StaticColorFrameWrapper());
      return info;
   }

   private static VSAestheticRef regionColour() {
      VSAestheticRef color = new VSAestheticRef();
      color.setDataRef(dim("region"));
      color.setVisualFrameWrapper(new CategoricalColorFrameWrapper());
      return color;
   }

   private static VSChartDimensionRef dim(String name) {
      VSChartDimensionRef dim = new VSChartDimensionRef(new AttributeRef(null, name));
      dim.setGroupColumnValue(name);
      dim.setDataType(XSchema.STRING);
      return dim;
   }

   private static VSChartAggregateRef agg(String name) {
      VSChartAggregateRef agg = new VSChartAggregateRef();
      agg.setDataRef(new AttributeRef(null, name));
      agg.setColumnValue(name);
      agg.setFormula(AggregateFormula.SUM);
      return agg;
   }
}
