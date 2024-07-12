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

import inetsoft.report.internal.binding.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A sectionInfo is store some attribute of sectionelement to
 * process some attribute in the composer
 */

public class SectionElementInfo extends ElementInfo implements TableGroupableInfo {
   /**
    * build the class from the super
    */
   public SectionElementInfo() {
      super();
   }

   /**
    * Set the filter info holder in this table.
    */
   @Override
   public void setBindingAttr(BindingAttr infos) {
      filters = infos;
   }

   /**
    * Get the filter info holder of this table.
    */
   @Override
   public BindingAttr getBindingAttr() {
      return filters;
   }

   /**
    * Clone the object.
    */
   @Override
   public Object clone() {
      try {
         SectionElementInfo tinfo = (SectionElementInfo) super.clone();

         if(filters != null) {
            tinfo.setBindingAttr((BindingAttr) filters.clone());
         }

         return tinfo;
      }
      catch(Exception e) {
         LOG.error("Failed to clone section element info", e);
      }

      return null;
   }

   private BindingAttr filters;

    private static final Logger LOG =
      LoggerFactory.getLogger(SectionElementInfo.class);
}
