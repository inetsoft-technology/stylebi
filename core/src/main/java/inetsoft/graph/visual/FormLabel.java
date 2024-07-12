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
package inetsoft.graph.visual;

import inetsoft.graph.TextSpec;
import inetsoft.graph.guide.VLabel;

/**
 * VLabel for form elements.
 *
 * @version 12.2
 * @author InetSoft Technology
 */
public class FormLabel extends VLabel {
   public FormLabel(Object label, TextSpec textSpec) {
      super(label, textSpec);
   }

   /**
    * Set whether this label can be removed by text layout manager.
    */
   public void setRemovable(boolean removeable) {
      this.removeable = removeable;
   }

   /**
    * Check whether this label can be removed by text layout manager.
    */
   @Override
   public boolean isRemovable() {
      return removeable;
   }

   private boolean removeable = true;
}
