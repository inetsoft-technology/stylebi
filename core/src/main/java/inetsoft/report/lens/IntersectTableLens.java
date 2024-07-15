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
package inetsoft.report.lens;

import inetsoft.report.TableLens;
import inetsoft.report.internal.table.MergedRow;
import inetsoft.report.internal.table.MergedTable;

/**
 * Intersect table lens does the intersect operation.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class IntersectTableLens extends SetTableLens {
   /**
    * Constructor.
    */
   public IntersectTableLens() throws Exception {
      super();
   }

   /**
    * Constructor.
    */
   public IntersectTableLens(TableLens ltable, TableLens rtable) {
      super(ltable, rtable);
   }

   /**
    * Get the merged table visitor.
    * @return the merged table visitor.
    */
   @Override
   protected MergedTable.Visitor getVisitor() {
      return new Visitor();
   }

   /**
    * Merged row visitor.
    */
   private class Visitor implements MergedTable.Visitor {
      @Override
      public void visit(MergedRow val) throws Exception {
         // @by cehnw, count must equal to final count for multiple table merge.
         if(val.getTableCount() < 2 || val.getTableCount() != getTableCount()) {
            return;
         }

         boolean add = true;

         for(int i = 0; i < val.getTableCount(); i++) {
            if(val.getRows(i).length == 0) {
               add = false;
               break;
            }
         }

         if(add) {
            Row row = new Row(0, val.getRows(0)[0]);

            if(!isSetRowsInitialized()) {
               throw new InterruptedException("I am interrupted!");
            }

            addSetRow(row);

            if(getSetRowCount() % 20 == 0) {
               synchronized(IntersectTableLens.this) {
                  // notify waiting consumers
                  IntersectTableLens.this.notifyAll();
               }
            }
         }
      }
   }
}
