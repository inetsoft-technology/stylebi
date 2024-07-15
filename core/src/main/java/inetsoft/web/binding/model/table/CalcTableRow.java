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
package inetsoft.web.binding.model.table;

import java.util.ArrayList;
import java.util.List;

public class CalcTableRow extends TableFormatable {
   /**
    * Constructor.
    */
   public CalcTableRow() {
   }
   
   /**
    * Get table cells.
    * @return the table cells.
    */
   public List<CalcTableCell> getTableCells() {
      return cells;
   }
   
   /**
    * Set table cells.
    * @param cells the table cells.
    */
   public void setTableCells(List<CalcTableCell> cells) {
   }

   /**
    * Add table cell.
    * @param cell the table cell.
    */
   public void addTableCell(CalcTableCell cell) {
      cells.add(cell);
   }

   /**
    * Get height.
    * @return the height.
    */
   public int getHeight() {
      return height;
   }
   
   /**
    * Set height.
    * @param height the height.
    */
   public void setHeight(int height) {
      this.height = height;
   }
   
   /**
    * Get text.
    * @return the text.
    */
   public String getText() {
      return text;
   }

   /**
    * Set text.
    * @param text the text.
    */
   public void setText(String text) {
      this.text = text;
   }
   
   /**
    * Set row.
    * @param r the row.
    */
   public void setRow(int r) {
      this.row = r;
   }

   /**
    * Get row.
    * @return the row.
    */
   public int getRow() {
      return row;
   }
   
   private List<CalcTableCell> cells = new ArrayList<>();
   private int height;
   private String text;
   private int row = -1;
}
