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
package inetsoft.web.binding.model.table;

import inetsoft.report.internal.info.TableLayoutColInfo;

public class TableColumn extends TableFormatable {
   /**
    * Constructor.
    */
   public TableColumn() {
   }
   
   /**
    * Constructor.
    */
   public TableColumn(TableLayoutColInfo col) {
      super();
      setWidth((int) col.getWidth());
   }
   
   /**
    * Get width.
    * @return the width.
    */
   public int getWidth() {
      return width;
   }
   
   /**
    * Set width.
    * @param width the width.
    */
   public void setWidth(int width) {
      this.width = width;
   }
   
   /**
    * Get col.
    * @return the col.
    */
   public int getCol() {
      return col;
   }

   /**
    * Set col.
    * @param c the col.
    */
   public void setCol(int c) {
      this.col = c;
   }
   
   private int width;
   private int col = -1;
}
