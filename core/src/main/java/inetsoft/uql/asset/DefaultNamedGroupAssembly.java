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
import java.util.*;

/**
 * Default named group assembly.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class DefaultNamedGroupAssembly extends AbstractWSAssembly
   implements NamedGroupAssembly
{
   /**
    * Constructor.
    */
   public DefaultNamedGroupAssembly() {
      super();

      attached = new AttachedAssemblyImpl();
   }

   /**
    * Constructor.
    */
   public DefaultNamedGroupAssembly(Worksheet ws, String name) {
      super(ws, name);

      attached = new AttachedAssemblyImpl();
   }

   /**
    * Get the type.
    * @return the type of the assembly.
    */
   @Override
   public int getAssemblyType() {
      return Worksheet.NAMED_GROUP_ASSET;
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
    * Update the assembly.
    * @return <tt>true</tt> if successful, <tt>false</tt> otherwise.
    */
   @Override
   public boolean update() {
      if(!super.update()) {
         return false;
      }

      if(info != null) {
         return info.update(ws);
      }

      return true;
   }

   /**
    * Replace all embeded user variables.
    * @param vars the specified variable table.
    */
   @Override
   public void replaceVariables(VariableTable vars) {
      if(info != null) {
         info.replaceVariables(vars);
      }
   }

   /**
    * Get all variables in the condition value list.
    * @return the variable list.
    */
   @Override
   public UserVariable[] getAllVariables() {
      if(info == null) {
         return new UserVariable[0];
      }

      return info.getAllVariables();
   }

   /**
    * Get the named group info.
    * @return the named group info of the assembly.
    */
   @Override
   public NamedGroupInfo getNamedGroupInfo() {
      return info;
   }

   /**
    * Set the named group info.
    * @param info the specified named group info.
    */
   @Override
   public void setNamedGroupInfo(NamedGroupInfo info) {
      this.info = info;
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
      info.getDependeds(ws, set);
   }

   @Override
   public void getAugmentedDependeds(Map<String, Set<DependencyType>> dependeds) {
      Set<AssemblyRef> deps = new HashSet<>();
      info.getDependeds(ws, deps);
      addToDependencyTypes(dependeds, deps, DependencyType.VARIABLE_FILTER);
   }

   /**
    * Rename the assemblies depended on.
    * @param oname the specified old name.
    * @param nname the specified new name.
    */
   @Override
   public void renameDepended(String oname, String nname) {
      info.renameDepended(oname, nname, ws);
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(info != null) {
         info.writeXML(writer);
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
      Element cnode = Tool.getChildNodeByTagName(elem, "namedGroupInfo");

      if(cnode != null) {
         info.parseXML(cnode);
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
         DefaultNamedGroupAssembly assembly =
            (DefaultNamedGroupAssembly) super.clone();
         assembly.info = (NamedGroupInfo) info.clone();
         assembly.attached = (AttachedAssembly) attached.clone();
         return assembly;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone object", ex);
         return null;
      }
   }

   private static final Dimension MIN_SIZE = new Dimension(AssetUtil.defw, 2 * AssetUtil.defh);

   private NamedGroupInfo info = new NamedGroupInfo();
   private AttachedAssembly attached;

   private static final Logger LOG =
      LoggerFactory.getLogger(DefaultNamedGroupAssembly.class);
}
