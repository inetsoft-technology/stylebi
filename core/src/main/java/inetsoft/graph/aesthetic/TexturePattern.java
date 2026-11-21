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
package inetsoft.graph.aesthetic;

import java.awt.*;
import java.awt.geom.*;

/**
 * The built-in texture patterns ({@code GTexture.PATTERN_0} to {@code PATTERN_19}). Each is a
 * small tile of horizontal or vertical lines, dots or other symmetric shapes, with no diagonal
 * lines, so a textured bar still reads as upright. The tile is repeated from the bottom-left
 * corner of the shape it fills, so the edges of a bar are flush with the pattern.
 * <p>
 * The same designs are drawn in the portal's texture picker; keep
 * {@code texture-patterns.ts} in step with this file.
 * <p>
 * The order is the order a categorical texture frame assigns the patterns, so neighbouring
 * entries differ as much as possible.
 */
enum TexturePattern {
   H_LINES_FINE(5, 5),
   DOTS_FINE(5, 5),
   V_LINES_FINE(5, 5),
   CHECKER_SMALL(8, 8),
   GRID_FINE(6, 6),
   H_LINES_BOLD(8, 8),
   RINGS(10, 10),
   V_LINES_BOLD(8, 8),
   PLUS_MARKS(10, 10),
   BRICK(12, 8),
   DOTS_LARGE(10, 10),
   SQUARES_OPEN(10, 10),
   CHECKER_LARGE(16, 16),
   TRIANGLES(12, 12),
   GRID_BOLD(12, 12),
   LADDER(10, 8),
   DIAMONDS(10, 10),
   H_THICK_THIN(10, 10),
   V_THICK_THIN(10, 10),
   HALFTONE(12, 12);

   TexturePattern(int width, int height) {
      this.width = width;
      this.height = height;
   }

   /**
    * Get the width of the repeating tile.
    */
   int getTileWidth() {
      return width;
   }

   /**
    * Get the height of the repeating tile.
    */
   int getTileHeight() {
      return height;
   }

   /**
    * Draw one tile in the current color.
    * @param g the graphics, with the stroke set to a 1 pixel line.
    * @param x the left of the tile.
    * @param y the top of the tile.
    */
   void paintTile(Graphics2D g, double x, double y) {
      switch(this) {
      case H_LINES_FINE -> rect(g, x, y, 0, 0, 5, 1);
      case DOTS_FINE -> dot(g, x, y, 2.5, 2.5, 1.1);
      case V_LINES_FINE -> rect(g, x, y, 0, 0, 1, 5);
      case CHECKER_SMALL -> {
         rect(g, x, y, 0, 0, 4, 4);
         rect(g, x, y, 4, 4, 4, 4);
      }
      case GRID_FINE -> {
         rect(g, x, y, 0, 0, 6, 1);
         rect(g, x, y, 0, 0, 1, 6);
      }
      case H_LINES_BOLD -> rect(g, x, y, 0, 0, 8, 3);
      case RINGS -> {
         g.setStroke(new BasicStroke(1.2f));
         g.draw(new Ellipse2D.Double(x + 5 - 2.6, y + 5 - 2.6, 5.2, 5.2));
         g.setStroke(LINE);
      }
      case V_LINES_BOLD -> rect(g, x, y, 0, 0, 3, 8);
      case PLUS_MARKS -> {
         rect(g, x, y, 2, 4.4, 6, 1.2);
         rect(g, x, y, 4.4, 2, 1.2, 6);
      }
      case BRICK -> {
         rect(g, x, y, 0, 0, 12, 1);
         rect(g, x, y, 0, 4, 12, 1);
         rect(g, x, y, 0, 0, 1, 4);
         rect(g, x, y, 6, 4, 1, 4);
      }
      case DOTS_LARGE -> dot(g, x, y, 5, 5, 2.4);
      case SQUARES_OPEN -> {
         g.setStroke(new BasicStroke(1.2f));
         g.draw(new Rectangle2D.Double(x + 1.6, y + 1.6, 6.8, 6.8));
         g.setStroke(LINE);
      }
      case CHECKER_LARGE -> {
         rect(g, x, y, 0, 0, 8, 8);
         rect(g, x, y, 8, 8, 8, 8);
      }
      case TRIANGLES -> polygon(g, x, y, 6, 2, 10, 9, 2, 9);
      case GRID_BOLD -> {
         rect(g, x, y, 0, 0, 12, 2);
         rect(g, x, y, 0, 0, 2, 12);
      }
      case LADDER -> {
         rect(g, x, y, 0, 0, 1, 8);
         rect(g, x, y, 5, 0, 1, 8);
         rect(g, x, y, 0, 3, 6, 1);
      }
      case DIAMONDS -> polygon(g, x, y, 5, 1.6, 8.4, 5, 5, 8.4, 1.6, 5);
      case H_THICK_THIN -> {
         rect(g, x, y, 0, 0, 10, 3);
         rect(g, x, y, 0, 5, 10, 1);
      }
      case V_THICK_THIN -> {
         rect(g, x, y, 0, 0, 3, 10);
         rect(g, x, y, 5, 0, 1, 10);
      }
      case HALFTONE -> {
         dot(g, x, y, 3, 3, 2.2);
         dot(g, x, y, 9, 9, 1);
         dot(g, x, y, 9, 3, 1);
         dot(g, x, y, 3, 9, 1);
      }
      }
   }

   private static void rect(Graphics2D g, double x, double y, double dx, double dy,
                            double w, double h)
   {
      g.fill(new Rectangle2D.Double(x + dx, y + dy, w, h));
   }

   private static void dot(Graphics2D g, double x, double y, double cx, double cy, double r) {
      g.fill(new Ellipse2D.Double(x + cx - r, y + cy - r, 2 * r, 2 * r));
   }

   private static void polygon(Graphics2D g, double x, double y, double... points) {
      Path2D path = new Path2D.Double();

      for(int i = 0; i < points.length; i += 2) {
         if(i == 0) {
            path.moveTo(x + points[i], y + points[i + 1]);
         }
         else {
            path.lineTo(x + points[i], y + points[i + 1]);
         }
      }

      path.closePath();
      g.fill(path);
   }

   static final BasicStroke LINE = new BasicStroke(1f);
   private final int width;
   private final int height;
}
