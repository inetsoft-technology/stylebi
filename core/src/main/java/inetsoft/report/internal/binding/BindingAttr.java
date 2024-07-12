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
package inetsoft.report.internal.binding;

import inetsoft.report.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;

/**
 * This is the top-level class that stores all binding related information.
 * All data related information is stored in the DataAttr objects. All non-data
 * information is in a BindingOption object.
 *
 * @version 9.1
 * @author InetSoft Technology Corp
 */
public class BindingAttr implements Cloneable, Serializable {
   /**
    * Create all non-grouping (group or crosstab) filters.
    */
   public static final int NOGROUPING_FILTER = 1;
   /**
    * Create only grouping filters.
    */
   public static final int GROUPING_FILTER = 2;
   /**
    * Create visibility filter.
    */
   public static final int VISIBLE_FILTER = 4;
   /**
    * Create all filters.
    */
   public static final int ALL_FILTER = NOGROUPING_FILTER | GROUPING_FILTER |
      VISIBLE_FILTER;

   /**
    * A binding defined on a cube.
    */
   public static final int CUBE = 2;
   /**
    * A binding defined on an element supporting tabular resultset.
    */
   public static final int GROUPABLE = 3;

   /**
    * Create a binding attribute class for an element.
    * @param elemclass the class of the element this binding attr is for.
    */
   public BindingAttr(Class elemclass) {
      if(ChartElement.class.isAssignableFrom(elemclass)) {
         option = new ChartOption();
      }
   }

   /**
    * Set the type of the data binding. One of the constants defined in this
    * class.
    */
   public void setType(int type) {
      this.type = type;
   }

   /**
    * Get the type of the data binding.
    */
   public int getType() {
      return type;
   }

   /**
    * Get the report option attributes.
    */
   public BindingOption getBindingOption() {
      return option;
   }

   /**
    * Make a copy of this object.
    */
   @Override
   public Object clone() {
      try {
         BindingAttr attr = (BindingAttr) super.clone();

         if(option != null) {
            attr.option = (BindingOption) option.clone();
         }

         return attr;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone binding attributes", ex);
      }

      return null;
   }

   public String toString() {
      return super.toString();
   }

   /**
    * Check if binding changed.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof BindingAttr)) {
         return false;
      }

      BindingAttr battr = (BindingAttr) obj;

      return equals(option, battr.option);
   }

   /**
    * Check if two objects is equals.
    */
   private static boolean equals(Object obj1, Object obj2) {
      if(obj1 == null || obj2 == null) {
         return obj1 == obj2;
      }

      return obj1.equals(obj2);
   }

   private int type = GROUPABLE;
    private BindingOption option;

   private static final Logger LOG =
      LoggerFactory.getLogger(BindingAttr.class);
}
