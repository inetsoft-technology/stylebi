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

import inetsoft.uql.viewsheet.internal.CheckBoxVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.util.Tool;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;

/**
 * CheckBoxVSAssembly represents one checkbox assembly contained in a
 * <tt>Viewsheet</tt>.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class CheckBoxVSAssembly extends ListInputVSAssembly
   implements CompoundVSAssembly, CompositeInputVSAssembly, CompositeVSAssembly
{
   /**
    * Constructor.
    */
   public CheckBoxVSAssembly() {
      super();
   }

   /**
    * Constructor.
    */
   public CheckBoxVSAssembly(Viewsheet vs, String name) {
      super(vs, name);
   }

   /**
    * Create assembly info.
    * @return the associated assembly info.
    */
   @Override
   protected VSAssemblyInfo createInfo() {
      return new CheckBoxVSAssemblyInfo();
   }

   /**
    * Get the type.
    * @return the type of the assembly.
    */
   @Override
   public int getAssemblyType() {
      return Viewsheet.CHECKBOX_ASSET;
   }

   /**
    * Get the group title.
    * @return the title of the checkbox assembly.
    */
   @Override
   public String getTitle() {
      return getCheckBoxInfo().getTitle();
   }

   /**
    * Get the group title value.
    * @return the title value of the checkbox assembly.
    */
   @Override
   public String getTitleValue() {
      return getCheckBoxInfo().getTitleValue();
   }

   /**
    * Set the group title value.
    * @param value the specified group title.
    */
   @Override
   public void setTitleValue(String value) {
      getCheckBoxInfo().setTitleValue(value);
   }

   /**
    * If the group title is visible.
    * @return visibility of group title.
    */
   @Override
   public boolean isTitleVisible() {
      return getCheckBoxInfo().isTitleVisible();
   }

   /**
    * Set the visibility of group title.
    * @param visible the visibility of group title.
    */
   @Override
   public void setTitleVisible(boolean visible) {
      getCheckBoxInfo().setTitleVisible(visible);
   }

   /**
    * Get the selected object of this assembly info.
    * @return the selected object of this assembly info.
    */
   @Override
   public Object[] getSelectedObjects() {
      return getCheckBoxInfo().getSelectedObjects();
   }

   /**
    * Set the selected object of this assembly info.
    * @param objs the selected object of this assembly info.
    * @return the hint to reset output data.
    */
   @Override
   public int setSelectedObjects(Object[] objs) {
      return getCheckBoxInfo().setSelectedObjects(objs);
   }

   /**
    * Get checkbox assembly info.
    * @return the checkbox assembly info.
    */
   protected CheckBoxVSAssemblyInfo getCheckBoxInfo() {
      return (CheckBoxVSAssemblyInfo) getInfo();
   }

   /**
    * Get the cell width.
    * @return the cell width.
    */
   @Override
   public int getCellWidth() {
      /*
      TableDataPath path = new TableDataPath(-1, TableDataPath.DETAIL);
      VSFormat format = getFormatInfo().getFormat(path);
      Dimension span = format == null ? null : format.getSpan();
      return span == null ? 1 : span.width;
      */
      return 1;
   }

   /**
    * Write the state.
    * @param writer the specified print writer.
    */
   @Override
   protected void writeStateContent(PrintWriter writer, boolean runtime) {
      super.writeStateContent(writer, runtime);

      Object[] objs = getSelectedObjects();
      writer.print("<state_selectedObjects>");

      for(int i = 0; i < objs.length; i++) {
         writer.print("<selectedObject>");
         writer.print("<![CDATA[" + Tool.getPersistentDataString(objs[i], getDataType()) +
                      "]]>");
         writer.print("</selectedObject>");
      }

      writer.println("</state_selectedObjects>");
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

      Element osnode = Tool.getChildNodeByTagName(elem,
         "state_selectedObjects");

      if(osnode == null) {
         return;
      }

      NodeList onodes = Tool.getChildNodesByTagName(osnode, "selectedObject");
      Object[] objs = new Object[onodes.getLength()];

      for(int i = 0; i < onodes.getLength(); i++) {
         Element onode = (Element) onodes.item(i);
         objs[i] = getCheckBoxInfo().getPersistentData(getDataType(), Tool.getValue(onode));
      }

      setSelectedObjects(objs);
   }
}
