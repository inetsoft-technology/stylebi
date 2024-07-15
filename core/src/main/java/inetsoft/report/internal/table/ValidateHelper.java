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
package inetsoft.report.internal.table;

import inetsoft.report.BaseLayout;
import inetsoft.report.TableLayout;

import java.awt.*;

/**
 * ValidateHelper, span helper for check the span is valid or not.
 *
 * @version 10.3
 * @author InetSoft Technology Corp
 */
public class ValidateHelper extends SpanHelper {
   /**
    * Constructor.
    */
   public ValidateHelper(BaseLayout layout) {
      super(layout);
   }

   /**
    * Check if the span is avaliable.
    */
   @Override
   protected boolean isAvailable(Rectangle span) {
      // span out of bounds? not avaliable
      if(span.x + span.width > layout.getColCount()) {
         return false;
      }

      if(span.y + span.height > layout.getRowCount()) {
         return false;
      }

      // span across region, but not full of region? not avaliable
      // cross region span is only avaliable for normal table,
      // for calc and crosstab it is ok
      if(layout instanceof TableLayout && ((TableLayout) layout).isNormal()) {
         BaseLayout.Region reg1 = layout.locateRegion(span.y);
         BaseLayout.Region reg2 = layout.locateRegion(span.y + span.height - 1);

         if(reg1 != null && reg2 != null && reg1 != reg2) {
            int gr1 = layout.convertToGlobalRow(reg1, 0);

            if(span.y > gr1) {
               return false;
            }

            int gr2 = layout.convertToGlobalRow(reg2, reg2.getRowCount() - 1);

            if(span.y + span.height <= gr2) {
               return false;
            }
         }
      }

      return true;
   }
}
