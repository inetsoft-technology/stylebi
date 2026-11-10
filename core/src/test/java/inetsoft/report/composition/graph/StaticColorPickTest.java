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
package inetsoft.report.composition.graph;

import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.LibManagerTestConfiguration;
import inetsoft.test.SreeHome;
import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.graph.aesthetic.StaticColorFrameWrapper;
import inetsoft.uql.viewsheet.internal.VizContext;
import inetsoft.uql.viewsheet.internal.VizMark;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Color;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A measure's colour picked at bind time comes from the chart's own palette.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class StaticColorPickTest {
   private static final Color LEGACY_1 = new Color(0x518DB9);
   private static final Color LEGACY_2 = new Color(0xB9DBF4);
   private static final Color MODERN_1 = new Color(0x0490FF);
   private static final Color MODERN_2 = new Color(0xFF5A35);
   private static final Color MODERN_3 = new Color(0x241C4F);
   private static final VizContext MODERN = VizContext.of(VizMark.MODERN_LIGHT);

   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      SreeEnv.setProperty("viewsheet.density", null);
      SreeEnv.setProperty("map.default.color", null);
      SreeEnv.setProperty("map.empty.color", null);
   }

   @Test
   void aModernDropTakesTheNextModernColour() {
      DefaultVSChartInfo info = new DefaultVSChartInfo();
      info.addYField(agg("a", MODERN_1, null));
      VSChartAggregateRef dropped = agg("b", MODERN_1, null);

      GraphUtil.fixStaticColorFrame(dropped, info, null, MODERN);
      assertEquals(MODERN_2, frame(dropped).getDefaultColor());
   }

   @Test
   void aDropSkipsTheAuthorsAndTheSeededColours() {
      DefaultVSChartInfo info = new DefaultVSChartInfo();
      info.addYField(agg("a", MODERN_1, null));
      info.addYField(agg("b", MODERN_1, MODERN_2));
      VSChartAggregateRef dropped = agg("c", MODERN_1, null);

      GraphUtil.fixStaticColorFrame(dropped, info, null, MODERN);
      assertEquals(MODERN_3, frame(dropped).getDefaultColor());
   }

   @Test
   void aClassicDropIsUnchanged() {
      DefaultVSChartInfo info = new DefaultVSChartInfo();
      info.addYField(agg("a", LEGACY_1, null));
      VSChartAggregateRef dropped = agg("b", LEGACY_1, null);

      GraphUtil.fixStaticColorFrame(dropped, info, null, VizContext.LEGACY);
      assertEquals(LEGACY_2, frame(dropped).getDefaultColor());
   }

   @Test
   void theWizardSeedsTheMarksPalette() {
      VSChartAggregateRef a = agg("a", LEGACY_1, null);
      VSChartAggregateRef b = agg("b", LEGACY_1, null);

      GraphUtil.fixStaticColorFrame(List.of(a, b), null, null, MODERN);
      assertEquals(MODERN_1, frame(a).getDefaultColor());
      assertEquals(MODERN_2, frame(b).getDefaultColor());
   }

   @Test
   void theOldGreenStillShortCircuits() {
      for(VizContext ctx : new VizContext[] { VizContext.LEGACY, MODERN }) {
         VSChartAggregateRef lone = agg("a", GraphUtil.OLD_COLOR_PALETTE_DEFAULT_COLOR, null);
         GraphUtil.fixStaticColorFrame(lone, new DefaultVSChartInfo(), null, ctx);
         assertEquals(GraphUtil.OLD_COLOR_PALETTE_DEFAULT_COLOR, frame(lone).getDefaultColor());
      }
   }

   private static StaticColorFrameWrapper frame(ChartAggregateRef ref) {
      return (StaticColorFrameWrapper) ref.getColorFrameWrapper();
   }

   private static VSChartAggregateRef agg(String name, Color color, Color user) {
      VSChartAggregateRef agg = new VSChartAggregateRef();
      agg.setDataRef(new AttributeRef(null, name));
      agg.setColumnValue(name);
      agg.setFormula(AggregateFormula.SUM);
      frame(agg).setDefaultColor(color);

      if(user != null) {
         frame(agg).setUserColor(user);
      }

      return agg;
   }
}
