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
package inetsoft.report.composition;

import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.DependencyComparator;
import inetsoft.uql.schema.UserVariable;
import inetsoft.util.Tool;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;
import java.security.Principal;
import java.util.*;

/**
 * WorksheetWrapper, it wraps one worksheet, so that the worksheet will not be
 * polluted when touching assemblies. Meanwhile, the wrapper is more lightweight
 * than the base worksheet, so we could gain better performance by using this
 * wrapper than cloning the base worksheet.
 *
 * @author InetSoft Technology
 * @version 10.2
 */
public final class WorksheetWrapper extends Worksheet {
   /**
    * Create an instance of WorksheetWrapper.
    */
   public WorksheetWrapper(Worksheet ws) {
      super();
      this.inner_ws = ws;
   }

   /**
    * Get the worksheet info.
    */
   @Override
   public WorksheetInfo getWorksheetInfo() {
      return inner_ws.getWorksheetInfo();
   }

   /**
    * Set the worksheet info.
    * @return true if the property change should cause the worksheet to be
    * re-executed.
    */
   @Override
   public boolean setWorksheetInfo(WorksheetInfo winfo) {
      return inner_ws.setWorksheetInfo(winfo);
   }

   /**
    * Get the worksheet.
    */
   public Worksheet getWorksheet() {
      return inner_ws;
   }

   /**
    * Check if contains an assembly.
    * @param name the specified assembly name.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   @Override
   public boolean containsAssembly(String name) {
      return containsAssembly(name, true);
   }

   /**
    * Check if contains an assembly.
    * @param name the specified assembly name.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   public boolean containsAssembly(String name, boolean all) {
      int len = alist.size();

      for(int i = 0; i < len; i++) {
         if(alist.get(i).getName().equals(name)) {
            return true;
         }
      }

      return all ? inner_ws.containsAssembly(name) : false;
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
      return getAssembly(name, true);
   }

   /**
    * Get an assembly by its name.
    * @param name the specified assembly name.
    * @return the assembly, <tt>null</tt> if not found.
    */
   public Assembly getAssembly(String name, boolean create) {
      int len = alist.size();

      for(int i = 0; i < len; i++) {
         WSAssembly assembly = alist.get(i);

         if(assembly.getName().equals(name)) {
            return assembly;
         }
      }

      if(!create || inner_ws == null) {
         return null;
      }

      WSAssembly assembly = (WSAssembly) inner_ws.getAssembly(name);

      if(assembly != null) {
         assembly = (WSAssembly) assembly.clone();
         assembly.setWorksheet(this);
         alist.add(assembly);
      }

      return assembly;
   }

   /**
    * Get the table assembly used in viewsheet by the name of the base assembly.
    * @param bname the specified base assembly name.
    * @return the assembly, <tt>null</tt> if not found.
    */
   @Override
   public TableAssembly getVSTableAssembly(String bname) {
      if(bname == null) {
         return null;
      }

      String tname = Assembly.TABLE_VS + bname;
      TableAssembly assembly = (TableAssembly) getAssembly(tname, false);

      if(assembly != null) {
         return assembly;
      }

      assembly = (TableAssembly) getAssembly(bname);

      if(assembly == null) {
         return null;
      }

      assembly = new MirrorTableAssembly(this, tname, null, false, assembly);
      assembly.setWorksheet(this);
      assembly.setVisible(false);
      alist.add(assembly);

      return assembly;
   }

   /**
    * Get all the assemblies.
    * @return all the assemblies.
    */
   @Override
   public Assembly[] getAssemblies() {
      Assembly[] arr = new Assembly[alist.size()];
      alist.toArray(arr);
      return arr;
   }

   /**
    * Get all the assemblies.
    * @param sort <tt>true</tt> to sort the assemblies by dependency,
    * <tt>false</tt> otherwise.
    * @return all the assemblies.
    */
   @Override
   public Assembly[] getAssemblies(boolean sort) {
      Assembly[] arr = new Assembly[alist.size()];
      alist.toArray(arr);

      if(sort) {
         Arrays.sort(arr, new DependencyComparator(this, true));
      }

      return arr;
   }

   /**
    * Add an assembly.
    * @param assembly the specified assembly, <tt>null</tt> to remove it.
    * @return <tt>true</tt> if successful, <tt>false</tt> otherwise.
    */
   @Override
   public boolean addAssembly(WSAssembly assembly) {
      String name = assembly.getName();
      assembly.setWorksheet(this);
      removeAssembly(name);
      alist.add(assembly);

      return true;
   }

   /**
    * Remove an assembly.
    * @param name the specified assembly name.
    * @return <tt>true</tt> if successful, <tt>false</tt> otherwise.
    */
   @Override
   public boolean removeAssembly(String name) {
      int len = alist.size();
      boolean removed = false;

      for(int i = 0; i < len; i++) {
         WSAssembly assembly = alist.get(i);

         if(assembly.getName().equals(name)) {
            alist.remove(i);
            removed = true;
            break;
         }
      }

      return removed;
   }

   /**
    * Rename an assembly.
    * @param oname the specified old assembly name.
    * @param nname the specified new assembly name.
    * @param both <tt>true</tt> to rename both assembly name and variable name,
    * <tt>false</tt> to rename assembly name only.
    * @return <tt>true</tt> if successful, <tt>false</tt> otherwise.
    */
   @Override
   public boolean renameAssembly(String oname, String nname, boolean both) {
      throw new RuntimeException("Unsupported method called!");
   }

   @Override
   public void checkValidity() throws Exception {
      throw new RuntimeException("Unsupported method called.");
   }

