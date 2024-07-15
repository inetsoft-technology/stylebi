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

import java.awt.*;

/**
 * SpanHelper, APIs to process table layout span.
 *
 * @version 10.3
 * @author InetSoft Technology Corp
 */
abstract class SpanHelper {
   /**
    * Constructor.
    */
   public SpanHelper(BaseLayout layout) {
      this.layout = layout;
   }

   /**
    * Process spans.
    */
   public void process() {
      for(int r = 0; r < layout.getRowCount(); r++) {
         for(int c = 0; c < layout.getColCount(); c++) {
            Dimension dim = layout.getSpan(r, c);

            if(dim != null) {
               Rectangle span = new Rectangle(c, r, dim.width, dim.height);

               if(dim.width <= 1 && dim.height <= 1 || !isAvailable(span)) {
                  layout.setSpan(r, c, null);
               }
            }
         }
      }
   }

   /**
    * Check if the span is avaliable.
    */
   protected abstract boolean isAvailable(Rectangle span);

   protected BaseLayout layout;
}
