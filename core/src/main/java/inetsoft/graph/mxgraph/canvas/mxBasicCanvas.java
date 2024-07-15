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
package inetsoft.graph.mxgraph.canvas;

import inetsoft.graph.mxgraph.util.*;

import java.awt.image.BufferedImage;
import java.util.Hashtable;
import java.util.Map;

public abstract class mxBasicCanvas implements mxICanvas {

   /**
    * Specifies if image aspect should be preserved in drawImage. Default is true.
    */
   public static boolean PRESERVE_IMAGE_ASPECT = true;

   /**
    * Defines the default value for the imageBasePath in all GDI canvases.
    * Default is an empty string.
    */
   public static String DEFAULT_IMAGEBASEPATH = "";

   /**
    * Defines the base path for images with relative paths. Trailing slash
    * is required. Default value is DEFAULT_IMAGEBASEPATH.
    */
   protected String imageBasePath = DEFAULT_IMAGEBASEPATH;

   /**
    * Specifies the current translation. Default is (0,0).
    */
   protected mxPoint translate = new mxPoint();

   /**
    * Specifies the current scale. Default is 1.
    */
   protected double scale = 1;

   /**
    * Specifies whether labels should be painted. Default is true.
    */
   protected boolean drawLabels = true;

   /**
    * Cache for images.
    */
   protected Hashtable<String, BufferedImage> imageCache = new Hashtable<String, BufferedImage>();

   /**
    * Sets the current translate.
    */
   public void setTranslate(double dx, double dy)
   {
      translate = new mxPoint(dx, dy);
   }

   /**
    * Returns the current translate.
    */
   public mxPoint getTranslate()
   {
      return translate;
   }

   /**
    *
    */
   public double getScale()
   {
      return scale;
   }

   /**
    *
    */
   public void setScale(double scale)
   {
      this.scale = scale;
   }

   /**
    *
    */
   public String getImageBasePath()
   {
      return imageBasePath;
   }

   /**
    *
    */
   public void setImageBasePath(String imageBasePath)
   {
      this.imageBasePath = imageBasePath;
   }

   /**
    *
    */
   public boolean isDrawLabels()
   {
      return drawLabels;
   }

   /**
    *
    */
   public void setDrawLabels(boolean drawLabels)
   {
      this.drawLabels = drawLabels;
   }

   /**
    * Returns an image instance for the given URL. If the URL has
    * been loaded before than an instance of the same instance is
    * returned as in the previous call.
    */
   public BufferedImage loadImage(String image)
   {
      BufferedImage img = imageCache.get(image);

      if(img == null) {
         img = mxUtils.loadImage(image);

         if(img != null) {
            imageCache.put(image, img);
         }
      }

      return img;
   }

   /**
    *
    */
   public void flushImageCache()
   {
      imageCache.clear();
   }

   /**
    * Gets the image path from the given style. If the path is relative (does
    * not start with a slash) then it is appended to the imageBasePath.
    */
   public String getImageForStyle(Map<String, Object> style)
   {
      String filename = mxUtils.getString(style, mxConstants.STYLE_IMAGE);

      if(filename != null && !filename.startsWith("/") && !filename.startsWith("file:/")) {
         filename = imageBasePath + filename;
      }

      return filename;
   }

}
