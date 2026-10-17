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
package inetsoft.graph;

import inetsoft.graph.internal.GTool;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.awt.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78735: plot background alpha is 0-1. An out-of-range value used to be stored by the setter
 * and then fail (or silently misrender) the chart when the plot background was painted.
 */
@Tag("core")
class PlotSpecAlphaTest {
   @ParameterizedTest
   @ValueSource(doubles = { -0.5, -1, 1.5, 50, Double.NaN, Double.POSITIVE_INFINITY })
   void setAlphaRejectsOutOfRange(double alpha) {
      PlotSpec spec = new PlotSpec();
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                                                 () -> spec.setAlpha(alpha));
      assertTrue(ex.getMessage().contains("0 to 1"), ex.getMessage());
      assertEquals(1, spec.getAlpha());
   }

   @ParameterizedTest
   @ValueSource(doubles = { 0, 0.5, 1 })
   void setAlphaAcceptsTheValidRange(double alpha) {
      PlotSpec spec = new PlotSpec();
      spec.setAlpha(alpha);
      assertEquals(alpha, spec.getAlpha());
   }

   @ParameterizedTest
   @ValueSource(doubles = { -0.5, -100, 1.5, 50, Double.NaN })
   void paintingWithAnOutOfRangeAlphaDoesNotThrow(double alpha) {
      Color base = new Color(10, 20, 30);

      assertDoesNotThrow(() -> GTool.applyAlpha(base, alpha));
      assertDoesNotThrow(() -> GTool.getColor(base, alpha));
   }

   @Test
   void outOfRangeAlphaIsClampedWhenPainting() {
      Color base = new Color(10, 20, 30);

      assertEquals(0, GTool.applyAlpha(base, -0.5).getAlpha());
      assertEquals(255, GTool.applyAlpha(base, 50).getAlpha());
      assertEquals(255, GTool.applyAlpha(base, Double.NaN).getAlpha());
      assertEquals(0, GTool.getColor(base, -0.5).getAlpha());
      assertEquals(255, GTool.getColor(base, 50).getAlpha());
      assertEquals(255, GTool.getColor(base, Double.NaN).getAlpha());
      assertEquals(127, GTool.applyAlpha(base, 0.5).getAlpha());
   }
}
