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
package inetsoft.report.internal.info;

import inetsoft.report.TableDataPath;
import inetsoft.report.internal.Cacheable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;

/**
 * A TableLayoutRowInfo is to store some table layout row information.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class TableLayoutRowInfo implements Serializable, Cacheable {
   /**
    * Default row height.
    */
   public static final float DEFAULT_HEIGHT = 15f;

   /**
    * Constructor.
    */
   public TableLayoutRowInfo() {
   }

   /**
    * Constructor.
    * @param height the specified row height.
    * @param datapath the specified row datapath.
    */
   public TableLayoutRowInfo(float height, TableDataPath datapath) {
      this.height = height == -1 ? DEFAULT_HEIGHT : height;
      this.datapath = datapath;
   }

   /**
    * Clone method.
    */
   @Override
   public Object clone() {
      try {
         TableLayoutRowInfo info = (TableLayoutRowInfo) super.clone();

         if(datapath != null) {
            info.datapath = (TableDataPath) datapath.clone();
         }

         return info;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone table layout row info", ex);
         return this;
      }
   }

   /**
    * Set true if the row height has been cleared, otherwise false.
    */
   public void setHeightCleared(boolean heightCleared) {
      this.heightCleared = heightCleared;
   }

   public TableDataPath getPath() {
      return datapath;
   }

   public float getHeight() {
      return height;
   }

   public void setHeight(float h) {
      height = h;
   }

   private float height;
   private TableDataPath datapath = null;
   private boolean heightCleared = false;

   private static final Logger LOG =
      LoggerFactory.getLogger(TableLayoutRowInfo.class);
}
