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

import inetsoft.uql.asset.internal.CompositeTableAssemblyInfo;
import inetsoft.uql.asset.internal.WSAssemblyInfo;
import inetsoft.util.MessageException;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;
import java.util.*;

/**
 * Composite table assembly, contains one or more sub table assemblies.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public abstract class CompositeTableAssembly extends ComposedTableAssembly {
   /**
    * Constructor.
    */
   public CompositeTableAssembly() {
      super();

      tnames = new String[0];
   }

   /**
    * Constructor.
    */
   public CompositeTableAssembly(Worksheet ws, String name,
      TableAssembly[] tables, TableAssemblyOperator[] operators)
   {
      super(ws, name);

      if(!setTableAssemblies(tables)) {
         throw new RuntimeException("Invalid table assemblies found!");
      }

      setOperators(operators);
   }

   /**
    * Get the table names.
    */
   @Override
   public String[] getTableNames() {
      return tnames;
   }

   /**
    * Create assembly info.
    * @return the associated assembly info.
    */
   @Override
   protected WSAssemblyInfo createInfo() {
      return new CompositeTableAssemblyInfo();
   }

   /**
    * Get the composite table assembly info.
    * @return the composite table assembly info of the table assembly.
    */
   protected CompositeTableAssemblyInfo getCompositeTableInfo() {
      return (CompositeTableAssemblyInfo) super.getTableInfo();
   }

   /**
    * Get the index of a table assembly.
    * @param table the specified table assembly.
    * @return the index of the table assembly.
    */
   public int indexOfTableAssembly(TableAssembly table) {
      for(int i = 0; i < tnames.length; i++) {
         if(tnames[i].equals(table.getName())) {
            return i;
         }
      }

      return -1;
   }

   /**
    * Get all the table assemblies.
    * @return all the table assemblies of the composite table assembly.
    */
   @Override
   public TableAssembly[] getTableAssemblies() {
      TableAssembly[] tables = new TableAssembly[tnames.length];

      for(int i = 0; i < tnames.length; i++) {
         TableAssembly table = (ws == null) ? null : (TableAssembly) ws.getAssembly(tnames[i]);

         if(table == null) {
            if(ws == null || ws.log) {
               LOG.error("Table assembly not found: " + tnames[i]);
            }

            return null;
         }

         tables[i] = table;
         table.update();
      }

      return tables;
   }

   /**
    * Get the table assembly count.
    * @return the table assembly count.
    */
   @Override
   public int getTableAssemblyCount() {
      return tnames.length;
   }

   /**
    * Check if the column is a join column.
    * @param table the specified table.
    * @param column the specified column.
    * @return <tt>true</tt> if used, <tt>false</tt> otherwise.
    */
   public boolean isColumnUsed(TableAssembly table, ColumnRef column) {
      boolean found = false;

      for(int i = 0; i < tnames.length; i++) {
         if(tnames[i].equals(table.getName())) {
            found = true;
            break;
         }
      }

      if(!found) {
         return false;
      }

      for(int i = 0; i < tnames.length; i++) {
         if(tnames[i].equals(table.getName())) {
            continue;
         }

         TableAssemblyOperator op = getOperator(tnames[i], table.getName());

         if(isColumnUsed(op, column, false)) {
            return true;
         }

         op = getOperator(table.getName(), tnames[i]);

         if(isColumnUsed(op, column, true)) {
            return true;
         }
      }

      return false;
   }

   /**
    * Check if the column is used.
    * @param operator the specified table assembly operator.
    * @param column the specified column.
    * @param left <tt>true</tt> to check left columns, <tt>false</tt> to
    * check right columns.
    * @return <tt>true</tt> if used, <tt>false</tt> otherwise.
    */
   private boolean isColumnUsed(TableAssemblyOperator operator,
                                ColumnRef column, boolean left) {
      if(operator == null) {
         return false;
      }

      for(int i = 0; i < operator.getOperatorCount(); i++) {
         TableAssemblyOperator.Operator op = operator.getOperator(i);

         if((left && Tool.equals(column, op.getLeftAttribute())) ||
            (!left && Tool.equals(column, op.getRightAttribute())))
         {
            return true;
         }
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

      tnames = Arrays.stream(tables).map(t -> t.getName()).toArray(String[]::new);
      getCompositeTableInfo().resetOperators(Arrays.asList(tnames));
      return true;
   }

   /**
    * Reorder the subtables of this table
    * @param tables the specified table assemblies containing the same elements as the current
    *               subtables
    * @return false if change was rejected
    */
   public boolean reorderTableAssemblies(TableAssembly[] tables) {
      if(tables == null || tables.length != tnames.length) {
         return false;
      }

      final String[] newTNames = Arrays.stream(tables)
         .map(TableAssembly::getName)
         .toArray(String[]::new);

      final List<String> newTNamesList = Arrays.asList(newTNames);
      final List<String> oldTNames = Arrays.asList(tnames);

      if(oldTNames.containsAll(newTNamesList)) {
         tnames = newTNames;
         getCompositeTableInfo().resetOperators(newTNamesList);
         return true;
      }

      return false;
   }

   /**
    * Check if the composite table assembly is valid.
    */
   @Override
   public void checkValidity(boolean checkCrossJoins) throws Exception {
      super.checkValidity(checkCrossJoins);

      Enumeration<?> iter = getOperatorTables();

      while(iter.hasMoreElements()) {
         try {
            String[] pair = (String[]) iter.nextElement();
            TableAssemblyOperator op = getOperator(pair[0], pair[1]);

            op.checkValidity();
            op.validateAttribute(ws, checkCrossJoins);
         }
         catch(CrossJoinException e) {
            String message = String.format(
               "Change in column selection would result in a cross join in %s between %s and %s, " +
                  "removing %s.", getName(), e.getLeftTable(), e.getRightTable(), getName());
            e.setJoinTable(getName());
            throw new MessageException(message, e);
         }
      }
   }

   /**
    * Get the operator count.
    * @return the operator count.
    */
   public int getOperatorCount() {
      return getCompositeTableInfo().getOperatorCount();
   }

   /**
    * Get the operator at an index.
    * @param index the specified index;
    */
   public TableAssemblyOperator getOperator(int index) {
      String ltable = tnames[index];
      String rtable = tnames[index + 1];
      return getOperator(ltable, rtable);
   }

   /**
    * Set the operator at an index.
    * @param operators the specified operator.
    */
   public void setOperators(TableAssemblyOperator[] operators) {
      getCompositeTableInfo().resetOperators(null);

      for(int i = 0; i < operators.length; i++) {
         setOperator(tnames[i], tnames[i + 1], operators[i]);
      }
   }

   /**
    * Set the operator at an index.
    * @param index the specified index.
    * @param operator the specified operator.
    */
   public void setOperator(int index, TableAssemblyOperator operator) {
      String ltable = tnames[index];
      String rtable = tnames[index + 1];
      setOperator(ltable, rtable, operator);
   }

   /**
    * Get all the operators in the table.
    */
   public Enumeration<TableAssemblyOperator> getOperators() {
      return getCompositeTableInfo().getOperators();
   }

   /**
    * Get the table pairs with operators defined.
    * @return table pairs are returned as String[] with exactly two items.
    */
   public Enumeration getOperatorTables() {
      return getCompositeTableInfo().getOperatorTables();
   }

   /**
    * Get the operator of two tables.
    * @param ltable the specified left table.
    * @param rtable the specified right table.
    */
   public TableAssemblyOperator getOperator(String ltable, String rtable) {
      return getCompositeTableInfo().getOperator(ltable, rtable);
   }

   /**
    * Get the operator of a table.
    * @param table the specified table.
    * @return the operator which relates to the table, <tt>null</tt> otherwise.
    */
   public TableAssemblyOperator getOperator(String table) {
      return getCompositeTableInfo().getOperator(table);
   }

   /**
    * Get the operator of a table, prioritizing lower indexed tables.
    * @param table the specified table.
    * @return the operator which relates to the table, <tt>null</tt> otherwise.
    */
   public TableAssemblyOperator getOperator(String table, String[] tnames) {
      return getCompositeTableInfo().getOperator(table, tnames);
   }

   /**
    * Set the operator at an index.
    * @param ltable the specified left table.
    * @param rtable the specified right table.
    * @param operator the specified operator.
    */
   public void setOperator(String ltable, String rtable,
                           TableAssemblyOperator operator) {
      getCompositeTableInfo().setOperator(ltable, rtable, operator);
   }

   /**
    * Remove the operator.
    * @param ltable the specified left table.
    * @param rtable the specified right table.
    * @return true if this table is no longer valid and should be removed.
    */
   public boolean removeOperator(String ltable, String rtable) {
      getCompositeTableInfo().removeOperator(ltable, rtable);
      return false;
   }

   /**
    * Remove a sub-table.
    * @param subtable the specified sub-table.
    * @return true if this table is no longer valid and should be removed.
    */
   public boolean removeTable(String subtable) {
      TableAssembly[] tables = getTableAssemblies();
      Vector vec = new Vector();

      for(int i = 0; i < tables.length; i++) {
         if(!subtable.equals(tables[i].getName())) {
            vec.add(tables[i]);
         }
      }

      // all tables are dis-joint, remove
      if(vec.size() < 2) {
         return true;
      }

      Enumeration iter = getOperatorTables();
      Vector ops = new Vector();

      while(iter.hasMoreElements()) {
         String[] tbls = (String[]) iter.nextElement();

         if(tbls[0].equals(subtable) || tbls[1].equals(subtable)) {
            ops.add(tbls);
         }
      }

      for(int i = 0; i < ops.size(); i++) {
         String[] tbls = (String[]) ops.get(i);
         removeOperator(tbls[0], tbls[1]);
      }

      setTableAssemblies(
         (TableAssembly[]) vec.toArray(new TableAssembly[vec.size()]));
      return false;
   }

   /**
    * Get the assemblies depended on.
    * @param set the set stores the assemblies depended on.
    */
   @Override
   public void getDependeds(Set<AssemblyRef> set) {
      super.getDependeds(set);

      for(int i = 0; i < tnames.length; i++) {
         set.add(new AssemblyRef(new AssemblyEntry(tnames[i], Worksheet.TABLE_ASSET)));
      }
   }

   /**
    * Rename the assemblies depended on.
    * @param oname the specified old name.
    * @param nname the specified new name.
    */
   @Override
   public void renameDepended(String oname, String nname) {
      super.renameDepended(oname, nname);

      for(int i = 0; i < tnames.length; i++) {
         if(oname.equals(tnames[i])) {
            tnames[i] = nname;
         }
      }

      getCompositeTableInfo().renameDepended(oname, nname, ws);
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);
      writer.println("<tables>");

      for(int i = 0; i < tnames.length; i++) {
         writer.print("<oneTableAssembly>");
         writer.print("<![CDATA[" + tnames[i] + "]]>");
         writer.println("</oneTableAssembly>");
      }

      writer.println("</tables>");
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseContents(Element elem) throws Exception {
      super.parseContents(elem);

      Element tsnode = Tool.getChildNodeByTagName(elem, "tables");
      NodeList tnodes = Tool.getChildNodesByTagName(tsnode, "oneTableAssembly");
      tnames = new String[tnodes.getLength()];

      for(int i = 0; i < tnames.length; i++) {
         Element tnode = (Element) tnodes.item(i);
         tnames[i] = Tool.getValue(tnode);
      }
   }

   /**
    * Clone the object.
    * @return the cloned object.
    */
   @Override
   public Object clone() {
      try {
         CompositeTableAssembly assembly = (CompositeTableAssembly) super.clone();
         assembly.tnames = (String[]) tnames.clone();
         return assembly;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone object", ex);
         return null;
      }
   }

    /**
    * Get the hash code only considering content.
    * @return the hash code only considering content.
    */
   @Override
   public int getContentCode() {
      int hash = super.getContentCode();

      for(int i = 0; i < tnames.length; i++) {
         for(int j = i + 1; j < tnames.length; j++) {
            TableAssemblyOperator top = getOperator(tnames[i], tnames[j]);

            if(top != null) {
               hash = hash ^ top.hashCode();
            }
         }
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

      writer.print("Composite[");
      writer.print(tnames.length);

      for(int i = 0; i < tnames.length; i++) {
         for(int j = i + 1; j < tnames.length; j++) {
            TableAssemblyOperator top = getOperator(tnames[i], tnames[j]);

            if(top != null) {
               writer.print(",");
               top.printKey(writer);
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

      if(!(obj instanceof CompositeTableAssembly)) {
         return false;
      }

      CompositeTableAssembly ctable = (CompositeTableAssembly) obj;

      if(tnames.length != ctable.tnames.length) {
         return false;
      }

      for(int i = 0; i < tnames.length; i++) {
         for(int j = i + 1; j < tnames.length; j++) {
            TableAssemblyOperator top1 = getOperator(tnames[i], tnames[j]);
            TableAssemblyOperator top2 =
               ctable.getOperator(ctable.tnames[i], ctable.tnames[j]);
            if(!Tool.equals(top1, top2)) {
               return false;
            }
         }
      }

      return true;
   }

   /**
    * Print the table information.
    */
   @Override
   public void print(int level, StringBuilder sb) {
      for(int i = 0; i < tnames.length - 1; i++) {
         for(int j = i; j < tnames.length; j++) {
            TableAssemblyOperator top = getOperator(tnames[i], tnames[j]);

            if(top != null) {
               printHead(level, sb);
               sb.append(getName() + " op[" + tnames[i] + ", " + tnames[j] + "]: " + top);
               sb.append('\n');
            }
         }
      }

      super.print(level, sb);
   }

   /**
    * update properties of table.
    */
   @Override
   public void updateTable(TableAssembly table) {
      super.updateTable(table);

      if(!(table instanceof CompositeTableAssembly)) {
         return;
      }

      CompositeTableAssembly ctable = (CompositeTableAssembly) table;
      boolean isUpdate = false;

      if(tnames == null || ctable.tnames == null) {
         return;
      }

      if(tnames.length != ctable.tnames.length) {
         isUpdate = true;
      }

      for(int i = 0; !isUpdate && i < tnames.length; i++) {
         for(int j = i + 1; j < tnames.length; j++) {
            TableAssemblyOperator top1 = getOperator(tnames[i], tnames[j]);
            TableAssemblyOperator top2 = ctable.getOperator(ctable.tnames[i], ctable.tnames[j]);

            if(!Tool.equals(top1, top2)) {
               isUpdate = true;
               break;
            }
         }
      }

      if(isUpdate) {
         ArrayList<TableAssemblyOperator> ops = new ArrayList<>();
         Enumeration opIterator = ctable.getOperators();

         while(opIterator.hasMoreElements()) {
            ops.add((TableAssemblyOperator)
               ((TableAssemblyOperator) opIterator.nextElement()).clone());
         }

         setTableAssemblies(ctable.getTableAssemblies(true).clone());
         setOperators(ops.toArray(new TableAssemblyOperator[0]));
      }
   }

   protected String[] tnames;

   private static final Logger LOG =
      LoggerFactory.getLogger(CompositeTableAssembly.class);
}
