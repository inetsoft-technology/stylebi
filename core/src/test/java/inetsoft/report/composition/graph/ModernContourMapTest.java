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
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.graph.GraphTypes;
import inetsoft.uql.viewsheet.graph.PlotDescriptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.geom.Rectangle2D;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Which maps fit their extent to the points under the modern mark.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ModernContourMapTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void onlyMarkedNonWebContourMapsFit() {
      assertTrue(MapGenerator.isModernContour(true, GraphTypes.CHART_MAP_CONTOUR, false, false));
      assertFalse(MapGenerator.isModernContour(false, GraphTypes.CHART_MAP_CONTOUR, false, false),
                  "classic");
      assertFalse(MapGenerator.isModernContour(true, GraphTypes.CHART_MAP, false, false), "map");
      assertFalse(MapGenerator.isModernContour(true, GraphTypes.CHART_MAP_CONTOUR, true, false),
                  "web map");
      assertFalse(MapGenerator.isModernContour(true, GraphTypes.CHART_MAP_CONTOUR, false, true),
                  "facet");
   }

   @Test
   void anUntouchedMapFits() {
      assertTrue(MapGenerator.fitsToData(true, false, new PlotDescriptor()));
   }

   @Test
   void aTouchedMapIsPinnedNotFitted() {
      PlotDescriptor zoomed = new PlotDescriptor();
      zoomed.setZoom(1.2);
      PlotDescriptor panned = new PlotDescriptor();
      panned.setPanX(5);
      PlotDescriptor pinned = new PlotDescriptor();
      pinned.setLonLat(new Rectangle2D.Double(-120, 30, 50, 15));

      assertFalse(MapGenerator.fitsToData(true, false, zoomed), "zoom");
      assertFalse(MapGenerator.fitsToData(true, false, panned), "pan");
      assertFalse(MapGenerator.fitsToData(true, false, pinned), "lonlat");
      assertFalse(MapGenerator.fitsToData(true, true, new PlotDescriptor()), "data zoom");
   }

   @Test
   void onlyAModernContourFits() {
      assertFalse(MapGenerator.fitsToData(false, false, new PlotDescriptor()));
   }
}
