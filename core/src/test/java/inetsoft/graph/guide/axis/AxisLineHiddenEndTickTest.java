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

import inetsoft.graph.*;
import inetsoft.graph.coord.RectCoord;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.element.PointElement;
import inetsoft.graph.internal.ICoordinate;
import inetsoft.graph.scale.LinearScale;
import inetsoft.test.*;
import inetsoft.util.graphics.SVGSupport;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/**
 * Bug #82028: with the max tick hidden, the minor tick at the max value was still painted
 * at the hidden tick's position.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class AxisLineHiddenEndTickTest {
   private static final int WIDTH = 300;
   private static final int HEIGHT = 400;

   /**
    * Paints the left axis line of a 0..100 chart with a major increment of 25, with the
    * default minor increment (null) and with an explicit one (12.5).
    */
   @ParameterizedTest
   @NullSource
   @ValueSource(doubles = { 12.5 })
   void hiddenEndTickLeavesNoMinorTickAtItsRow(Double minorIncrement) {
      SortedMap<Integer, Integer> visible = paintTickRows(minorIncrement, true, true);
      SortedMap<Integer, Integer> maxHidden = paintTickRows(minorIncrement, false, true);
      SortedMap<Integer, Integer> minHidden = paintTickRows(minorIncrement, true, false);

      int majorLength = Collections.max(visible.values());
      assertTrue(visible.containsValue(AxisLine.MINOR_TICK_LENGTH),
                 "minor ticks should be painted: " + visible);

      // hiding the min tick removes exactly the row of the min major tick
      Set<Integer> minRows = new TreeSet<>(visible.keySet());
      minRows.removeAll(minHidden.keySet());
      assertEquals(1, minRows.size(), "visible=" + visible + " minHidden=" + minHidden);
      int minRow = minRows.iterator().next();
      assertEquals(majorLength, visible.get(minRow));
      assertEquals(without(visible, minRow), minHidden);

      // the max major tick is at the other end of the axis
      int maxRow = minRow == visible.firstKey() ? visible.lastKey() : visible.firstKey();
      assertEquals(majorLength, visible.get(maxRow), "visible=" + visible);

      assertFalse(maxHidden.containsKey(maxRow),
                  "a tick is painted at the hidden max tick row " + maxRow + ": " + maxHidden);
      assertEquals(without(visible, maxRow), maxHidden);
   }

   private static SortedMap<Integer, Integer> without(SortedMap<Integer, Integer> rows, int row) {
      SortedMap<Integer, Integer> result = new TreeMap<>(rows);
      result.remove(row);
      return result;
   }

   /**
    * Paints the left axis line and returns, for each image row, the number of painted pixels
    * outside the axis line column (the tick length at that row).
    */
   private static SortedMap<Integer, Integer> paintTickRows(Double minorIncrement,
                                                            boolean maxVisible,
                                                            boolean minVisible)
   {
      DefaultAxis axis = leftAxis(minorIncrement);
      axis.setMaxTickVisible(maxVisible);
      axis.setMinTickVisible(minVisible);
      axis.setTickDown(true);

      BufferedImage img = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = img.createGraphics();

      try(MockedStatic<SVGSupport> svgSupport = mockStatic(SVGSupport.class)) {
         svgSupport.when(SVGSupport::getInstance).thenReturn(mock(SVGSupport.class));
         axis.getAxisLine().paint(g);
      }
      finally {
         g.dispose();
      }

      int lineColumn = -1;
      int lineCount = -1;

      for(int x = 0; x < WIDTH; x++) {
         int count = 0;

         for(int y = 0; y < HEIGHT; y++) {
            if(painted(img, x, y)) {
               count++;
            }
         }

         if(count > lineCount) {
            lineCount = count;
            lineColumn = x;
         }
      }

      SortedMap<Integer, Integer> rows = new TreeMap<>();

      for(int y = 0; y < HEIGHT; y++) {
         int length = 0;

         for(int x = 0; x < WIDTH; x++) {
            if(x != lineColumn && painted(img, x, y)) {
               length++;
            }
         }

         if(length > 0) {
            rows.put(y, length);
         }
      }

      return rows;
   }

   private static boolean painted(BufferedImage img, int x, int y) {
      return (img.getRGB(x, y) >>> 24) != 0;
   }

   private static DefaultAxis leftAxis(Double minorIncrement) {
      Object[][] rows = { { "x", "y" }, { "a", 10 }, { "b", 90 } };
      EGraph graph = new EGraph();
      graph.addElement(new PointElement("x", "y"));
      LinearScale yscale = new LinearScale("y");
      yscale.setMin(0);
      yscale.setMax(100);
      yscale.setIncrement(25);

      if(minorIncrement != null) {
         yscale.setMinorIncrement(minorIncrement);
      }

      graph.setScale("y", yscale);
      VGraph vgraph = Plotter.getPlotter(graph)
         .plotAndLayout(new DefaultDataSet(rows), 0, 0, WIDTH, HEIGHT);
      return ((RectCoord) vgraph.getCoordinate()).getAxisAt(ICoordinate.LEFT_AXIS);
   }
}
