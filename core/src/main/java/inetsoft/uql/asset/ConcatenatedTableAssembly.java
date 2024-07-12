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
package inetsoft.uql.asset;

import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.util.Tool;
import inetsoft.web.composer.model.ws.DependencyType;

import java.awt.*;
import java.util.*;

/**
 * Concatenated table assembly, contains concatenated sub table assemblies.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class ConcatenatedTableAssembly extends CompositeTableAssembly {
   /**
    * Constructor.
    */
   public ConcatenatedTableAssembly() {
      super();
   }

   /**
    * Constructor.
    */
   public ConcatenatedTableAssembly(Worksheet ws, String name,
      TableAssembly[] tables, TableAssemblyOperator[] operators)
   {
      super(ws, name, tables, operators);
   }

   /**
    * Remove a sub-table.
    * @param subtable the specified sub-table.
    * @return true if this table is no longer valid and should be removed.
    */
   @Override
   public boolean removeTable(String subtable) {
      TableAssemblyOperator op = getOperator(0);

      boolean rmAll = super.removeTable(subtable);

      // make sure the operators are valid after a table is removed
      for(int i = 0; i < tnames.length - 1; i++) {
         setOperator(tnames[i], tnames[i + 1], op);
      }

      return rmAll;
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
      if(!operator.isConcatenation()) {
         throw new RuntimeException("Only concatenation operation is allowed!");
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

      Enumeration iter = getOperatorTables();
      final Set<String> joinedTables = new HashSet<>();

      while(iter.hasMoreElements()) {
         String[] pair = (String[]) iter.nextElement();
         joinedTables.add(pair[0]);
         joinedTables.add(pair[1]);
      }

      TableAssembly[] tables = getTableAssemblies();
      final Vector<TableAssembly> vec = new Vector<>();

      for(TableAssembly table : tables) {
         if(joinedTables.contains(table.getName())) {
            vec.add(table);
         }
      }

      // all tables are dis-joint, remove
      if(vec.size() < 2) {
         return true;
      }

      setTableAssemblies(vec.toArray(new TableAssembly[0]));
      return false;
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

         for(TableAssembly table : tables) {
            Dimension size = table.getMinimumSize(true);
            width = Math.max(size.width, width);
            height += size.height;
         }

         width += getExpressionWidth(embedded); // expression count
         height += AssetUtil.defh; // to draw table header
         width += AssetUtil.defw; // to draw operators
         height = Math.max(3 * AssetUtil.defh, height);
         return new Dimension(width, height);
      }
   }

   /**
    * Check if the mirror assembly is valid.
    */
   @Override
   public void checkValidity(boolean checkCrossJoins) throws Exception {
      super.checkValidity(checkCrossJoins);
      TableAssembly[] tables = getTableAssemblies();

      if(tables == null || tables.length == 0) {
         return;
      }

      ColumnSelection cols = getColumnSelection();
      ColumnSelection subs = tables[0].getColumnSelection();
      boolean changed = false;
      // make sure the column type is in-sync with sub-tables
      if(cols.getAttributeCount() == subs.getAttributeCount()) {
         for(int i = 0; i < cols.getAttributeCount(); i++) {
            ColumnRef col1 = (ColumnRef) cols.getAttribute(i);
            ColumnRef col2 = (ColumnRef) subs.getAttribute(i);
            String ref1 = col1.getAttribute();
            String ref2 = col2.getAttribute();

            if(!Tool.equals(ref1, ref2)) {
               for(int j = 0; j < subs.getAttributeCount(); j++) {
                  ColumnRef col3 = (ColumnRef) subs.getAttribute(j);

                  if(Tool.equals(ref1, col3.getAttribute()) ||
                     Tool.equals(ref1, col3.getAlias()))
                  {
                     col2 = col3;
                  }
               }
            }

            if(!Tool.equals(col1.getDataType(), col2.getDataType())) {
               col1.setDataType(col2.getDataType());
               changed = true;
            }
         }

         if(changed) {
            setColumnSelection(cols);
         }
      }
   }

   @Override
   public void getAugmentedDependeds(Map<String, Set<DependencyType>> dependeds) {
      super.getAugmentedDependeds(dependeds);

      for(int i = 0; i < tnames.length; i++) {
         String tname = tnames[i];

         if(i == 0) {
            addToDependencyTypes(dependeds, tname, DependencyType.BASE_CONCATENATED_TABLE);
         }
         else if(getOperator(tname) != null) {
            int operation = getOperator(tname, tnames).getKeyOperator().getOperation();
            DependencyType dependencyType;

            switch(operation) {
               case TableAssemblyOperator.UNION:
                  dependencyType = DependencyType.UNION;
                  break;
               case TableAssemblyOperator.INTERSECT:
                  dependencyType = DependencyType.INTERSECTION;
                  break;
               case TableAssemblyOperator.MINUS:
                  dependencyType = DependencyType.MINUS;
                  break;
               default:
                  dependencyType = null;
            }

            addToDependencyTypes(dependeds, tname, dependencyType);
         }
      }
   }

   /**
    * Check if assemblies are compatible with each other.
    */
   public boolean tableAssembliesAreCompatible() {
      TableAssembly[] assemblies = getTableAssemblies();

      for(int i = 1; i < assemblies.length; i++) {
         if(!areCompatible(assemblies[i - 1], assemblies[i])) {
            return false;
         }
      }

      return true;
   }

   public void initDefaultColumnSelection() {
      final TableAssembly[] subtables = getTableAssemblies(true);
      final ColumnSelection privateColumnSelection = getDefaultColumnSelection(subtables);
      setColumnSelection(privateColumnSelection, false);
   }

   public static ColumnSelection getDefaultColumnSelection(TableAssembly[] subtables) {
      if(subtables == null || subtables.length == 0) {
         return null;
      }

      final TableAssembly firstSubtable = subtables[0];
      final String tname = firstSubtable.getName();

      final ColumnSelection columns = new ColumnSelection();
      final ColumnSelection firstSubtableColumns = firstSubtable.getColumnSelection(true);

      for(int i = 0; i < firstSubtableColumns.getAttributeCount(); i++) {
         final ColumnRef subtableColumn = (ColumnRef) firstSubtableColumns.getAttribute(i);
         final DataRef attr = AssetUtil.getOuterAttribute(tname, subtableColumn);
         String dtype = subtableColumn.getDataType();

         for(int j = 1; j < subtables.length; j++) {
            ColumnSelection subsequentSubtableColumns = subtables[j].getColumnSelection(true);

            if(i < subsequentSubtableColumns.getAttributeCount()) {
               String dtype2 = subsequentSubtableColumns.getAttribute(i).getDataType();
               dtype = XSchema.mergeNumericType(dtype, dtype2);
            }
         }

         final ColumnRef column = new ColumnRef(attr);
         column.setDataType(dtype);
         columns.addAttribute(column);
      }

      return columns;
   }

   /**
    * Check if two tables can be concatenated.
    */
   private static boolean areCompatible(
      TableAssembly leftTable,
      TableAssembly rightTable)
   {
      ColumnSelection leftColumns = leftTable.getColumnSelection(true);
      ColumnSelection rightColumns = rightTable.getColumnSelection(true);

      if(leftColumns.getAttributeCount() != rightColumns.getAttributeCount()) {
         return false;
      }

      for(int i = 0; i < leftColumns.getAttributeCount(); i++) {
         ColumnRef lcolumn = (ColumnRef) leftColumns.getAttribute(i);
         ColumnRef rcolumn = (ColumnRef) rightColumns.getAttribute(i);
         String ltype = lcolumn.getDataType();
         String rtype = rcolumn.getDataType();

         if(!AssetUtil.isMergeable(ltype, rtype)) {
            return false;
         }
      }

      return true;
   }
}
