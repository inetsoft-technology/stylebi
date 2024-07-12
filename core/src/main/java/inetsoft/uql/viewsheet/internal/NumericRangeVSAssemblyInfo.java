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
package inetsoft.uql.viewsheet.internal;

import inetsoft.report.internal.table.TableFormat;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XUtil;
import inetsoft.uql.viewsheet.*;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.text.Format;
import java.util.List;

/**
 * NumericRangeVSAssemblyInfo stores basic numeric range assembly information.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class NumericRangeVSAssemblyInfo extends InputVSAssemblyInfo {
   /**
    * Constructor.
    */
   public NumericRangeVSAssemblyInfo() {
      super();
   }

   /**
    * Get the maximum.
    * @return the maximum of the assembly.
    */
   public double getMax() {
      Double val = (Double) maxValue.getRuntimeValue(true);
      return val == null ? 100 : val.doubleValue();
   }

   /**
    * Get the minimum.
    * @return the minimum of the assembly.
    */
   public double getMin() {
      Double val = (Double) minValue.getRuntimeValue(true);
      return val == null ? 0 : val.doubleValue();
   }

   /**
    * Get the major increment.
    * @return the major increment of the assembly.
    */
   public double getIncrement() {
      Double val = (Double) incrementValue.getRuntimeValue(true);
      return val == null || val == 0 ? getDefaultIncrement() : val;
   }

   protected int getDefaultIncrement() {
      return 20;
   }

   /**
    * Set the maximum value.
    * @param max the specified maximum.
    */
   public void setMax(String max) {
      this.maxValue.setRValue(max);
   }

   /**
    * Set the minimum value.
    * @param min the specified minimum.
    */
   public void setMin(String min) {
      this.minValue.setRValue(min);
   }

   /**
    * Set the increment.
    * @param increment the specified increment.
    */
   public void setIncrement(String increment) {
      this.incrementValue.setRValue(increment);
   }

   /**
    * Get the selected object.
    * @return the value of the numeric range assembly.
    */
   @Override
   public Object getSelectedObject() {
      return value;
   }

   // @by: ChrisSpagnoli bug1397460287828 #3 2014-8-19
   /**
    * Get the selected object, before min/max limits were applied.
    * @return the object which is selected, before min/max limits were applied.
    */
   public Object getUnboundedSelectedObject() {
      return unboundedValue;
   }

   /**
    * Get the text label corresponding to the selected object.
    */
   @Override
   public String getSelectedLabel() {
      return getValueLabel();
   }

   /**
    * Get the text labels corresponding to the selected objects.
    */
   @Override
   public String[] getSelectedLabels() {
      return new String[] {getSelectedLabel()};
   }

   /**
    * Set the selected object.
    * @param val the specified value.
    * @return the hint to reset output data.
    */
   @Override
   public int setSelectedObject(Object val) {
      if(!(val instanceof Number)) {
         return VSAssembly.NONE_CHANGED;
      }

      if(Tool.equals(this.value, val)) {
         return VSAssembly.NONE_CHANGED;
      }

      double doubleVal = ((Number) val).doubleValue();
      this.unboundedValue = (Number) val; // @by: ChrisSpagnoli bug1397460287828 #3 2014-8-19

      if(doubleVal > getMax() && !VSUtil.isDynamicValue(getMaxValue())) {
         this.value = getMax();
      }
      else if(doubleVal < getMin()) {
         this.value = getMin();
      }
      else {
         this.value = (Number) val;
      }

      return VSAssembly.OUTPUT_DATA_CHANGED;
   }

   /**
    * Get the selected objects of this assembly, to be overriden by
    * composite input assemblies.
    */
   @Override
   public Object[] getSelectedObjects() {
      return new Object[] {value};
   }

   /**
    * Set the selected objects.
    */
   @Override
   public int setSelectedObjects(Object[] val) {
      return setSelectedObject((val.length == 0) ? null : val[0]);
   }

   /**
    * Get the maximum value.
    * @return the maximum of the numeric range assembly.
    */
   public String getMaxValue() {
      return maxValue.getDValue();
   }

   /**
    * Set the maximum value.
    * @param max the specified maximum.
    */
   public void setMaxValue(String max) {
      this.maxValue.setDValue(max);
   }

   /**
    * Get the minimum value.
    * @return the minimum of the numeric range assembly.
    */
   public String getMinValue() {
      return minValue.getDValue();
   }

   /**
    * Set the minimum value.
    * @param min the specified minimum.
    */
   public void setMinValue(String min) {
      this.minValue.setDValue(min);
   }

   /**
    * Get the increment value.
    * @return the increment of the numeric range assembly.
    */
   public String getIncrementValue() {
      return incrementValue.getDValue();
   }

   /**
    * Set the increment.
    * @param increment the specified increment.
    */
   public void setIncrementValue(String increment) {
      this.incrementValue.setDValue(increment);
   }

   /**
    * Get the view dynamic values.
    * @return the view dynamic values.
    */
   @Override
   public List<DynamicValue> getViewDynamicValues(boolean all) {
      List<DynamicValue> list = super.getViewDynamicValues(all);

      list.add(maxValue);
      list.add(minValue);
      list.add(incrementValue);

      return list;
   }

   /**
    * Get the formatted label for the current value. It returns null if no
    * format is specified.
    */
   public String getValueLabel() {
      return formatLabel(value);
   }

   /**
    * Set value for current assembly.
    */
   public void setValue(Number val) {
      this.value = val;
   }

   /**
    * Rename the depended. This method should be called when an assembly or
    * other named variables are renamed. It updates of the dynamic references
    * to use the new name.
    * @param oname the specified old name.
    * @param nname the specified new name.
    */
   @Override
   public void renameDepended(String oname, String nname, Viewsheet vs) {
      super.renameDepended(oname, nname, vs);

      VSUtil.renameDynamicValueDepended(oname, nname, maxValue, vs);
      VSUtil.renameDynamicValueDepended(oname, nname, minValue, vs);
      VSUtil.renameDynamicValueDepended(oname, nname, incrementValue, vs);
   }

   /**
    * Get the data type.
    * @return the data type of this list data.
    */
   @Override
   public String getDataType() {
      String dtype = super.getDataType();

      if(XSchema.STRING.equals(dtype)) {
         dtype = XSchema.DOUBLE;
      }

      return dtype;
   }

   /**
    * Clone this object.
    * @param shallow <tt>true</tt> to perform shallow clone,
    * <tt>false</tt> to perform deep clone.
    * @return the cloned object.
    */
   @Override
   public NumericRangeVSAssemblyInfo clone(boolean shallow) {
      try {
         NumericRangeVSAssemblyInfo info = (NumericRangeVSAssemblyInfo) super.clone(shallow);

         if(!shallow) {
            if(maxValue != null) {
               info.maxValue = (DynamicValue) maxValue.clone();
            }

            if(minValue != null) {
               info.minValue = (DynamicValue) minValue.clone();
            }

            if(incrementValue != null) {
               info.incrementValue = (DynamicValue) incrementValue.clone();
            }
         }

         return info;
      }
      catch(Exception ex) {
         LOG.error(
                     "Failed to clone NumericRangeVSAssemblyInfo", ex);
      }

      return null;
   }

   /**
    * Write attributes.
    * @param writer the specified writer.
    */
   @Override
   protected void writeAttributes(PrintWriter writer) {
      super.writeAttributes(writer);

      if(value != null) {
         writer.print(" value=\"" + value + "\"");

         String vstr = formatLabel(value);

         if(vstr != null) {
            writer.print(" valueLabel=\"" + Tool.escape(vstr) + "\"");
         }
      }

      writer.print(" max=\"" + getMax() + "\"");
      writer.print(" min=\"" + getMin() + "\"");
      writer.print(" increment=\"" + getIncrement() + "\"");
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseAttributes(Element elem) {
      super.parseAttributes(elem);

      String str = Tool.getAttribute(elem, "value");

      if(str != null) {
         value = Double.valueOf(str);
      }
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(maxValue.getDValue() != null) {
         writer.print("<maxValue>");
         writer.print("<![CDATA[" + maxValue.getDValue() + "]]>");
         writer.println("</maxValue>");
      }

      if(minValue.getDValue() != null) {
         writer.print("<minValue>");
         writer.print("<![CDATA[" + minValue.getDValue() + "]]>");
         writer.println("</minValue>");
      }

      if(incrementValue.getDValue() != null) {
         writer.print("<incrementValue>");
         writer.print("<![CDATA[" + incrementValue.getDValue() + "]]>");
         writer.println("</incrementValue>");
      }
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseContents(Element elem) throws Exception {
      super.parseContents(elem);

      maxValue.setDValue(getContentsStr(elem, "max", "100"));
      minValue.setDValue(getContentsStr(elem, "min", "0"));
      incrementValue.setDValue(getContentsStr(elem, "increment",
                                              getDefaultIncrement() + ""));
   }

   /**
    * Copy the view part assembly info.
    * @param info the specified viewsheet assembly info.
    * @return <tt>true</tt> if changed, <tt>false</tt> otherwise.
    */
   @Override
   protected boolean copyViewInfo(VSAssemblyInfo info, boolean deep) {
      boolean result = super.copyViewInfo(info, deep);
      NumericRangeVSAssemblyInfo cinfo = (NumericRangeVSAssemblyInfo) info;

      if(!Tool.equals(maxValue, cinfo.maxValue)) {
         maxValue = cinfo.maxValue;
         result = true;
      }

      if(!Tool.equals(minValue, cinfo.minValue)) {
         minValue = cinfo.minValue;
         result = true;
      }

      if(!Tool.equals(incrementValue, cinfo.incrementValue)) {
         incrementValue = cinfo.incrementValue;
         result = true;
      }

      return result;
   }

   /**
    * Copy the output data part assembly info.
    * @param info the specified viewsheet assembly info.
    * @return <tt>true</tt> if changed, <tt>false</tt> otherwise.
    */
   @Override
   protected boolean copyOutputDataInfo(VSAssemblyInfo info) {
      boolean result = super.copyOutputDataInfo(info);
      NumericRangeVSAssemblyInfo cinfo = (NumericRangeVSAssemblyInfo) info;

      if(!Tool.equals(value, cinfo.value)) {
         value = cinfo.value;
         result = true;
      }

      return result;
   }

   /**
    * Format a label string according to object format.
    */
   protected String formatLabel(Object num) {
      VSCompositeFormat vfmt = getFormat();

      if(vfmt == null) {
         return null;
      }

      Format fmt = TableFormat.getFormat(vfmt.getFormat(),
                                         vfmt.getFormatExtent());
      return XUtil.format(fmt, num);
   }

   /**
    * Clear selected object.
    */
   public void clearSelectedObject() {
      value = null;
   }

   /**
    * Reset runtime values.
    */
   @Override
   public void resetRuntimeValues() {
      super.resetRuntimeValues();

      maxValue.setRValue(null);
      minValue.setRValue(null);
      incrementValue.setRValue(null);
   }

   @Override
   public void update(Viewsheet vs, ColumnSelection columns) throws Exception {
      super.update(vs, columns);

      if(getTableName() == null) {
         setDataType(XSchema.DOUBLE);
      }
   }

   // view
   private DynamicValue maxValue = new DynamicValue("100", XSchema.DOUBLE);
   private DynamicValue minValue = new DynamicValue("0", XSchema.DOUBLE);
   private DynamicValue incrementValue = new DynamicValue("20", XSchema.DOUBLE);

   // output data
   private Number value = Integer.valueOf(0);
   private Number unboundedValue = Integer.valueOf(0); // @by: ChrisSpagnoli bug1397460287828 #3 2014-8-19

   private static final Logger LOG =
      LoggerFactory.getLogger(NumericRangeVSAssemblyInfo.class);
}
