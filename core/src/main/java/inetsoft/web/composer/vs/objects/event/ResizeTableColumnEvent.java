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
package inetsoft.web.composer.vs.objects.event;

/**
 * Class that encapsulates the parameters for resizing a table column.
 *
 * @since 12.3
 */
public class ResizeTableColumnEvent extends VSObjectEvent {
   public double[] getWidths() {
      return widths;
   }

   public void setWidths(double[] widths) {
      this.widths = widths;
   }

   public int getStartCol() {
      return startCol;
   }

   public void setStartCol(int startCol) {
      this.startCol = startCol;
   }

   public int getEndCol() {
      return endCol;
   }

   public void setEndCol(int endCol) {
      this.endCol = endCol;
   }

   public int getRow() {
      return row;
   }

   public void setRow(int row) {
      this.row = row;
   }

   // new column widths
   private double[] widths;

   // the column that was resized
   private int startCol;

   // starting column + the colspan value
   private int endCol;

   // row to get the datapath to the cell
   private int row;
}
