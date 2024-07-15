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
import inetsoft.uql.asset.internal.*;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.schema.XSchema;
import inetsoft.util.log.LogLevel;
import inetsoft.web.composer.model.ws.DependencyType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.security.Principal;
import java.util.Map;
import java.util.Set;

/**
 * MirrorNamedGroupAssembly, the mirror of a named group assembly.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class MirrorNamedGroupAssembly extends AbstractWSAssembly
   implements NamedGroupAssembly, MirrorAssembly
{
   /**
    * Constructor.
    */
   public MirrorNamedGroupAssembly() {
      super();
   }

   /**
    * Constructor.
    */
   public MirrorNamedGroupAssembly(Worksheet ws, String name, AssetEntry entry,
                                   boolean outer, WSAssembly assembly) {
      super(ws, name);

      MirrorAssemblyImpl impl = new MirrorAssemblyImpl(entry, outer, assembly);
      impl.setWorksheet(ws);

      setImpl(impl);
   }

   /**
    * Create assembly info.
    * @return the associated assembly info.
    */
   @Override
   protected WSAssemblyInfo createInfo() {
      return new MirrorNamedGroupAssemblyInfo();
   }

   /**
    * Get the mirror assembly impl.
    * @return the mirror assembly impl.
    */
   private MirrorAssemblyImpl getImpl() {
      return ((MirrorNamedGroupAssemblyInfo) getInfo()).getImpl();
   }

   /**
    * Set the mirror assembly impl.
    * @param impl the specified mirror assembly impl.
    */
   private void setImpl(MirrorAssemblyImpl impl) {
      ((MirrorNamedGroupAssemblyInfo) getInfo()).setImpl(impl);
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
    * Set the worksheet.
    * @param ws the specified worksheet.
    */
   @Override
   public void setWorksheet(Worksheet ws) {
      super.setWorksheet(ws);
      getImpl().setWorksheet(ws);
   }

   /**
    * Get the minimum size.
    * @return the minimum size of the assembly.
    */
   @Override
   public Dimension getMinimumSize() {
      NamedGroupAssembly assembly = getNamedGroupAssembly();

      if(assembly == null) {
         return new Dimension(AssetUtil.defw, 2 * AssetUtil.defh);
      }

      return assembly.getMinimumSize();
   }

   /**
    * Replace all embeded user variables.
    * @param vars the specified variable table.
    */
   @Override
   public void replaceVariables(VariableTable vars) {
      getNamedGroupInfo().replaceVariables(vars);
   }

   /**
    * Get all variables in the condition value list.
    * @return the variable list.
    */
   @Override
   public UserVariable[] getAllVariables() {
      return getNamedGroupInfo().getAllVariables();
   }

   /**
    * Get the worksheet entry.
    * @return the worksheet entry of the mirror assembly.
    */
   @Override
   public AssetEntry getEntry() {
      return getImpl().getEntry();
   }

   /**
    * Set the worksheet entry.
    * @param entry the specified worksheet entry.
    */
   @Override
   public void setEntry(AssetEntry entry) {
      getImpl().setEntry(entry);
   }

   /**
    * Get the assembly name.
    * @return the assembly name.
    */
   @Override
   public String getAssemblyName() {
      return getImpl().getAssemblyName();
   }

   /**
    * Check if is outer mirror.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isOuterMirror() {
      return getImpl().isOuterMirror();
   }

   /**
    * Get the last modified time.
    * @return the last modified time of the assembly.
    */
   @Override
   public long getLastModified() {
      return getImpl().getLastModified();
   }

   /**
    * Set last modified time.
    * @param modified the specified last modified time.
    */
   @Override
   public void setLastModified(long modified) {
      getImpl().setLastModified(modified);
   }

   /**
    * Check if is auto update.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isAutoUpdate() {
      return getImpl().isAutoUpdate();
   }

   /**
    * Set auto update.
    * @param auto <tt>true</tt> to open auto update.
    */
   @Override
   public void setAutoUpdate(boolean auto) {
      getImpl().setAutoUpdate(auto);
   }

   /**
    * Update the inner mirror assembly.
    * @return <tt>true</tt> if successful, <tt>false</tt> otherwise.
    */
   @Override
   public boolean update() {
      if(!super.update()) {
         return false;
      }

      return getImpl().update();
   }

   /**
    * Update the outer mirror assembly.
    * @param engine the specified asset repository.
    * @param user the specified user.
    */
   @Override
   public void updateMirror(AssetRepository engine, Principal user)
      throws Exception
   {
      getImpl().updateMirror(engine, user);
   }

   /**
    * Get the assembly.
    * @return the assembly of the mirror assembly.
    */
   @Override
   public Assembly getAssembly() {
      return getImpl().getAssembly();
   }

   @Override
   public void checkValidity() throws Exception {
      checkValidity(true);
   }

   /**
    * Check if the mirror assembly is valid.
    */
   @Override
   public void checkValidity(boolean checkCrossJoins) throws Exception {
      super.checkValidity(checkCrossJoins);
      getImpl().checkValidity(checkCrossJoins);
   }

   /**
    * Get the named group assembly.
    * @return the named group assembly.
    */
   private NamedGroupAssembly getNamedGroupAssembly() {
      if(!(getAssembly() instanceof NamedGroupAssembly)) {
         LOG.debug("The mirror assembly is invalid",
            new Exception("Stack trace"));
      }

      return getAssembly() instanceof NamedGroupAssembly ?
         (NamedGroupAssembly) getAssembly() :
         null;
   }

   /**
    * Get the named group info.
    * @return the named group info of the assembly.
    */
   @Override
   public NamedGroupInfo getNamedGroupInfo() {
      NamedGroupAssembly assembly = getNamedGroupAssembly();

      if(assembly == null) {
         return new NamedGroupInfo();
      }

      return assembly.getNamedGroupInfo();
   }

   /**
    * Set the named group info.
    * @param info the specified named group info.
    */
   @Override
   public void setNamedGroupInfo(NamedGroupInfo info) {
      throw new RuntimeMessageException("Mirror named group is not editable!",
                                        LogLevel.DEBUG);
   }

   /**
    * Get the attached type.
    * @return the attached type.
    */
   @Override
   public int getAttachedType() {
      NamedGroupAssembly assembly = getNamedGroupAssembly();

      if(assembly == null) {
         return -1;
      }

      return assembly.getAttachedType();
   }

   /**
    * Set the attached type.
    * @param type the specified type.
    */
   @Override
   public void setAttachedType(int type) {
      throw new RuntimeException("Mirror named group is not editable!");
   }

   /**
    * Get the attached source.
    * @return the attached source.
    */
   @Override
   public SourceInfo getAttachedSource() {
      NamedGroupAssembly assembly = getNamedGroupAssembly();

      if(assembly == null) {
         return new SourceInfo();
      }

      return assembly.getAttachedSource();
   }

   /**
    * Set the attached source.
    * @param info the specified source.
    */
   @Override
   public void setAttachedSource(SourceInfo info) {
      throw new RuntimeException("Mirror named group is not editable!");
   }

   /**
    * Get the attached attribute.
    * @return the attached attribute.
    */
   @Override
   public DataRef getAttachedAttribute() {
      NamedGroupAssembly assembly = getNamedGroupAssembly();

      if(assembly == null) {
         return null;
      }

      return assembly.getAttachedAttribute();
   }

   /**
    * Set the attached attribute.
    * @param ref the specified attribute.
    */
   @Override
   public void setAttachedAttribute(DataRef ref) {
      throw new RuntimeException("Mirror named group is not editable!");
   }

   /**
    * Get the attached data type.
    * @return the attached data type.
    */
   @Override
   public String getAttachedDataType() {
      NamedGroupAssembly assembly = getNamedGroupAssembly();

      if(assembly == null) {
         return XSchema.STRING;
      }

      return assembly.getAttachedDataType();
   }

   /**
    * Set the attached data type.
    */
   @Override
   public void setAttachedDataType(String dtype) {
      throw new RuntimeException("Mirror named group is not editable!");
   }

   /**
    * Check if the attached assembly is valid.
    */
   @Override
   public void isAttachedValid() throws Exception {
      NamedGroupAssembly assembly = getNamedGroupAssembly();

      if(assembly == null) {
         return;
      }

      assembly.isAttachedValid();
   }

   /**
    * Get the assemblies depended on.
    * @param set the set stores the assemblies depended on.
    */
   @Override
   public void getDependeds(Set<AssemblyRef> set) {
      String name = getAssemblyName();
      set.add(new AssemblyRef(new AssemblyEntry(name, Worksheet.NAMED_GROUP_ASSET)));
   }

   @Override
   public void getAugmentedDependeds(Map<String, Set<DependencyType>> dependeds) {
      String name = getAssemblyName();
      addToDependencyTypes(dependeds, name, DependencyType.VARIABLE_FILTER);
   }

   /**
    * Rename the assemblies depended on.
    * @param oname the specified old name.
    * @param nname the specified new name.
    */
   @Override
   public void renameDepended(String oname, String nname) {
      getImpl().renameDepended(oname, nname);
   }

   private static final Logger LOG =
      LoggerFactory.getLogger(MirrorNamedGroupAssembly.class);
}
