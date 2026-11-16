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
package inetsoft.graph.guide.axis;

import inetsoft.graph.AxisSpec;
import inetsoft.graph.EGraph;
import inetsoft.graph.Plotter;
import inetsoft.graph.VGraph;
import inetsoft.graph.coord.RectCoord;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.element.IntervalElement;
import inetsoft.graph.guide.VLabel;
import inetsoft.graph.internal.GTool;
import inetsoft.graph.scale.CategoricalScale;
import inetsoft.graph.scale.LinearScale;
import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The axis label length limit: labels are cut with an ellipsis at the limit and thinned
 * without overlap, and a classic axis (no limit) is unchanged.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ModernAxisLabelTest {
   private static final String LONG = "Northeast Regional Distribution Hub";
   private static final double CLASSIC_SMALL_45_WIDTH = 103;
   private static final List<Integer> CLASSIC_STORES_SHOWN =
      java.util.stream.IntStream.range(0, 30).boxed().toList();

   @AfterEach
   void clearProperties() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void aLimitedLabelEndsInAnEllipsisAt66px() {
      DefaultAxis axis = xAxis(eightWithLong(), 66, 0, 0, 800, 300);
      VLabel[] labels = axis.getLabels();

      assertTrue(text(labels[3]).endsWith("…"), text(labels[3]));
      assertTrue(width(labels[3]) <= 66 + 0.5, "width " + width(labels[3]));
      assertEquals("Store 1", text(labels[0]));
   }

   @Test
   void theLimitHoldsOnASmallCard() {
      DefaultAxis axis = xAxis(eightWithLong(), 66, 0, 0, 300, 180);
      VLabel label = axis.getLabels()[3];

      assertTrue(text(label).endsWith("…"), text(label));
      assertTrue(width(label) <= 66 + 0.5, "width " + width(label));
   }

   @Test
   void theLimitAppliesAt45Degrees() {
      DefaultAxis axis = xAxis(eightWithLong(), 66, 45, 0, 800, 300);
      VLabel label = axis.getLabels()[3];
      FontMetrics fm = GTool.getFontMetrics(label.getFont());

      assertTrue(text(label).endsWith("…"), text(label));
      assertTrue(width(label) <= 66 + 0.5, "width " + width(label));

      double extent = (66 + fm.getHeight()) * Math.sin(Math.PI / 4);
      assertEquals(extent, label.getBounds().getHeight(), 2, "rotated label extent");
      assertTrue(axis.getBounds().getHeight() >= extent - 2, "axis height");
   }

   @Test
   void limitedLabelsNeverOverlap() {
      DefaultAxis axis = xAxis(stores(30), 66, 0, 0, 400, 240);
      List<Rectangle2D> shown = shownBounds(axis);

      assertTrue(shown.size() > 1);

      for(int i = 0; i < shown.size() - 1; i++) {
         for(int j = i + 1; j < shown.size(); j++) {
            assertFalse(shown.get(i).intersects(shown.get(j)),
                        "labels " + i + " and " + j + " overlap " + shown + " " + shownIndices(axis));
         }
      }
   }

   @Test
   void limitedLabelsNeverOverlapAt45Degrees() {
      DefaultAxis axis = xAxis(stores(30), 66, 45, 0, 400, 240);
      List<Integer> idx = shownIndices(axis);
      List<java.awt.geom.Area> areas = new ArrayList<>();

      for(int i : idx) {
         VLabel label = axis.getLabels()[i];
         Font font = label.getFont();
         double x = label.getTextPosition().getX();
         double y = label.getTextPosition().getY();
         Rectangle2D box = new Rectangle2D.Double(
            x, y, GTool.stringWidth(label.getDisplayLabel(), font),
            GTool.getFontMetrics(font).getHeight());
         areas.add(new java.awt.geom.Area(java.awt.geom.AffineTransform
            .getRotateInstance(Math.toRadians(45), x, y).createTransformedShape(box)));
      }

      assertTrue(idx.size() > 1, "shown " + idx);

      for(int i = 0; i < areas.size() - 1; i++) {
         for(int j = i + 1; j < areas.size(); j++) {
            java.awt.geom.Area a = (java.awt.geom.Area) areas.get(i).clone();
            a.intersect(areas.get(j));
            assertTrue(a.isEmpty(), "labels " + idx.get(i) + " and " + idx.get(j) + " overlap");
         }
      }

      int step = idx.get(1) - idx.get(0);

      for(int i = 1; i < idx.size(); i++) {
         assertEquals(step, idx.get(i) - idx.get(i - 1), "shown " + idx);
      }
   }

   @Test
   void shownLabelsKeepARegularStep() {
      DefaultAxis axis = xAxis(stores(30), 66, 0, 0, 400, 240);
      List<Integer> idx = shownIndices(axis);

      assertTrue(idx.size() > 1, "shown " + idx);
      int step = idx.get(1) - idx.get(0);

      for(int i = 1; i < idx.size(); i++) {
         assertEquals(step, idx.get(i) - idx.get(i - 1), "shown " + idx);
      }
   }

   @Test
   void aFixedAxisSizeBeatsTheLimit() {
      double auto = xAxis(eightWithLong(), 66, 45, 0, 800, 300).getBounds().getHeight();
      double fixed = xAxis(eightWithLong(), 66, 45, 30, 800, 300).getBounds().getHeight();

      assertEquals(30, fixed, 0.5);
      assertTrue(auto > 30 + 5, "auto " + auto);
   }

   @Test
   void classicLabelsKeepTwoDotsAndTheFloor() {
      VLabel wide = xAxis(eightWithLong(), 0, 0, 0, 800, 300).getLabels()[3];
      assertTrue(text(wide).endsWith(".."), text(wide));
      assertFalse(text(wide).contains("…"));

      VLabel small = xAxis(eightWithLong(), 0, 45, 0, 300, 180).getLabels()[3];
      assertTrue(text(small).endsWith(".."), text(small));
      assertEquals(CLASSIC_SMALL_45_WIDTH, width(small), 0.5);
   }

   @Test
   void classicCategoryAxisStillToleratesOverlap() {
      DefaultAxis axis = xAxis(stores(30), 0, 0, 0, 400, 240);
      assertEquals(CLASSIC_STORES_SHOWN, shownIndices(axis));
   }

   private static Object[][] stores(int n) {
      Object[][] rows = new Object[n + 1][];
      rows[0] = new Object[] { "Name", "Sales" };

      for(int i = 1; i <= n; i++) {
         rows[i] = new Object[] { String.format("Store %02d", i), i * 100 };
      }

      return rows;
   }

   private static Object[][] eightWithLong() {
      Object[][] rows = new Object[9][];
      rows[0] = new Object[] { "Name", "Sales" };

      for(int i = 1; i <= 8; i++) {
         rows[i] = new Object[] { i == 4 ? LONG : "Store " + i, i * 100 };
      }

      return rows;
   }

   private static DefaultAxis xAxis(Object[][] rows, double limit, int rotation,
                                    double axisSize, int width, int height)
   {
      CategoricalScale xscale = new CategoricalScale("Name");
      AxisSpec spec = xscale.getAxisSpec();
      spec.setLabelLimit(limit);
      spec.getTextSpec().setRotation(rotation);
      spec.setAxisSize(axisSize);

      EGraph graph = new EGraph();
      graph.addElement(new IntervalElement("Name", "Sales"));
      graph.setCoordinate(new RectCoord(xscale, new LinearScale("Sales")));
      VGraph vgraph = Plotter.getPlotter(graph)
         .plotAndLayout(new DefaultDataSet(rows), 0, 0, width, height);
      DefaultAxis axis = ((RectCoord) vgraph.getCoordinate()).getXAxis1();
      axis.prepareLabels();
      return axis;
   }

   private static String text(VLabel label) {
      return String.join("", label.getDisplayLabel());
   }

   private static double width(VLabel label) {
      return GTool.stringWidth(label.getDisplayLabel(), label.getFont());
   }

   private static List<Integer> shownIndices(DefaultAxis axis) {
      List<Integer> idx = new ArrayList<>();
      VLabel[] labels = axis.getLabels();

      for(int i = 0; i < labels.length; i++) {
         if(labels[i].getZIndex() >= 0) {
            idx.add(i);
         }
      }

      return idx;
   }

   private static List<Rectangle2D> shownBounds(DefaultAxis axis) {
      List<Rectangle2D> list = new ArrayList<>();

      for(int i : shownIndices(axis)) {
         VLabel label = axis.getLabels()[i];
         Font font = label.getFont();
         list.add(new Rectangle2D.Double(label.getTextPosition().getX(),
                                         label.getTextPosition().getY(),
                                         GTool.stringWidth(label.getDisplayLabel(), font),
                                         GTool.getFontMetrics(font).getHeight()));
      }

      return list;
   }
}
