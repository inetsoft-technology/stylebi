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
package inetsoft.report.internal.table;

import inetsoft.report.BaseLayout;

import java.awt.*;

/**
 * CellHelper, span helper for from rectangle to another rectangle.
 *
 * @version 10.3
 * @author InetSoft Technology Corp
 */
public class CellHelper extends SpanHelper {
   /**
    * Constructor.
    */
   public CellHelper(BaseLayout layout, Rectangle cspan) {
      super(layout);
      this.cspan = cspan;
   }

   /**
    * Check if the span is avaliable.
    */
   @Override
   protected boolean isAvailable(Rectangle span) {
      // another span intersect current span? another is not avaliable
      if(cspan != null) {
         // if a span contains current span wholly, it is avaliable
         // see bug1284437777900, crosstab span bug?
         if(span.contains(cspan)) {
            return true;
         }

         Rectangle sec = cspan.intersection(span);

         if(sec != null && sec.width > 0 && sec.height > 0) {
            return false;
         }
      }

      return true;
   }

   private Rectangle cspan = null;
}
