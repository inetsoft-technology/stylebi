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
package inetsoft.report.lens;

import inetsoft.report.TableLens;

import java.awt.*;

/**
 * Max rows table lens returns limited table rows.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class MaxRowsTableLens2 extends MaxRowsTableLens
{
   /**
    * Constructor.
    */
   public MaxRowsTableLens2() {
      super();
   }

   /**
    * Constructor.
    */
   public MaxRowsTableLens2(TableLens table) {
      super(table);
   }

   /**
    * Constructor.
    */
   public MaxRowsTableLens2(TableLens table, int max) {
      super(table, max);
   }

   @Override
   public Dimension getSpan(int r, int c) {
      Dimension dim = super.getSpan(r, c);

      if(dim != null && r + dim.height > getMaxRows()) {
         dim.height = Math.max(1, getMaxRows() - r);
      }

      return dim;
   }
}
