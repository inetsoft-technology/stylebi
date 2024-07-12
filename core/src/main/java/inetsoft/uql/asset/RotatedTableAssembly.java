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

import inetsoft.uql.asset.internal.RotatedTableAssemblyInfo;
import inetsoft.uql.asset.internal.WSAssemblyInfo;
import inetsoft.util.Tool;
import inetsoft.web.composer.model.ws.DependencyType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.util.Map;
import java.util.Set;

/**
 * Rotated table assembly, rotates a table assembly.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class RotatedTableAssembly extends ComposedTableAssembly {
   /**
    * Constructor.
    */
   public RotatedTableAssembly() {
      super();
   }

   /**
    * Constructor.
    */
   public RotatedTableAssembly(Worksheet ws, String name,
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
      return new RotatedTableAssemblyInfo();
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
            LOG.error("Table assembly not found:" + tname);
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
    * Check if show live data.
    * @return <tt>true</tt> to show live data, <tt>false</tt> to show metadata.
    */
   @Override
   public boolean isLiveData() {
      return super.isLiveData();
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
      addToDependencyTypes(dependeds, tname, DependencyType.ROTATION);
   }

   private String tname;

   private static final Logger LOG = LoggerFactory.getLogger(RotatedTableAssembly.class);
}
