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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;

/**
 * This is the base class of all other binding option classes.
 *
 * @version 6.0 9/30/2003
 * @author mikec
 */
public abstract class BindingOption implements Cloneable, Serializable {
   /**
    * Clear all settings.
    */
   public void clear() {
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
         LOG.error("Failed to clone binding option", ex);
      }

      return null;
   }

   /**
    * Check if the binding option require runtime binding attr.
    */
   protected boolean requiresRuntime() {
      return false;
   }

   /**
    * Check two option is equals.
    */
   public boolean equals(Object obj) {
      if(obj == null) {
         return false;
      }

      return obj instanceof BindingOption &&
         getClass().getName().equals(obj.getClass().getName());
   }

   /**
    * Check if 'Others' group should always be sorted as the last item.
    */
   public boolean isSortOthersLast() {
      return false;
   }

   private static final Logger LOG =
      LoggerFactory.getLogger(BindingOption.class);
}
