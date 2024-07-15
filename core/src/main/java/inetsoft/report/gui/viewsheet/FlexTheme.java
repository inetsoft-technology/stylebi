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
package inetsoft.report.gui.viewsheet;

import inetsoft.util.Tool;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;

/**
 * This class provides API for getting information defined through flex theme
 * css files.
 *
 * @version 9.5, 2/4/2008
 * @author InetSoft Technology Corp
 */
public class FlexTheme {
   /**
    * Create a theme.
    * @param theme the color theme, e.g. blue, vista.
    */
   public FlexTheme(String theme) throws Exception {
      this.theme = theme;

      String res = "/inetsoft/report/gui/images/binding/" + theme + "/" + theme + ".css";
      InputStream inp = getClass().getResourceAsStream(res);
      css.parse(inp);
      inp.close();
   }

   /**
    * Get an image with the specified size.
    * @param comp the component name, e.g. VSTimeSlider.
    * @param icon the name of the image, e.g. sliderTrack.
    * @param w the scaled width.
    * @param h the scaled height.
    */
   public Image getImage(String comp, String icon, int w, int h) {
      String source = (String) css.getValue(comp, icon, "source");

      if(source == null) {
         return null;
      }

      String res = "/inetsoft/report/gui/images/binding/" + theme + "/" + source;
      Image img = Tool.getImage(getClass(), res);
      Object topS = css.getValue(comp, icon, "scaleGridTop");
      Object leftS = css.getValue(comp, icon, "scaleGridLeft");
      Object bottomS = css.getValue(comp, icon, "scaleGridBottom");
      Object rightS = css.getValue(comp, icon, "scaleGridRight");

      Tool.waitForImage(img);

      int imgW = img.getWidth(null);
      int imgH = img.getHeight(null);

      if(w <= 0) {
         w = imgW;
      }

      if(h <= 0) {
         h = imgH;
      }

      if(w == imgW && h == imgH) {
         return img;
      }

      if(topS == null || leftS == null || bottomS == null || rightS == null) {
         img = img.getScaledInstance(w, h, Image.SCALE_SMOOTH);
         Tool.waitForImage(img);
         return img;
      }

      // scale using scale grid
      int top = Integer.parseInt((String) topS);
      int left = Integer.parseInt((String) leftS);
      int bottom = Integer.parseInt((String) bottomS);
      int right = Integer.parseInt((String) rightS);

      // copy original image to a buffered image
      BufferedImage img2 = new BufferedImage(imgW, imgH, BufferedImage.TYPE_INT_ARGB);
      Graphics g2 = img2.getGraphics();

      g2.drawImage(img, 0, 0, null);
      g2.dispose();

      // scaled image
      BufferedImage scaled = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
      g2 = scaled.getGraphics();

      // copy top-left corner
      g2.drawImage(img2.getSubimage(0, 0, left, top), 0, 0, null);
      // copy middle-left
      g2.drawImage(img2.getSubimage(0, top, left, bottom - top),
                   0, top, left, h - (imgH - bottom) - top, null);
      // copy bottom-left corner
      g2.drawImage(img2.getSubimage(0, bottom, left, imgH - bottom),
                   0, h - (imgH - bottom), null);

      // copy top-center
      g2.drawImage(img2.getSubimage(left, 0, right - left, top),
                   left, 0, w - (imgW - right) - left, top, null);
      // copy middle-center
      g2.drawImage(img2.getSubimage(left, top, right - left, bottom - top),
                   left, top, w - (imgW - right) - left,
                   h - (imgH - bottom) - top, null);
      // copy bottom-center
      g2.drawImage(img2.getSubimage(left, bottom, right - left, imgH - bottom),
                   left, h - (imgH - bottom), w - (imgW - right) - left,
                   imgH - bottom, null);

      if(imgW > right) {
         // copy top-right corner
         g2.drawImage(img2.getSubimage(right, 0, imgW - right, top),
                      w - (imgW - right), 0, null);
         // copy middle-right
         g2.drawImage(img2.getSubimage(right, top, imgW - right, bottom - top),
                      w - (imgW - right), top,
                      imgW - right, h - (imgH - bottom) - top, null);
         // copy bottom-right corner
         g2.drawImage(img2.getSubimage(right, bottom, imgW - right, imgH - bottom),
                      w - (imgW - right), h - (imgH - bottom), null);
      }

      g2.dispose();
      return scaled;
   }

   /**
    * Get a css property value.
    */
   public String getValue(String comp, String name) {
      return (String) css.getValue(comp, name);
   }

   private String theme;
   private FlexCSSParser css = new FlexCSSParser();
}
