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
package inetsoft.report.internal.info;

import inetsoft.report.CellBinding;
import inetsoft.report.TableDataPath;
import inetsoft.report.internal.Cacheable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.Serializable;

/**
 * A TableLayoutCellInfo is to store some table layout cell information.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class TableLayoutCellInfo implements Serializable, Cacheable {
   /**
    * Constructor.
    */
   public TableLayoutCellInfo() {
      super();
   }

   /**
    * Constructor.
    * @param row the specified row number of the cell.
    * @param col the specified column number of the cell.
    * @param cellPath the specified datapath of the cell.
    * @param span the specified span of the cell.
    * @param cellbinding the specified cell binding of the cell.
    */
   public TableLayoutCellInfo(int row, int col, TableDataPath cellPath,
      Dimension span, CellBinding cellbinding, Object val)
   {
      this.row = row;
      this.col = col;
      this.cellPath = cellPath;
      this.cellbinding = cellbinding;
      this.span = span;
      text = (val == null) ? null : val.toString();
   }

   /**
    * Clone method.
    */
   @Override
   public Object clone() {
      try {
         TableLayoutCellInfo info = (TableLayoutCellInfo) super.clone();

         if(cellPath != null) {
            info.cellPath = (TableDataPath) cellPath.clone();
         }

         if(cellbinding != null) {
            info.cellbinding = (CellBinding) cellbinding.clone();
         }

         return info;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone table layout cell info", ex);
         return this;
      }
   }

   /**
    * Get table data path of cell.
    */
   public TableDataPath getCellPath() {
      return cellPath;
   }

   /**
    * Get cell text.
    */
   public String getText() {
      return text;
   }

   public Dimension getSpan() {
      return span;
   }

   private TableDataPath cellPath = null;
   private Dimension span = null;
   private CellBinding cellbinding = null;
   private String text = null;
   private int row = 0;
   private int col = 0;

   private static final Logger LOG =
      LoggerFactory.getLogger(TableLayoutCellInfo.class);
}
