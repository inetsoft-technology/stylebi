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
package inetsoft.report.style;

import inetsoft.report.StyleConstants;
import inetsoft.report.TableLens;

import java.awt.*;

/**
 * Table style to mimic crosstab for freehand table.
 *
 * @version 13.1, 3/29/2019
 * @auther InetSoft Technology Corp
 */
public class CrosstabStyle extends TableStyle {
   public CrosstabStyle() {
   }

   public CrosstabStyle(TableLens table) {
      super(table);
   }

   @Override
   protected TableLens createStyle(TableLens tbl) {
      return new Style();
   }

   @Override
   public int getAlignment(int r, int c) {
      if(r < getHeaderRowCount()) {
         return StyleConstants.H_CENTER;
      }

      if(c < getHeaderColCount()) {
         return StyleConstants.H_CENTER;
      }

      return StyleConstants.H_RIGHT;
   }

   /**
    * Style lens.
    */
   class Style extends Transparent {
      @Override
      public Color getRowBorderColor(int row, int col) {
         return Color.lightGray;
      }

      @Override
      public Color getColBorderColor(int row, int col) {
         return Color.lightGray;
      }
   }
}
