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

import inetsoft.uql.viewsheet.internal.TextInputVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.io.PrintWriter;

/**
 * TextInputVSAssembly represents basic TextInput assembly contained in a
 * <tt>Viewsheet</tt>.
 *
 * @version 11.2
 * @author InetSoft Technology Corp
 */
public class TextInputVSAssembly extends InputVSAssembly
   implements SingleInputVSAssembly
{
   /**
    * Constructor.
    */
   public TextInputVSAssembly() {
      super();
   }

   /**
    * Constructor.
    */
   public TextInputVSAssembly(Viewsheet vs, String name) {
      super(vs, name);
   }

   /**
    * Create assembly info.
    * @return the associated assembly info.
    */
   @Override
   protected VSAssemblyInfo createInfo() {
      return new TextInputVSAssemblyInfo();
   }

   /**
    * Get the type.
    * @return the type of the assembly.
    */
   @Override
   public int getAssemblyType() {
      return Viewsheet.TEXTINPUT_ASSET;
   }

   /**
    * Get the selected object.
    * @return the object which is selected.
    */
   @Override
   public Object getSelectedObject() {
      return getTextInputInfo().getSelectedObject();
   }

   /**
    * Set the selected object.
    * @param obj the specified object selected.
    * @return the hint to reset output data.
    */
   @Override
   public int setSelectedObject(Object obj) {
      return getTextInputInfo().setSelectedObject(obj);
   }

   /**
    * Get TextInput assembly info.
    * @return the TextInput assembly info.
    */
   protected TextInputVSAssemblyInfo getTextInputInfo() {
      return (TextInputVSAssemblyInfo) getInfo();
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
      writer.print("<![CDATA[" + Tool.getDataString(obj) + "]]>");
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
      Object obj = Tool.getData(getDataType(), Tool.getValue(snode));
      setSelectedObject(obj);
   }
}