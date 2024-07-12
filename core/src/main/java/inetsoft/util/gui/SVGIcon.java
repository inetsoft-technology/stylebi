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
/*
 * SVGIcon.java
 *
 * A Swing Icon that draws an SVG image.
 *
 * Cameron McCormack <cam (at) mcc.id.au>
 *
 * Permission is hereby granted to use, copy, modify and distribute this
 * code for any purpose, without fee.
 *
 * Initial version: April 21, 2005
 */
package inetsoft.util.gui;

import inetsoft.util.graphics.SVGSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Dimension2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;

/**
 * A Swing Icon that draws an SVG image.
 *
 * */
public class SVGIcon extends ImageIcon {
   /**
    * Create a new SVGIcon object.
    * @param iconFile The file name of the SVG document.
    */
   public SVGIcon(String iconFile) {
      this(iconFile, false);
   }

   /**
    * Create a new SVGIcon object.
    * @param iconFile The file name of the SVG document.
    * @param enlarged <true> to get a standard large icon</true>
    */
   public SVGIcon(String iconFile, boolean enlarged) {
      this(iconFile, enlarged ? ENLARGED_FONT_WIDTH : FONT_WIDTH,
           enlarged ? ENLARGED_FONT_HEIGHT : FONT_HEIGHT);
   }

   /**
    * Create a new SVGIcon object.
    * @param iconFile The file name of the SVG document.
    * @param w the width of the icon
    * @param h the height of the icon
    */
   public SVGIcon(String iconFile, int w, int h) {
      if(iconFile.startsWith(FONT_ICON_ROOT)) {
         this.iconFile = iconFile;
      }
      else {
         this.iconFile = FONT_ICON_ROOT + iconFile;
      }

      this.width = w;
      this.height = h;
   }

   /**
    * Create a new SVGIcon object.
    * @param inputStream The input stream of the SVG document.
    */
   public SVGIcon(InputStream inputStream) {
      this(inputStream, false);
   }

   /**
    * Create a new SVGIcon object.
    * @param inputStream The input stream of the SVG document.
    * @param enlarged <true>to get a standard large icon</true>
    */
   public SVGIcon(InputStream inputStream, boolean enlarged) {
      this(inputStream, enlarged ? ENLARGED_FONT_WIDTH : FONT_WIDTH,
           enlarged ? ENLARGED_FONT_HEIGHT : FONT_HEIGHT);
   }

   /**
    * Create a new SVGIcon object.
    * @param inputStream The input stream of the SVG document.
    * @param w The width of the icon.
    * @param h The height of the icon.
    */
   public SVGIcon(InputStream inputStream, int w, int h) {
      generateBufferedImage(inputStream, w, h);
   }

   /**
    * Generate the BufferedImage.
    */
   protected void generateBufferedImage(InputStream in, int w, int h) {
      generateBufferedImage(in, w, h, null);
   }

   /**
    * Generate the BufferedImage.
    */
   private void generateBufferedImage(InputStream in, int w, int h, String color) {
      try {
         bufferedImage = SVGSupport.getInstance().generateBufferedImage(in, w, h, theme, color);
         width = bufferedImage.getWidth();
         height = bufferedImage.getHeight();
      }
      catch(Exception e) {
         LOG.error("Failed to generate image for {} ", iconFile);
         throw e;
      }
   }

   /**
    * Returns the icon's width.
    */
   public int getIconWidth() {
      return width;
   }

   /**
    * Returns the icon's height.
    */
   public int getIconHeight() {
      return height;
   }

   public BufferedImage getImage() {
      if(bufferedImage == null) {
         InputStream inputStream = getClass().getResourceAsStream(this.iconFile);
         generateBufferedImage(inputStream, width, height);
      }

      return bufferedImage;
   }

   public BufferedImage getImage(String color) {
      if(bufferedImage == null) {
         InputStream inputStream = getClass().getResourceAsStream(this.iconFile);
         generateBufferedImage(inputStream, width, height, color);
      }

      return bufferedImage;
   }

   public void setImage(BufferedImage image) {
      bufferedImage = image;
   }

   /**
    * Draw the icon at the specified location.
    */
   public void paintIcon(Component c, Graphics g, int x, int y) {
      g.drawImage(getImage(), x, y, null);
   }

   /**
    * Returns the default size of this user agent.
    */
   public Dimension2D getViewportSize() {
      return new Dimension(width, height);
   }

   public String toString() {
      return super.toString() + "(" + iconFile + ")";
   }

   public static String getPath(String iconFile) {
      return SVGIcon.FONT_ICON_ROOT + iconFile;
   }

   private BufferedImage bufferedImage;
   private String iconFile;
   protected int width;
   protected int height;

   private static final String FONT_ICON_ROOT = "/inetsoft/web/resources/app/assets/ineticons/icon_svg/";
   private static final int FONT_WIDTH = 20;
   private static final int FONT_HEIGHT = 20;
   private static final int ENLARGED_FONT_WIDTH = 50;
   private static final int ENLARGED_FONT_HEIGHT = 50;
   private static final String theme = "light";
   private static final Logger LOG = LoggerFactory.getLogger(SVGIcon.class);
}
