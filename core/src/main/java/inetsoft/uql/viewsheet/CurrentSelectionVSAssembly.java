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
package inetsoft.uql.viewsheet;

import inetsoft.uql.asset.Assembly;
import inetsoft.uql.asset.AssemblyRef;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.awt.*;
import java.io.PrintWriter;
import java.util.List;
import java.util.*;

/**
 * CurrentSelectionVSAssembly represents current selection assembly contained
 * in a <tt>Viewsheet</tt>.
 *
 * @version 10.1
 * @author InetSoft Technology Corp
 */
public class CurrentSelectionVSAssembly extends AbstractContainerVSAssembly
   implements TitledVSAssembly, MaxModeSupportAssembly
{
   /**
    * Constructor.
    */
   public CurrentSelectionVSAssembly() {
      super();
   }

   /**
    * Constructor.
    */
   public CurrentSelectionVSAssembly(Viewsheet vs, String name) {
      super(vs, name);
   }

   /**
    * Get the type.
    * @return the type of the assembly.
    */
   @Override
   public int getAssemblyType() {
      return Viewsheet.CURRENTSELECTION_ASSET;
   }

   /**
    * Set the size.
    * @param size the specified size.
    */
   @Override
   public void setPixelSize(Dimension size) {
      super.setPixelSize(size);
      updateChildSize();
   }

   /**
    * Check if show runtime current selection or not at runtime.
    */
   public boolean isShowCurrentSelection() {
      return getContainerInfo().isShowCurrentSelection();
   }

   /**
    * Check if show current selection or not at design time.
    */
   public boolean getShowCurrentSelectionValue() {
      return getContainerInfo().getShowCurrentSelectionValue();
   }

   /**
    * Set show current selection or not at design time.
    */
   public void setShowCurrentSelectionValue(boolean show) {
      getContainerInfo().setShowCurrentSelectionValue(show);
   }

   /**
    * Create assembly info.
    * @return the associated assembly info.
    */
   @Override
   protected VSAssemblyInfo createInfo() {
      return new CurrentSelectionVSAssemblyInfo();
   }

   /**
    * Get container assembly info.
    * @return the container assembly info.
    */
   @Override
   protected CurrentSelectionVSAssemblyInfo getContainerInfo() {
      return (CurrentSelectionVSAssemblyInfo) info;
   }

   /**
    * Update the children position.
    */
   protected void updateChildSize() {
      String[] children = getAssemblies();
      Rectangle bounds = getBounds();

      for(int i = 0; i < children.length; i++) {
         Assembly child = getViewsheet().getAssembly(children[i]);
         child.setPixelSize(new Dimension(bounds.width, child.getBounds().height));
      }
   }

   /**
    * Layout the Container Assembly.
    * @return the names of the assemblies relocated.
    */
   @Override
   public Assembly[] layout() {
      String[] children = getAssemblies();
      Point pos = getPixelOffset();
      Dimension size = getPixelSize();
      ArrayList arr = new ArrayList();
      int rowsHeight = AssetUtil.defh;// title covered

      if(isShowCurrentSelection()) {
         rowsHeight += getOutSelectionTitles().length * AssetUtil.defh;
      }

      for(String child : children) {
         Assembly assembly = getViewsheet().getAssembly(child);

         if(assembly == null) {
            continue;
         }

         Point childPos = assembly.getPixelOffset();
         Dimension asize = assembly.getPixelSize();

         if(childPos.y != pos.y + rowsHeight || childPos.x != pos.x ||
            asize.width != size.width)
         {
            childPos.y = pos.y + rowsHeight;
            childPos.x = pos.x;
            asize.width = size.width;
            arr.add(assembly);
         }

         rowsHeight += assembly.getPixelSize().height;
      }

      Assembly[] assemblies = new Assembly[arr.size()];
      arr.toArray(assemblies);
      return assemblies;
   }

   /**
    * Clear outward selections.
    */
   public void clearOutSelections() {
      getContainerInfo().clearOutSelections();
   }

   /**
    * Get outward selections title.
    * @return the outward selections' title.
    */
   public String[] getOutSelectionTitles() {
      return getContainerInfo().getOutSelectionTitles();
   }

   /**
    * Set outward selection title.
    */
   public void setOutSelectionValue(String name, String title, String value) {
      getContainerInfo().setOutSelectionValue(name, title, value);
   }

   /**
    * Get the assemblies depended on.
    * @param set the set stores the assemblies depended on.
    */
   @Override
   public void getDependeds(Set<AssemblyRef> set) {
      super.getDependeds(set);
      // current selection depends on child and out selection dynamic values,
      // such as title, so here put all child/out selection to depend on
      getDepends(set, AssemblyRef.OUTPUT_DATA);
   }

   /**
    * Get the view assemblies depended on.
    * @param set the set stores the presentation assemblies depended on.
    * @param self <tt>true</tt> to include self, <tt>false</tt> otherwise.
    */
   @Override
   public void getViewDependeds(Set<AssemblyRef> set, boolean self) {
      super.getViewDependeds(set, self);
      getDepends(set, AssemblyRef.VIEW);
   }

   /**
    * Update the out ward selections for current selections.
    */
   public void updateOutSelection() {
      if(isShowCurrentSelection()) {
         Viewsheet vs = getViewsheet();
         Assembly[] assemblies = vs.getAssemblies();
         sortOutSelections(assemblies);
         clearOutSelections();

         for(int i = 0; i < assemblies.length; i++) {
            if(isOutSelection(assemblies[i])) {
               SelectionVSAssembly ass = (SelectionVSAssembly) assemblies[i];
               String name = ass.getName();
               String title = ass instanceof TitledVSAssembly ?
                  ((TitledVSAssembly) ass).getTitle() : name;
               String value = ass.getDisplayValue(true);
               value = (value == null) ? Catalog.getCatalog().getString("(none)") : value;
               setOutSelectionValue(name, title, value);
            }
         }
      }
   }

   /**
    * Update objects index.
    */
   public void update(int index0, int index1, boolean changeOutSels) {
      getContainerInfo().update(index0, index1, changeOutSels);
      layout();
   }

   /**
    * Get the group title.
    * @return the title of the checkbox assembly.
    */
   @Override
   public String getTitle() {
      return getContainerInfo().getTitle();
   }

   /**
    * Get the group title value.
    * @return the title value of the checkbox assembly.
    */
   @Override
   public String getTitleValue() {
      return getContainerInfo().getTitleValue();
   }

   /**
    * Set the group title value.
    * @param value the specified group title.
    */
   @Override
   public void setTitleValue(String value) {
      getContainerInfo().setTitleValue(value);
   }

   @Override
   public MaxModeSupportAssemblyInfo getMaxModeInfo() {
      return getContainerInfo();
   }

   /**
    * Check is outward selection ot not.
    */
   private boolean isOutSelection(Assembly assembly) {
      if(assembly instanceof VSAssembly) {
         VSAssembly vsassembly = (VSAssembly) assembly;
         Assembly container = vsassembly.getContainer();
         return vsassembly instanceof SelectionVSAssembly &&
            !(container instanceof CurrentSelectionVSAssembly);
      }

      return false;
   }

   /**
    * Get out/child selection depends.
    */
   private void getDepends(Set<AssemblyRef> set, int type) {
      if(isShowCurrentSelection()) {
         Viewsheet vs = getViewsheet();
         Assembly[] assemblies = vs.getAssemblies();

         for(Assembly assembly : assemblies) {
            if(isOutSelection(assembly)) {
               set.add(new AssemblyRef(type, assembly.getAssemblyEntry()));
            }
         }
      }
   }

   /**
    * Write the state.
    * @param writer the specified print writer.
    */
   @Override
   protected void writeStateContent(PrintWriter writer, boolean runtime) {
      super.writeStateContent(writer, runtime);
      String[] arr = getAssemblies();

      for(int i = 0; i < arr.length; i++) {
         writer.println("<oneAssembly>");
         Assembly assembly = getViewsheet().getAssembly(arr[i]);
         assembly.writeXML(writer);
         writer.println("</oneAssembly>");
      }
   }

   /**
    * Parse the state.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseStateContent(Element elem, boolean runtime)
      throws Exception
   {
      super.parseStateContent(elem, runtime);
      Viewsheet vs = getViewsheet();
      // do not remove assembly here, the Viewsheet will handle all it
      /*
      String arr[] = getAssemblies();

      // remove contained assemblies
      for(int i = 0; i < arr.length; i++) {
         vs.removeAssembly(arr[i]);
      }
      */

      NodeList list = Tool.getChildNodesByTagName(elem, "oneAssembly");
      String[] arr = new String[list.getLength()];

      // add new contained assemblies
      for(int i = 0; i < list.getLength(); i++) {
         Element onenode = (Element) list.item(i);
         Element anode = Tool.getChildNodeByTagName(onenode, "assembly");
         VSAssembly assembly = AbstractVSAssembly.createVSAssembly(anode, vs);

         if(assembly == null) {
            continue;
         }

         arr[i] = assembly.getName();
         vs.addAssembly(assembly);
      }

      setAssemblies(arr);
   }

   /**
    * Sort the assemblies according to the outNames's order.
    */
   private void sortOutSelections(Assembly[] assemblies) {
      Arrays.sort(assemblies, comparator);
   }

   /**
    * Comparator to compare assemblies according to the outNames's order.
    * If the assembly is new added, just return -1.
    */
   private Comparator comparator = new Comparator<Assembly>() {
      @Override
      public int compare(Assembly ass1, Assembly ass2) {
         List outNames = getContainerInfo().getOutSelection();
         int idx1 = outNames.indexOf(ass1.getName());
         int idx2 = outNames.indexOf(ass2.getName());
         return idx2 == -1 ? -1 : idx1 - idx2;
      }
   };
}
