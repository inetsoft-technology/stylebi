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
package inetsoft.uql.viewsheet;

import inetsoft.uql.asset.Assembly;
import inetsoft.uql.viewsheet.internal.TabVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.awt.*;
import java.io.PrintWriter;

/**
 * TabVSAssembly represents one tab assembly contained in a
 * <tt>Viewsheet</tt>.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class TabVSAssembly extends AbstractContainerVSAssembly {
   /**
    * Constructor.
    */
   public TabVSAssembly() {
      super();
   }

   /**
    * Constructor.
    */
   public TabVSAssembly(Viewsheet vs, String name) {
      super(vs, name);
   }

   /**
    * Create assembly info.
    * @return the associated assembly info.
    */
   @Override
   protected VSAssemblyInfo createInfo() {
      return new TabVSAssemblyInfo();
   }

   /**
    * Get the type.
    * @return the type of the assembly.
    */
   @Override
   public int getAssemblyType() {
      return Viewsheet.TAB_ASSET;
   }

   /**
    * Get the runtime labels.
    */
   public String[] getLabels() {
      return getTabInfo().getLabels();
   }

   /**
    * Get the design time labels.
    */
   public String[] getLabelsValue() {
      return getTabInfo().getLabelsValue();
   }

   /**
    * Set the runtime labels.
    */
   public void setLabelsValue(String[] labels) {
      getTabInfo().setLabelsValue(labels);
   }

   /**
    * Get the runtime selected object name.
    * @return the name of the selected object.
    */
   public String getSelected() {
      return getTabInfo().getSelected();
   }

   /**
    * Get the design time selected object name.
    * @return the name of the selected object.
    */
   public String getSelectedValue() {
      return getTabInfo().getSelectedValue();
   }

   /**
    * Set the selected object name.
    * @param selected the name of the selected object.
    */
   public void setSelectedValue(String selected) {
      int index = selected == null ? -1 : selected.lastIndexOf(".");
      selected = index == -1 ? selected : selected.substring(index + 1);
      getTabInfo().setSelectedValue(selected);
   }

   /**
    * Remove a child assembly from the tab.
    */
   @Override
   public boolean removeAssembly(String assembly) {
      String[] arr = getAssemblies();
      String[] labels = getLabelsValue();

      for(int i = 0; i < arr.length; i++) {
         if(arr[i].equals(assembly)) {
            String[] narr = new String[arr.length - 1];

            System.arraycopy(arr, 0, narr, 0, i);
            System.arraycopy(arr, i + 1, narr, i, arr.length - i - 1);
            setAssemblies(narr);

            if(i < labels.length) {
               String[] nlabels = new String[labels.length - 1];

               System.arraycopy(labels, 0, nlabels, 0, i);
               System.arraycopy(labels, i + 1, nlabels, i, labels.length-i-1);
               setLabelsValue(nlabels);
            }

            return true;
         }
      }

      return false;
   }

   /**
    * Get tab assembly info.
    * @return the tab assembly info.
    */
   protected TabVSAssemblyInfo getTabInfo() {
      return (TabVSAssemblyInfo) info;
   }

   /**
    * Check if supports container.
    * @return <tt>true</tt> if this assembly may be laid as one sub component
    * of a tab, <tt>false</tt> otherwise.
    */
   @Override
   public boolean supportsContainer() {
      return false;
   }

   /**
    * Write the state.
    * @param writer the specified print writer.
    */
   @Override
   protected void writeStateContent(PrintWriter writer, boolean runtime) {
      super.writeStateContent(writer, runtime);
      String selected = getSelectedValue();

      if(selected != null) {
         writer.print("<state_selected>");
         writer.print("<![CDATA[" + selected + "]]>");
         writer.println("</state_selected>");
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

      String selected = Tool.getChildValueByTagName(elem, "state_selected");
      setSelectedValue(selected);
   }

   /**
    * Set the size.
    * @param size the specified size.
    */
   @Override
   public void setPixelSize(Dimension size) {
      super.setPixelSize(size);
      updateChildPosition(getPixelOffset());
   }

   /**
    * Rename the depended. This method should be called when an assembly or
    * other named variables are renamed. It updates of the dynamic references
    * to use the new name.
    * @param oname the specified old name.
    * @param nname the specified new name.
    */
   @Override
   public void renameDepended(String oname, String nname) {
      super.renameDepended(oname, nname);

      //bug1315993063216, make selected and rename be same.
      if(oname.equals(getSelected())) {
         getTabInfo().setSelected(nname);
      }
   }

   /**
    * Update the children position.
    */
   @Override
   protected void updateChildPosition(Point opos) {
      String[] children = getAssemblies();
      Rectangle bounds = getBounds();

      for(int i = 0; i < children.length; i++) {
         Assembly child = getViewsheet().getAssembly(children[i]);

         if(child != null) {
            int x = bounds.x;
            int y = bounds.y + bounds.height;
            child.setPixelOffset(new Point(x, y));
         }
      }
   }

   /**
    * Set the offset from the grid position.
    */
   @Override
   public void setPixelOffset(Point poff) {
      getTabInfo().setPixelOffset(poff);
      updateChildPosition(getPixelOffset());
   }
}
