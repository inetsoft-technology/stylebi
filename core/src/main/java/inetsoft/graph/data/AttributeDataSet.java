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
package inetsoft.graph.data;

import java.text.Format;

/**
 * AttributeDataSet, extends DataSet by supporting more attributes,
 * e.g. hyperlink.
 *
 * @version 10
 * @author InetSoft Technology Corp
 */
public interface AttributeDataSet extends DataSet {
   /**
    * Get the hyperlink at the specified cell.
    *
    * @param col the specified column index.
    * @param row the specified row index.
    */
   HRef getHyperlink(int col, int row);

   /**
    * Set the hyperlink at the specified cell.
    */
   void setHyperlink(int col, int row, HRef link);

   /**
    * Set the hyperlink at the specified cell.
    * @param col the specified column name.
    * @param row the specified row index.
    */
   default void setHyperlink(String col, int row, HRef link) {
      int cidx = indexOfHeader(col);
      setHyperlink(cidx, row, link);
   }

   /**
    * Get the hyperlink at the specified cell.
    *
    * @param col the specified column name.
    * @param row the specified row index.
    */
   default HRef getHyperlink(String col, int row) {
      int cidx = indexOfHeader(col);
      return getHyperlink(cidx, row);
   }

   /**
    * Get the drill hyperlinks at the specified cell.
    *
    * @param col the specified column index.
    * @param row the specified row index.
    */
   default HRef[] getDrillHyperlinks(int col, int row) {
      return new HRef[0];
   }

   /**
    * Get the drill hyperlinks at the specified cell.
    *
    * @param col the specified column name.
    * @param row the specified row index.
    */
   default HRef[] getDrillHyperlinks(String col, int row) {
      int cidx = indexOfHeader(col);
      return getDrillHyperlinks(cidx, row);
   }

   /**
    * Get the per cell format.
    *
    * @param row row number.
    * @param col column number.
    *
    * @return format for the specified cell.
    */
   default Format getFormat(int col, int row) {
      return null;
   }

   /**
    * Get the per cell format.
    *
    * @param col the specified column name.
    * @param row the specified row index.
    *
    * @return format for the specified cell.
    */
   default Format getFormat(String col, int row) {
      int cidx = indexOfHeader(col);
      return getFormat(cidx, row);
   }
}
