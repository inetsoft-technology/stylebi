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

import inetsoft.uql.viewsheet.internal.NumericRangeVSAssemblyInfo;
import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.io.PrintWriter;

/**
 * NumericRangeVSAssembly represents one numeric range assembly contained in a
 * <tt>Viewsheet</tt>.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public abstract class NumericRangeVSAssembly extends InputVSAssembly
   implements SingleInputVSAssembly
{
   /**
    * Constructor.
    */
   public NumericRangeVSAssembly() {
      super();
   }

   /**
    * Constructor.
    */
   public NumericRangeVSAssembly(Viewsheet vs, String name) {
      super(vs, name);
   }

   /**
    * Get the maximum.
    * @return the maximum of the numeric range assembly.
    */
   public double getMax() {
      return getNumericRangeInfo().getMax();
   }

   /**
    * Get the minimum.
    * @return the minimum of the numeric range assembly.
    */
   public double getMin() {
      return getNumericRangeInfo().getMin();
   }

   /**
    * Get the increment.
    * @return the increment of the numeric range assembly.
    */
   public double getIncrement() {
      return getNumericRangeInfo().getIncrement();
   }

   /**
    * Get the selected object.
    * @return the object which is selected.
    */
   @Override
   public Object getSelectedObject() {
      return getNumericRangeInfo().getSelectedObject();
   }
   
   /**
    * Get the selected object, before min/max limits were applied.
    * @return the object which is selected, before min/max limits were applied.
    */
   // @by: ChrisSpagnoli bug1397460287828 #3 2014-8-19
   public Object getUnboundedSelectedObject() {
      return getNumericRangeInfo().getUnboundedSelectedObject();
   }

   /**
    * Set the selected object.
    * @param obj the specified object selected.
    * @return the hint to reset output data.
    */
   @Override
   public int setSelectedObject(Object obj) {
      return getNumericRangeInfo().setSelectedObject(obj);
   }

   /**
    * Clear the selected object.
    */
   public void clearSelectedObject() {
      getNumericRangeInfo().clearSelectedObject();
   }

   /**
    * Get the maximum value.
    * @return the maximum of the numeric range assembly.
    */
   public String getMaxValue() {
      return getNumericRangeInfo().getMaxValue();
   }

   /**
    * Set the maximum value.
    * @param max the specified maximum.
    */
   public void setMaxValue(String max) {
      getNumericRangeInfo().setMaxValue(max);
   }

   /**
    * Get the minimum value.
    * @return the minimum of the numeric range assembly.
    */
   public String getMinValue() {
      return getNumericRangeInfo().getMinValue();
   }

   /**
    * Set the minimum value.
    * @param min the specified minimum.
    */
   public void setMinValue(String min) {
      getNumericRangeInfo().setMinValue(min);
   }

   /**
    * Get the increment value.
    * @return the increment of the numeric range assembly.
    */
   public String getIncrementValue() {
      return getNumericRangeInfo().getIncrementValue();
   }

   /**
    * Set the increment.
    * @param increment the specified increment.
    */
   public void setIncrementValue(String increment) {
      getNumericRangeInfo().setIncrementValue(increment);
   }

   /**
    * Get numeric range assembly info.
    * @return the numeric range assembly info.
    */
   protected NumericRangeVSAssemblyInfo getNumericRangeInfo() {
      return (NumericRangeVSAssemblyInfo) getInfo();
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
