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
package inetsoft.uql.viewsheet;

import inetsoft.uql.asset.*;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.internal.InputVSAssemblyInfo;
import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.util.*;

/**
 * InputVSAssembly represents one input assembly contained in a
 * <tt>Viewsheet</tt>.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public abstract class InputVSAssembly extends AbstractVSAssembly implements BindableVSAssembly {
   /**
    * Constructor.
    */
   public InputVSAssembly() {
      super();
   }

   /**
    * Constructor.
    */
   public InputVSAssembly(Viewsheet vs, String name) {
      super(vs, name);
   }

   /**
    * Get the InputVSAssemblyInfo.
    * @return the InputVSAssemblyInfo.
    */
   protected InputVSAssemblyInfo getInputVSAssemblyInfo() {
      return (InputVSAssemblyInfo) getInfo();
   }

   /**
    * Get the worksheet assemblies depended on.
    * @return the worksheet assemblies depended on.
    */
   @Override
   public AssemblyRef[] getDependedWSAssemblies() {
      return new AssemblyRef[0];
   }

   /**
    * Get the depending worksheet assemblies to modify.
    * @return the depending worksheet assemblies to modify.
    */
   @Override
   public AssemblyRef[] getDependingWSAssemblies() {
      String table = getTableName();
      Worksheet ws = getWorksheet();
      Assembly assembly = ws == null || table == null ? null :
         ws.getAssembly(table);

      if(assembly instanceof TableAssembly) {
         return new AssemblyRef[] {new AssemblyRef(AssemblyRef.OUTPUT_DATA,
            assembly.getAssemblyEntry())};
      }

      if(isVariable() && table != null && ws != null) {
         String name = table;

         if(table.startsWith("$(")) {
            name = table.substring(2, table.length() - 1);
         }

         assembly = ws.getAssembly(name);

         if(assembly instanceof VariableAssembly) {
            return new AssemblyRef[] {new AssemblyRef(AssemblyRef.OUTPUT_DATA,
               assembly.getAssemblyEntry())};
         }
      }
      // if an input is not bound to anything, it is treated as a variable
      // with the input assembly name by default
      else if(table == null && ws != null) {
         List<AssemblyRef> condtbls = new ArrayList<>();
         String name = getName();
         Collection<Assembly> tables = getViewsheet().getTables(name);

         if(tables != null) {
            for(Assembly wstable : tables) {
               condtbls.add(new AssemblyRef(AssemblyRef.OUTPUT_DATA, wstable.getAssemblyEntry()));
            }
         }

         return condtbls.toArray(new AssemblyRef[condtbls.size()]);
      }

      return new AssemblyRef[0];
   }

   /**
    * Get the target column.
    * @return the target column of this assembly info.
    */
   public DataRef getColumn() {
      return getInputVSAssemblyInfo().getColumn();
   }

   /**
    * Set the target column to this assembly info.
    * @param column the specified target column.
    */
   public void setColumn(DataRef column) {
      getInputVSAssemblyInfo().setColumn(column);
   }

    /**
    * Get the target column value.
    * @return the target column of this assembly info.
    */
   public String getColumnValue() {
      return getInputVSAssemblyInfo().getColumnValue();
   }

   /**
    * Set the target column value to this assembly info.
    * @param column the specified target column.
    */
   public void setColumnValue(String columnValue) {
      getInputVSAssemblyInfo().setColumnValue(columnValue);
   }

   /**
    * Get the target data type.
    * @return the target data type of this assembly info.
    */
   public String getDataType() {
      return getInputVSAssemblyInfo().getDataType();
   }

   /**
    * Set the data type to this assembly info.
    * @param dtype the specified data type.
    */
   public void setDataType(String dtype) {
      getInputVSAssemblyInfo().setDataType(dtype);
   }

   /**
    * Get the target row.
    * @return the target row of this assembly info.
    */
   public int getRow() {
      return getInputVSAssemblyInfo().getRow();
   }

   /**
    * Get the target rowValue.
    * @return the target row of this assembly info.
    */
   public String getRowValue() {
      return getInputVSAssemblyInfo().getRowValue();
   }

   /**
    * Set the target rowValue to this assembly info.
    * @param row the specified target row.
    */
   public void setRowValue(String rowValue) {
      getInputVSAssemblyInfo().setRowValue(rowValue);
   }

   /**
    * Get the name of the target table.
    * @return the name of the target table.
    */
   @Override
   public String getTableName() {
      return getInputVSAssemblyInfo().getTableName();
   }

   /**
    * Set the name of the target table.
    * @param table the specified name of the target table.
    */
   @Override
   public void setTableName(String table) {
      getInputVSAssemblyInfo().setTableName(table);
   }

   /**
    * Get whether the assembly is variable input.
    * @return true if the assembly is variable input.
    */
   public boolean isVariable() {
      return getInputVSAssemblyInfo().isVariable();
   }

   /**
    * Set the assembly is variable input.
    * @param variable the specified type of the assembly.
    */
   public void setVariable(boolean variable) {
      getInputVSAssemblyInfo().setVariable(variable);
   }

   @Override
   public void removeBindingCol(String ref) {
      // do nothing
   }

   @Override
   public void renameBindingCol(String oname, String nname) {
      // do nothing
   }

   /**
    * Change calc type: detail and aggregate.
    */
   @Override
   public void changeCalcType(String refName, CalculateRef ref) {
      // do nothing
   }

   /**
    * Write the state.
    * @param writer the specified print writer.
    */
   @Override
   protected void writeStateContent(PrintWriter writer, boolean runtime) {
      super.writeStateContent(writer, runtime);
      writer.print("<strictNull><![CDATA[true]]></strictNull>");
   }

   /**
    * Parse the state.
    * @param elem the specified xml element.
    * @param runtime if is runtime mode, default is true.
    */
   @Override
   protected void parseStateContent(Element elem, boolean runtime)
      throws Exception
   {
      super.parseStateContent(elem, runtime);
      Element snode = Tool.getChildNodeByTagName(elem, "strictNull");

      if(snode != null) {
         boolean strictNull = "true".equalsIgnoreCase(Tool.getValue(snode));
         getInputVSAssemblyInfo().setStrictNull(strictNull);
      }
   }
}
