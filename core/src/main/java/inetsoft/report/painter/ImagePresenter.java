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

import inetsoft.report.ReportElement;
import inetsoft.report.internal.ExpandablePresenter;
import inetsoft.sree.SreeEnv;
import inetsoft.util.Catalog;
import inetsoft.util.FileSystemService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URL;
import java.util.Properties;

/**
 * Image presenters supports displaying of basic image.
 *
 * @version 13.3, 4/13/2020
 * @author InetSoft Technology Corp
 */
public class ImagePresenter implements ExpandablePresenter {
   /**
    * Check if the presenter can handle this type of objects.
    * @param type object type.
    * @return true if the presenter can handle this type.
    */
   @Override
   public boolean isPresenterOf(Class type) {
      return true;
   }

   /**
    * Check if the presenter can handle this particular object. Normally
    * a presenter handles a class of objects, which is checked by the
    * isPresenterOf(Class) method. If this presenter does not care about
    * the value in the object, it can just call the isPresenterOf() with
    * the class of the object, e.g.<pre>
    *   if(type == null) {
    *      return false;
    *   }
    *   return isPresenterOf(obj.getClass());
    * </pre>
    * @param obj object type.
    * @return true if the presenter can handle this type.
    */
   @Override
   public boolean isPresenterOf(Object obj) {
      return obj != null;
   }

   /**
    * Check if this presenter should always fill the entire area of a cell.
    */
   @Override
   public boolean isFill() {
      return true;
   }

   /**
    * Set the font to use for this presenter. A table calls this function
    * before the cell is printed when a presenter is used.
    */
   @Override
   public void setFont(Font font) {
      comp.setFont(font);
   }

   /**
    * Get the display name of this presenter.
    *
    * @return a user-friendly name for this presenter.
    *
    * @since 5.1
    */
   @Override
   public String getDisplayName() {
      return Catalog.getCatalog().getString("Image");
   }

   /**
    * Determine if this Presenter requires raw (unformatted) data.
    *
    * @return <code>true</code> if the presenter requires raw data.
    */
   @Override
   public boolean isRawDataRequired() {
      return false;
   }

   /**
    * Calculate the preferred size of the object representation.
    * @param v object value.
    * @return preferred size.
    */
   @Override
   public Dimension getPreferredSize(Object v) {
      try {
         BufferedImage image = ImagePresenterCache.getImageCache(v + "");

         if(image == null) {
            image = ImageIO.read(new URL(v + ""));
            ImagePresenterCache.addImageCache(v + "", image);
         }

         return new Dimension(image.getWidth(), image.getHeight());
      }
      catch(Exception ex) {
         LOG.info("The url can not apply image presenter!");
      }
      
      return new Dimension(0, 0);
   }

   /**
    * Calculate the preferred size of the object representation.
    * @param v object value.
    * @param width the maximum width of the presenter.
    * @return preferred size.
    */
   @Override
   public Dimension getPreferredSize(Object v, float width) {
      return getPreferredSize(v);
   }

   /**
    * Draw an empty box with label HTML Painter
    */
   @Override
   public void paint(Graphics g, Object html, int x, int y, int w, int h) {
      paint(g, html, x, y, w, h, 0, 0);
   }

   /**
    * Draw the html contents.
    * @param g target graphics.
    * @param html html string.
    * @param x x position of the drawing area.
    * @param y y position of the drawing area.
    * @param w width of the drawing area.
    * @param h height of the drawing area.
    * @param starty the already consumed height.
    * @param bufh the current drawing buffer height.
    */
   @Override
   public void paint(Graphics g, Object html, int x, int y, int w, int h,
                     float starty, float bufh)
   {
      try {
         BufferedImage image = ImagePresenterCache.getImageCache(html + "");

         if(image == null) {
            image = ImageIO.read(new URL(html + ""));
            ImagePresenterCache.addImageCache(html + "", image);
         }

         if(auto) {
            g.drawImage(image, x, y, null);
         }
         else if(ratio) {
            int w2 = image.getWidth();
            int h2 = image.getHeight();
            double aspectRatio = w2 * 1.0 / h2;
            double w3 = Math.min(w, h * aspectRatio);
            double h3 = w / aspectRatio;
            g.drawImage(image, x, y, (int) w3, (int) h3, null);
         }
         else {
            g.drawImage(image, x, y, w, h, null);
         }
      }
      catch(Exception ex) {
         LOG.info("The url can not apply image presenter!");
      }
   }

