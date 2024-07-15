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
package inetsoft.uql.asset;

import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.TableAssemblyOperator.Operator;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.util.MessageException;

import java.awt.*;
import java.util.Enumeration;

/**
 * Abstract join table assembly, contains joined table assemblies.
 *
 * @version 12.3
 * @author InetSoft Technology Corp
 */
public abstract class AbstractJoinTableAssembly extends CompositeTableAssembly {
   /**
    * Constructor.
    */
   public AbstractJoinTableAssembly() {
      super();
   }

   /**
    * Constructor.
    */
   public AbstractJoinTableAssembly(Worksheet ws, String name,
                                    TableAssembly[] tables, TableAssemblyOperator[] operators)
   {
      super(ws, name, tables, operators);
   }

   /**
    * Set the operator at an index.
    * @param ltable the specified left table.
    * @param rtable the specified right table.
    * @param operator the specified operator.
    */
   @Override
   public void setOperator(String ltable, String rtable,
                           TableAssemblyOperator operator) {
      if(!operator.isJoin()) {
         throw new RuntimeException("Only join operation is allowed!");
      }

      super.setOperator(ltable, rtable, operator);
   }

   /**
    * Remove the operator.
    * @param ltable the specified left table.
    * @param rtable the specified right table.
    * @return true if this table is no longer valid and should be removed.
    */
   @Override
   public boolean removeOperator(String ltable, String rtable) {
      super.removeOperator(ltable, rtable);

      if(getOperatorCount() == 0) {
         TableAssemblyOperator operator = new TableAssemblyOperator();
         Operator op = new Operator();
         op.setOperation(TableAssemblyOperator.CROSS_JOIN);
         operator.addOperator(op);

         setOperator(ltable, rtable, operator);
      }

      return false;
   }

   /**
    * Set all the table assemblies.
    * @param tables the specified table assemblies.
    * @return false if the change is rejected.
    */
   @Override
   public boolean setTableAssemblies(TableAssembly[] tables) {
      if(tables == null || tables.length <= 1) {
         return false;
      }

      for(int i = 0; i < tables.length - 1; i++) {
         // no operator between left table and right table?
         // cross join as default
         // fix bug1261641701999
         if(getOperator(tables[i].getName(), tables[i + 1].getName()) == null) {
            TableAssemblyOperator operator = new TableAssemblyOperator();
            Operator op = new Operator();
            op.setOperation(TableAssemblyOperator.CROSS_JOIN);
            operator.addOperator(op);
            setOperator(tables[i].getName(), tables[i + 1].getName(), operator);
         }
      }

      return super.setTableAssemblies(tables);
   }

   /**
    * Get the minimum size.
    * @param embedded <tt>true</tt> to embed the table assembly.
    * @return the minimum size of the assembly.
    */
   @Override
   public Dimension getMinimumSize(boolean embedded) {
      if(embedded || isLiveData() || isRuntime() || !isHierarchical()) {
         return super.getMinimumSize(embedded);
      }
      else {
         TableAssembly[] tables = getTableAssemblies();

         if(tables == null) {
            return super.getMinimumSize(embedded);
         }

         int width = 0;
         int height = 0;

         for(int i = 0; i < tables.length; i++) {
            Dimension size = tables[i].getMinimumSize(true);

            if(isIconized(tables[i].getName())) {
               width++;
            }
            else {
               width += size.width;
            }

            height = Math.max(size.height, height);
         }

         width += getExpressionWidth(embedded); // expression count

         if(requiresColumn()) {
            height += AssetUtil.defh; // to draw operators
         }

         height += AssetUtil.defh; // to draw table header
         height = Math.max(3 * AssetUtil.defh, height);
         return new Dimension(width, height);
      }
   }

   /**
    * Check if requires column.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   public boolean requiresColumn() {
      Enumeration iter = getOperators();

      while(iter.hasMoreElements()) {
         TableAssemblyOperator op = (TableAssemblyOperator) iter.nextElement();

         if(op.requiresColumn()) {
            return true;
         }
      }

      return false;
   }

   /**
    * Set the column selection.
    * @param selection the specified selection.
    * @param pub <tt>true</tt> indicates the public column selection,
    * <tt>false</tt> otherwise.
    */
   @Override
   public void setColumnSelection(ColumnSelection selection, boolean pub) {
      super.setColumnSelection(selection, pub);

      if(!pub) {
         try {
            checkValidity();
         }
         catch(MessageException cje) {
            throw cje;
         }
         catch(Exception ex) {
            // ignore
         }
      }
   }
}
