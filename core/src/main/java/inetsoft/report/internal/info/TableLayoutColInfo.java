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

import inetsoft.report.TableDataPath;
import inetsoft.report.internal.Cacheable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;

/**
 * A TableLayoutColInfo is to store some table layout col information.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class TableLayoutColInfo implements Serializable, Cacheable {
   /**
    * Default column width.
    */
   public static final float DEFAULT_WIDTH = 100f;

   /**
    * Constructor.
    */
   public TableLayoutColInfo() {
   }

   /**
    * Constructor.
    * @param width the specified column width.
    * @param datapath the specified column datapath.
    */
   public TableLayoutColInfo(float width, TableDataPath datapath) {
      this.width = width == -1 ? DEFAULT_WIDTH : width;
      this.datapath = datapath;
   }

   /**
    * Clone method.
    */
   @Override
   public Object clone() {
      try {
         TableLayoutColInfo info = (TableLayoutColInfo) super.clone();

         if(datapath != null) {
            info.datapath = (TableDataPath) datapath.clone();
         }

         return info;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone table layout column info", ex);
         return this;
      }
   }

   /**
    * Get width of col.
    */
   public float getWidth() {
      return width;
   }

   public void setWidth(float w) {
      width = w;
   }

   public TableDataPath getPath() {
      return datapath;
   }

   private float width;
   private TableDataPath datapath = null;

   private static final Logger LOG =
      LoggerFactory.getLogger(TableLayoutColInfo.class);
}
