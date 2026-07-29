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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards "Labels on Opposite Side" on a single-axis chart. A single axis has one line, so
 * moving its labels must move the whole axis to the opposite side rather than leaving the
 * original line behind and drawing a second one. A double-style axis keeps both lines and
 * only relocates the labels.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class RectCoordOppositeSideLabelsTest {
   @Test
   void singleStyleResolvesToSecondaryAxisStyle() {
      AxisSpec spec = new AxisSpec();
      spec.setAxisStyle(AxisSpec.AXIS_SINGLE);
      spec.setLabelOnSecondaryAxis(true);

      assertEquals(AxisSpec.AXIS_SINGLE2, spec.getAxisStyle(),
                   "single axis with relocated labels is the secondary axis, not a second axis");
   }

   @Test
   void doubleStyleKeepsBothAxes() {
      AxisSpec spec = new AxisSpec();
      spec.setAxisStyle(AxisSpec.AXIS_DOUBLE);
      spec.setLabelOnSecondaryAxis(true);

      assertEquals(AxisSpec.AXIS_DOUBLE2, spec.getAxisStyle(),
                   "double axis with relocated labels must keep both axis lines");
   }

   @Test
   void secondaryStyleIsIdempotent() {
      AxisSpec spec = new AxisSpec();
      spec.setAxisStyle(AxisSpec.AXIS_SINGLE2);
      spec.setLabelOnSecondaryAxis(true);

      assertEquals(AxisSpec.AXIS_SINGLE2, spec.getAxisStyle());
   }

   @Test
   void crossStyleKeepsCrossBit() {
      AxisSpec spec = new AxisSpec();
      spec.setAxisStyle(AxisSpec.AXIS_CROSS);
      spec.setLabelOnSecondaryAxis(true);

      int crossBit = AxisSpec.AXIS_CROSS & ~AxisSpec.AXIS_SINGLE;

      assertEquals(crossBit, spec.getAxisStyle() & crossBit,
                   "relocating labels must not drop the cross-axis bit");
   }

   @Test
   void yAxisMovesToTheRight() {
      RectCoord coord = coord(true, false);
      DefaultAxis y1 = coord.getAxisAt(Coordinate.LEFT_AXIS);
      DefaultAxis y2 = coord.getAxisAt(Coordinate.RIGHT_AXIS);

      assertTrue(y1 == null || y1.getZIndex() < 0,
                 "left y axis must not be drawn once its labels moved right; regression: both "
                    + "the left and right axis lines are visible");
      assertNotNull(y2, "right y axis must exist");
      assertTrue(y2.getZIndex() >= 0, "right y axis must be drawn");
      assertTrue(y2.isLabelVisible(), "right y axis must carry the labels");
   }

   @Test
   void xAxisMovesToTheTop() {
      RectCoord coord = coord(false, true);
      DefaultAxis x1 = coord.getAxisAt(Coordinate.BOTTOM_AXIS);
      DefaultAxis x2 = coord.getAxisAt(Coordinate.TOP_AXIS);

      assertTrue(x1 == null || x1.getZIndex() < 0,
                 "bottom x axis must not be drawn once its labels moved to the top");
      assertNotNull(x2, "top x axis must exist");
      assertTrue(x2.getZIndex() >= 0, "top x axis must be drawn");
      assertTrue(x2.isLabelVisible(), "top x axis must carry the labels");
   }

   @Test
   void doubleStyleYKeepsBothLines() {
      EGraph egraph = new EGraph();
      egraph.addElement(new LineElement("Year", "Price"));

      Scale yscale = new LinearScale("Price");
      yscale.getAxisSpec().setAxisStyle(AxisSpec.AXIS_DOUBLE);
      yscale.getAxisSpec().setLabelOnSecondaryAxis(true);

      RectCoord coord = new RectCoord(new CategoricalScale("Year"), yscale);
      egraph.setCoordinate(coord);
      assertNotNull(Plotter.getPlotter(egraph).plotAndLayout(data(), 0, 0, 800, 600));

      DefaultAxis y1 = coord.getAxisAt(Coordinate.LEFT_AXIS);
      DefaultAxis y2 = coord.getAxisAt(Coordinate.RIGHT_AXIS);

      assertNotNull(y1, "double style must keep the primary axis");
      assertTrue(y1.getZIndex() >= 0, "double style must keep the left axis line");
      assertNotNull(y2, "double style must keep the secondary axis");
      assertTrue(y2.getZIndex() >= 0, "double style must keep the right axis line");
      assertTrue(y2.isLabelVisible(), "labels move to the right on double style too");
   }

   @Test
   void plainSingleStyleKeepsLabelsInPlace() {
      RectCoord coord = coord(false, false);
      DefaultAxis y1 = coord.getAxisAt(Coordinate.LEFT_AXIS);
      DefaultAxis y2 = coord.getAxisAt(Coordinate.RIGHT_AXIS);

      assertNotNull(y1, "left y axis must be drawn when labels are not relocated");
      assertTrue(y1.getZIndex() >= 0, "left y axis must be drawn");
      assertTrue(y1.isLabelVisible(), "left y axis must carry the labels");
      assertTrue(y2 == null || y2.getZIndex() < 0, "single style must not draw a right axis");
   }

   private static RectCoord coord(boolean yOpposite, boolean xOpposite) {
      EGraph egraph = new EGraph();
      egraph.addElement(new LineElement("Year", "Price"));

      Scale xscale = new CategoricalScale("Year");
      xscale.getAxisSpec().setAxisStyle(AxisSpec.AXIS_SINGLE);
      xscale.getAxisSpec().setLabelOnSecondaryAxis(xOpposite);

      Scale yscale = new LinearScale("Price");
      yscale.getAxisSpec().setAxisStyle(AxisSpec.AXIS_SINGLE);
      yscale.getAxisSpec().setLabelOnSecondaryAxis(yOpposite);

      RectCoord coord = new RectCoord(xscale, yscale);
      egraph.setCoordinate(coord);

      assertNotNull(Plotter.getPlotter(egraph).plotAndLayout(data(), 0, 0, 800, 600),
                    "Plotter.plotAndLayout must produce a vgraph for the test fixture");
      return coord;
   }

   private static DefaultDataSet data() {
      return new DefaultDataSet(new Object[][] {
         { "Year",  "Price" },
         { "2022",  400000.0 },
         { "2023",  450000.0 },
         { "2024",  420000.0 },
         { "2025",  480000.0 },
      });
   }
}
