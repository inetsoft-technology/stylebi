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
package inetsoft.report.composition.region;

import inetsoft.graph.guide.legend.Legend;
import inetsoft.graph.internal.GTool;
import inetsoft.report.internal.RectangleRegion;
import inetsoft.report.internal.Region;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.List;

/**
 * ScalarLegendContentArea defines the method of write data to an OutputStream
 *  and parse it from an InputStream.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class ScalarLegendContentArea extends LegendContentArea implements RollOverArea {
   /**
    * Constructor.
    */
   public ScalarLegendContentArea(Legend legend, List<String> targetFields,
                                  boolean sharedColor, AffineTransform trans,
                                  IndexedSet<String> palette)
   {
      super(legend, targetFields, sharedColor, trans, palette);
   }

   /**
    * Get the first region, used for sizing the canvas div.
    * Returns the full content bounds so the div has room for the selection stroke.
    */
   @Override
   public Region getRegion() {
      Rectangle2D bounds = ((Legend) vobj).getContentBounds();
      Rectangle2D.Double rect2d = (Rectangle2D.Double) GTool.transform(bounds, trans);
      Point2D p = getRelPos();
      rect2d.x -= p.getX();
      rect2d.y -= p.getY();
      return new RectangleRegion(rect2d);
   }

   /**
    * Get regions for selection highlighting.
    * Insets the content bounds by the canvas lineWidth (2px) on each side so the
    * centered 2px stroke does not overflow the canvas div boundary.
    */
   @Override
   public Region[] getRegions() {
      Rectangle2D bounds = ((Legend) vobj).getContentBounds();
      Rectangle2D.Double rect2d = (Rectangle2D.Double) GTool.transform(bounds, trans);
      Point2D p = getRelPos();
      rect2d.x = rect2d.x - p.getX() + SELECTION_INSET;
      rect2d.y = rect2d.y - p.getY() + SELECTION_INSET;
      rect2d.width -= SELECTION_INSET * 2;
      rect2d.height -= SELECTION_INSET * 2;

      return new Region[] {new RectangleRegion(rect2d)};
   }

   /**
    * Paint area.
    * @param g the graphic of the area.
    */
   @Override
   public void paintArea(Graphics g, Color color) {
      RectangleRegion region = (RectangleRegion) getRegion();

      if(region != null) {
         region.fill(g, color);
      }
   }

   // Half the canvas lineWidth: the 2px stroke is centered on the path, so it extends
   // 1px outside on each side. We need 2px inset so the stroke fits within the canvas div.
   private static final int SELECTION_INSET = 2;
}
