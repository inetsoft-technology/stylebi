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

import inetsoft.uql.*;
import inetsoft.uql.asset.internal.*;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.UserVariable;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.awt.*;
import java.io.PrintWriter;
import java.util.Set;

/**
 * Default condition assembly.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class DefaultConditionAssembly extends AbstractWSAssembly
   implements ConditionAssembly
{
   /**
    * Constructor.
    */
   public DefaultConditionAssembly() {
      super();

      conds = new ConditionList();
      attached = new AttachedAssemblyImpl();
   }

   /**
    * Constructor.
    */
   public DefaultConditionAssembly(Worksheet ws, String name) {
      super(ws, name);

      conds = new ConditionList();
      attached = new AttachedAssemblyImpl();
   }

   /**
    * Create assembly info.
    * @return the associated assembly info.
    */
   @Override
   protected WSAssemblyInfo createInfo() {
      return new ConditionAssemblyInfo();
   }

   /**
    * Get the type.
    * @return the type of the assembly.
    */
   @Override
   public int getAssemblyType() {
      return Worksheet.CONDITION_ASSET;
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
    * Get the condition.
    * @return the condition of the condition assembly.
    */
   @Override
   public ConditionList getConditionList() {
      return conds;
   }

   /**
    * Set the condition list.
    * @param conditions the specified condition list.
    */
   @Override
   public void setConditionList(ConditionList conditions) {
      this.conds = conditions;
   }

   /**
    * Get the size.
    * @return size of the ConditionList.
    */
   @Override
   public int getConditionSize() {
      return conds.getConditionSize();
   }

   /**
    * Check if this list is empty.
    * @return <tt>true</tt> if empty, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isEmpty() {
      return conds.isEmpty();
   }

   /**
    * Set the worksheet.
    * @param ws the specified worksheet.
    */
   @Override
   public void setWorksheet(Worksheet ws) {
      super.setWorksheet(ws);

      for(int i = 0; i < conds.getConditionSize(); i += 2) {
         ConditionItem item = conds.getConditionItem(i);
         XCondition condition = item.getXCondition();

         if(condition instanceof WSAssembly) {
            ((WSAssembly) condition).setWorksheet(ws);
         }
      }
   }

   /**
    * Get the HierarchyItem at the specified index.
    * @param index the specified index.
    * @return the HierarchyItem at the specified index.
    */
   @Override
   public HierarchyItem getItem(int index) {
      return conds.getItem(index);
   }

   /**
    * Check if the item at the specified index is a ConditionItem.
    * @param index the specified index.
    * @return true if is a ConditionItem.
    */
   @Override
   public boolean isConditionItem(int index) {
      return conds.isConditionItem(index);
   }

   /**
    * Check if the item at the specified index is a JunctionOperator.
    * @param index the specified index.
    * @return true if is a JunctionOperator.
    */
   @Override
   public boolean isJunctionOperator(int index) {
      return conds.isJunctionOperator(index);
   }

   /**
    * Get the ConditionItem at the specified index.
    * @param index the specified index.
    * @return the ConditionItem at the specified index.
    */
   @Override
   public ConditionItem getConditionItem(int index) {
      return conds.getConditionItem(index);
   }

   /**
    * Get the JunctionOperator at the specified index.
    * @param index the specified index.
    * @return the JunctionOperator at the specified index.
    */
   @Override
   public JunctionOperator getJunctionOperator(int index) {
      return conds.getJunctionOperator(index);
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

      conds = (ConditionList) ConditionUtil.updateConditionListWrapper(conds, ws);
      conds = conds == null ? new ConditionList() : conds;

      return true;
   }

   /**
    * Replace all embeded user variables.
    * @param vars the specified variable table.
    */
   @Override
   public void replaceVariables(VariableTable vars) {
      conds.replaceVariables(vars);
   }

   /**
    * Get all variables in the condition value list.
    * @return the variable list.
    */
   @Override
   public UserVariable[] getAllVariables() {
      return conds.getAllVariables();
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
      AssetUtil.getConditionDependeds(ws, conds, set);
   }

   /**
    * Rename the assemblies depended on.
    * @param oname the specified old name.
    * @param nname the specified new name.
    */
   @Override
   public void renameDepended(String oname, String nname) {
      ConditionUtil.renameConditionListWrapper(conds, oname, nname, ws);
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);
      conds.writeXML(writer);
      attached.writeXML(writer);
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseContents(Element elem) throws Exception {
      super.parseContents(elem);

      Element cnode = Tool.getChildNodeByTagName(elem, "conditions");
      conds.parseXML(cnode);

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
         DefaultConditionAssembly assembly =
            (DefaultConditionAssembly) super.clone();
         assembly.conds = (ConditionList) conds.clone();
         assembly.attached = (AttachedAssembly) attached.clone();
         return assembly;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone object", ex);
         return null;
      }
   }

   private static final Dimension MIN_SIZE = new Dimension(AssetUtil.defw, 2 * AssetUtil.defh);

   private ConditionList conds;
   private AttachedAssembly attached;

   private static final Logger LOG =
      LoggerFactory.getLogger(DefaultConditionAssembly.class);
}
