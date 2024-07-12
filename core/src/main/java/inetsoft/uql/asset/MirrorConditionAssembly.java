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

import inetsoft.uql.*;
import inetsoft.uql.asset.internal.*;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.schema.XSchema;
import inetsoft.util.log.LogLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.security.Principal;
import java.util.Set;

/**
 * MirrorConditionAssembly, the mirror of a condition assembly.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class MirrorConditionAssembly extends AbstractWSAssembly
   implements ConditionAssembly, MirrorAssembly
{
   /**
    * Constructor.
    */
   public MirrorConditionAssembly() {
      super();
   }

   /**
    * Constructor.
    */
   public MirrorConditionAssembly(Worksheet ws, String name, AssetEntry entry,
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
      return new MirrorConditionAssemblyInfo();
   }

   /**
    * Get the mirror assembly impl.
    * @return the mirror assembly impl.
    */
   private MirrorAssemblyImpl getImpl() {
      return ((MirrorConditionAssemblyInfo) getInfo()).getImpl();
   }

   /**
    * Set the mirror assembly impl.
    * @param impl the specified mirror assembly impl.
    */
   private void setImpl(MirrorAssemblyImpl impl) {
      ((MirrorConditionAssemblyInfo) getInfo()).setImpl(impl);
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
      ConditionAssembly assembly = getConditionAssembly();

      if(assembly == null) {
         return new Dimension(AssetUtil.defw, 2 * AssetUtil.defh);
      }

      return getConditionAssembly().getMinimumSize();
   }

   /**
    * Replace all embeded user variables.
    * @param vars the specified variable table.
    */
   @Override
   public void replaceVariables(VariableTable vars) {
      getConditionList().replaceVariables(vars);
   }

   /**
    * Get all variables in the condition value list.
    * @return the variable list.
    */
   @Override
   public UserVariable[] getAllVariables() {
      return getConditionList().getAllVariables();
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
    * Get the condition assembly.
    * @return the condition assembly.
    */
   private ConditionAssembly getConditionAssembly() {
      if(!(getAssembly() instanceof ConditionAssembly)) {
         LOG.debug("The mirror assembly is invalid",
            new Exception("Stack trace"));
      }

      return getAssembly() instanceof ConditionAssembly ?
         (ConditionAssembly) getAssembly() :
         null;
   }

   /**
    * Get the condition.
    * @return the condition of the condition assembly.
    */
   @Override
   public ConditionList getConditionList() {
      ConditionAssembly assembly = getConditionAssembly();

      if(assembly == null) {
         return new ConditionList();
      }

      return assembly.getConditionList();
   }

   /**
    * Check if this list is empty.
    * @return <tt>true</tt> if empty, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isEmpty() {
      ConditionAssembly assembly = getConditionAssembly();

      if(assembly == null) {
         return true;
      }

      return assembly.isEmpty();
   }

   /**
    * Set the condition list.
    * @param conditions the specified condition list.
    */
   @Override
   public void setConditionList(ConditionList conditions) {
      throw new RuntimeMessageException("Mirror condition is not editable!",
                                        LogLevel.DEBUG);
   }

   /**
    * Get the size.
    * @return size of the ConditionList.
    */
   @Override
   public int getConditionSize() {
      return getConditionList().getConditionSize();
   }

   /**
    * Get the HierarchyItem at the specified index.
    * @param index the specified index.
    * @return the HierarchyItem at the specified index.
    */
   @Override
   public HierarchyItem getItem(int index) {
      return getConditionList().getItem(index);
   }

   /**
    * Check if the item at the specified index is a ConditionItem.
    * @param index the specified index.
    * @return true if is a ConditionItem.
    */
   @Override
   public boolean isConditionItem(int index) {
      return getConditionList().isConditionItem(index);
   }

   /**
    * Check if the item at the specified index is a JunctionOperator.
    * @param index the specified index.
    * @return true if is a JunctionOperator.
    */
   @Override
   public boolean isJunctionOperator(int index) {
      return getConditionList().isJunctionOperator(index);
   }

   /**
    * Get the ConditionItem at the specified index.
    * @param index the specified index.
    * @return the ConditionItem at the specified index.
    */
   @Override
   public ConditionItem getConditionItem(int index) {
      return getConditionList().getConditionItem(index);
   }

   /**
    * Get the JunctionOperator at the specified index.
    * @param index the specified index.
    * @return the JunctionOperator at the specified index.
    */
   @Override
   public JunctionOperator getJunctionOperator(int index) {
      return getConditionList().getJunctionOperator(index);
   }

   /**
    * Get the attached type.
    * @return the attached type.
    */
   @Override
   public int getAttachedType() {
      ConditionAssembly assembly = getConditionAssembly();

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
      throw new RuntimeException("Mirror condition is not editable!");
   }

   /**
    * Get the attached source.
    * @return the attached source.
    */
   @Override
   public SourceInfo getAttachedSource() {
      ConditionAssembly assembly = getConditionAssembly();

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
      throw new RuntimeException("Mirror condition is not editable!");
   }

   /**
    * Get the attached attribute.
    * @return the attached attribute.
    */
   @Override
   public DataRef getAttachedAttribute() {
      ConditionAssembly assembly = getConditionAssembly();

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
      throw new RuntimeException("Mirror condition is not editable!");
   }

   /**
    * Get the attached data type.
    * @return the attached data type.
    */
   @Override
   public String getAttachedDataType() {
      ConditionAssembly assembly = getConditionAssembly();

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
      throw new RuntimeException("Mirror condition is not editable!");
   }

   /**
    * Check if the attached assembly is valid.
    */
   @Override
   public void isAttachedValid() throws Exception {
      ConditionAssembly assembly = getConditionAssembly();

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
      set.add(new AssemblyRef(new AssemblyEntry(name, Worksheet.CONDITION_ASSET)));
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
      LoggerFactory.getLogger(MirrorConditionAssembly.class);
}
