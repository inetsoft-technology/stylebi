/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.graph.coord;

import inetsoft.graph.*;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.element.LineElement;
import inetsoft.graph.guide.axis.DefaultAxis;
import inetsoft.graph.scale.*;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards the right-side axis of a dual-axis rect coord. When a measure is bound to the
 * secondary axis a separate yscale2 is installed, and that axis must be drawn (line, ticks
 * and labels) whatever style the primary scale carries - the primary style only governs
 * whether a mirrored axis is drawn for the primary scale itself.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class RectCoordSecondaryAxisTest {
   @ParameterizedTest
   @ValueSource(ints = { AxisSpec.AXIS_SINGLE, AxisSpec.AXIS_DOUBLE })
   void secondaryAxisDrawnForAnyPrimaryStyle(int primaryStyle) {
      RectCoord coord = dualAxisCoord(primaryStyle, AxisSpec.AXIS_SINGLE);
      DefaultAxis y2 = coord.getAxisAt(Coordinate.RIGHT_AXIS);

      assertNotNull(y2, "secondary y axis must exist when yscale2 is set");
      assertTrue(y2.getZIndex() >= 0,
                 "secondary y axis must be painted (zIndex=" + y2.getZIndex() + "); regression: "
                    + "the AXIS_SINGLE2 bit test on the primary scale style hides it");
      assertTrue(y2.isLabelVisible(), "secondary y axis labels must be visible");
      assertTrue(y2.getLabels().length > 0, "secondary y axis must have tick labels");
   }

   @Test
   void primaryAxisStillDrawnWithSecondaryAxis() {
      RectCoord coord = dualAxisCoord(AxisSpec.AXIS_SINGLE, AxisSpec.AXIS_SINGLE);
      DefaultAxis y1 = coord.getAxisAt(Coordinate.LEFT_AXIS);

      assertNotNull(y1, "primary y axis must exist");
      assertTrue(y1.getZIndex() >= 0, "primary y axis must still be painted");
      assertTrue(y1.isLabelVisible(), "primary y axis labels must remain visible");
   }

   @Test
   void secondaryAxisHiddenWhenItsOwnStyleIsNone() {
      RectCoord coord = dualAxisCoord(AxisSpec.AXIS_SINGLE, AxisSpec.AXIS_NONE);
      DefaultAxis y2 = coord.getAxisAt(Coordinate.RIGHT_AXIS);

      assertTrue(y2 == null || y2.getZIndex() < 0,
                 "secondary axis must stay hidden when its own spec sets AXIS_NONE (sparkline)");
   }

   @Test
   void noSecondaryAxisWhenScale2Absent() {
      EGraph egraph = new EGraph();
      egraph.addElement(new LineElement("Year", "Price"));

      Scale yscale = new LinearScale("Price");
      yscale.getAxisSpec().setAxisStyle(AxisSpec.AXIS_SINGLE);

      RectCoord coord = new RectCoord(new CategoricalScale("Year"), yscale);
      egraph.setCoordinate(coord);

      assertNotNull(Plotter.getPlotter(egraph).plotAndLayout(data(), 0, 0, 800, 600));

      DefaultAxis y2 = coord.getAxisAt(Coordinate.RIGHT_AXIS);

      assertTrue(y2 == null || y2.getZIndex() < 0,
                 "single-style axis must not draw a right-side axis when there is no yscale2");
   }

   private static RectCoord dualAxisCoord(int primaryStyle, int secondaryStyle) {
      EGraph egraph = new EGraph();
      egraph.addElement(new LineElement("Year", "Price"));
      egraph.addElement(new LineElement("Year", "Total"));

      Scale yscale = new LinearScale("Price");
      yscale.getAxisSpec().setAxisStyle(primaryStyle);

      Scale yscale2 = new LinearScale("Total");
      yscale2.getAxisSpec().setAxisStyle(secondaryStyle);

      RectCoord coord = new RectCoord(new CategoricalScale("Year"), yscale);
      coord.setYScale2(yscale2);
      egraph.setCoordinate(coord);

      VGraph vgraph = Plotter.getPlotter(egraph).plotAndLayout(data(), 0, 0, 800, 600);
      assertNotNull(vgraph, "Plotter.plotAndLayout must produce a vgraph for the test fixture");
      return coord;
   }

   private static DefaultDataSet data() {
      return new DefaultDataSet(new Object[][] {
         { "Year",  "Price", "Total" },
         { "2022",  400000.0, 390000.0 },
         { "2023",  450000.0, 470000.0 },
         { "2024",  420000.0, 450000.0 },
         { "2025",  480000.0, 480000.0 },
      });
   }
}
