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
package inetsoft.report.composition.region;

import inetsoft.graph.GraphConstants;
import inetsoft.graph.LegendSpec;
import inetsoft.graph.TextSpec;
import inetsoft.graph.aesthetic.VisualFrame;
import inetsoft.graph.guide.legend.Legend;
import inetsoft.report.internal.RectangleRegion;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies the asymmetric insets in LegendTitleArea: getRegion() insets only
 * width (for layout/SVG sizing); getRegions()[0] also insets height bottom
 * (for the selection rect).
 */
@Tag("core")
class LegendTitleAreaTest {
   private static final double TITLE_WIDTH = 116;
   private static final double TITLE_HEIGHT_RAW = 19;  // titleh + TITLE_LINE_GAP

   @Test
   void thinBorderInsetsBy1() {
      assertInsets(GraphConstants.THIN_LINE, 1);
   }

   @Test
   void mediumBorderInsetsBy2() {
      assertInsets(GraphConstants.MEDIUM_LINE, 2);
   }

   @Test
   void thickBorderInsetsBy3() {
      assertInsets(GraphConstants.THICK_LINE, 3);
   }

   @Test
   void zeroBorderInsetsByZero() {
      assertInsets(GraphConstants.NONE, 0);
   }

   private void assertInsets(int borderStyle, double expectedLw) {
      LegendTitleArea area = newTitleArea(borderStyle);

      Rectangle2D layoutBounds = ((RectangleRegion) area.getRegion()).getBounds();
      assertEquals(TITLE_WIDTH - 2 * expectedLw, layoutBounds.getWidth(), "layout width");
      assertEquals(TITLE_HEIGHT_RAW, layoutBounds.getHeight(), "layout height");

      Rectangle2D selectionBounds = ((RectangleRegion) area.getRegions()[0]).getBounds();
      assertEquals(TITLE_WIDTH - 2 * expectedLw, selectionBounds.getWidth(), "selection width");
      assertEquals(TITLE_HEIGHT_RAW - (1 + expectedLw), selectionBounds.getHeight(),
                   "selection height");
   }

   private LegendTitleArea newTitleArea(int borderStyle) {
      // Identity transform → only (w, h) matter for inset checks.
      Rectangle2D titleBounds = new Rectangle2D.Double(0, 0, TITLE_WIDTH, TITLE_HEIGHT_RAW);

      TextSpec titleTextSpec = mock(TextSpec.class);
      when(titleTextSpec.getBackground()).thenReturn(null);

      LegendSpec spec = mock(LegendSpec.class);
      when(spec.getBorder()).thenReturn(borderStyle);
      when(spec.getTitleTextSpec()).thenReturn(titleTextSpec);

      VisualFrame frame = mock(VisualFrame.class);
      when(frame.getLegendSpec()).thenReturn(spec);
      when(frame.getTitle()).thenReturn("Title");
      when(frame.getField()).thenReturn("field");

      Legend legend = mock(Legend.class);
      when(legend.getTitleBounds()).thenReturn(titleBounds);
      when(legend.getVisualFrame()).thenReturn(frame);
      when(legend.getBounds()).thenReturn(titleBounds);
      when(legend.getGraphElement()).thenReturn(null);

      return new LegendTitleArea(
         legend, java.util.Collections.emptyList(), false,
         new AffineTransform(), new IndexedSet<>());
   }
}
