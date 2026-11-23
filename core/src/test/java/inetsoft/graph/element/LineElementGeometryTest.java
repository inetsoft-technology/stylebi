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
package inetsoft.graph.element;

import inetsoft.graph.EGraph;
import inetsoft.graph.GGraph;
import inetsoft.graph.aesthetic.CategoricalColorFrame;
import inetsoft.graph.coord.RectCoord;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.geometry.LineGeometry;
import inetsoft.graph.scale.CategoricalScale;
import inetsoft.graph.scale.LinearScale;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #82050: the stack-group branch of LineElement.createGeometry() keyed each geometry by
 * {@code groupKey + "|" + var} and later recovered the group key by cutting that string at the
 * last '|'. A measure name containing '|' (e.g. "Sales|Q1") made the recovered key wrong and the
 * chart failed with a NullPointerException. UI line and area charts always use stack group.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class, initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class LineElementGeometryTest {
   private static final String MEASURE = "Sales|Q1";
   private static final String MEASURE2 = "Profit";

   /**
    * One geometry per (color group, measure), each holding exactly the rows of its group, for
    * line and area elements, and also when the group value itself contains '|'.
    */
   @ParameterizedTest(name = "{0}, regions {1}/{2}")
   @CsvSource({
      "line, East, West",
      "area, East, West",
      "line, East|1, West|2",
      "area, East|1, West|2",
   })
   void measureNameWithPipe(String type, String region1, String region2) {
      DefaultDataSet data = new DefaultDataSet(new Object[][] {
         { "Month", "Region", MEASURE, MEASURE2 },
         { "Jan",   region1,  10.0,    1.0 },
         { "Jan",   region2,  20.0,    2.0 },
         { "Feb",   region1,  30.0,    3.0 },
         { "Feb",   region2,  40.0,    4.0 },
         { "Mar",   region1,  50.0,    5.0 },
         { "Mar",   region2,  60.0,    6.0 },
      });

      CategoricalScale xScale = new CategoricalScale("Month");
      xScale.init(data);
      LinearScale yScale = new LinearScale(MEASURE, MEASURE2);
      yScale.init(data);

      LineElement element = "area".equals(type) ? new AreaElement() : new LineElement();
      element.addDim("Month");
      element.addVar(MEASURE);
      element.addVar(MEASURE2);
      element.setColorFrame(new CategoricalColorFrame("Region"));
      element.setCollisionModifier(GraphElement.MOVE_STACK);
      element.setStackGroup(true);

      EGraph egraph = new EGraph();
      egraph.addElement(element);
      RectCoord coord = new RectCoord(xScale, yScale);
      egraph.setCoordinate(coord);

      GGraph ggraph = egraph.createGGraph(coord, data);

      assertEquals(4, ggraph.getGeometryCount(), "2 regions x 2 measures");
      Set<String> series = new HashSet<>();

      for(int i = 0; i < ggraph.getGeometryCount(); i++) {
         LineGeometry geom = (LineGeometry) ggraph.getGeometry(i);
         int[] rows = geom.getRowIndexes();

         assertEquals(3, rows.length, "one point per month for " + geom.getVar());
         assertEquals(3, geom.getTupleCount(), "one tuple per month for " + geom.getVar());
         Object region = data.getData("Region", rows[0]);

         for(int row : rows) {
            assertEquals(region, data.getData("Region", row),
                         "a series must hold the rows of one region only");
         }

         series.add(region + "/" + geom.getVar());
      }

      assertEquals(Set.of(region1 + "/" + MEASURE, region2 + "/" + MEASURE,
                          region1 + "/" + MEASURE2, region2 + "/" + MEASURE2), series);
   }
}
