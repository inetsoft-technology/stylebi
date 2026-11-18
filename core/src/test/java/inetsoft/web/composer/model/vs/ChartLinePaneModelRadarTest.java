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
package inetsoft.web.composer.model.vs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.VizContext;
import inetsoft.uql.viewsheet.internal.VizMark;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The Lines tab offers the Y grid row but not the X one on a marked radar.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ChartLinePaneModelRadarTest {
   private static final VizContext MARKED = VizContext.of(VizMark.MODERN_LIGHT);
   private static final VizContext CLASSIC = VizContext.of((VizMark) null);

   @Test
   void aMarkedRadarOffersOnlyTheYGridRow() {
      ChartLinePaneModel model = model(new RadarVSChartInfo(), GraphTypes.CHART_RADAR, MARKED);

      assertTrue(model.isGridLineVisible());
      assertFalse(model.isXGridLineVisible());
   }

   @Test
   void aMarkedFilledRadarDoesTheSame() {
      ChartLinePaneModel model =
         model(new RadarVSChartInfo(), GraphTypes.CHART_FILL_RADAR, MARKED);

      assertTrue(model.isGridLineVisible());
      assertFalse(model.isXGridLineVisible());
   }

   @Test
   void aClassicRadarOffersNeither() {
      ChartLinePaneModel model = model(new RadarVSChartInfo(), GraphTypes.CHART_RADAR, CLASSIC);

      assertFalse(model.isGridLineVisible());
      assertFalse(model.isXGridLineVisible());
   }

   @Test
   void aBarOffersBothMarkedOrNot() {
      for(VizContext ctx : new VizContext[] { MARKED, CLASSIC }) {
         ChartLinePaneModel model = model(new DefaultVSChartInfo(), GraphTypes.CHART_BAR, ctx);

         assertTrue(model.isGridLineVisible());
         assertTrue(model.isXGridLineVisible());
      }
   }

   @Test
   void aMarkedPieOffersNeither() {
      ChartLinePaneModel model = model(new DefaultVSChartInfo(), GraphTypes.CHART_PIE, MARKED);

      assertFalse(model.isGridLineVisible());
      assertFalse(model.isXGridLineVisible());
   }

   @Test
   void theTwoArgumentConstructorIsClassic() {
      RadarVSChartInfo info = new RadarVSChartInfo();
      info.setChartType(GraphTypes.CHART_RADAR);
      info.setRTChartType(GraphTypes.CHART_RADAR);
      ChartLinePaneModel model = new ChartLinePaneModel(info, new PlotDescriptor());

      assertFalse(model.isGridLineVisible());
      assertFalse(model.isXGridLineVisible());
   }

   @Test
   void theFlagIsSerializedAsXGridLineVisible() {
      ChartLinePaneModel model = model(new RadarVSChartInfo(), GraphTypes.CHART_RADAR, MARKED);
      JsonNode tree = new ObjectMapper().valueToTree(model);

      assertTrue(tree.has("xGridLineVisible"), tree.fieldNames().toString());
      assertFalse(tree.get("xGridLineVisible").asBoolean());
   }

   private static ChartLinePaneModel model(VSChartInfo info, int type, VizContext ctx) {
      info.setChartType(type);
      info.setRTChartType(type);
      return new ChartLinePaneModel(info, new PlotDescriptor(), ctx);
   }
}
