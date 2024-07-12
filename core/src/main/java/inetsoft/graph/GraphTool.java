/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.graph;

import inetsoft.graph.internal.GTool;
import inetsoft.util.ObjectWrapper;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;

/**
 * This class contains public utility methods in the graph package.
 *
 * @version 10.1
 * @author InetSoft Technology
 */
public class GraphTool {
   /**
    * Draw a string on the graphics output. This method should be used to draw
    * a string on graphics objects passed in through graph API.
    */
   public static void drawString(Graphics2D g, String str, double x, double y) {
      GTool.drawString(g, str, x, y);
   }

   /**
    * Draw an image on the graphics output with y-axis reversed, used for Chart.
    */
   public static void drawImage(Graphics2D g, Image img, double x, double y,
                                double w, double h)
   {
      if(img instanceof ObjectWrapper) {
         img = (Image) ((ObjectWrapper) img).unwrap();
      }

      AffineTransform trans = new AffineTransform();
      trans.translate(x, y + h);
      trans.concatenate(new AffineTransform(1, 0, 0, -1, 0, 0));

      if(w > 0 && h > 0) {
         /*
         int iw = img.getWidth(null);
         int ih = img.getHeight(null);
         trans.scale(w / iw, h / ih);
         */
         img = getScaledInstance(img, (int) w, (int) h);
      }

      if(img instanceof RenderedImage) {
         g.drawRenderedImage((RenderedImage) img, trans);
      }
      else {
         g.drawImage(img, trans, null);
      }
   }

   /**
    * Get high quality scaled image.
    */
   public static Image getScaledInstance(Image img, int targetw, int targeth) {
      int type = BufferedImage.TYPE_INT_ARGB;
      Image ret = img;
      int w = img.getWidth(null);
      int h = img.getHeight(null);

      if(w == targetw && h == targeth) {
         return img;
      }

      do {
         if(w > targetw) {
            w /= 2;

            if(w < targetw) {
               w = targetw;
            }
         }
         else if(w < targetw) {
            w *= 2;

            if(w > targetw) {
               w = targetw;
            }
         }

         if(h > targeth) {
            h /= 2;

            if(h < targeth) {
               h = targeth;
            }
         }
         else if(h < targeth) {
            h *= 2;

            if(h > targeth) {
               h = targeth;
            }
         }

         BufferedImage tmp = new BufferedImage(w, h, type);
         Graphics2D g2 = tmp.createGraphics();
         g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                             RenderingHints.VALUE_INTERPOLATION_BILINEAR);
         g2.drawImage(ret, 0, 0, w, h, null);
         g2.dispose();

         ret = tmp;
      }
      while(w != targetw || h != targeth);

      return ret;
   }
}
