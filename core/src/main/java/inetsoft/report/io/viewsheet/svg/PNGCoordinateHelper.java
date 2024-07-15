/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.report.io.viewsheet.svg;

import inetsoft.report.internal.Common;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Coordinate helper used when exporting as PNG.
 *
 * @since 12.1
 */
public class PNGCoordinateHelper extends SVGCoordinateHelper {
   /**
    * Once the width and height of the PNG are known, set up the buffered image.
    * @param width  of the png
    * @param height of the png
    */
   protected void init(int width, int height) {
      if(bufferedImage != null) {
         getGraphics().dispose();
         images.add(bufferedImage);
      }

      bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
      Graphics2D graphics = bufferedImage.createGraphics();
      graphics.setRenderingHint(Common.EXPORT_GRAPHICS, Common.VALUE_EXPORT_GRAPHICS_ON);
      graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      graphics.setRenderingHint(
         RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_GASP);
      setGraphics(graphics);
   }

   /**
    * Writes the PNG image.
    *
    * @param output the output stream to which to write the image.
    *
    * @throws IOException if an I/O error occurs.
    */
   public void write(OutputStream output) throws IOException {
      getGraphics().dispose();

      // combine images into one
      if(images.size() > 0) {
         images.add(bufferedImage);

         int w = 0;
         int h = 0;

         for(BufferedImage img : images) {
            w = Math.max(w, img.getWidth());
            h += img.getHeight();
         }

         bufferedImage = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
         Graphics2D g = bufferedImage.createGraphics();
         int y = 0;

         for(BufferedImage img : images) {
            g.drawImage(img, 0, y, null);
            y += img.getHeight();
         }
      }

      RenderedImage renderedImage = bufferedImage;
      ImageIO.write(renderedImage, "png", output);
   }

   private BufferedImage bufferedImage;
   private List<BufferedImage> images = new ArrayList<>();
}
