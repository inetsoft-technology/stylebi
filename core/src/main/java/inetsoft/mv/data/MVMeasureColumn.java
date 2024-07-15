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
package inetsoft.mv.data;

/**
 *
 * @author InetSoft Technology
 * @version 11.1
 */
public interface MVMeasureColumn extends XMVColumn {
   /**
    * Get the value at the specified index.
    */
   public double getValue(int r);

   /**
    * Set the value at the specified index.
    */
   public void setValue(int idx, double value);

   /**
    * Get the number of bits per value.
    */
   public int getBits();

   /**
    * Set the number of rows (items) in the column.
    */
   public void setRowCount(int rcnt);
}
