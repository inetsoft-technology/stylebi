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

import inetsoft.uql.XFormatInfo;
import inetsoft.uql.asset.internal.UnpivotTableAssemblyInfo;
import inetsoft.uql.asset.internal.WSAssemblyInfo;
import inetsoft.uql.erm.DataRef;
import inetsoft.util.Tool;
import inetsoft.web.composer.model.ws.DependencyType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.util.*;

/**
 * Un-pivot table assembly, un-pivot a table assembly.
 *
 * @version 11.2
 * @author InetSoft Technology Corp
 */
public class UnpivotTableAssembly extends ComposedTableAssembly {
   /**
    * Constructor.
    */
   public UnpivotTableAssembly() {
      super();
   }

   /**
    * Constructor.
    */
   public UnpivotTableAssembly(Worksheet ws, String name,
                               TableAssembly assembly) {
      super(ws, name);

      this.tname = assembly.getName();
   }

   /**
    * Create assembly info.
    * @return the associated assembly info.
    */
   @Override
   protected WSAssemblyInfo createInfo() {
      return new UnpivotTableAssemblyInfo();
   }

   /**
    * Get the table assembly.
    * @return the table assembly.
    */
   public TableAssembly getTableAssembly() {
      return ws == null ? null : (TableAssembly) ws.getAssembly(tname);
   }

   /**
    * Get the table assembly count.
    * @return the table assembly count.
    */
   @Override
   public int getTableAssemblyCount() {
      return 1;
   }

   @Override
   public boolean printKey(PrintWriter writer) throws Exception {
      boolean success = super.printKey(writer);

      if(!success) {
         return false;
      }

      UnpivotTableAssemblyInfo unpivotInfo = getUnpivotInfo();
      List<String> changedTypeCols = unpivotInfo.getChangedTypeCols();
      Collections.sort(changedTypeCols);

      if(changedTypeCols == null) {
         return true;
      }

      writer.print("Unpivot[");
      int index = 0;

      for(String changedTypeCol : changedTypeCols) {
         if(index > 0) {
            writer.print(",");
         }
         writer.print("TypeChange[");
         writer.print(changedTypeCol);
         writer.print(",");
         String changeFormat;
         XFormatInfo changedTypeColumnFormat = unpivotInfo.getChangedTypeColumnFormatInfo(changedTypeCol);

         if(changedTypeColumnFormat == null) {
            changeFormat = "";
         }
         else {
            changeFormat = changedTypeColumnFormat.getFormat() + "_" + changedTypeColumnFormat.getFormatSpec();
         }

         writer.print(changeFormat);
         writer.print(",");
         writer.print(unpivotInfo.getChangedColType(changedTypeCol));
         writer.print(",");
         writer.print(unpivotInfo.forceParseDataByFormat(changedTypeCol));
         writer.print("]");
      }

      writer.print("]");

      return true;
   }

   /**
    * Get the table assembly name.
    * @return the table assembly name.
    */
   public String getTableAssemblyName() {
      TableAssembly base = getTableAssembly();

      return (base == null) ? null : base.getName();
   }

   /**
    * Get the table names.
    */
   @Override
   public String[] getTableNames() {
      TableAssembly base = getTableAssembly();
      return base == null ? new String[0] : new String[] {base.getName()};
   }

   /**
    * Get all the table assemblies.
    * @return all the table assemblies of the composite table assembly.
    */
   @Override
   public TableAssembly[] getTableAssemblies() {
      TableAssembly table = ws == null ?
         null :
         (TableAssembly) ws.getAssembly(tname);

      if(table == null) {
         if(ws == null || ws.log) {
            LOG.error("Table assembly not found: " + tname);
         }

         return null;
      }

      return new TableAssembly[] {table};
   }

   /**
    * Set all the table assemblies.
    * @param tables the specified table assemblies.
    * @return false if the change is rejected.
    */
   @Override
   public boolean setTableAssemblies(TableAssembly[] tables) {
      if(tables.length != 1) {
         return false;
      }

      tname = tables[0].getName();
      return true;
   }

   /**
    * Rename the assemblies depended on.
    * @param oname the specified old name.
    * @param nname the specified new name.
    */
   @Override
   public void renameDepended(String oname, String nname) {
      super.renameDepended(oname, nname);

      if(oname.equals(tname)) {
         tname = nname;
      }
   }

   /**
    * Check if is a plain table.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isPlain() {
      return false;
   }

   /**
    * Set header columns.
    * @param hcol the specified header columns count.
    */
   public void setHeaderColumns(int hcol) {
      getUnpivotInfo().setHeaderColumns(hcol);
   }

   /**
    * Get header columns.
    * @return header columns count.
    */
   public int getHeaderColumns() {
      return getUnpivotInfo().getHeaderColumns();
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);
      writer.print("<tableAssembly>");
      writer.print("<![CDATA[" + tname + "]]>");
      writer.println("</tableAssembly>");
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseContents(Element elem) throws Exception {
      super.parseContents(elem);
      tname = Tool.getChildValueByTagName(elem, "tableAssembly");
   }

   /**
    * Get the assemblies depended on.
    * @param set the set stores the assemblies depended on.
    */
   @Override
   public void getDependeds(Set<AssemblyRef> set) {
      super.getDependeds(set);
      set.add(new AssemblyRef(new AssemblyEntry(tname, Worksheet.TABLE_ASSET)));
   }

   @Override
   public void getAugmentedDependeds(Map<String, Set<DependencyType>> dependeds) {
      super.getAugmentedDependeds(dependeds);
      addToDependencyTypes(dependeds, tname, DependencyType.UNPIVOT);
   }

   public void changeColumnType(DataRef col, String type, XFormatInfo format, boolean force) {
      getUnpivotInfo().setColumnType(col, type, format, force);
      setColumnSelection(getColumnSelection(false), false);
   }

   /**
    * Get UnpivotTableAssemblyInfo.
    */
   private UnpivotTableAssemblyInfo getUnpivotInfo() {
      return (UnpivotTableAssemblyInfo) getInfo();
   }

   private String tname;

   private static final Logger LOG =
      LoggerFactory.getLogger(UnpivotTableAssembly.class);
}
