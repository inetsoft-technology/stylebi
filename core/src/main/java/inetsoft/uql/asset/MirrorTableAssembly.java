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

import inetsoft.report.internal.Util;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.internal.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.web.composer.model.ws.DependencyType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.PrintWriter;
import java.security.Principal;
import java.util.Map;
import java.util.Set;

/**
 * MirrorTableAssembly, the mirror of a table assembly.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class MirrorTableAssembly extends ComposedTableAssembly
   implements MirrorAssembly
{
   /**
    * Constructor.
    */
   public MirrorTableAssembly() {
      super();
   }

   /**
    * Create a mirror of an asset.
    * @param name the name of the mirror assembly.
    * @param assembly the base assembly.
    */
   public MirrorTableAssembly(Worksheet ws, String name, WSAssembly assembly) {
      this(ws, name, null, false, assembly);
   }

   /**
    * Create a mirror of an asset.
    * @param name the name of the mirror assembly.
    * @param entry the external asset entry if this is a mirror (outer) of an
    * external asset.
    * @param assembly the base assembly.
    */
   public MirrorTableAssembly(Worksheet ws, String name, AssetEntry entry,
                              boolean outer, WSAssembly assembly) {
      super(ws, name);

      MirrorAssemblyImpl impl = new MirrorAssemblyImpl(entry, outer, assembly);
      impl.setWorksheet(ws);
      setImpl(impl);
      updateColumnSelection();
   }

   /**
    * Update base column selection.
    */
   public void updateColumnSelection() {
      updateColumnSelection(false);
   }

   /**
    * Update columns selection.
    * @param keepPub to keep self public columns.
    */
   public void updateColumnSelection(boolean keepPub) {
      updateColumnSelection(keepPub, false);
   }

   /**
    * Update columns selection.
    * @param keepPub to keep self public columns.
    * @param keepAlias if keep alias when update by sub table.
    */
   public void updateColumnSelection(boolean keepPub, boolean keepAlias) {
      TableAssembly table = (TableAssembly) getAssembly();

      if(table != null) {
         String tname = table.getName();
         ColumnSelection basecols = table.getColumnSelection(true);
         ColumnSelection icolumns = getColumnSelection(false);
         ColumnSelection pcolumns = getColumnSelection(true);
         ColumnSelection columns = new ColumnSelection();

         for(int i = 0; i < basecols.getAttributeCount(); i++) {
            ColumnRef tcolumn = (ColumnRef) basecols.getAttribute(i);
            DataRef attr = AssetUtil.getOuterAttribute(tname, tcolumn);
            String dtype = tcolumn.getDataType();
            ColumnRef column = new ColumnRef(attr);
            column.setDataType(dtype);
            column.setDescription(tcolumn.getDescription());

            // for vs wizard, update mirror cols filtered inner table cols should keep the alias.
            if(keepAlias) {
               DataRef ocolumn = Util.findColumn(icolumns, column.getEntity(), column.getAttribute());

               if(ocolumn != null && ((ColumnRef) ocolumn).getAlias() != null) {
                  column.setAlias(((ColumnRef) ocolumn).getAlias());
               }
            }

            columns.addAttribute(column, false);

            if(attr instanceof AttributeRef) {
               ((AttributeRef) attr).setRefType(tcolumn.getRefType());
               ((AttributeRef) attr).setDefaultFormula(tcolumn.getDefaultFormula());
            }
         }

         int c = 0;

         for(int i = 0; i < pcolumns.getAttributeCount(); i++) {
            ColumnRef col = (ColumnRef) pcolumns.getAttribute(i);
            DataRef ref = col.getDataRef();

            // don't lose the named group, if aggregate is empty, we need
            // to keep the named group in the column selection or it
            // will be lost
            if(ref instanceof NamedRangeRef || keepPub && !columns.containsAttribute(col)) {
               // @by ChrisSpagnoli, for Bug #7271
               // Add NamedRangeRef columns to the head of the column list,
               // to be consistent with ChartVSAQuery.createTableAssembly()
               // column ordering without an MV.
               columns.addAttribute(c++, col);
            }
         }

         setColumnSelection(columns, false);
      }
   }

   /**
    * Print table property as cache key if necessary.
    */
   @Override
   protected void printProperties(PrintWriter writer) throws Exception {
      writer.print(",");
      writer.print(getProperty("noEmpty"));
   }

   /**
    * Create assembly info.
    * @return the associated assembly info.
    */
   @Override
   protected WSAssemblyInfo createInfo() {
      return new MirrorTableAssemblyInfo();
   }

   /**
    * Get the mirror assembly impl.
    * @return the mirror assembly impl.
    */
   private MirrorAssemblyImpl getImpl() {
      return ((MirrorTableAssemblyInfo) getInfo()).getImpl();
   }

   /**
    * Set the mirror assembly impl.
    * @param impl the specified mirror assembly impl.
    */
   private void setImpl(MirrorAssemblyImpl impl) {
      ((MirrorTableAssemblyInfo) getInfo()).setImpl(impl);
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
    * @param embedded <tt>true</tt> to embed the table assembly.
    * @return the minimum size of the assembly.
    */
   @Override
   public Dimension getMinimumSize(boolean embedded) {
      if(embedded || isLiveData() || isRuntime() || !isHierarchical()) {
         return super.getMinimumSize(embedded);
      }
      else {
         TableAssembly table = getTableAssembly();

         if(table == null) {
            return super.getMinimumSize(embedded);
         }

         Dimension size = table.getMinimumSize(true);

         if(isIconized(table.getName())) {
            size.width = AssetUtil.defw;
         }

         int width = getExpressionWidth(embedded); // expression count
         return new Dimension(size.width + width, size.height + AssetUtil.defh);
      }
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
    * Get the table names.
    */
   @Override
   public String[] getTableNames() {
      return new String[] {getImpl().getAssemblyName()};
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
      if(!getImpl().update()) {
         return false;
      }

      return super.update();
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
    * Clear cache.
    */
   @Override
   public void clearCache() {
      super.clearCache();
      getImpl().clearCache();
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
    * Get the table assembly.
    * @return the table assembly.
    */
   public TableAssembly getTableAssembly() {
      Assembly assembly = getImpl().getAssembly(false);

      if(assembly != null && !(assembly instanceof TableAssembly)) {
         LOG.debug("The mirror assembly is invalid",
            new Exception("Stack trace"));
      }

      return assembly instanceof TableAssembly ?
         (TableAssembly) assembly :
         null;
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
    * Get all the table assemblies.
    * @return all the table assemblies of the composite table assembly.
    */
   @Override
   public TableAssembly[] getTableAssemblies() {
      TableAssembly assembly = getTableAssembly();

      if(assembly == null) {
         return new TableAssembly[0];
      }

     return new TableAssembly[] {assembly};
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

      getImpl().setAssembly(tables[0]);
      return true;
   }

   /**
    * Get the assemblies depended on.
    * @param set the set stores the assemblies depended on.
    */
   @Override
   public void getDependeds(Set<AssemblyRef> set) {
      super.getDependeds(set);
      String name = getAssemblyName();
      set.add(new AssemblyRef(new AssemblyEntry(name, Worksheet.TABLE_ASSET)));
   }

   @Override
   public void getAugmentedDependeds(Map<String, Set<DependencyType>> dependeds) {
      super.getAugmentedDependeds(dependeds);
      addToDependencyTypes(dependeds, getAssemblyName(), DependencyType.MIRROR);
   }

   /**
    * Rename the assemblies depended on.
    * @param oname the specified old name.
    * @param nname the specified new name.
    */
   @Override
   public void renameDepended(String oname, String nname) {
      super.renameDepended(oname, nname);
      getImpl().renameDepended(oname, nname);
   }

   private static final Logger LOG = LoggerFactory.getLogger(MirrorTableAssembly.class);
}
