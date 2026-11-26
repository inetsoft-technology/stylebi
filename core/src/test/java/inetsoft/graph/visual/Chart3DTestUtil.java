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
package inetsoft.graph.visual;

import inetsoft.graph.VGraph;
import inetsoft.graph.Visualizable;
import inetsoft.graph.internal.GDefaults;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

/**
 * Paints a chart whole and in tiles the way VGraphPair.getSubimage(VGraph, ...) does, to check
 * that the tile clip culling (VGraph.paintVisualizables -> insideClip) keeps every painted pixel.
 */
final class Chart3DTestUtil {
   /** ChartArea.MAX_IMAGE_SIZE: plot areas larger than this are painted in tiles. */
   static final int TILE = 1024;
   private static final int SWEEP_STEP = 64;
   // ignore low alpha anti-alias pixels at a clip edge
   private static final int MIN_ALPHA = 0x40;

   private Chart3DTestUtil() {
   }

   /** Find the visual objects of a type in a graph, including the sub-graphs of a facet. */
   static <T> List<T> collect(VGraph vg, Class<T> type) {
      List<T> list = new ArrayList<>();
      collect(vg, type, list, Collections.newSetFromMap(new IdentityHashMap<>()));
      return list;
   }

   private static <T> void collect(VGraph vg, Class<T> type, List<T> list, Set<Object> seen) {
      if(vg == null || !seen.add(vg)) {
         return;
      }

      for(int i = 0; i < vg.getVisualCount(); i++) {
         Visualizable v = vg.getVisual(i);

         if(type.isInstance(v) && seen.add(v)) {
            list.add(type.cast(v));
         }
         // a facet holds each sub-graph in a GraphVO
         else if(v instanceof GraphVO) {
            collect(((GraphVO) v).getVGraph(), type, list, seen);
         }
      }
   }

   /** Map a box in chart coordinates to image coordinates (VGraph.paintGraph flips y). */
   static Rectangle2D toImage(VGraph vg, Rectangle2D chart) {
      Rectangle2D b = vg.getBounds();
      AffineTransform t = new AffineTransform();
      t.translate(0, b.getHeight() - 1 + 2 * b.getY());
      t.concatenate(GDefaults.FLIPY);
      return t.createTransformedShape(chart).getBounds2D();
   }

   /** The box of the pixels painted by one visual object, in image coordinates. */
   static Rectangle paintedBox(VGraph vg, Visualizable vo, int w, int h) {
      BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = img.createGraphics();
      Rectangle2D b = vg.getBounds();
      g.translate(0, b.getHeight() - 1 + 2 * b.getY());
      g.transform(GDefaults.FLIPY);
      vo.paint(g);
      g.dispose();

      int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, x1 = -1, y1 = -1;

      for(int y = 0; y < h; y++) {
         for(int x = 0; x < w; x++) {
            if((img.getRGB(x, y) >>> 24) != 0) {
               x0 = Math.min(x0, x);
               y0 = Math.min(y0, y);
               x1 = Math.max(x1, x);
               y1 = Math.max(y1, y);
            }
         }
      }

      return x1 < 0 ? null : new Rectangle(x0, y0, x1 - x0 + 1, y1 - y0 + 1);
   }

   static BufferedImage paintFull(VGraph vg, int w, int h) {
      BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = img.createGraphics();
      vg.paintGraph(g, true);
      g.dispose();
      return img;
   }

   /** The VGraphPair.getSubimage(VGraph, ...) sequence. */
   static BufferedImage paintTile(VGraph vg, Rectangle tile) {
      BufferedImage img = new BufferedImage(tile.width, tile.height, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = img.createGraphics();
      g.clipRect(0, 0, tile.width + 1, tile.height + 1);
      g.translate(-tile.getX(), -tile.getY());
      vg.paintGraph(g, true);
      g.dispose();
      return img;
   }

   /** Count the pixels painted in the whole chart that the tile leaves empty. */
   static int missingPixels(VGraph vg, int w, int h, Rectangle tile) {
      return missingPixels(paintFull(vg, w, h), paintTile(vg, tile), tile);
   }

   private static int missingPixels(BufferedImage full, BufferedImage img, Rectangle tile) {
      int n = 0;

      for(int y = 0; y < img.getHeight(); y++) {
         for(int x = 0; x < img.getWidth(); x++) {
            int fx = tile.x + x, fy = tile.y + y;

            if(fx < full.getWidth() && fy < full.getHeight() &&
               (full.getRGB(fx, fy) >>> 24) > MIN_ALPHA && (img.getRGB(x, y) >>> 24) == 0)
            {
               n++;
            }
         }
      }

      return n;
   }

   /**
    * Paint the chart in 1024 px tiles, moving the tile grid along x and then along y, and
    * return the number of tiles that lost pixels painted in the whole chart.
    */
   static int sweepTiles(VGraph vg, int w, int h) {
      BufferedImage full = paintFull(vg, w, h);
      int bad = 0;

      for(int axis = 0; axis < 2; axis++) {
         int size = axis == 0 ? w : h;

         if(size <= TILE) {
            continue;
         }

         for(int off = 0; off < TILE; off += SWEEP_STEP) {
            for(int start = -off; start < size; start += TILE) {
               int s0 = Math.max(0, start);
               int s1 = Math.min(size, start + TILE);

               if(s1 <= s0) {
                  continue;
               }

               Rectangle tile = axis == 0 ? new Rectangle(s0, 0, s1 - s0, h)
                  : new Rectangle(0, s0, w, s1 - s0);

               if(missingPixels(full, paintTile(vg, tile), tile) > 0) {
                  bad++;
               }
            }
         }
      }

      return bad;
   }
}
