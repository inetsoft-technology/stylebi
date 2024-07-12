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

import inetsoft.uql.viewsheet.internal.ComboBoxVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.io.PrintWriter;

/**
 * ComboBoxVSAssembly represents one combobox assembly contained in a
 * <tt>Viewsheet</tt>.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class ComboBoxVSAssembly extends ListInputVSAssembly
   implements SingleInputVSAssembly
{
   /**
    * Constructor.
    */
   public ComboBoxVSAssembly() {
      super();
   }

   /**
    * Constructor.
    */
   public ComboBoxVSAssembly(Viewsheet vs, String name) {
      super(vs, name);
   }

   /**
    * Create assembly info.
    * @return the associated assembly info.
    */
   @Override
   protected VSAssemblyInfo createInfo() {
      return new ComboBoxVSAssemblyInfo();
   }

   /**
    * Get the type.
    * @return the type of the assembly.
    */
   @Override
   public int getAssemblyType() {
      return Viewsheet.COMBOBOX_ASSET;
   }

   /**
    * Get the selected object of this assembly info.
    * @return the selected object of this assembly info.
    */
   @Override
   public Object getSelectedObject() {
      return getComboBoxInfo().getSelectedObject();
   }

   /**
    * Set the selected object of this assembly info.
    * @param obj the selected object of this assembly info.
    * @return the hint to reset output data.
    */
   @Override
   public int setSelectedObject(Object obj) {
      return getComboBoxInfo().setSelectedObject(obj);
   }

   /**
    * Set the editable.
    */
   public void setTextEditable(boolean editable) {
      getComboBoxInfo().setTextEditable(editable);
   }

   /**
    * Check whether edit is allowed.
    */
   public boolean isTextEditable() {
      return getComboBoxInfo().isTextEditable();
   }

   /**
    * Get combobox assembly info.
    * @return the combobox assembly info.
    */
   protected ComboBoxVSAssemblyInfo getComboBoxInfo() {
      return (ComboBoxVSAssemblyInfo) getInfo();
   }

   /**
    * Write the state.
    * @param writer the specified print writer.
    */
   @Override
   protected void writeStateContent(PrintWriter writer, boolean runtime) {
      super.writeStateContent(writer, runtime);
      Object obj = getSelectedObject();

      writer.print("<state_selectedObject>");
      writer.print("<![CDATA[" + Tool.getPersistentDataString(obj, getDataType()) +
         "]]>");
      writer.print("</state_selectedObject>");
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

      Element snode = Tool.getChildNodeByTagName(elem, "state_selectedObject");
      Object obj = getComboBoxInfo().getPersistentData(getDataType(), Tool.getValue(snode));
      setSelectedObject(obj);
   }
}
