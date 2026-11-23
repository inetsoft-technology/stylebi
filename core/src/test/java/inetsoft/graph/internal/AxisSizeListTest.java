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
package inetsoft.graph.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/**
 * Sizes of the axes shared by a group of coordinates (AxisSizeList and AxisSizeStrategy). The
 * coordinates are stubs; no layout runs.
 */
@Tag("core")
class AxisSizeListTest {
   private static ICoordinate coord(boolean visible, double min, double pref, double size) {
      ICoordinate coord = mock(ICoordinate.class);
      when(coord.isAxisLabelVisible(anyInt())).thenReturn(visible);
      when(coord.getAxisMinSize(anyInt())).thenReturn(min);
      when(coord.getAxisPreferredSize(anyInt())).thenReturn(pref);
      when(coord.getAxisSize(anyInt())).thenReturn(size);
      return coord;
   }

   private static AxisSizeList list(ICoordinate... coords) {
      AxisSizeList list = new AxisSizeList(new AxisSizeStrategy(ICoordinate.LEFT_AXIS));

      for(ICoordinate coord : coords) {
         list.add(coord);
      }

      return list;
   }

   @Test
   @DisplayName("add keeps only coordinates whose axis labels are visible")
   void addSkipsCoordinatesWithHiddenAxis() {
      AxisSizeList list = list(coord(true, 1, 2, 0), coord(false, 1, 2, 0), coord(true, 1, 2, 0));

      assertEquals(2, list.getCount());
   }

   @Test
   @DisplayName("min size is the largest min size of the coordinates")
   void minSizeIsMaximum() {
      AxisSizeList list = list(coord(true, 10, 50, 0), coord(true, 30, 50, 0),
                               coord(true, 20, 50, 0));

      assertEquals(30, list.getMinSize(), 1e-9);
   }

   @Test
   @DisplayName("preferred size is the largest preferred size of the coordinates")
   void preferredSizeIsMaximum() {
      AxisSizeList list = list(coord(true, 1, 12, 0), coord(true, 1, 25, 0),
                               coord(true, 1, 7, 0));

      assertEquals(25, list.getPreferredSize(), 1e-9);
   }

   @Test
   @DisplayName("empty list has zero min and preferred size and zero count")
   void emptyList() {
      AxisSizeList list = list();

      assertEquals(0, list.getMinSize(), 1e-9);
      assertEquals(0, list.getPreferredSize(), 1e-9);
      assertEquals(0, list.getCount());
   }

   @Test
   @DisplayName("setSize assigns the size once to every coordinate with the strategy axis")
   void setSizeReachesEveryCoordinate() {
      ICoordinate a = coord(true, 1, 2, 0);
      ICoordinate b = coord(true, 1, 2, 0);
      ICoordinate c = coord(true, 1, 2, 0);
      AxisSizeList list = list(a, b, c);

      list.setSize(42);

      verify(a, times(1)).setAxisSize(ICoordinate.LEFT_AXIS, 42);
      verify(b, times(1)).setAxisSize(ICoordinate.LEFT_AXIS, 42);
      verify(c, times(1)).setAxisSize(ICoordinate.LEFT_AXIS, 42);
   }

   @Test
   @DisplayName("getSize reports the size of the first coordinate")
   void sizeIsFirstCoordinate() {
      AxisSizeList list = list(coord(true, 1, 2, 5), coord(true, 1, 2, 9));

      assertEquals(5, list.getSize(), 1e-9);
   }

   @Test
   @DisplayName("sizes are cached until invalidate")
   void sizesCachedUntilInvalidate() {
      ICoordinate coord = coord(true, 30, 40, 0);
      AxisSizeList list = list(coord);

      assertEquals(30, list.getMinSize(), 1e-9);
      assertEquals(40, list.getPreferredSize(), 1e-9);

      when(coord.getAxisMinSize(anyInt())).thenReturn(50.0);
      when(coord.getAxisPreferredSize(anyInt())).thenReturn(70.0);

      assertEquals(30, list.getMinSize(), 1e-9);
      assertEquals(40, list.getPreferredSize(), 1e-9);

      list.invalidate();

      assertEquals(50, list.getMinSize(), 1e-9);
      assertEquals(70, list.getPreferredSize(), 1e-9);
   }

   @ParameterizedTest
   @ValueSource(longs = { 1, 2, 3, 42, 2026 })
   @DisplayName("list min size never exceeds the list preferred size")
   void minNotAbovePreferred(long seed) {
      Random random = new Random(seed);
      ICoordinate[] coords = new ICoordinate[8];

      for(int i = 0; i < coords.length; i++) {
         double min = random.nextInt(100);
         double pref = min + random.nextInt(100);
         coords[i] = coord(true, min, pref, 0);
      }

      AxisSizeList list = list(coords);

      assertTrue(list.getMinSize() <= list.getPreferredSize());
   }

   @ParameterizedTest
   @ValueSource(ints = { ICoordinate.TOP_AXIS, ICoordinate.LEFT_AXIS, ICoordinate.BOTTOM_AXIS,
                         ICoordinate.RIGHT_AXIS })
   @DisplayName("strategy forwards every call to the coordinate with its own axis")
   void strategyDelegates(int axis) {
      AxisSizeStrategy strategy = new AxisSizeStrategy(axis);
      ICoordinate coord = coord(true, 3, 4, 5);

      assertEquals(axis, strategy.getAxis());
      assertEquals(3, strategy.getMinSize(coord), 1e-9);
      assertEquals(4, strategy.getPreferredSize(coord), 1e-9);
      assertEquals(5, strategy.getSize(coord), 1e-9);
      assertTrue(strategy.isVisible(coord));
      strategy.setSize(coord, 6);

      verify(coord).getAxisMinSize(axis);
      verify(coord).getAxisPreferredSize(axis);
      verify(coord).getAxisSize(axis);
      verify(coord).isAxisLabelVisible(axis);
      verify(coord).setAxisSize(axis, 6);
      verifyNoMoreInteractions(coord);
   }
}
