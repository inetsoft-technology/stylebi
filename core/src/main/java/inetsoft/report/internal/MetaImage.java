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

import inetsoft.report.io.Builder;
import inetsoft.util.*;
import inetsoft.util.graphics.SVGSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

import java.awt.*;
import java.awt.image.*;
import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.WeakHashMap;

/**
 * The MetaImage object contains meta information for an image.
 * It is primarily used for adding images to a report without the
 * need for loading the image in memory during DHTML generation. This
 * speeds up the image processing and removes the need for requiring
 * an AWT toolkit for processing images.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class MetaImage extends Image implements ObjectWrapper {
   /**
    * Define an image from a image location.
    * @param iloc image location.
    */
   public MetaImage(ImageLocation iloc) throws MalformedURLException  {
      this.iloc = iloc;

      String path = iloc.getPath();

      if(path == null) {
         path = "";
      }

      FileSystemService fileSystemService = FileSystemService.getInstance();

      if(iloc.getPathType() == ImageLocation.RESOURCE) {
         resource = path;
      }
      else if(iloc.getPathType() == ImageLocation.IMAGE_URL) {
         url = new URL(Builder.getBaseURL(), path);
      }
      else if(iloc.getPathType() == ImageLocation.RELATIVE_PATH) {
         file = fileSystemService.getFile(path);
         String fpath = file.isAbsolute() ?
            iloc.getPath() :
            iloc.getDirectory() + File.separator + iloc.getPath();
         file = fileSystemService.getFile(fpath);
      }
      else {
         file = fileSystemService.getFile(path);
      }
   }

   /**
    * Determine whether the file path exists.
    */
   public boolean isFileExist() {
      if(iloc.getPathType() == ImageLocation.IMAGE_URL) {
         return true;
      }

      return file.exists();
   }

   /**
    * Get the location of this image.
    */
   public ImageLocation getImageLocation() {
      return iloc;
   }

   /**
    * Set the width of this image.
    */
   public void setWidth(int w) {
      width = w;
   }

   /**
    * Determines the width of the image. If the width is not yet known,
    * this method returns <code>-1</code> and the specified
    * <code>ImageObserver</code> object is notified later.
    * @param     observer   an object waiting for the image to be loaded.
    * @return    the width of this image, or <code>-1</code>
    *                   if the width is not yet known.
    */
   @Override
   public int getWidth(ImageObserver observer) {
      return getWidth(observer, -1, -1);
   }

   public int getWidth(ImageObserver observer, int svgContextWidth, int svgContextHeight) {
      if(width >= 0) {
         return width;
      }

      if(preferredSize != null) {
         return preferredSize.width;
      }

      try {
         preferredSize = getPreferredSize(observer, svgContextWidth, svgContextHeight);
      }
      catch(Exception e) {
         return -1;
      }

      return preferredSize.width;
   }

   /**
    * Set the height of the image.
    */
   public void setHeight(int h) {
      height = h;
   }

   /**
    * Determines the height of the image. If the height is not yet known,
    * this method returns <code>-1</code> and the specified
    * <code>ImageObserver</code> object is notified later.
    * @param     observer   an object waiting for the image to be loaded.
    * @return    the height of this image, or <code>-1</code>
    *                   if the height is not yet known.
    */
   @Override
   public int getHeight(ImageObserver observer) {
      return getHeight(observer, -1, -1);
   }

   public int getHeight(ImageObserver observer, int svgContextWidth, int svgContextHeight) {
      if(height >= 0) {
         return height;
      }

      if(preferredSize != null) {
         return preferredSize.height;
      }

      try {
         preferredSize = getPreferredSize(observer, svgContextWidth, svgContextHeight);
      }
      catch(Exception e) {
         return -1;
      }

      return preferredSize.height;
   }

   /**
    * Gets the object that produces the pixels for the image.
    * This method is called by the image filtering classes and by
    * methods that perform image conversion and scaling.
    * @return     the image producer that produces the pixels
    *                                  for this image.
    */
   @Override
   public ImageProducer getSource() {
      Image img = getImage();
      return (img == null) ? null : img.getSource();
   }

   /**
    * Creates a graphics context for drawing to an off-screen image.
    * This method can only be called for off-screen images.
    * @return  a graphics context to draw to the off-screen image.
    */
   @Override
   public Graphics getGraphics() {
      Image img = getImage();
      return (img == null) ? null : img.getGraphics();
   }

   /**
    * Gets a property of this image by name.
    * <p>
    * Individual property names are defined by the various image
    * formats. If a property is not defined for a particular image, this
    * method returns the <code>UndefinedProperty</code> object.
    * <p>
    * If the properties for this image are not yet known, this method
    * returns <code>null</code>, and the <code>ImageObserver</code>
    * object is notified later.
    * <p>
    * The property name <code>"comment"</code> should be used to store
    * an optional comment which can be presented to the application as a
    * description of the image, its source, or its author.
    * @param       name   a property name.
    * @param       observer   an object waiting for this image to be loaded.
    * @return      the value of the named property.
    */
   @Override
   public Object getProperty(String name, ImageObserver observer) {
      Image img = getImage();
      return (img == null) ? null : img.getProperty(name, observer);
   }

   /**
    * Flushes all resources being used by this Image object.  This
    * includes any pixel data that is being cached for rendering to
    * the screen as well as any system resources that are being used
    * to store data or pixels for the image.  The image is reset to
    * a state similar to when it was first created so that if it is
    * again rendered, the image data will have to be recreated or
    * fetched again from its source.
    * <p>
    * This method always leaves the image in a state such that it can
    * be reconstructed.  This means the method applies only to cached
    * or other secondary representations of images such as those that
    * have been generated from an <tt>ImageProducer</tt> (read from a
    * file, for example). It does nothing for off-screen images that
    * have only one copy of their data.
    */
   @Override
   public void flush() {
      if(image != null) {
         image.flush();
      }
   }

   /**
    * Get the wrapped object.
    *
    * @return the wrapped object
    */
   @Override
   public Object unwrap() {
      return getImage();
   }

   /**
    * Set the image object to be used in this meta image.
    */
   public void setImage(Image image) {
      this.image = image;
   }

   /**
    * Get the actual image from the image location information.
    */
   public synchronized Image getImage() {
      return getImage(-1, -1);
   }

   /**
    * Get the actual image from the image location information.
    */
   public synchronized Image getImage(int svgContextWidth, int svgContextHeight) {
      if(image != null) {
         return image;
      }

      if(isSVGImage()) {
         image = getSVGImage(svgContextWidth, svgContextHeight);
         imgmap.put(iloc, image);
         return image;
      }

      image = imgmap.get(iloc);

      if(image != null) {
         return image;
      }

      try {
         InputStream input = getInputStream();

         if(input != null) {
            image = Tool.getImage(input);
            Tool.waitForImage(image);
         }
         else {
            if(resource != null) {
               LOG.warn("Image resource not found: " + resource);
            }
            else if(url != null) {
               LOG.warn("Image URL not found: " + url);
            }
            else if(file != null) {
               LOG.warn("Image file not found: " + file);
            }
         }
      }
      catch(Exception ex) {
         LOG.warn("Failed to get image", ex);
      }

      imgmap.put(iloc, image);
      return image;
   }

   /**
    * Print svg image into the graphics.
    * @param g the graphics.
    * @param x coordinate of the left edge of the paint area.
    * @param y coordinate of the upper edge of the paint area.
    * @param width area width.
    * @param height area height.
    */
   public synchronized void printImage(Graphics g, int x, int y,
                                       int width, int height) {
      if(!isSVGImage()) {
         throw new RuntimeException("The image printed should be svg format");
      }

      try {
         Document doc =
            SVGSupport.getInstance().createSVGDocument(new URL(getConvertURLString(getURL())));
         printSVG(g, x, y, width, height, doc);
      }
      catch(Exception ex) {
         throw new RuntimeException(ex);
      }
   }

   public static void printSVG(Graphics g, double x, double y, double width, double height,
                               Document svg)
   {
      SVGSupport.getInstance().printSVG(g, x, y, width, height, svg);
   }

   /**
    * Get image of svg format.
    */
   private Image getSVGImage(int svgContextWidth, int svgContextHeight) {
      int width = getWidth(null, svgContextWidth, svgContextHeight);
      int height = getHeight(null, svgContextWidth, svgContextHeight);

      BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g2 = img.createGraphics();
      PageFormat pg = new PageFormat();
      Paper pp = new Paper();

      float scalex = 1f;
      float scaley = 1f;

      try {
         Dimension dim = getPreferredSize(null, svgContextWidth, svgContextHeight);
         scalex = (float) width / dim.width;
         scaley = (float) height / dim.height;
         g2.scale(scalex, scaley);
      }
      catch(Exception ex) {
         LOG.error("Failed to scale SVG image", ex);
      }

      pp.setImageableArea(0, 0, width / scalex, height / scaley);
      pg.setPaper(pp);
      SVGSupport.getInstance().printSVG(g2, pg, getURL());
      g2.dispose();

      return img;
   }

   /**
    * Get an input stream to read image data.
    */
   public InputStream getInputStream() {
      try {
         if(resource != null) {
            DataSpace space = DataSpace.getDataSpace();
            InputStream istream = space.getInputStream(null, resource);

            if(istream == null) {
               istream = loadResource(getImageLoader());
            }

            return istream;
         }
         else if(url != null) {
            return url.openStream();
         }
         else if(file != null && file.exists()) {
            return new FileInputStream(file);
         }
      }
      catch(Exception ex) {
         LOG.warn("Failed to get input stream", ex);
      }

      return null;
   }

   private InputStream loadResource(ClassLoader loader) {
      if(loader == null) {
         return null;
      }

      InputStream istream = loader.getResourceAsStream(resource);

      if(istream == null && resource.startsWith("/")) {
         istream = loader.getResourceAsStream(resource.substring(1));
      }

      return istream;
   }

   /**
    * Get the image file suffix.
    */
   public String getSuffix() {
      String name = null;

      if(resource != null) {
         name = resource;
      }
      else if(url != null) {
         name = url.getFile();
      }
      else if(file != null && file.exists()) {
         name = file.getName();
      }

      if(name != null) {
         int idx = name.lastIndexOf('.');

         if(idx > 0) {
            return name.substring(idx + 1);
         }
      }

      return null;
   }

   /**
    * Check if the MetaImage is svg format.
    */
   public boolean isSVGImage() {
      return "svg".equals(getSuffix());
   }

   /**
    * Get the preferred size of image.
    */
   private Dimension getPreferredSize(ImageObserver observer) throws Exception {
      return getPreferredSize(observer, -1, -1);
   }

   /**
    * Get the preferred size of image.
    */
   private Dimension getPreferredSize(ImageObserver observer, int svgContextWidth,
                                      int svgContextHeight)
      throws Exception
   {
      if(isSVGImage()) {
         URL url = getURL();

         if(url == null) {
            return SVGSupport.getInstance().getSVGSize(getInputStream(), svgContextWidth,
               svgContextHeight);
         }
         else {
            return SVGSupport.getInstance().getSVGSize(url, svgContextWidth, svgContextHeight);
         }
      }
      else {
         Image img = getImage();
         return (img == null) ? new Dimension(-1, -1) :
            new Dimension(img.getWidth(observer), img.getHeight(observer));
      }
   }

   /**
    * Get the url of the source of the image.
    */
   private URL getURL() {
      URL url = null;

      if(resource != null) {
         url = getImageLoader().getResource(resource);

         if(url == null && resource.startsWith("/")) {
            url = getImageLoader().getResource(resource.substring(1));
         }
      }
      else if(this.url != null) {
         url = this.url;
      }
      else if(file != null && file.exists()) {
         try {
            url = file.toURI().toURL();
         }
         catch(MalformedURLException ex) {
            LOG.warn("Failed to get URL for file: " + file, ex);
         }
      }

      return url;
   }

   /**
    * Get the class loader used to load resources.
    */
   public static ClassLoader getImageLoader() {
      if(imageLoader == null) {
         return MetaImage.class.getClassLoader();
      }
      else {
         return imageLoader;
      }
   }

   /**
    * Set the class loader used to load resources.
    */
   public static void setImageLoader(ClassLoader loader) {
      imageLoader = loader;
   }

   /**
    * To avoid problems about weblogic error. Weblogic confuses jar and zip url.
    * Reference http://article.gmane.org/gmane.text.xml.cocoon.devel/75461.
    * If url starts with 'zip', replace the 'zip' to 'jar:file:'.
    */
   private String getConvertURLString(URL url) {
      String urlString = url + "";
      int index = urlString.indexOf("zip:");

      if(index == 0) {
         urlString = "jar:file" + urlString.substring(3);
      }

      return urlString;
   }

   public String toString() {
      if(iloc == null) {
         return "Image[]";
      }

      return "Image[" + iloc.getPath() + "]";
   }

   private static ClassLoader imageLoader;
   private static WeakHashMap<ImageLocation, Image> imgmap = new WeakHashMap<>();

   private int width = -1, height = -1;
   private String resource;
   private File file;
   private URL url;
   private transient Image image;
   private final ImageLocation iloc;
   private Dimension preferredSize = null;
   private static final Logger LOG = LoggerFactory.getLogger(MetaImage.class);
}
