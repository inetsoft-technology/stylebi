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
package inetsoft.report.internal;

import inetsoft.report.*;
import inetsoft.report.internal.info.ElementInfo;
import inetsoft.report.internal.info.ImageElementInfo;
import inetsoft.report.painter.ImagePainter;
import inetsoft.util.Tool;
import inetsoft.util.css.CSSConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.IOException;
import java.io.InputStream;

/**
 * Image element.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class ImageElementDef extends PainterElementDef {
   /**
    * Create an empty image element.
    */
   public ImageElementDef() {
      super();
   }

   /**
    * Create an empty image element.
    */
   public ImageElementDef(ReportSheet report) {
      super(report, null);

      try {
         Image img =
            Tool.getImage(this, "/inetsoft/report/images/emptyimage.gif");
         setPainter(new ImagePainter(img));
      }
      catch(Exception ex) {
         LOG.warn("Failed to load empty image", ex);
      }
   }

   /**
    * CSS Type to be used when styling this element.
    */
   @Override
   public String getCSSType() {
      return CSSConstants.IMAGE;
   }

   /**
    * Create a proper element info to save the attribute of this element.
    */
   @Override
   protected ElementInfo createElementInfo() {
      return new ImageElementInfo();
   }

    @Override
   public boolean print(StylePage pg, ReportSheet report) {
      try {
         updateImage();
      }
      catch(IOException e) {
         LOG.warn(e.getMessage(), e);
      }

      return super.print(pg, report);
   }

   /**
    * Return true if the image data is embedded in the template.
    */
   public boolean isEmbedded() {
      ImageLocation iloc = ((ImageElementInfo) einfo).getImageLocation();
      return iloc != null && iloc.isEmbedded();
   }

   /**
    * Set the image embedding option.
    */
   public void setEmbedded(boolean embed) {
      ImageLocation iloc = ((ImageElementInfo) einfo).getImageLocation();

      if(iloc == null && embed) {
         iloc = new ImageLocation(getReport().getDirectory());
      }

      if(embed || iloc != null) {
         iloc.setEmbedded(embed);
      }

      ((ImageElementInfo) einfo).setImageLocation(iloc);
   }

   /**
    * Get the image path. The meaning of the path depends on the
    * path type setting.
    */
   public String getPath() {
      ImageLocation iloc = ((ImageElementInfo) einfo).getImageLocation();
      return (iloc != null) ? iloc.getPath() : null;
   }

   /**
    * Set the image path.
    */
   public void setPath(String path) throws IOException {
      ImageLocation iloc = ((ImageElementInfo) einfo).getImageLocation();

      if(iloc == null) {
         iloc = new ImageLocation(getReport().getDirectory());
      }

      iloc.setPath(path);
      ((ImageElementInfo) einfo).setImageLocation(iloc);
      updateImage();
   }

   /**
    * Get the image path type.
    */
   public int getPathType() {
      ImageLocation iloc = ((ImageElementInfo) einfo).getImageLocation();
      return (iloc != null) ? iloc.getPathType() : ImageLocation.NONE;
   }

   /**
    * Set the image path type.
    */
   public void setPathType(int type) throws IOException {
      ImageLocation iloc = ((ImageElementInfo) einfo).getImageLocation();

      if(iloc == null) {
         iloc = new ImageLocation(getReport().getDirectory());
      }

      iloc.setPathType(type);
      ((ImageElementInfo)einfo).setImageLocation(iloc);
   }

   /**
    * Update the image in this element.
    */
   public void updateImage() throws IOException {
      ImageLocation iloc = ((ImageElementInfo) einfo).getImageLocation();

      if(iloc != null) {
         MetaImage img = (MetaImage) iloc.getImage();

         if(img != null) {
            InputStream input = img.getInputStream();

            if(input == null) {
               if(!isEmbedded()) {
                  throw new IOException("Image not found: " + iloc.getPath());
               }
            }
            else {
               input.close();
               Size psize = getPreferredSize();
               setPainter(new ImagePainter(img, psize));
               einfo.setProperty("imgw", img.getWidth(null) + "");
               einfo.setProperty("imgh", img.getHeight(null) + "");
            }
         }
      }
   }

   /**
    * Set the image to be used in the element.
    */
   @Override
   public void setImage(Image img) {
      Image currentImage = getImage();

      // @by stephenwebster, For Bug #9467
      // Avoid pointing this element to another instance of an image if it is
      // the same image based on the image location object.  This will optimize
      // the size of PDF generation as well as avoid unnecessary memory usage
      // across the application.
      if(img instanceof MetaImage && currentImage instanceof MetaImage &&
         ((MetaImage) img).getImageLocation().equals(
            ((MetaImage) currentImage).getImageLocation()))
      {
         return;
      }

      ImageLocation iloc = ((ImageElementInfo)einfo).getImageLocation();

      if(iloc != null) {
         try {
            MetaImage mimg = (MetaImage) iloc.getImage();

            if(mimg != null) {
               mimg.setImage(img);
            }
         }
         catch(Exception ex) {
            LOG.error("Failed to set image in element " + getID(), ex);
         }
      }

      setPainter(new ImagePainter(img));
   }

   /**
    * Get the image object in this element.
    */
   @Override
   public Image getImage() {
      try {
         return ((ImagePainter) getPainter()).getImage();
      }
      catch(ClassCastException ex) {
         // not an image
         return null;
      }
   }

   /**
    * This function returns the relative path if the path type is
    * relative.
    * @return adjusted path suitable for storing in file.
    */
   public String getAdjustedPath() {
      ImageLocation iloc = ((ImageElementInfo) einfo).getImageLocation();
      return (iloc != null) ? iloc.getAdjustedPath() : null;
   }

   /**
    * Get the image location object.
    */
   public ImageLocation getImageLocation() {
      return ((ImageElementInfo) einfo).getImageLocation();
   }

   /**
    * Set the image location.
    */
   public void setImageLocation(ImageLocation loc) throws IOException {
      ((ImageElementInfo) einfo).setImageLocation(loc);
      updateImage();
   }

   /**
    * Gets the human-readable description of the image.
    *
    * @return the tool tip.
    */
   public String getToolTip() {
      return ((ImageElementInfo) einfo).getToolTip();
   }

   /**
    * Sets the human-readable description of the image.
    *
    * @param toolTip the tool tip.
    */
   public void setToolTip(String toolTip) {
      ((ImageElementInfo) einfo).setToolTip(toolTip);
   }

   @Override
   public String toString() {
      return getID();
   }

   @Override
   public String getType() {
      return "Image";
   }

   /**
    * Create a PainterPaintable.
    */
   @Override
   protected PainterPaintable createPaintable(
      float x, float y, float painterW, float painterH, Dimension pd,
      int prefW, int prefH, ReportElement elem, Painter painter,
      int offsetX, int offsetY, int rotation)
   {
      if(painter instanceof ImagePainter) {
         return new ImagePainterPaintable(
            x, y, painterW, painterH, pd, prefW, prefH,
            elem, painter, offsetX, offsetY, rotation);
      }
      else {
         return new PainterPaintable(
            x, y, painterW, painterH, pd, prefW,
            prefH, elem, painter, offsetX, offsetY, rotation);
      }
   }

   /**
    * Set the aspect of the element.
    * @param aspect.
    */
   public void setAspect(boolean aspect) {
      ((ImageElementInfo) einfo).setAspect(aspect);
   }

   /**
    * Get the aspect of the element.
    * @return aspect.
    */
   public boolean isAspect() {
      return ((ImageElementInfo) einfo).isAspect();
   }

   private static final Logger LOG =
      LoggerFactory.getLogger(ImageElementDef.class);
}
