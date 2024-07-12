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

import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.asset.internal.AttachedAssemblyImpl;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.UserVariable;
import inetsoft.util.Tool;
import inetsoft.web.composer.model.ws.DependencyType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.awt.*;
import java.io.PrintWriter;
import java.util.Map;
import java.util.Set;

/**
 * Default variable assembly.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class DefaultVariableAssembly extends AbstractWSAssembly implements
   VariableAssembly
{
   /**
    * Constructor.
    */
   public DefaultVariableAssembly() {
      super();

      attached = new AttachedAssemblyImpl();
   }

   /**
    * Constructor.
    */
   public DefaultVariableAssembly(Worksheet ws, String name) {
      super(ws, name);

      attached = new AttachedAssemblyImpl();
   }

   /**
    * Set the name.
    * @param name the specified name.
    */
   @Override
   public void setName(String name) {
      setName(name, true);
   }

   /**
    * Set the name.
    * @param name the specified name.
    * @param both <tt>true</tt> to rename both assembly name and variable name,
    * <tt>false</tt> to rename assembly name only.
    */
   @Override
   public void setName(String name, boolean both) {
      super.setName(name);

      if(var != null && both) {
         // rename variable as well
         var.setName(name);
      }
   }

   /**
    * Get the type.
    * @return the type of the assembly.
    */
   @Override
   public int getAssemblyType() {
      return Worksheet.VARIABLE_ASSET;
   }

   /**
    * Get the minimum size.
    * @return the minimum size of the assembly.
    */
   @Override
   public Dimension getMinimumSize() {
      return MIN_SIZE;
   }

   /**
    * Check if the assembly is valid.
    */
   @Override
   public void checkValidity(boolean checkCrossJoins) throws Exception {
      super.checkValidity(checkCrossJoins);
      isAttachedValid();
   }

   /**
    * Get the asset variable.
    * @return the asset variable of the variable assembly.
    */
   @Override
   public AssetVariable getVariable() {
      return var;
   }

   /**
    * Set the asset variable.
    * @param var the specified asset variable.
    */
   @Override
   public void setVariable(AssetVariable var) {
      this.var = var;
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

      return var.update(ws);
   }

   /**
    * Replace all embeded user variables.
    * @param vars the specified variable table.
    */
   @Override
   public void replaceVariables(VariableTable vars) {
      // do nothing
   }

   /**
    * Get all variables in the condition value list.
    * @return the variable list.
    */
   @Override
   public UserVariable[] getAllVariables() {
      return new UserVariable[0];
   }

   /**
    * Get the attached type.
    * @return the attached type.
    */
   @Override
   public int getAttachedType() {
      return attached.getAttachedType();
   }

   /**
    * Set the attached type.
    * @param type the specified type.
    */
   @Override
   public void setAttachedType(int type) {
      attached.setAttachedType(type);
   }

   /**
    * Get the attached source.
    * @return the attached source.
    */
   @Override
   public SourceInfo getAttachedSource() {
      return attached.getAttachedSource();
   }

   /**
    * Set the attached source.
    * @param info the specified source.
    */
   @Override
   public void setAttachedSource(SourceInfo info) {
      attached.setAttachedSource(info);
   }

   /**
    * Get the attached attribute.
    * @return the attached attribute.
    */
   @Override
   public DataRef getAttachedAttribute() {
      return attached.getAttachedAttribute();
   }

   /**
    * Set the attached attribute.
    * @param ref the specified attribute.
    */
   @Override
   public void setAttachedAttribute(DataRef ref) {
      attached.setAttachedAttribute(ref);
   }

   /**
    * Get the attached data type.
    * @return the attached data type.
    */
   @Override
   public String getAttachedDataType() {
      return attached.getAttachedDataType();
   }

   /**
    * Set the attached data type.
    */
   @Override
   public void setAttachedDataType(String dtype) {
      attached.setAttachedDataType(dtype);
   }

   /**
    * Check if the attached assembly is valid.
    */
   @Override
   public void isAttachedValid() throws Exception {
      attached.isAttachedValid();
   }

   /**
    * Get the assemblies depended on.
    * @param set the set stores the assemblies depended on.
    */
   @Override
   public void getDependeds(Set<AssemblyRef> set) {
      if(var != null) {
         String table = var.getTableName();

         if(table != null) {
            set.add(new AssemblyRef(new AssemblyEntry(table,
                                                      Worksheet.TABLE_ASSET)));
         }
      }
   }

   @Override
   public void getAugmentedDependeds(Map<String, Set<DependencyType>> dependeds) {
      if(var != null) {
         String table = var.getTableName();

         if(table != null) {
            addToDependencyTypes(dependeds, table, DependencyType.VARIABLE_SUBQUERY);
         }
      }
   }

   /**
    * Rename the assemblies depended on.
    * @param oname the specified old name.
    * @param nname the specified new name.
    */
   @Override
   public void renameDepended(String oname, String nname) {
      if(var != null) {
         String table = var.getTableName();

         if(Tool.equals(table, oname)) {
            var.setTableName(nname);
         }

         Assembly assembly = ws.getAssembly(nname);

         /*
         if(!(assembly instanceof ComposedTableAssembly)) {
            return;
         }
         */

         DataRef attr = var.getLabelAttribute();

         if(attr instanceof ColumnRef) {
            ColumnRef.renameColumn((ColumnRef) attr, oname, nname);
         }

         attr = var.getValueAttribute();

         if(attr instanceof ColumnRef) {
            ColumnRef.renameColumn((ColumnRef) attr, oname, nname);
         }
      }
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(var != null) {
         var.writeXML(writer);
      }

      attached.writeXML(writer);
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseContents(Element elem) throws Exception {
      super.parseContents(elem);
      Element vnode = Tool.getChildNodeByTagName(elem, "variable");

      if(vnode != null) {
         var = new AssetVariable();
         var.parseXML(vnode);
      }

      Element anode = Tool.getChildNodeByTagName(elem, "attachedAssembly");
      attached.parseXML(anode);
   }

   /**
    * Clone the object.
    * @return the cloned object.
    */
   @Override
   public Object clone() {
      try {
         DefaultVariableAssembly assembly =
            (DefaultVariableAssembly) super.clone();
         assembly.var = (AssetVariable) var.clone();
         assembly.attached = (AttachedAssembly) attached.clone();
         return assembly;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone object", ex);
         return null;
      }
   }

   private static final Dimension MIN_SIZE = new Dimension(AssetUtil.defw, 2 * AssetUtil.defh);

   private AssetVariable var;
   private AttachedAssembly attached;

   private static final Logger LOG =
      LoggerFactory.getLogger(DefaultVariableAssembly.class);
}