   /**
    * Get the adjustment on height if the height is adjusted to line boundary.
    */
   @Override
   public float getHeightAdjustment(Object html, ReportElement elem,
                                    Dimension pd, float starty, float bufw,
                                    float bufh) {
      return 0;
   }

   /**
    * Set the background.
    */
   @Override
   public void setBackground(Color bg) {
      this.bg = bg;
   }

   /**
    * Get the background.
    */
   public Color getBackground() {
      return bg;
   }

   private void writeObject(ObjectOutputStream stream) throws IOException {
   }

   public void setAutoSize(boolean asize) {
      this.auto = asize;
   }

   public boolean isAutoSize() {
      return auto;
   }

   public void setMaintainAspectRatio(boolean ratio) {
      this.ratio = ratio;
   }

   public boolean isMaintainAspectRatio() {
      return ratio;
   }

   private static final class ImagePresenterCache {
      private static BufferedImage getImageCache(String url) {
         if(index == null) {
            loadImageCache();
         }

         try {
            if(index.containsKey(url)) {
               String value = index.getProperty(url);
               File img = new File(getFolder(), value + ".png");

               return ImageIO.read(img);
            }
         }
         catch(Exception e) {
            LOG.debug("Can not get image caches!", e);
         }

         return null;
      }

      private static void addImageCache(String url, BufferedImage image) {
         if(index == null) {
            loadImageCache();
         }

         try {
            File folder = getFolder();

            if(index.containsKey(url)) {
               String value = index.getProperty(url);
               File img = new File(folder, value + ".png");
               ImageIO.write(image, "png", img);
            }
            else {
               String value = index.size() + "";
               File img = new File(folder, value + ".png");
               index.setProperty(url, value);
               ImageIO.write(image, "png", img);
               saveImageCache();
            }
         }
         catch(Exception e) {
            LOG.info("Can not add image caches!");
         }
      }

      private static File getFolder() {
         FileSystemService fileSystemService = FileSystemService.getInstance();
         File folder = fileSystemService.getFile(SreeEnv.getProperty("sree.home") + "/" +
            "imageCache");

         if(!folder.exists()) {
            return null;
         }

         return folder;
      }

      private static void loadImageCache() {
         FileSystemService fileSystemService = FileSystemService.getInstance();
         File folder = fileSystemService.getFile(SreeEnv.getProperty("sree.home") + "/" +
            "imageCache");

         if(!folder.exists()) {
            folder.mkdirs();
         }

         File indexFile = fileSystemService.getFile(folder, "index.properties");
         index = new Properties();
         loadProperties(indexFile);
      }

      private static void saveImageCache() {
         FileSystemService fileSystemService = FileSystemService.getInstance();
         File folder = fileSystemService.getFile(SreeEnv.getProperty("sree.home") + "/" +
            "imageCache");

         if(!folder.exists()) {
            folder.mkdirs();
         }

         File indexFile = fileSystemService.getFile(folder, "index.properties");
         saveProperties(indexFile);
      }

      private static void loadProperties(File imageFile) {
         try(FileInputStream input = new FileInputStream(imageFile)) {
            index.load(input);
         }
         catch(IOException ex) {
            LOG.debug("Unable to load ImagePresenterCache", ex);
         }
      }

      private static void saveProperties(File imageFile) {
         try(FileOutputStream out = new FileOutputStream(imageFile)) {
            index.store(out, null);
         }
         catch(IOException ex) {
            LOG.debug("Unable to save ImagePresenterCache", ex);
         }
      }

      private static Properties index = null;
   }

   private boolean auto = false;
   private boolean ratio = false;
   private Color bg = null;
   private final JComponent comp = new JPanel();

   private static final Logger LOG = LoggerFactory.getLogger(ImagePresenter.class);
}
