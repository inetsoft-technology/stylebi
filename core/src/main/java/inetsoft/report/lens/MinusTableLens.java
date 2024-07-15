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
package inetsoft.report.lens;

import inetsoft.report.TableLens;
import inetsoft.report.internal.table.MergedRow;
import inetsoft.report.internal.table.MergedTable;

/**
 * Minus table lens does the minus operation.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class MinusTableLens extends SetTableLens {
   /**
    * Constructor.
    */
   public MinusTableLens() throws Exception {
      super();
   }

   /**
    * Constructor.
    */
   public MinusTableLens(TableLens ltable, TableLens rtable) {
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
    * Check if only left data might be contained in the result set.
    * @return <tt>true</tt> if yes, <tt>false</tt>.
    */
   @Override
   boolean isLeftOnly() {
      return true;
   }

   /**
    * Merged row visitor.
    */
   private class Visitor implements MergedTable.Visitor {
      @Override
      public void visit(MergedRow val) throws Exception {
         boolean matched = true;
         
         if(val.getTableCount() > 0) {
            matched = false;
            
            for(int i = 1; i < val.getTableCount(); i++) {
               if(val.getRows(i).length > 0) {
                  matched = true;
                  break;
               }
            }
         }
         
         if(!matched) {
            Row row = new Row(0, val.getRows(0)[0]);
            
            if(!isSetRowsInitialized()) {
               throw new InterruptedException("I am interrupted!");
            }

            addSetRow(row);

            if(getSetRowCount() % 20 == 0) {
               synchronized(MinusTableLens.this) {
                  // notify waiting consumers
                  MinusTableLens.this.notifyAll();
               }
            }
         }
      }
   }
}
