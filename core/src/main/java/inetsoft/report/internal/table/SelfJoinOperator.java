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
package inetsoft.report.internal.table;

import inetsoft.report.Comparer;
import inetsoft.uql.XConstants;
import inetsoft.uql.XTable;
import inetsoft.util.DataComparer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Selft Join operator performs the evaluation task of one self-join operation.
 * Any self join operation MUST be one of the following join operations:<p>
 * <ul>
 * <li><code>INNER_JOIN</code></li>
 * <li><code>NOT_EQUAL_JOIN</code></li>
 * <li><code>GREATER_JOIN</code></li>
 * <li><code>GREATER_EQUAL_JOIN</code></li>
 * <li><code>LESS_JOIN</code></li>
 * <li><code>LESS_EQUAL_JOIN</code></li>
 * </ul>
 * <p>
 * In other words, of all the join operations, outer join operations like
 * <code>LEFT_JOIN<code> are disallowed.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public final class SelfJoinOperator {
   /**
    * Constructor.
    * @param table the specified table.
    * @param col1 the specified left column.
    * @param op the specified operator.
    * @param col2 the specified column2.
    */
   public SelfJoinOperator(XTable table, int col1, int op, int col2) {
      super();

      if(op != XConstants.INNER_JOIN && op != XConstants.NOT_EQUAL_JOIN &&
         op != XConstants.GREATER_JOIN && op != XConstants.GREATER_EQUAL_JOIN &&
         op != XConstants.LESS_JOIN && op != XConstants.LESS_EQUAL_JOIN)
      {
         throw new RuntimeException("Unsupported join operation found: " + op);
      }

      if(col1 < 0 || col1 > table.getColCount()) {
         throw new RuntimeException("Invalid column found: " + col1);
      }

      if(col2 < 0 || col2 > table.getColCount()) {
         throw new RuntimeException("Invalid column found: " + col2);
      }

      if(col1 == col2) {
         throw new RuntimeException("Left and right columns are the same!");
      }

      this.table = table;
      this.col1 = col1;
      this.op = op;
      this.col2 = col2;

      initDataComparer();
   }

   /**
    * Initialize data comparer.
    */
   private void initDataComparer() {
      Comparer lcomp = null;
      Comparer rcomp = null;

      for(int i = table.getHeaderRowCount(); table.moreRows(i); i++) {
         Object obj = table.getObject(i, col1);

         if(obj != null) {
            lcomp = DataComparer.getDataComparer(obj.getClass());
            break;
         }
      }

      for(int i = table.getHeaderRowCount(); table.moreRows(i); i++) {
         Object obj = table.getObject(i, col2);

         if(obj != null) {
            rcomp = DataComparer.getDataComparer(obj.getClass());
            break;
         }
      }

      if(lcomp instanceof DataComparer.StringComparer && rcomp instanceof DataComparer.BooleanComparer ||
         lcomp instanceof DataComparer.BooleanComparer && rcomp instanceof DataComparer.StringComparer)
      {
         this.comp = DataComparer.STRING_COMPARER;
      }
      else if(lcomp != rcomp || lcomp == null || rcomp == null) {
         LOG.warn("Data type does not match");

         this.comp = DataComparer.DEFAULT_COMPARER;
      }
      else {
         this.comp = lcomp;
      }
   }

   /**
    * Get the table.
    * @return the table to perform operation on.
    */
   public XTable getTable() {
      return table;
   }

   /**
    * Get the left column.
    * @return the left column of the operation.
    */
   public int getLeftColumn() {
      return col1;
   }

   /**
    * Get the join operation.
    * @return the join operation.
    */
   public int getOp() {
      return op;
   }

   /**
    * Get the right column.
    * @return the right column of the operation.
    */
   public int getRightColumn() {
      return col2;
   }

   /**
    * Evalulate one row of the table.
    * @param row the specified row index.
    * @return <tt>true</tt> if ok, <tt>false</tt> otherwise.
    */
   public boolean evaluate(int row) {
      try {
         Object obj1 = table.getObject(row, col1);
         Object obj2 = table.getObject(row, col2);

         // keep in sync with sql
         if(obj1 == null || obj2 == null) {
            return false;
         }

         int result = comp.compare(obj1, obj2);

         switch(op) {
            case XConstants.INNER_JOIN:
               return result == 0 || result == DataComparer.IGNORED;
            case XConstants.NOT_EQUAL_JOIN:
               return result != 0 || result == DataComparer.IGNORED;
            case XConstants.GREATER_JOIN:
               return result > 0 || result == DataComparer.IGNORED;
            case XConstants.GREATER_EQUAL_JOIN:
               return result >= 0 || result == DataComparer.IGNORED;
            case XConstants.LESS_JOIN:
               return result < 0 || result == DataComparer.IGNORED;
            case XConstants.LESS_EQUAL_JOIN:
               return result <= 0 || result == DataComparer.IGNORED;
         }

         return true;
      }
      catch(Exception ex) {
         LOG.warn("Failed to evaluate row: " + row, ex);
         // ignore the invalid values
         return false;
      }
   }

   private XTable table;
   private int col1;
   private int op;
   private int col2;
   private Comparer comp;

   private static final Logger LOG =
      LoggerFactory.getLogger(SelfJoinOperator.class);
}
