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
package inetsoft.report.internal;

import inetsoft.util.FileSystemService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.*;

/**
 * Image Location.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class ImageLocation implements Serializable, Cloneable {
   /**
    * Image location specified as an absolute file path.
    */
   public static final int FULL_PATH = 1;
   /**
    * Image location specified as a relative file path.
    */
   public static final int RELATIVE_PATH = 2;
   /**
    * Image location specified as a Java resource path.
    */
   public static final int RESOURCE = 3;
   /**
    * Image location specified as a URL.
    */
   public static final int IMAGE_URL = 4;
   /**
    * Image location specified as a imported.
    */
   public static final int IMPORTED = 5;
   /**
    * None image.
    */
   public static final int NONE = -1;

   /**
    * Create an default location.
    * @param dir current directory.
    */
   public ImageLocation(String dir) {
      this.dir = dir;

      if(dir == null) {
         this.dir = ".";
      }
   }

   /**
    * Get the current directory.
    */
   public String getDirectory() {
      return dir;
   }

   /**
    * Return true if the image data is embedded in the template.
    */
   public boolean isEmbedded() {
      return embed;
   }

   /**
    * Set the image embedding option.
    */
   public void setEmbedded(boolean embed) {
      this.embed = embed;
   }

   /**
    * Return true if the path is null.
    */
   public boolean isNone() {
      return path == null;
   }

   /**
    * Get the image path. The meaning of the path depends on the
    * path type setting.
    */
   public String getPath() {
      return path;
   }

   /**
    * Set the image path.
    */
   public void setPath(String path) {
      this.path = path;
   }

   /**
    * Get the image object at the specified location.
    */
   public Image getImage() throws IOException {
      if(path != null) {
         return new MetaImage(this);
      }

      return null;
   }

   /**
    * Get the image path type.
    */
   public int getPathType() {
      return pathtype;
   }

   /**
    * Set the image path type.
    */
   public void setPathType(int type) {
      pathtype = type;
   }

   /**
    * This function returns the relative path if the path type is
    * relative.
    * @return adjusted path suitable for storing in file.
    */
   public String getAdjustedPath() {
      String file = path;

      if(path != null && pathtype == RELATIVE_PATH) {
         File fo = FileSystemService.getInstance().getFile(file);

         // if it's not absolute path, don't translate
         if(!fo.isAbsolute()) {
            return file;
         }

         // is win
         if(dir.indexOf(":\\") > 0) {
            file = path.toLowerCase();
         }

         int idx = 0;

         // find the max eq part
         for(; idx < file.length() && idx < dir.length(); idx++) {
            if(file.charAt(idx) != dir.charAt(idx)) {
               break;
            }
         }

         // find the directories that are same
         for(; idx >= 0; idx--) {
            if(file.charAt(idx) == File.separatorChar) {
               break;
            }
         }

         // idx now points to he file separator
         file = file.substring(idx + 1);
         if(dir.endsWith(File.separator)) {
            idx++;
         }

         while((idx = dir.indexOf(File.separator, idx) + 1) > 0) {
            file = ".." + File.separator + file;
         }
      }

      return file;
   }

   /**
    * Set the last modified time of the image file if any.
    * @param lastModified last modified time of the image file.
    */
   public void setLastModified(long lastModified) {
      this.lastModified = lastModified;
   }

   /**
    * Get the last modified time of the image file if any.
    * @return last modified time of the image file.
    */
   public long getLastModified() {
      return lastModified;
   }

   /**
    * Check if the image exists.
    * @return true if the image exists.
    */
   public boolean isImageExisting() {
      try {
         MetaImage image = new MetaImage(this);
         InputStream in = image.getInputStream();

         if(in != null) {
            in.close();
            return true;
         }
      }
      catch(Exception ex) {
         LOG.warn("Failed to read image: " + path, ex);
      }

      return false;
   }

   /**
    * Get a hashcode of the image location.
    */
   public int hashCode() {
      try {
         return path.hashCode() + pathtype + dir.hashCode() +
            Long.valueOf(lastModified).hashCode();
      }
      catch(Exception ex) {
         return pathtype;
      }
   }

   /**
    * Make a copy of this object.
    */
   @Override
   public Object clone() {
      try {
         return super.clone();
      }
      catch(Exception ex) {
         LOG.error("Failed to clone image location", ex);
      }

      return null;
   }

   /**
    * Compare two image location.
    */
   public boolean equals(Object obj) {
      try {
         ImageLocation iloc = (ImageLocation) obj;

         return path.equals(iloc.path) && pathtype == iloc.pathtype &&
            dir.equals(iloc.dir) && lastModified == iloc.lastModified;
      }
      catch(Exception ex) {
      }

      return false;
   }

   public String toString() {
      return super.toString() + " Path:" + path + " Type:" + pathtype +
         " embeded:" + embed + " dir:" + dir + " Last Modified:" + lastModified;
   }

   private String path;
   private int pathtype = FULL_PATH;
   private boolean embed = true;
   private String dir = "."; // current dir (for relative path)
   private long lastModified = -1;

   private static final Logger LOG =
      LoggerFactory.getLogger(ImageLocation.class);
}