   /**
    * Check if the worksheet is valid.
    */
   @Override
   public void checkValidity(boolean checkCrossJoins) throws Exception {
      throw new RuntimeException("Unsupported method called!");
   }

   /**
    * Check if the dependencies are valid.
    */
   @Override
   public void checkDependencies() throws InvalidDependencyException {
      throw new RuntimeException("Unsupported method called!");
   }

   /**
    * Replace all embeded user variables.
    * @param vars the specified variable table.
    */
   @Override
   public void replaceVariables(VariableTable vars) {
      throw new RuntimeException("Unsupported method called!");
   }

   /**
    * Get all variables in the condition value list.
    * @return the variable list.
    */
   @Override
   public UserVariable[] getAllVariables() {
      throw new RuntimeException("Unsupported method called!");
   }

   /**
    * Update this worksheet.
    * @param rep the specified asset repository.
    * @param entry the specified entry stored in.
    * @param user the specified principal.
    */
   @Override
   public boolean update(AssetRepository rep, AssetEntry entry, Principal user)
   {
      throw new RuntimeException("Unsupported method called!");
   }

   /**
    * Update the references in worksheet.
    * @return <tt>true</tt> if successful, <tt>false</tt> otherwise.
    */
   @Override
   public boolean update() {
      boolean result = true;
      Iterator<WSAssembly> values = alist.iterator();

      while(values.hasNext()) {
         WSAssembly assembly = values.next();

         if(!assembly.update()) {
            result = false;
         }
      }

      return result;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public boolean updateMirrors(AssetRepository engine, Principal user,
                                AssetEntry worksheetEntry)
   {
      throw new RuntimeException("Unsupported method called!");
   }

   /**
    * Get the outer dependents.
    * @return the outer dependents.
    */
   @Override
   public AssetEntry[] getOuterDependents() {
      throw new RuntimeException("Unsupported method called!");
   }

   /**
    * Rename an outer dependent.
    * @param oentry the specified old entry.
    * @param nentry the specified new entry.
    */
   @Override
   public void renameOuterDependent(AssetEntry oentry, AssetEntry nentry) {
      throw new RuntimeException("Unsupported method called!");
   }

   /**
    * Get the outer dependencies.
    * @return the outer dependencies.
    */
   @Override
   public AssetEntry[] getOuterDependencies(boolean sort) {
      return new AssetEntry[0];
   }

   /**
    * Add an outer dependency.
    * @param entry the specified entry.
    * @return <tt>true</tt> if successful, <tt>false</tt> otherwise.
    */
   @Override
   public boolean addOuterDependency(AssetEntry entry) {
      throw new RuntimeException("Unsupported method called!");
   }

   /**
    * Remove an outer dependency.
    * @param entry the specified entry.
    * @return <tt>true</tt> if successful, <tt>false</tt> otherwise.
    */
   @Override
   public boolean removeOuterDependency(AssetEntry entry) {
      throw new RuntimeException("Unsupported method called!");
   }

   /**
    * Remove all the outer dependencies.
    */
   @Override
   public void removeOuterDependencies() {
      throw new RuntimeException("Unsupported method called!");
   }

   /**
    * Reset the worksheet.
    */
   @Override
   public void reset() {
      Iterator<WSAssembly> values = alist.iterator();

      while(values.hasNext()) {
         WSAssembly assembly = values.next();
         assembly.reset();
      }
   }

   /**
    * Get the assemblies depended on of an assembly in a sheet.
    * @param entry the specified assembly entry.
    */
   @Override
   public AssemblyRef[] getDependeds(AssemblyEntry entry) {
      return getDependeds(entry, false);
   }

   /**
    * Get the assemblies depended on of an assembly in a viewsheet.
    * @param entry the specified assembly entry.
    * @param view <tt>true</tt> to include view, <tt>false</tt> otherwise.
    */
   public AssemblyRef[] getDependeds(AssemblyEntry entry, boolean view) {
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
    * Clone the object.
    */
   @Override
   public Object clone() {
      // WorksheetWrapper is a lightweight 'clone' copy of a worksheet. it's used to avoid
      // physically clone the worksheet so we shouldn't do a deep clone here. if this causes
      // problem, we may want to just make a new wrapper and self: new WorksheetWrapper(this)
      return this;
   }

   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);
      inner_ws.writeXML(writer);

      writer.println("<newAssemblies>");

      for(WSAssembly assembly : alist) {
         assembly.writeXML(writer);
      }

      writer.println("</newAssemblies>");
   }

   @Override
   public void parseXML(Element elem) throws Exception {
      super.parseXML(elem);

      Element node = Tool.getChildNodeByTagName(elem, "worksheet");
      inner_ws = new Worksheet();
      inner_ws.parseXML(node);

      node = Tool.getChildNodeByTagName(elem, "newAssemblies");
      NodeList list = Tool.getChildNodesByTagName(node, "assembly");

      for(int i = 0; i < list.getLength(); i++) {
         node = (Element) list.item(i);
         WSAssembly aobj = AbstractWSAssembly.createWSAssembly(node, this);
         addAssembly(aobj);
      }
   }

   /**
    * Print the key to identify this content object. If the keys of two content
    * objects are equal, the content objects are equal too.
    *
    * @param writer The writer to print the output to.
    * @throws Exception
    */
   @Override
   public boolean printKey(PrintWriter writer) throws Exception {
      return inner_ws.printKey(writer);
   }

   private Worksheet inner_ws;
   private List<WSAssembly> alist = new ArrayList<>();
}
