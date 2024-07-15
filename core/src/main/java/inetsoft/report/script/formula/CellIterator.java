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
package inetsoft.report.script.formula;

import inetsoft.report.lens.AttributeTableLens;
import inetsoft.uql.XTable;
import org.mozilla.javascript.Scriptable;

import java.awt.*;

/**
 * CellIterator is used to iterate through range of cells for cell selection.
 */
public abstract class CellIterator {
   public CellIterator() {
   }

   /**
    * Check if there is more cells to process.
    */
   public abstract boolean hasNext();

   /**
    * Get the location of the next cell.
    */
   public abstract Point next();

   /**
    * Get the value of the current cell.
    * @param scope the parent scope for executing expression.
    */
   public abstract Object getValue(Scriptable scope);

   /**
    * Get the evaluation scope of the current cell.
    */
   public abstract Scriptable getScope();

   /**
    * Get the value from a table lens.
    */
   protected Object getData(XTable tbl, int row, int col) {
      if(tbl instanceof AttributeTableLens) {
         return ((AttributeTableLens) tbl).getData(row, col);
      }

      return tbl.getObject(row, col);
   }

   protected boolean processCalc = false;
}
