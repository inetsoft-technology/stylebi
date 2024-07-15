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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;

/**
 * CalcTableLens cell attribute base structure.
 */
public abstract class CalcCellAttr
   implements Comparable, Cloneable, Serializable
{
   /**
    * Create an cell attr for the specified cell.
    */
   public CalcCellAttr(int row, int col) {
      this.row = row;
      this.col = (short) col;
   }

   /**
    * Get the row index of this attribute.
    */
   public final int getRow() {
      return row;
   }

   /**
    * Set the row index of this attribute.
    */
   public void setRow(int row) {
      this.row = row;
   }

   /**
    * Get the column index of this attribute.
    */
   public final int getCol() {
      return col;
   }

   /**
    * Set the column index of this attribute.
    */
   public void setCol(int col) {
      this.col = (short) col;
   }

   /**
    * Compare two CalcAttr, order by row and column.
    */
   @Override
   public int compareTo(Object o) {
      try {
	 CalcCellAttr attr = (CalcCellAttr) o;

	 if(row == attr.row) {
	    return col - attr.col;
	 }

	 return row - attr.row;
      }
      catch(Exception ex) {
	 return 1;
      }
   }

   @Override
   public Object clone() {
      try {
         CalcCellAttr attr = (CalcCellAttr) super.clone();
         attr.row = row;
         attr.col = col;
         return attr;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone object", ex);
      }

      return null;
   }

   protected int row;
   protected short col;

   private static final Logger LOG =
      LoggerFactory.getLogger(CalcCellAttr.class);
}
