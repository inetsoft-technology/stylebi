/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.uql.viewsheet;

import inetsoft.uql.viewsheet.internal.RadioButtonVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.io.PrintWriter;

/**
 * RadioButtonVSAssembly represents one radio button assembly contained in a
 * <tt>Viewsheet</tt>.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class RadioButtonVSAssembly extends ListInputVSAssembly
   implements CompoundVSAssembly, SingleInputVSAssembly, CompositeVSAssembly
{
   /**
    * Constructor.
    */
   public RadioButtonVSAssembly() {
      super();
   }

   /**
    * Constructor.
    */
   public RadioButtonVSAssembly(Viewsheet vs, String name) {
      super(vs, name);
   }

   /**
    * Create assembly info.
    * @return the associated assembly info.
    */
   @Override
   protected VSAssemblyInfo createInfo() {
      return new RadioButtonVSAssemblyInfo();
   }

   /**
    * Get the type.
    * @return the type of the assembly.
    */
   @Override
   public int getAssemblyType() {
      return Viewsheet.RADIOBUTTON_ASSET;
   }

   /**
    * Get the group title.
    * @return the title of the radio button assembly.
    */
   @Override
   public String getTitle() {
      return getRadioButtonInfo().getTitle();
   }

   /**
    * Get the group title value.
    * @return the title value of the radio button assembly.
    */
   @Override
   public String getTitleValue() {
      return getRadioButtonInfo().getTitleValue();
   }

   /**
    * Set the group title value.
    * @param value the specified group title.
    */
   @Override
   public void setTitleValue(String value) {
      getRadioButtonInfo().setTitleValue(value);
   }

   /**
    * If the group title is visible.
    * @return visibility of group title.
    */
   @Override
   public boolean isTitleVisible() {
      return getRadioButtonInfo().isTitleVisible();
   }

   /**
    * Set the visibility of group title.
    * @param visible the visibility of group title.
    */
   @Override
   public void setTitleVisible(boolean visible) {
      getRadioButtonInfo().setTitleVisible(visible);
   }

   /**
    * Get the selected object of this assembly info.
    * @return the selected object of this assembly info.
    */
   @Override
   public Object getSelectedObject() {
      return getRadioButtonInfo().getSelectedObject();
   }

   /**
    * Set the selected object of this assembly info.
    * @param obj the selected object of this assembly info.
    * @return the hint to reset output data.
    */
   @Override
   public int setSelectedObject(Object obj) {
      return getRadioButtonInfo().setSelectedObject(obj);
   }

   /**
    * Get checkbox assembly info.
    * @return the checkbox assembly info.
    */
   protected RadioButtonVSAssemblyInfo getRadioButtonInfo() {
      return (RadioButtonVSAssemblyInfo) getInfo();
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
      Object obj = getRadioButtonInfo().getPersistentData(getDataType(), Tool.getValue(snode));
      setSelectedObject(obj);
   }
}
