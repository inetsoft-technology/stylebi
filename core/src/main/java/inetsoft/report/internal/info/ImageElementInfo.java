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
package inetsoft.report.internal.info;

import inetsoft.report.Size;
import inetsoft.report.internal.ImageLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 */
public class ImageElementInfo extends PainterElementInfo {
   /**
    * construct the class
    */
   public ImageElementInfo() {
      super();
   }

   /**
    * Get the image size
    */
   @Override
   public Size getSize() {
      return size;
   }

   /**
    * Set the image size
    */
   @Override
   public void setSize(Size size) {
      this.size = size;
   }

   /**
    * Get the boolean aspect
    */
   public boolean isAspect() {
      return aspect;
   }

   /**
    * Set the boolean aspect
    */
   public void setAspect(boolean aspect) {
      this.aspect = aspect;
   }

   /**
    * Get the image location object
    */
   public ImageLocation getImageLocation() {
      return iloc;
   }

   /**
    * Set the image location object
    */
   public void setImageLocation(ImageLocation iloc) {
      this.iloc = iloc;
   }

   /**
    * Gets the human-readable description of the image.
    *
    * @return the tool tip.
    */
   public String getToolTip() {
      return toolTip;
   }

   /**
    * Sets the human-readable description of the image.
    *
    * @param toolTip the tool tip.
    */
   public void setToolTip(String toolTip) {
      this.toolTip = toolTip;
   }

   /**
    * Clones this object
    */
   @Override
   public Object clone() {
      try {
         ImageElementInfo iinfo = (ImageElementInfo) super.clone();

         if(size != null) {
            iinfo.size = size;
         }

         if(iloc != null) {
            iinfo.iloc = (ImageLocation) iloc.clone();
         }

         iinfo.aspect = aspect;

         return iinfo;
      }
      catch(Exception e) {
         LOG.error("Failed to clone image element info", e);
      }

      return null;
   }

   /**
    * Get the string representation.
    */
   @Override
   public String toString() {
      return "ImageElementInfo: " + size + ", " + iloc + "|" + hashCode();
   }

   /**
    * Get the name of the tag of the root of the properties xml tree.
    */
   @Override
   public String getTagName() {
      return "imageElementInfo";
   }

   /**
    * Create an ElementInfo.
    */
   @Override
   protected ElementInfo create() {
      return new ImageElementInfo();
   }

   /**
    * Get the default info in section.
    */
   @Override
   public  ElementInfo createInSection(boolean autoResize, String name) {
      ImageElementInfo info =
         (ImageElementInfo) super.createInSection(autoResize, name);

      if(autoResize) {
         info.setProperty("grow", "true");
      }

      return info;
   }

   private Size size;
   private ImageLocation iloc;
   private String toolTip;
   private boolean aspect = false;

   private static final Logger LOG =
      LoggerFactory.getLogger(ImageElementInfo.class);
}
