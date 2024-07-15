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
package inetsoft.uql.asset;

import inetsoft.uql.*;
import inetsoft.uql.asset.internal.*;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.UserVariable;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * composed table assembly, table asesmble composed of sub table assemblies.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public abstract class ComposedTableAssembly extends AbstractTableAssembly {
   /**
    * Get the table assembly count.
    * @return the table assembly count.
    */
   public abstract int getTableAssemblyCount();

   /**
    * Get all the table assemblies.
    * @return all the table assemblies of the composite table assembly.
    */
   protected abstract TableAssembly[] getTableAssemblies();

   /**
    * Set all the table assemblies.
    * @param tables the specified table assemblies.
    * @return false if the change is rejected.
    */
   public abstract boolean setTableAssemblies(TableAssembly[] tables);

   /**
    * Get the table names.
    */
   public abstract String[] getTableNames();

   /**
    * Constructor.
    */
   public ComposedTableAssembly() {
      super();
   }

   /**
    * Constructor.
    */
   public ComposedTableAssembly(Worksheet ws, String name) {
      super(ws, name);
   }

   /**
    * Get all the table assemblies.
    * @param cache <tt>true</tt> to cache the cloned table assembies,
    * <tt>false</tt> otherwise.
    * @return all the table assemblies of the composite table assembly.
    */
   public TableAssembly[] getTableAssemblies(boolean cache) {
      if(!cache) {
         return getTableAssemblies();
      }
      else {
         TableAssembly[] ctables = this.ctables;

         if(ctables == null) {
            ctables = getTableAssemblies();

            for(int i = 0; ctables != null && i < ctables.length; i++) {
               ctables[i] = (TableAssembly) ctables[i].clone();
            }

            this.ctables = ctables;
         }

         return ctables;
      }
   }

   /**
    * Get the sub table assembly.
    */
   public TableAssembly getTableAssembly(String name) {
      String[] tnames = getTableNames();

      for(int i = 0; i < tnames.length; i++) {
         if(name.equals(tnames[i])) {
            return (TableAssembly) ws.getAssembly(name);
         }
      }

      return null;
   }

   /**
    * Clear cache.
    */
   @Override
   public void clearCache() {
      super.clearCache();
      ctables = null;
   }

   /**
    * Check if is composed.
    * @return <tt>true</tt> if composed, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isComposed() {
      return getComposedTableInfo().isComposed();
   }

   /**
    * Check if show in a hierarchical mode.
    * @return <tt>true</tt> to show in a hierarchical mode, <tt>false</tt> to
    * show metadata.
    */
   public boolean isHierarchical() {
      return getComposedTableInfo().isHierarchical();
   }

   /**
    * Set the hierarchical option.
    * @param hier <tt>true</tt> to show in a hierarchical mode, <tt>false</tt>
    * to show metadata.
    */
   public void setHierarchical(boolean hier) {
      getComposedTableInfo().setHierarchical(hier);
   }

   /**
    * Set whether the child assembly should be iconized.
    */
   public void setIconized(String child, boolean iconized) {
      getComposedTableInfo().setIconized(child, iconized);
   }

   /**
    * Check whether the child assembly should be iconized.
    */
   public boolean isIconized(String child) {
      return getComposedTableInfo().isIconized(child);
   }

   /**
    * Create assembly info.
    * @return the associated assembly info.
    */
   @Override
   protected WSAssemblyInfo createInfo() {
      return new ComposedTableAssemblyInfo();
   }

   /**
    * Get the table assembly info.
    * @return the table assembly info of the table assembly.
    */
   @Override
   public TableAssemblyInfo getTableInfo() {
      return getComposedTableInfo();
   }

   /**
    * Get the composed table assembly info.
    * @return the composed table assembly info of the table assembly.
    */
   protected ComposedTableAssemblyInfo getComposedTableInfo() {
      return (ComposedTableAssemblyInfo) info;
   }

   /**
    * Check if the mirror assembly is valid.
    */
   @Override
   public void checkValidity(boolean checkCrossJoins) throws Exception {
      super.checkValidity(checkCrossJoins);
      TableAssembly[] tables = getTableAssemblies();

      if(tables == null) {
         throw new Exception("No tables found!");
      }

      for(int i = 0; i < tables.length; i++) {
         tables[i].checkValidity(checkCrossJoins);
      }
   }

   /**
    * Update the assembly.
    * @return <tt>true</tt> if successful, <tt>false</tt> otherwise.
    */
   @Override
   public boolean update() {
      if(!super.update()) {
         return false;
      }

      this.ctables = null;
      return true;
   }

   /**
    * Replace all embeded user variables.
    * @param vars the specified variable table.
    */
   @Override
   public void replaceVariables(VariableTable vars) {
      super.replaceVariables(vars);
      this.ctables = null;
      TableAssembly[] tables = getTableAssemblies(true);

      if(tables == null) {
         return;
      }

      for(int i = 0; i < tables.length; i++) {
         tables[i].replaceVariables(vars);
      }
   }

   /**
    * Get all variables in the condition value list.
    * @return the variable list.
    */
   @Override
   public UserVariable[] getAllVariables() {
      return getAllVariables(true);
   }

   /**
    * Get all variables in the condition value list.
    * @return the variable list.
    */
   public UserVariable[] getAllVariables(boolean recursive) {
      UserVariable[] vars = super.getAllVariables();
      List list = vars.length > 0 ? new ArrayList() : null;

      for(int i = 0; i < vars.length; i++) {
         list.add(vars[i]);
      }

      if(recursive) {
         TableAssembly[] tables = getTableAssemblies();

         if(tables == null) {
            return new UserVariable[0];
         }

         for(int i = 0; i < tables.length; i++) {
            vars = tables[i].getAllVariables();

            if(vars.length > 0 && list == null) {
               list = new ArrayList();
            }

            for(int j = 0; j < vars.length; j++) {
               if(!list.contains(vars[j])) {
                  list.add(vars[j]);
               }
            }
         }
      }

      if(list == null) {
         return new UserVariable[0];
      }

      vars = new UserVariable[list.size()];
      list.toArray(vars);
      return vars;
   }

   /**
    * Get the source of the table assembly.
    * @return the source of the table assembly.
    */
   @Override
   public String getSource() {
      TableAssembly[] tables = getTableAssemblies();
      String source = null;

      if(tables == null || tables.length == 0) {
         return null;
      }

      for(int i = 0; i < tables.length; i++) {
         if(i == 0) {
            source = tables[i].getSource();

            if(source == null) {
               return null;
            }
         }
         else {
            String source2 = tables[i].getSource();

            if(!Tool.equals(source, source2)) {
               return null;
            }
         }
      }

      return source;
   }

   /**
    * Rename the assemblies depended on.
    * @param oname the specified old name.
    * @param nname the specified new name.
    */
   @Override
   public void renameDepended(String oname, String nname) {
      super.renameDepended(oname, nname);

      renameColumnSelection(getColumnSelection(false), oname, nname);
      renameColumnSelection(getColumnSelection(true), oname, nname);
      renameSortInfo(oname, nname);
   }

   /**
    * Rename the column selection.
    * @param columns the specified column selections.
    * @param oname the specified old name.
    * @param nname the specified new name.
    */
   protected void renameColumnSelection(ColumnSelection columns, String oname,
                                        String nname) {
      for(int i = 0; i < columns.getAttributeCount(); i++) {
         ColumnRef column = (ColumnRef) columns.getAttribute(i);
         ColumnRef.renameColumn(column, oname, nname);
      }
   }

   /**
    * Rename the conditon list wrapper.
    * @param conds the specified condition list wrapper.
    * @param oname the specified old name.
    * @param nname the specified new name.
    * @param ws the associated worksheet.
    */
   @Override
   protected void renameConditionListWrapper(ConditionListWrapper conds,
                                             String oname, String nname,
                                             Worksheet ws) {
      super.renameConditionListWrapper(conds, oname, nname, ws);

      for(int i = 0; i < conds.getConditionSize(); i += 2) {
         ConditionItem item = conds.getConditionItem(i);
         DataRef ref = item.getAttribute();

         if(ref instanceof AggregateRef) {
            ref = ((AggregateRef) ref).getDataRef();
         }

         if(ref instanceof GroupRef) {
            ref = ((GroupRef) ref).getDataRef();
         }

         if(ref instanceof ColumnRef) {
            ColumnRef column = (ColumnRef) ref;
            ColumnRef.renameColumn(column, oname, nname);
         }

         XCondition cond = item.getXCondition();

         if(cond instanceof Condition) {
            DataRef[] refs = ((Condition) cond).getDataRefValues();

            for(int j = 0; j < refs.length; j++) {
               ref = refs[j];

               if(ref instanceof AggregateRef) {
                  ref = ((AggregateRef) ref).getDataRef();
               }

               if(ref instanceof GroupRef) {
                  ref = ((GroupRef) ref).getDataRef();
               }

               if(ref instanceof ColumnRef) {
                  ColumnRef column = (ColumnRef) ref;
                  ColumnRef.renameColumn(column, oname, nname);
               }
            }
         }

         if(cond instanceof RankingCondition) {
            ref = ((RankingCondition) cond).getDataRef();

            if(ref instanceof AggregateRef) {
               ref = ((AggregateRef) ref).getDataRef();
            }

            if(ref instanceof GroupRef) {
               ref = ((GroupRef) ref).getDataRef();
            }

            if(ref instanceof ColumnRef) {
               ColumnRef.renameColumn((ColumnRef) ref, oname, nname);
            }
         }
      }
   }

   /**
    * Rename the aggregate info.
    * @param oname the specified old name.
    * @param nname the specified new name.
    */
   @Override
   protected void renameAggregateInfo(String oname, String nname) {
      super.renameAggregateInfo(oname, nname);
      GroupRef[] groups = ginfo.getGroups();

      for(int i = 0; i < groups.length; i++) {
         ColumnRef column = (ColumnRef) groups[i].getDataRef();
         ColumnRef.renameColumn(column, oname, nname);
      }

      AggregateRef[] aggregates = ginfo.getAggregates();

      for(int i = 0; i < aggregates.length; i++) {
         ColumnRef column = (ColumnRef) aggregates[i].getDataRef();
         ColumnRef.renameColumn(column, oname, nname);
         column = (ColumnRef) aggregates[i].getSecondaryColumn();

         if(column != null) {
            ColumnRef.renameColumn(column, oname, nname);
         }
      }
   }

   /**
    * Rename the sort info.
    * @param oname the specified old name.
    * @param nname the specified new name.
    */
   protected void renameSortInfo(String oname, String nname) {
      SortRef[] sorts = getSortInfo().getSorts();

      for(int i = 0; i < sorts.length; i++) {
         ColumnRef column = (ColumnRef) sorts[i].getDataRef();
         ColumnRef.renameColumn(column, oname, nname);
      }
   }

   /**
    * Get the hash code only considering content.
    * @return the hash code only considering content.
    */
   @Override
   public int getContentCode() {
      int hash = super.getContentCode();
      TableAssembly[] tables = getTableAssemblies(true);

      for(int i = 0; tables != null && i < tables.length; i++) {
         hash = hash ^ tables[i].getContentCode();
      }

      return hash;
   }

   /**
    * Print the key to identify this content object. If the keys of two content
    * objects are equal, the content objects are equal too.
    */
   @Override
   public boolean printKey(PrintWriter writer) throws Exception {
      boolean success = super.printKey(writer);

      if(!success) {
         return false;
      }

      writer.print("Composed[");
      TableAssembly[] tables = getTableAssemblies(true);

      if(tables != null) {
         writer.print(tables.length);

         for(int i = 0; i < tables.length; i++) {
            writer.print(",");

            if(!tables[i].printKey(writer)) {
               return false;
            }
         }
      }

      writer.print("]");
      return true;
   }

   /**
    * Check if equals another object in content.
    * @param obj the specified object.
    * @return <tt>true</tt> if equals the object in content, <tt>false</tt>
    * otherwise.
    */
   @Override
   public boolean equalsContent(Object obj) {
      if(!super.equalsContent(obj)) {
         return false;
      }

      if(!(obj instanceof ComposedTableAssembly)) {
         return false;
      }

      ComposedTableAssembly ctable = (ComposedTableAssembly) obj;
      TableAssembly[] tables1 = getTableAssemblies(true);
      TableAssembly[] tables2 = ctable.getTableAssemblies(true);

      if(tables1 == null || tables2 == null) {
         if(tables1 != tables2) {
            return false;
         }
      }
      else {
         if(tables1.length != tables2.length) {
            return false;
         }

         for(int i = 0; i < tables1.length; i++) {
            if(!tables1[i].equalsContent(tables2[i])) {
               return false;
            }
         }
      }

      return true;
   }

   /**
    * Clone the object.
    * @return the cloned object.
    */
   @Override
   public Object clone() {
      try {
         ComposedTableAssembly assembly = (ComposedTableAssembly) super.clone();
         assembly.ctables = null;

         return assembly;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone object", ex);
         return null;
      }
   }

   /**
    * Clear property.
    * @param key the property name.
    */
   @Override
   public void clearProperty(String key) {
      super.clearProperty(key);

      for(int i = 0; ctables != null && i < ctables.length; i++) {
         ctables[i].clearProperty(key);
      }

      TableAssembly[] tables = getTableAssemblies();

      for(int i = 0; tables != null && i < tables.length; i++) {
         tables[i].clearProperty(key);

         if(ws != null) {
            TableAssembly table =
               (TableAssembly) ws.getAssembly(tables[i].getName());

            if(table != null) {
               table.clearProperty(key);
            }
         }
      }
   }

   /**
    * Print the table information.
    */
   @Override
   public void print(int level, StringBuilder sb) {
      super.print(level, sb);
      TableAssembly[] tables = getTableAssemblies();

      for(int i = 0; i < tables.length; i++) {
         printHead(level, sb);
         sb.append("------sub table------");
         sb.append('\n');
         tables[i].print(level + 1, sb);
      }
   }

   /**
    * Set the worksheet.
    * @param ws the specified worksheet.
    */
   @Override
   public void setWorksheet(Worksheet ws) {
      super.setWorksheet(ws);

      for(int i = 0; ctables != null && i < ctables.length; i++) {
         ctables[i].setWorksheet(ws);
      }
   }

   /**
    * update properties of table.
    */
   @Override
   public void updateTable(TableAssembly table) {
      super.updateTable(table);

      if(!(table instanceof ComposedTableAssembly)) {
         return;
      }

      ComposedTableAssembly ctable = (ComposedTableAssembly) table;
      TableAssembly[] tables1 = getTableAssemblies(true);
      TableAssembly[] tables2 = ctable.getTableAssemblies(true);

      if(tables2 == null || tables1 == null) {
         return;
      }

      boolean isUpdate = false;

      if(tables1.length != tables2.length) {
         isUpdate = true;
      }

      for(int i = 0; !isUpdate && i < tables1.length; i++) {
         if(!tables1[i].equalsContent(tables2[i])) {
            isUpdate = true;
         }
      }

      if(isUpdate) {
         setTableAssemblies(tables2.clone());
      }
   }

   private transient TableAssembly[] ctables;

   private static final Logger LOG =
      LoggerFactory.getLogger(ComposedTableAssembly.class);
}
