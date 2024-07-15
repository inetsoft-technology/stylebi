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
package inetsoft.report.painter;

import inetsoft.report.Painter;
import inetsoft.report.Size;
import inetsoft.report.internal.*;
import inetsoft.util.Encoder;
import inetsoft.util.Tool;
import inetsoft.util.graphics.SVGSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

import java.awt.*;
import java.io.*;

/**
 * The ImagePainter paints an image. It's used internally to handling the
 * painting of images in documents.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class ImagePainter implements Painter {
   /**
    * Create a painter for the specified image.
    * @param image image to paint.
    */
   public ImagePainter(Image image) {
      setImage(image);
   }

   /**
    * Create a painter for the specified image.
    * @param image image to paint.
    */
   public ImagePainter(Image image, Size contextSize) {
      setImage(image, contextSize);
   }

   public ImagePainter(byte[] svgImage, Dimension size) {
      setSvgImage(svgImage, size);
   }

   /**
    * Create a painter for the specified image.
    * @param image image to paint.
    * @param fit true to resize the image to the paint area, false
    * to paint image as is.
    */
   public ImagePainter(Image image, boolean fit) {
      this(image);
      this.fit = fit;
   }

   /**
    * Return the preferred size of this painter.
    * @return size.
    */
   @Override
   public Dimension getPreferredSize() {
      return (isize.width < 0 || isize.height < 0) ?
         new Dimension(20, 20) :
         new Dimension(isize);
   }

   /**
    * Set background Color
    */
   public void setBackground(Color bg) {
      this.bg = bg;
   }

   /**
    * Get background Color
    */
   public Color getBackground() {
      return bg;
   }

   /**
    * Set whether to maintain aspect ratio.
    */
   public void setAspect(boolean aspect) {
      this.aspect = aspect;
   }

   /**
    * Check whether to maintain aspect ratio.
    */
   public boolean isAspect() {
      return aspect;
   }

   /**
    * Paint contents at the specified location.
    * @param g graphical context.
    * @param x x coordinate of the left edge of the paint area.
    * @param y y coordinate of the upper edge of the paint area.
    * @param w area width.
    * @param h area height.
    */
   @Override
   public void paint(Graphics g, int x, int y, int w, int h) {
      Image iobj = null;

      if(svgImage != null && svgImage.length > 0) {
         try {
            Document doc =
               SVGSupport.getInstance().createSVGDocument(new ByteArrayInputStream(svgImage));
            MetaImage.printSVG(g, x, y, w, h, doc);
            return;
         }
         catch(IOException e) {
            LOG.debug("Failed to transcode image in SVG", e);
         }
      }
      else if(isSVGImage()) {
         ((MetaImage) image).printImage(g, x, y, w, h);
         return;
      }
      // JPEG is written to PDF as is
      else if((g instanceof CustomGraphics) &&
         ((CustomGraphics) g).isSupported(CustomGraphics.JPEG_EXPORT)) {
         iobj = image;
      }
      else {
         iobj = (image instanceof MetaImage) ?
            ((MetaImage) image).getImage() :
            image;
      }

      if(iobj == null || isize.width < 0 || isize.height < 0) {
         Color oc = g.getColor();

         g.setColor(Color.gray);
         g.fillRect(x, y, w, h);
         g.setColor(oc);
      }
      else if(aspect) {
         double ratio = isize.width / (double) isize.height;
         double iw, ih;

         if(w / (double) h > ratio) {
            ih = h;
            iw = h * ratio;
         }
         else {
            iw = w;
            ih = w / ratio;
         }

         Common.drawImage(g, iobj, x, y, (int) iw, (int) ih, null);
      }
      else if(fit) {
         if(bg != null) {
            Color oc = g.getColor();

            g.setColor(bg);
            g.fillRect(x, y, w, h);
            g.setColor(oc);
         }

         Common.drawImage(g, iobj, x, y, w, h, null);
      }
      else {
         Common.drawImage(g, iobj, x, y, 0, 0, null);
      }
   }

   /**
    * Image can be scaled.
    */
   @Override
   public boolean isScalable() {
      return isSVGImage();
   }

   /**
    * Get the image in the painter.
    * @return image.
    */
   public Image getImage() {
      return image == def ? null : image;
   }

   /**
    * Set the image in the painter.
    * @param image image icon.
    */
   public void setImage(Image image) {
      setImage(image, null);
   }

   /**
    * Set the image in the painter.
    * @param image image icon.
    */
   public void setImage(Image image, Size contextSize) {
      // @by watsonn, bug1094028146777
      // Load the default image if it is null
      if(image == null) {
         if(def == null) {
            def = Tool.getImage(this, "/inetsoft/report/images/emptyimage.gif");
         }

         image = def;
      }

      this.image = image;
      Tool.waitForImage(image);
      int width;
      int height;

      if(image instanceof MetaImage && contextSize != null && contextSize.width > 0 &&
         contextSize.height > 0)
      {
         width = ((MetaImage) image).getWidth(null, (int) contextSize.width, (int) contextSize.height);
         height = ((MetaImage) image).getHeight(null, (int) contextSize.width, (int)contextSize.height);
      }
      else {
         width = image.getWidth(null);
         height = image.getHeight(null);
      }

      isize = new Dimension(width, height);
   }

   /**
    * Get the svg image data in the painter.
    * @return image.
    */
   public byte[] getSvgImage() {
      return this.svgImage;
   }

   /**
    * Set the svg image in the painter.
    * @param svgImage svg image byte data.
    */
   public void setSvgImage(byte[] svgImage, Dimension size) {
      this.svgImage = svgImage;
      isize = size;
   }

   /**
    * If fit is true, the image is resized to the area assigned to it.
    * Otherwise it is drawn as is.
    */
   public boolean isFit() {
      return fit;
   }

   private void readObject(java.io.ObjectInputStream s)
      throws ClassNotFoundException, java.io.IOException
   {
      s.defaultReadObject();

      Object obj = s.readObject();

      if(obj instanceof ImageLocation) {
         setImage(new MetaImage((ImageLocation) obj));
         return;
      }

      byte[] buf = (byte[]) obj;

      if(buf != null) {
         int w = s.readInt();
         int h = s.readInt();

         if(w > 0 && h > 0) {
            setImage(Encoder.decodeImage(w, h, buf));
         }
      }
   }

   private void writeObject(ObjectOutputStream stream) throws IOException {
      stream.defaultWriteObject();

      if(image instanceof MetaImage && ((MetaImage) image).isSVGImage()) {
         stream.writeObject(((MetaImage) image).getImageLocation());
         return;
      }

      byte[] buf = (image != null) ? Encoder.encodeImage(image) : null;

      stream.writeObject(buf);

      if(buf != null) {
         stream.writeInt(image.getWidth(null));
         stream.writeInt(image.getHeight(null));
      }
   }

   /**
    * Is the painting image an svg image.
    */
   public boolean isSVGImage() {
      return (image instanceof MetaImage && ((MetaImage) image).isSVGImage()) ||
         (svgImage != null && svgImage.length > 0);
   }

   private transient Image image, def;
   private transient byte[] svgImage;
   private transient Dimension isize;
   private boolean fit = true;
   private Color bg = null;
   private boolean aspect = false;
   private static final Logger LOG = LoggerFactory.getLogger(ImagePainter.class);
}
