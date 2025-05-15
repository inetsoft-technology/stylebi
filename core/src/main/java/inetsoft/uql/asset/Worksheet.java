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
import inetsoft.uql.XCube;
import inetsoft.uql.asset.internal.*;
import inetsoft.uql.schema.UserVariable;
import inetsoft.util.*;
import inetsoft.util.xml.VersionControlComparators;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.awt.*;
import java.io.PrintWriter;
import java.security.Principal;
import java.util.List;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;

/**
 * Worksheet like a spreadsheet, contains condition/named group/variable/table
 * Assemblies, of which a primary assembly for binding.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class Worksheet extends AbstractSheet implements VariableProvider {
   /**
    * Constructor.
    */
   public Worksheet() {
      super();

      this.winfo = new WorksheetInfo();
      this.assemblies = new ArrayList<>();
      this.cubes = new ConcurrentHashMap<>();
      this.dependencies = new HashSet<>();
   }

   /**
    * Get the type of the worksheet.
    * @return the type of the worksheet.
    */
   @Override
   public int getType() {
      if(primary != null) {
         Assembly assembly = getAssembly(primary);

         if(assembly != null) {
            return assembly.getAssemblyType();
         }
      }

      return TABLE_ASSET;
   }

   /**
    * Check if is a condition worksheet.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   public boolean isCondition() {
      return getType() == CONDITION_ASSET;
   }

   /**
    * Check if is a named group worksheet.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   public boolean isNamedGroup() {
      return getType() == NAMED_GROUP_ASSET;
   }

   /**
    * Check if is a variable worksheet.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   public boolean isVariable() {
      return getType() == VARIABLE_ASSET;
   }

   /**
    * Check if is a table worksheet.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   public boolean isTable() {
      return getType() == TABLE_ASSET;
   }

   /**
    * Get the primary assembly name.
    * @return the primary assembly name, <tt>null</tt> if does not exist.
    */
   public String getPrimaryAssemblyName() {
      return primary;
   }

   /**
    * Get the primary assembly.
    * @return the primary assembly, <tt>null</tt> if does not exist.
    */
   public WSAssembly getPrimaryAssembly() {
      return primary == null ? null : (WSAssembly) getAssembly(primary);
   }

   /**
    * Set the primary assembly.
    * @param name the specified primary assembly.
    * @return <tt>true</tt> if successful, <tt>false</tt> otherwise.
    */
   public boolean setPrimaryAssembly(String name) {
      if(!containsAssembly(name)) {
         return false;
      }

      primary = name;
      return true;
   }

   /**
    * Set the primary assembly.
    * @param assembly the specified primary assembly.
    * @return <tt>true</tt> if successful, <tt>false</tt> otherwise.
    */
   public boolean setPrimaryAssembly(Assembly assembly) {
      if(!containsAssembly(assembly)) {
         return false;
      }

      primary = assembly.getName();
      return true;
   }

   /**
    * Get the worksheet info.
    */
   public WorksheetInfo getWorksheetInfo() {
      return winfo;
   }

   /**
    * Set the worksheet info.
    * @return true if the property change should cause the worksheet to be
    * re-executed.
    */
   public boolean setWorksheetInfo(WorksheetInfo winfo) {
      return this.winfo.copyInfo(winfo);
   }

   /**
    * Check if contains an assembly.
    * @param name the specified assembly name.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   @Override
   public synchronized boolean containsAssembly(String name) {
      for(WSAssembly assembly : assemblies) {
         if(assembly.getName().equals(name)) {
            return true;
         }
      }

      return false;
   }

   /**
    * Check if contains an assembly, ignoring case.
    *
    * @param name the specified assembly name.
    * @return <tt>true</tt> if the assembly name ignoring case is contained in this worksheet,
    *         <tt>false</tt> otherwise.
    */
   public synchronized boolean containsAssemblyIgnoreCase(String name) {
      for(WSAssembly assembly : assemblies) {
         if(assembly.getName().equalsIgnoreCase(name)) {
            return true;
         }
      }

      return false;
   }

   /**
    * Check if contains an assembly.
    * @param assembly the specified assembly.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   public boolean containsAssembly(Assembly assembly) {
      return assembly != null && containsAssembly(assembly.getName());
   }

   /**
    * Get an assembly by its entry.
    * @param entry the specified assembly entry.
    * @return the assembly, <tt>null</tt> if not found.
    */
   @Override
   public Assembly getAssembly(AssemblyEntry entry) {
      return getAssembly(entry.getName());
   }

   /**
    * Get an assembly by its name.
    * @param name the specified assembly name.
    * @return the assembly, <tt>null</tt> if not found.
    */
   @Override
   public Assembly getAssembly(String name) {
      if(name == null) {
         return null;
      }

      if(name.contains(Assembly.CUBE_VS)) {
         WSAssembly assembly = cubes.get(name);

         if(assembly == null) {
            synchronized(cubes) {
               assembly = cubes.get(name);

               if(assembly == null) {
                  assembly = getCubeTableAssembly(name);

                  if(assembly != null) {
                     cubes.put(name, assembly);
                  }
               }
            }
         }

         if(assembly != null) {
            return assembly;
         }
      }

      Map<String, WSAssembly> amap = this.amap;

      // @by larryl, optimization, getAssembly() may be called many times
      // from script iterator
      if(amap == null) {
         amap = createCache();
      }

      WSAssembly assembly = amap.get(name);

      // called from script iterator, name not found is expected.
      if(assembly == null && !ScriptIterator.isProcessing() ||
         // name changed, re-create
         assembly != null && !name.equals(assembly.getName()))
      {
         synchronized(this) {
            amap = createCache();
         }

         assembly = amap.get(name);
      }

      return assembly;
   }

   /**
    * Create the assembly cache.
    */
   private Map<String, WSAssembly> createCache() {
      ConcurrentHashMap<String, WSAssembly> amap = new ConcurrentHashMap<>();
      List<WSAssembly> assemblies = new ArrayList<>(this.assemblies);

      for(WSAssembly assembly : assemblies) {
         amap.put(assembly.getName(), assembly);
      }

      this.amap = amap;
      return amap;
   }

   /**
    * Get the table assembly used in viewsheet by the name of the base assembly.
    * @param bname the specified base assembly name.
    * @return the assembly, <tt>null</tt> if not found.
    */
   public TableAssembly getVSTableAssembly(String bname) {
      if(bname == null) {
         return null;
      }

      String tname = Assembly.TABLE_VS + bname;
      TableAssembly assembly = (TableAssembly) getAssembly(tname);

      if(assembly == null) {
         assembly = (TableAssembly) getAssembly(bname);

         if(assembly == null) {
            return null;
         }

         assembly = new MirrorTableAssembly(this, tname, null, false, assembly);
         assembly.setVisible(false);
         addAssembly(assembly);
      }

      return assembly;
   }

   /**
    * Get all the assemblies.
    * @return all the assemblies.
    */
   @Override
   public Assembly[] getAssemblies() {
      return getAssemblies(false);
   }

   /**
    * Get all the assemblies.
    * @param sort <tt>true</tt> to sort the assembliies by dependency,
    * <tt>false</tt> otherwise.
    * @return all the assemblies.
    */
   public synchronized Assembly[] getAssemblies(boolean sort) {
      List<WSAssembly> assemblies = sort ? new ArrayList<>(this.assemblies) : this.assemblies;

      if(sort) {
         Tool.mergeSort(assemblies, new DependencyComparator(this, true));
      }

      return assemblies.toArray(new Assembly[assemblies.size()]);
   }

   /**
    * Add an assembly.
    * @param assembly the specified assembly, <tt>null</tt> to remove it.
    * @return <tt>true</tt> if successful, <tt>false</tt> otherwise.
    */
   public boolean addAssembly(WSAssembly assembly) {
      String name = assembly.getName();
      final boolean contained;

      synchronized(this) {
         if(assembly instanceof CubeTableAssembly && assembly.getName().contains(Assembly.CUBE_VS))
         {
            cubes.put(assembly.getName(), assembly);
            return true;
         }

         WSAssembly assembly2 = (WSAssembly) getAssembly(name);
         contained = assembly2 != null;

         if(assembly2 != null && assembly2.getAssemblyType() != assembly.getAssemblyType()) {
            LOG.warn("Could not add assembly " + assembly + ", type does not match: " +
                     assembly2.getAssemblyType() + ", " + assembly.getAssemblyType());
            return false;
         }

         if(assembly2 != null) {
            assemblies.remove(assembly2);
         }

         assembly.setWorksheet(this);
         assemblies.add(assembly);
         amap = null;

         if(assembly instanceof TableAssembly) {
            ((TableAssembly) assembly).clearCache();
         }

         if(primary == null && assembly.isVisible()) {
            primary = name;
         }
      }

      if(!contained) {
         fireEvent(ADD_ASSEMBLY, name);
      }

      return true;
   }

   /**
    * Remove an assembly.
    * @param name the specified assembly name.
    * @return <tt>true</tt> if successful, <tt>false</tt> otherwise.
    */
   public boolean removeAssembly(String name) {
      boolean removed = false;

      synchronized(this) {
         for(int i = 0; i < assemblies.size(); i++) {
            WSAssembly assembly = assemblies.get(i);

            if(assembly.getName().equals(name)) {
               if(Tool.equals(name, primary)) {
                  primary = null;
               }

               assemblies.remove(i);
               amap = null;
               removeMirrors(assembly);
               removed = true;
               break;
            }
         }
      }

      if(removed) {
         fireEvent(REMOVE_ASSEMBLY, name);
         return true;
      }

      return cubes.remove(name) != null;
   }

   /**
    * Remove an assembly.
    * @param assembly the specified assembly.
    * @return <tt>true</tt> if successful, <tt>false</tt> otherwise.
    */
   public boolean removeAssembly(Assembly assembly) {
      return removeAssembly(assembly.getName());
   }

   /**
    * Rename an assembly.
    * @param oname the specified old assembly name.
    * @param nname the specified new assembly name.
    * @param both <tt>true</tt> to rename both assembly name and variable name,
    * <tt>false</tt> to rename assembly name only.
    * @return <tt>true</tt> if successful, <tt>false</tt> otherwise.
    */
   public boolean renameAssembly(String oname, String nname, boolean both) {
      synchronized(this) {
         if(!containsAssembly(oname) ||
            (!oname.equalsIgnoreCase(nname) && containsAssemblyIgnoreCase(nname)))
         {
            return false;
         }

         if(!update()) {
            return false;
         }

         if(Tool.equals(primary, oname)) {
            primary = nname;
         }

         AbstractWSAssembly oassembly = (AbstractWSAssembly) getAssembly(oname);

         if(oassembly instanceof VariableAssembly) {
            ((VariableAssembly) oassembly).setName(nname, both);
         }
         else {
            oassembly.setName(nname);
         }

         for(int i = 0; i < assemblies.size(); i++) {
            WSAssembly assembly = assemblies.get(i);
            assembly.renameDepended(oname, nname);
         }
      }

      fireEvent(RENAME_ASSEMBLY, oname + "^" + nname);
      return true;
   }

   /**
    * Get the gap between two assemblies.
    * @return the gap between two assemblies.
    */
   @Override
   protected int getGap() {
      return 1;
   }

   /**
    * Get the size of the worksheet.
    * @return the size of the worksheet.
    */
   @Override
   public synchronized Dimension getPixelSize() {
      int maxw = 0;
      int maxh = 0;

      for(WSAssembly assembly : assemblies) {
         Dimension size = assembly.getPixelSize();
         Point pos = assembly.getPixelOffset();

         maxw = Math.max(maxw, size.width + pos.x);
         maxh = Math.max(maxh, size.height + pos.y);
      }

      maxw += (10 * AssetUtil.defw);
      maxh += (10 * AssetUtil.defh);

      maxw = Math.max(MIN_SIZE.width, maxw);
      maxh = Math.max(MIN_SIZE.height, maxh);

      return new Dimension(maxw, maxh);
   }

   /**
    * Get the description of the worksheet.
    * @return the description of the worksheet.
    */
   @Override
   public String getDescription() {
      WSAssembly assembly = getPrimaryAssembly();

      return assembly == null ? null : assembly.getDescription();
   }

   /**
    * Check if the worksheet is valid.
    */
   @Override
   public synchronized void checkValidity(boolean checkCrossJoins) throws Exception {
      for(int i = 0; i < assemblies.size(); i++) {
         WSAssembly assembly = assemblies.get(i);
         assembly.checkValidity(checkCrossJoins);
      }
   }

   /**
    * Check if the dependencies are valid.
    */
   @Override
   public synchronized void checkDependencies() throws InvalidDependencyException {
      for(int i = 0; i < assemblies.size(); i++) {
         WSAssembly assembly = assemblies.get(i);
         assembly.checkDependency();
      }
   }

   /**
    * Replace all embeded user variables.
    * @param vars the specified variable table.
    */
   public synchronized void replaceVariables(VariableTable vars) {
      for(int i = 0; i < assemblies.size(); i++) {
         WSAssembly assembly = assemblies.get(i);

         if(assembly.getAssemblyType() == TABLE_ASSET) {
            assembly.replaceVariables(vars);
         }
      }
   }

   /**
    * Get all variables in the condition value list.
    * @return the variable list.
    */
   @Override
   public synchronized UserVariable[] getAllVariables() {
      List<UserVariable> list = new ArrayList<>();
      List<WSAssembly> assemblyList = new ArrayList<>();
      assemblyList.addAll(assemblies);
      assemblyList.addAll(cubes.values());

      for(WSAssembly assembly : assemblyList) {
         if(assembly.getAssemblyType() == TABLE_ASSET) {
            UserVariable[] vars = assembly.getAllVariables();
            mergeVariables(list, vars);
         }
      }

      UserVariable[] vars = new UserVariable[list.size()];
      list.toArray(vars);
      return vars;
   }

   /**
    * Get the assemblies that depends on this (entry) assembly. For example, if
    * a table A is used in a join, the join table is returned for entry A.
    */
   public synchronized AssemblyRef[] getDependings(AssemblyEntry entry) {
      Set<AssemblyRef> set = new HashSet<>();
      Set<AssemblyRef> set2 = new HashSet<>();
      AssemblyRef ref = new AssemblyRef(entry);
      final Assembly[] assemblies = getAssemblies();

      for(Assembly assembly : assemblies) {
         set2.clear();
         assembly.getDependeds(set2);

         if(set2.contains(ref)) {
            set.add(new AssemblyRef(ref.getType(), assembly.getAssemblyEntry()));
         }
      }

      AssemblyRef[] arr = new AssemblyRef[set.size()];
      set.toArray(arr);
      return arr;
   }

   /**
    * Update this worksheet.
    * @param rep the specified asset repository.
    * @param entry the specified entry stored in.
    * @param user the specified principal.
    */
   @Override
   public boolean update(AssetRepository rep, AssetEntry entry, Principal user) {
      boolean result = updateMirrors(rep, user, entry);
      result = update() && result;

      return result;
   }

   /**
    * Update the references in worksheet.
    * @return <tt>true</tt> if successful, <tt>false</tt> otherwise.
    */
   public synchronized boolean update() {
      boolean result = true;

      for(int i = 0; i < assemblies.size(); i++) {
         WSAssembly assembly = assemblies.get(i);

         if(!assembly.update()) {
            result = false;
         }
      }

      return result;
   }

   /**
    * Update the auto update outer mirrors in the worksheet.
    * @param engine the specified asset repository.
    * @param user the specified user.
    * @param worksheetEntry the specified worksheet entry.
    * @return <tt>true</tt> if successful, <tt>false</tt> otherwise.
    */
   public synchronized boolean updateMirrors(AssetRepository engine, Principal user,
                                             AssetEntry worksheetEntry)
   {
      boolean ok = true;
      List<WSAssembly> allAssemblies = new ArrayList<>(assemblies);

      for(int i = 0; i < allAssemblies.size(); i++) {
         WSAssembly assembly = allAssemblies.get(i);

         if(!(assembly instanceof MirrorAssembly)) {
            continue;
         }

         MirrorAssembly massembly = (MirrorAssembly) assembly;

         // don't update if not auto-update only if it already contains the mirror.
         // otherwise it will always be null. (50056)
         if(!assembly.isVisible() || !massembly.isAutoUpdate() && massembly.getAssembly() != null ||
            !massembly.isOuterMirror() || offline)
         {
            continue;
         }

         try {
            AssetEntry entry = massembly.getEntry();

            if(worksheetEntry.getScope() == AssetRepository.REPORT_SCOPE
               && entry != null && entry.getScope() != AssetRepository.REPORT_SCOPE)
            {
               massembly.updateMirror(
                  AssetUtil.getAssetRepository(false), user);
            }
            else {
               massembly.updateMirror(engine, user);
            }
         }
         catch(Exception ex) {
            ok = false;
            List<Object> exs = (List<Object>) engine.ASSET_ERRORS.get();

            if(exs == null) {
               exs = new ArrayList<>();
            }

            if(!exs.contains(assembly.getName())) {
               exs.add(assembly.getName());
            }

            if(ex instanceof MessageException && worksheetEntry != null) {
               Catalog catalog = Catalog.getCatalog();
               String message = catalog.getString(
                  "composer.ws.outerDependencies.updateError.messsage",
                  worksheetEntry.getSheetName(), ex.getMessage());
               Tool.addUserMessage(message, ConfirmException.WARNING);
            }
         }
      }

      removeOrphanedOuterAssemblies(engine, user);
      return ok;
   }

   /**
    * Remove the useless outer mirrors in the worksheet.
    */
   private synchronized void removeMirrors(WSAssembly assembly) {
      if(!(assembly instanceof MirrorAssembly)) {
         return;
      }

      Worksheet ws = assembly.getWorksheet();
      MirrorAssembly massembly = (MirrorAssembly) assembly;

      if(!massembly.isOuterMirror()) {
         return;
      }

      AssetEntry entry = massembly.getEntry();
      String rname = massembly.getAssemblyName();
      Assembly root = ws.getAssembly(rname);

      if(root == null) {
         return;
      }

      AssemblyRef[] refs = ws.getDependings(root.getAssemblyEntry());

      if(refs != null && refs.length > 0) {
         return;
      }

      String prefix = AssetUtil.createPrefix(entry);

      for(int i = assemblies.size() - 1; i >= 0 ; i--) {
         Assembly tassembly = assemblies.get(i);

         if(tassembly.getName().startsWith(prefix)) {
            assemblies.remove(i);
            amap = null;
         }
      }
   }

   /**
    * Get the outer dependents.
    * @return the outer dependents.
    */
   @Override
   public synchronized AssetEntry[] getOuterDependents() {
      Set<AssetEntry> list = new HashSet<>();

      for(int i = 0; i < assemblies.size(); i++) {
         WSAssembly assembly = assemblies.get(i);

         if(!(assembly instanceof MirrorAssembly)) {
            continue;
         }

         MirrorAssembly massembly = (MirrorAssembly) assembly;

         if(!massembly.isOuterMirror()) {
            continue;
         }

         AssetEntry entry = massembly.getEntry();

         if(entry != null) {
            list.add(entry);
         }
      }

      AssetEntry[] arr = new AssetEntry[list.size()];
      list.toArray(arr);
      return arr;
   }

   /**
    * Rename an outer dependent.
    * @param oentry the specified old entry.
    * @param nentry the specified new entry.
    */
   @Override
   public synchronized void renameOuterDependent(AssetEntry oentry, AssetEntry nentry) {
      for(int i = 0; i < assemblies.size(); i++) {
         WSAssembly assembly = assemblies.get(i);

         if(!(assembly instanceof MirrorAssembly)) {
            continue;
         }

         MirrorAssembly massembly = (MirrorAssembly) assembly;

         if(!massembly.isOuterMirror()) {
            continue;
         }

         AssetEntry entry = massembly.getEntry();

         if(Tool.equals(entry, oentry)) {
            massembly.setEntry(nentry);
         }
      }
   }

   /**
    * Get the outer dependencies.
    * @return the outer dependencies.
    */
   public AssetEntry[] getOuterDependencies(boolean sort) {
      AssetEntry[] arr = new AssetEntry[dependencies.size()];
      dependencies.toArray(arr);

      if(sort) {
         Arrays.sort(arr, VersionControlComparators.assetEntry);
      }

      return arr;
   }

   /**
    * Add an outer dependency.
    * @param entry the specified entry.
    * @return <tt>true</tt> if successful, <tt>false</tt> otherwise.
    */
   @Override
   public boolean addOuterDependency(AssetEntry entry) {
      return dependencies.add(entry);
   }

   /**
    * Remove an outer dependency.
    * @param entry the specified entry.
    * @return <tt>true</tt> if successful, <tt>false</tt> otherwise.
    */
   @Override
   public boolean removeOuterDependency(AssetEntry entry) {
      return dependencies.remove(entry);
   }

   /**
    * Remove all the outer dependencies.
    */
   @Override
   public void removeOuterDependencies() {
      dependencies.clear();
   }

   /**
    * Merge a variable array to list.
    * @param list the specified list.
    * @param vars the specified variable array.
    */
   private void mergeVariables(List<UserVariable> list, UserVariable[] vars) {
      for(UserVariable var : vars) {
         if(!AssetUtil.containsVariable(list, var)) {
            list.add(var);
         }
      }
   }

   /**
    * Rearrange the assemblies into a tree like structure where the root
    * is displayed on top, and children below, and so on.
    */
   public synchronized void autoLayout() {
      // sort the assemblies base on dependency association so the autoLayout0
      // can assume the assemblies are already in the correct order.
      List<WSAssembly> as = new ArrayList<>(assemblies);
      Tool.mergeSort(as, new DependencyComparator(this));

      autoLayout0(as);
   }

   /**
    * Arrange the assemblies from root to leaf.
    */
   private void autoLayout0(List<WSAssembly> assemblies) {
      // this algorithm is not very optimal but given the limited number of
      // tables on a worksheet, the efficiency should not be an issue:
      // 1. go through all assemblies in the worksheet
      // 2. for each assembly, move its children to the next level
      // 3. if an assembly is moved, all it's children are moved as well

      Point top = new Point(0, 0);
      int bottom = -18; // index of the bottom occupied row

      // move all assemblies to top row
      for(WSAssembly assembly : assemblies) {
         assembly.setPixelOffset(new Point(top));
      }

      // process dependency
      for(int i = 0; i < assemblies.size(); i++) {
         WSAssembly assembly = assemblies.get(i);

         if(!assembly.isVisible()) {
            continue;
         }

         // move assembly and all dependencies to bottom
         if(AssetUtil.getDependedAssemblies(this, assembly, false).length > 0) {
            if(assembly.getPixelOffset().equals(top)) {
               assembly.setPixelOffset(new Point(AssetUtil.defw, (bottom + (2 * AssetUtil.defh))));
               bottom = assembly.getPixelOffset().y + assembly.getPixelSize().height
                  - AssetUtil.defh;
            }

            bottom = moveChildren(assembly, bottom, top);
         }
      }

      int x = AssetUtil.defw;
      int maxH = 0;

      // push all unrelated elements to the bottom
      for(WSAssembly assembly : assemblies) {
         if(!assembly.isVisible()) {
            continue;
         }

         if(assembly.getPixelOffset().equals(top)) {
            assembly.setPixelOffset(new Point(x, bottom + (2 * AssetUtil.defh)));

            x += assembly.getPixelSize().width + AssetUtil.defw;
            maxH = Math.max(maxH, assembly.getPixelSize().height);

            if(x > PREF_COL * AssetUtil.defh) {
               bottom += maxH + AssetUtil.defh;
               x = AssetUtil.defw;
               maxH = 0;
            }
         }
      }
   }

   /**
    * Push all children of the assembly to below the parent.
    * @return the bottom of all children.
    */
   private int moveChildren(WSAssembly parent, int bottom, Point top) {
      HashSet<AssemblyRef> deps = new HashSet<>();
      parent.getDependeds(deps);
      Iterator<AssemblyRef> iter = deps.iterator();
      int x = AssetUtil.defw;
      int bottom2 = bottom;

      while(iter.hasNext()) {
         AssemblyRef ref = iter.next();
         AssemblyEntry entry = ref.getEntry();
         Assembly child = getAssembly(entry.getName());

         // this should never happen but in case the state is out of sync
         if(child == null) {
            continue;
         }

         if(!child.isVisible()) {
            continue;
         }

         // already positioned
         if(!child.getPixelOffset().equals(top)) {
            continue;
         }

         child.setPixelOffset(new Point(x, bottom + (2 * AssetUtil.defh)));
         bottom2 = Math.max(bottom2, child.getPixelOffset().y +
            child.getPixelSize().height - AssetUtil.defh);
         x += child.getPixelSize().width + AssetUtil.defw;

         if(x > PREF_COL) {
            x = AssetUtil.defw;
            bottom = bottom2;
         }
      }

      return bottom2;
   }

   /**
    * Reset the worksheet.
    */
   @Override
   public synchronized void reset() {
      for(int i = 0; i < assemblies.size(); i++) {
         WSAssembly assembly = assemblies.get(i);
         assembly.reset();
      }
   }

   /**
    * Get the assemblies depended on of an assembly in a sheet.
    * @param entry the specified assembly entry.
    */
   @Override
   public AssemblyRef[] getDependeds(AssemblyEntry entry) {
      return getDependeds(entry, false, false);
   }

   /**
    * Get the assemblies depended on of an assembly in a viewsheet.
    * @param entry the specified assembly entry.
    * @param view <tt>true</tt> to include view, <tt>false</tt> otherwise.
    * @param out <tt>out</tt> to include out, <tt>false</tt> otherwise.
    */
   @Override
   public AssemblyRef[] getDependeds(AssemblyEntry entry, boolean view,
                                     boolean out) {
      WSAssembly assembly = (WSAssembly) getAssembly(entry);

      if(assembly == null) {
         return new AssemblyRef[0];
      }

      Set<AssemblyRef> set = new HashSet<>();
      assembly.getDependeds(set);
      AssemblyRef[] refs = new AssemblyRef[set.size()];
      set.toArray(refs);

      return refs;
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.print("<worksheet modified=\"" + getLastModified());

      if(getLastModifiedBy() != null) {
         writer.print("\" modifiedBy=\"" + Tool.escape(getLastModifiedBy()));
      }

      if(getCreatedBy() != null) {
         writer.print("\" createdBy=\"" + Tool.escape(getCreatedBy()));
         writer.print("\" created=\"" + getCreated());
      }

      writer.println("\" class=\"" + getClass().getName() +
         "\" offline=\"" + offline + "\">");
      writeContents(writer);
      writer.println("</worksheet>");
   }

   /**
    * Write the content part(child node) of XML segment.
    */
   protected void writeContents(PrintWriter writer) {
      writer.println("<Version>" + getVersion() + "</Version>");

      writer.println("<worksheetInfo>");
      winfo.writeXML(writer);
      writer.println("</worksheetInfo>");

      if(primary != null) {
         writer.print("<primary>");
         writer.print("<![CDATA[" + primary + "]]>");
         writer.println("</primary>");
      }

      List<WSAssembly> assemblies;

      // WSAssembly.writeXML may synchornize on itself. don't lock worksheet when
      // calling writeXML to avoid deadlock.
      synchronized(this) {
         assemblies = new ArrayList<>(this.assemblies);
      }

      int size = assemblies.size();

      if(size > 0) {
         writer.println("<assemblies>");

         for(WSAssembly assembly : assemblies) {
            writer.println("<oneAssembly>");
            assembly.writeXML(writer);
            writer.println("</oneAssembly>");
         }

         writer.println("</assemblies>");
      }

      AssetEntry[] entries = getOuterDependencies(true);

      if(entries.length > 0) {
         writer.println("<dependencies>");

         for(AssetEntry entry : entries) {
            entry.writeXML(writer);
         }

         writer.println("</dependencies>");
      }
   }

   /**
    * Method to parse an xml segment.
    * @param elem the specified xml element.
    */
   @Override
   public void parseXML(Element elem) throws Exception {
      String val = Tool.getAttribute(elem, "modified");

      if(val != null) {
         setLastModified(Long.parseLong(val));
      }

      val = Tool.getAttribute(elem, "modifiedBy");

      if(val != null) {
         setLastModifiedBy(val);
      }

      val = Tool.getAttribute(elem, "created");

      if(val != null) {
         setCreated(Long.parseLong(val));
      }

      val = Tool.getAttribute(elem, "createdBy");

      if(val != null) {
         setCreatedBy(val);
      }

      offline = "true".equals(Tool.getAttribute(elem, "offline"));
      setVersion(Tool.getChildValueByTagName(elem, "Version"));
      this.primary = Tool.getChildValueByTagName(elem, "primary");

      Element dsnode = Tool.getChildNodeByTagName(elem, "dependencies");

      if(dsnode != null) {
         NodeList list = Tool.getChildNodesByTagName(dsnode, "assetEntry");

         for(int i = 0; i < list.getLength(); i++) {
            Element anode = (Element) list.item(i);
            AssetEntry entry = AssetEntry.createAssetEntry(anode);
            this.dependencies.add(entry);
         }
      }

      Element winode = Tool.getChildNodeByTagName(elem, "worksheetInfo");

      if(winode != null) {
         winfo.parseXML(Tool.getFirstChildNode(winode));
      }

      Element asnode = Tool.getChildNodeByTagName(elem, "assemblies");

      if(asnode != null) {
         NodeList list = Tool.getChildNodesByTagName(asnode, "oneAssembly");

         for(int i = 0; i < list.getLength(); i++) {
            Element onenode = (Element) list.item(i);
            Element anode = Tool.getChildNodeByTagName(onenode, "assembly");
            WSAssembly aobj = AbstractWSAssembly.createWSAssembly(anode, this);
            this.assemblies.add(aobj);
         }
      }

      // do not log for context parse
      try {
         log = false;
         update();
      }
      finally {
         log = true;
      }
   }

   /**
    * Clone the object.
    */
   @Override
   public Object clone() {
      try {
         Worksheet ws = (Worksheet) super.clone();

         ws.winfo = (WorksheetInfo) winfo.clone();
         ws.assemblies = cloneAssemblyList(assemblies);
         ws.dependencies = new HashSet<>(dependencies);

         // set proper worksheet
         for(int i = 0; i < ws.assemblies.size(); i++) {
            WSAssembly assembly = ws.assemblies.get(i);
            assembly.setWorksheet(ws);
         }

         ws.amap = null;
         ws.update();

         return ws;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone object", ex);
         return null;
      }
   }

   /**
    * Clone assembly list.
    */
   private synchronized List<WSAssembly> cloneAssemblyList(List<WSAssembly> list) {
      return list.stream()
         .map((assembly) -> (WSAssembly) assembly.clone())
         .collect(Collectors.toList());
   }

   /**
    * Get cube table assembly.
    */
   public CubeTableAssembly getCubeTableAssembly(String name) {
      if(name == null || !name.startsWith(Assembly.CUBE_VS)) {
         return null;
      }

      String name0 = name.substring(Assembly.CUBE_VS.length());
      int idx = name0.lastIndexOf("/");

      if(idx < 0) {
         return null;
      }

      String dx = name0.substring(0, idx);
      String cubeName = name0.substring(idx + 1);
      XCube cube = AssetUtil.getCube(dx, cubeName);

      if(cube == null) {
         return null;
      }

      CubeTableAssembly cass = new CubeTableAssembly(this, name);
      SourceInfo sinfo = new SourceInfo(SourceInfo.CUBE, dx, cubeName);
      cass.setSourceInfo(sinfo);

      return cass;
   }

   /**
    * Set it's offline worksheet or not.
    */
   public void setOffline(boolean offline) {
      this.offline = offline;
   }

   /**
    * return if it's offline worksheet or not.
    */
   public boolean isOffline() {
      return offline;
   }

   /**
    * Write out data content in each assembly.
    */
   @Override
   public void writeData(JarOutputStream out) {
      Assembly[] arr = getAssemblies();

      for(Assembly anArr : arr) {
         if(anArr instanceof TableAssembly) {
            boolean needWrite = true;

            // Bug #45308. .tdat file only write once
            // when write SnapshotEmbeddedTableAssembly of dependent ws
            if(anArr instanceof SnapshotEmbeddedTableAssembly
               && anArr.getName().startsWith(AssetUtil.OUTER_PREFIX))
            {
               needWrite = false;
            }

            if(needWrite) {
               ((TableAssembly) anArr).writeData(out);
            }
         }
      }
   }

   /**
    * Dispose executing assembly.
    */
   public void dispose() {
      Assembly[] arr = getAssemblies();

      for(Assembly anArr : arr) {
         if(anArr instanceof SnapshotEmbeddedTableAssembly) {
            ((SnapshotEmbeddedTableAssembly) anArr).dispose();
         }
      }
   }

   public void clearSnapshot() {
      for(WSAssembly assembly : assemblies) {
         if(assembly instanceof SnapshotEmbeddedTableAssembly) {
            ((SnapshotEmbeddedTableAssembly) assembly).deleteDataFiles("Worksheet removed");
         }
      }
   }

   /**
    * Sets whether the current thread is writing to temp (swap/auto-save) storage.
    */
   public static void setIsTEMP(boolean is_Temp) {
      if(is_Temp) {
         temp.set(is_Temp);
      }
      else {
         temp.remove();
      }
   }

   /**
    * Check whether the current thread is writing to temp (swap/auto-save) storage.
    */
   public static boolean isTemp() {
      return temp.get();
   }

   /**
    * Removes outer assemblies that don't have a dependency to an outer mirror
    */
   private void removeOrphanedOuterAssemblies(AssetRepository engine, Principal user) {
      try {
         List<WSAssembly> assemblyList = new ArrayList<>(this.assemblies);
         Set<String> outerAssemblyNames = new HashSet<>();

         for(int i = assemblyList.size() - 1; i >= 0; i--) {
            WSAssembly assembly = assemblyList.get(i);

            if(assembly instanceof MirrorAssembly && ((MirrorAssembly) assembly).isOuterMirror()) {
               assemblyList.remove(i);
               outerAssemblyNames.addAll(
                  AssetUtil.getOuterAssemblyNames(engine, ((MirrorAssembly) assembly).getEntry(),
                                                  user));
            }
         }

         // check if outer assembly has dependency on outer mirror
         for(WSAssembly assembly : assemblyList) {
            // remove the outer assembly if there is no dependency
            if(assembly.isOuter() && !outerAssemblyNames.contains(assembly.getName())) {
               this.assemblies.remove(assembly);
            }
         }
      }
      catch(Exception e) {
         LOG.debug("Failed to remove orphaned outer tables", e);
      }
   }

   private static final Logger LOG = LoggerFactory.getLogger(Worksheet.class);
   private static ThreadLocal<Boolean> temp = ThreadLocal.withInitial(() -> Boolean.FALSE);

   boolean log = true; // true to log error, false otherwise
   private String primary; // primary assembly
   private List<WSAssembly> assemblies; // assembly list
   private final Map<String, WSAssembly> cubes; // cube table assembly map
   private HashSet<AssetEntry> dependencies; // outer dependencies
   private WorksheetInfo winfo; // viewsheet info
   private boolean offline;
   private transient Map<String, WSAssembly> amap;
}
