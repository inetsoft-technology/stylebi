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
package inetsoft.uql.viewsheet.internal;

import inetsoft.graph.internal.GDefaults;
import inetsoft.graph.internal.GTool;
import inetsoft.report.Hyperlink.Ref;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.awt.*;
import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.text.Format;
import java.util.List;

/**
 * This is the base class for all output assembly info that displays the value
 * in a range.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class RangeOutputVSAssemblyInfo extends OutputVSAssemblyInfo {
   /**
    * Get the max value.
    * @return the max value of the assembly.
    */
   public String getMaxValue() {
      return maxValue.getDValue();
   }

   /**
    * Set the max value.
    * @param val the specified max value.
    */
   public void setMaxValue(String val) {
      this.maxValue.setDValue(val);
   }

   /**
    * Set the max value.
    * @param val the specified max value.
    */
   public void setMax(String val) {
      this.maxValue.setRValue(val);
   }

   /**
    * Get the min value.
    * @return the min value of the assembly.
    */
   public String getMinValue() {
      return minValue.getDValue();
   }

   /**
    * Set the min value.
    * @param val the specified min value.
    */
   public void setMinValue(String val) {
      this.minValue.setDValue(val);
   }

   /**
    * Set the min value.
    * @param val the specified min value.
    */
   public void setMin(String val) {
      this.minValue.setRValue(val);
   }

   /**
    * Get the major increment value.
    * @return the major increment value of the assembly.
    */
   public String getMajorIncValue() {
      return majorIncValue.getDValue();
   }

   /**
    * Set the major increment value.
    * @param val the specified major increment value.
    */
   public void setMajorIncValue(String val) {
      this.majorIncValue.setDValue(val);
   }

   /**
    * Set the major increment value.
    * @param val the specified major increment value.
    */
   public void setMajorInc(String val) {
      this.majorIncValue.setRValue(val);
   }

   /**
    * Get the minor increment value.
    * @return the minor increment value of the assembly.
    */
   public String getMinorIncValue() {
      return minorIncValue.getDValue();
   }

   /**
    * Set the minor increment value.
    * @param val the specified minor increment value.
    */
   public void setMinorIncValue(String val) {
      this.minorIncValue.setDValue(val);
   }

   /**
    * Set the minor increment value.
    * @param val the specified minor increment value.
    */
   public void setMinorInc(String val) {
      this.minorIncValue.setRValue(val);
   }

   /**
    * Get the target value.
    */
   public String getTargetValue() {
      return targetValue.getDValue();
   }

   /**
    * Set the target value.
    */
   public void setTargetValue(String val) {
      this.targetValue.setDValue(val);
   }

   /**
    * Get the face.
    * @return the face of the assembly.
    */
   public int getFace() {
      return face;
   }

   /**
    * Set the face.
    * @param face the specified face of the assembly.
    */
   public void setFace(int face) {
      this.face = face;
   }

   /**
    * Get hyperlink ref.
    */
   @Override
   public Ref getHyperlinkRef() {
      return super.getHyperlinkRef(getValue());
   }

   /**
    * Get hyperlink and drill info.
    */
   @Override
   public Ref[] getHyperlinks() {
      return super.getHyperlinks(getValue());
   }

   /**
    * Set the current value.
    * @param value the specified current value.
    */
   @Override
   public void setValue(Object value) {
      if(value == null) {
         this.value = 0;
         super.setValue(value);
         return;
      }

      if(!(value instanceof Number)) {
         throw new RuntimeException("Numeric value expected: " + value);
      }

      super.setValue(value);
      this.value = ((Number) value).doubleValue();
   }

   /**
    * Get the current value.
    */
   public double getDoubleValue() {
      return value;
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
   public double getMajorInc() {
      Double val = (Double) majorIncValue.getRuntimeValue(true);
      return val == null ? 20 : val.doubleValue();
   }

   /**
    * Get the minor increment.
    * @return the minor increment of the assembly.
    */
   public double getMinorInc() {
      Double val = (Double) minorIncValue.getRuntimeValue(true);
      return val == null ? 5 : val.doubleValue();
   }

   /**
    * Get the target.
    */
   public double getTarget() {
      Double val = (Double) targetValue.getRuntimeValue(true);
      return val == null ? 80 : val.doubleValue();
   }

   /**
    * Check if the runtime range color should be filled using gradient.
    */
   public boolean isRangeGradient() {
      return Boolean.parseBoolean(gradientValue.getRuntimeValue(true) + "");
   }

   /**
    * Check if the design time range color should be filled using gradient.
    */
   public boolean isRangeGradientValue() {
      return Boolean.parseBoolean(gradientValue.getDValue());
   }

   /**
    * Set if the runtime range color should be filled using gradient.
    */
   public void setRangeGradient(boolean gradient) {
      gradientValue.setRValue(gradient);
   }

   /**
    * Set if the design time range color should be filled using gradient.
    */
   public void setRangeGradientValue(boolean gradient) {
      gradientValue.setDValue(gradient + "");
   }

   /**
    * Get the range values.
    * @return the range values of the assembly.
    */
   public String[] getRangeValues() {
      if(rangeValues == null) {
         return null;
      }

      String[] arr = new String[rangeValues.length];

      for(int i = 0; i < arr.length; i++) {
         arr[i] = rangeValues[i].getDValue();
      }

      return arr;
   }

   /**
    * Set the range values.
    * @param val the specified range values.
    */
   public void setRangeValues(Object[] val) {
      this.rangeValues = val == null ? null : new DynamicValue[val.length];

      for(int i = 0; rangeValues != null && i < rangeValues.length; i++) {
         rangeValues[i] = new DynamicValue((String) val[i], XSchema.DOUBLE);
      }
   }

   /**
    * Set the range values.
    * @param val the specified range values.
    */
   public void setRanges(Object[] val) {
      if(val == null) {
         return;
      }

      if(rangeValues == null) {
         rangeValues = new DynamicValue[val.length];
      }
      else if(rangeValues.length < val.length) {
         DynamicValue[] arr = new DynamicValue[val.length];
         System.arraycopy(rangeValues, 0, arr, 0, rangeValues.length);
         rangeValues = arr;
      }

      for(int i = 0; i < val.length; i++) {
         if(rangeValues[i] == null) {
            rangeValues[i] = new DynamicValue("0", XSchema.DOUBLE);
         }

         rangeValues[i].setRValue(val[i]);
      }
   }

   /**
    * Get the ranges.
    * @return the ranges of the assembly.
    */
   public double[] getRanges() {
      if(rangeValues == null) {
         return null;
      }

      double[] vals = new double[rangeValues.length];
      boolean auto = vals.length > 0 && Double.isNaN(vals[0]);

      for(int i = 0; i < vals.length; i++) {
         Double val = (Double) rangeValues[i].getRuntimeValue(true);

         if(val != null && !Double.isNaN(val)) {
            vals[i] = val;
         }
         else if(auto) {
            vals[i] = 20 * (i + 1);
         }
         else {
            vals[i] = Double.NaN;
         }
      }

      return vals;
   }

   /**
    * Get the runtime range colors.
    * @return the range colors of the assembly.
    */
   public Color[] getRangeColors() {
      if(rangeColorsValue == null) {
         return null;
      }

      Color[] colors = new Color[rangeColorsValue.length];

      for(int i = 0; i < rangeColorsValue.length; i++) {
         colors[i] = rangeColorsValue[i].getColorValue(false, null);
      }

      return colors;
   }

   /**
    * Get the design time range colors.
    * @return the range colors of the assembly.
    */
   public Color[] getRangeColorsValue() {
      if(rangeColorsValue == null) {
         return null;
      }

      Color[] colors = new Color[rangeColorsValue.length];

      for(int i = 0; i < rangeColorsValue.length; i++) {
         colors[i] = rangeColorsValue[i].getColorValue(true, null);
      }

      return colors;
   }

   /**
    * Set the runtime ranges colors.
    * @param colors the specified range colors.
    */
   public void setRangeColors(Color[] colors) {
      if(colors == null) {
         return;
      }

      rangeColorsValue = rangeColorsValue != null ?
         rangeColorsValue : new DynamicValue2[colors.length];
      int length = Math.min(rangeColorsValue.length, colors.length);

      for(int i = 0; i< length; i++) {
         rangeColorsValue[i].setRValue(colors[i]);
      }
   }

   /**
    * Set the design time ranges colors.
    * @param colors the specified range colors.
    */
   public void setRangeColorsValue(Color[] colors) {
      if(colors == null) {
         return;
      }

      rangeColorsValue = new DynamicValue2[Math.max(colors.length, 4)];

      for(int i = 0; i < colors.length; i++) {
         rangeColorsValue[i] = new DynamicValue2();

         if(colors[i] != null) {
            rangeColorsValue[i].setDValue(colors[i].getRGB() + "");
         }
      }
   }

   /**
    * Return the default format for value and ticks.
    */
   public Format getDefaultValueFormat() {
      return defFmt;
   }

   /*
    * Get the view dynamic values.
    * @return the view dynamic values.
    */
   @Override
   public List<DynamicValue> getViewDynamicValues(boolean all) {
      List<DynamicValue> list = super.getViewDynamicValues(all);

      list.add(maxValue);
      list.add(minValue);
      list.add(majorIncValue);
      list.add(minorIncValue);
      list.add(targetValue);

      for(int i = 0; rangeValues != null && i < rangeValues.length; i++) {
         list.add(rangeValues[i]);
      }

      for(int i = 0; rangeColorsValue != null && i < rangeColorsValue.length;
         i++)
      {
        list.add(rangeColorsValue[i]);
      }

      return list;
   }

   /**
    * Get gauge min/max/incr/minor value to be reset by entering empty value.
    */
   public double[] getResetValues() {
      double max = getMax();

      // if value is max (e.g. 100), don't force the max to 200. (50879)
      if(value != max) {
         // handle the case of zero, and negative
         int n = (int) Math.floor(Math.log(Math.abs(value == 0 ? 1 : value) * 2) / Math.log(10));
         max = Math.pow(10, n);
         max = max * (int) (value * 2 / max); // e.g. value=140, max = 200
      }

      // current initial defaults are in this proportion
      double[] ns = GTool.getNiceNumbers(0, Math.abs(max), Double.NaN, Double.NaN, 6);

      // since the ticks often needs to be skipped, having odd number of ticks
      // make the skipping more balanced. Also having less ticks make it look
      // less crowded, so we try to get to 5 ticks.
      // Edit: But unfortunately this leads to the terrible default minor
      // increment value of 6.25 so we are actually going to aim for 6 ticks.
      // As shown in bug #10631

      if(Math.abs(max) / ns[2] > 5) {
         ns = GTool.getNiceNumbers(0, Math.abs(max), Double.NaN, Double.NaN, 5);
      }

      double major = ns[2]; // get the calculated increment
      double minor = major/4;

      return (major <= 0 || minor <= 0) ? null : new double[] {max, major, minor};
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
      VSUtil.renameDynamicValueDepended(oname, nname, majorIncValue, vs);
      VSUtil.renameDynamicValueDepended(oname, nname, minorIncValue, vs);
      VSUtil.renameDynamicValueDepended(oname, nname, targetValue, vs);

      for(int i = 0; rangeValues != null && i < rangeValues.length; i++) {
         VSUtil.renameDynamicValueDepended(oname, nname, rangeValues[i], vs);
      }

      for(int i = 0; rangeColorsValue != null && i < rangeColorsValue.length;
         i++)
      {
         VSUtil.renameDynamicValueDepended(
            oname, nname, rangeColorsValue[i], vs);
      }
   }

   /**
    * Clone this object.
    * @param shallow <tt>true</tt> to perform shallow clone,
    * <tt>false</tt> to perform deep clone.
    * @return the cloned object.
    */
   @Override
   public RangeOutputVSAssemblyInfo clone(boolean shallow) {
      try {
         RangeOutputVSAssemblyInfo info = (RangeOutputVSAssemblyInfo) super.clone(shallow);

         if(!shallow) {
            if(maxValue != null) {
               info.maxValue = (DynamicValue) maxValue.clone();
            }

            if(minValue != null) {
               info.minValue = (DynamicValue) minValue.clone();
            }

            if(majorIncValue != null) {
               info.majorIncValue = (DynamicValue) majorIncValue.clone();
            }

            if(minorIncValue != null) {
               info.minorIncValue = (DynamicValue) minorIncValue.clone();
            }

            if(targetValue != null) {
               info.targetValue = (DynamicValue) targetValue.clone();
            }

            if(rangeValues != null) {
               info.rangeValues = (DynamicValue[]) rangeValues.clone();
            }

            if(rangeColorsValue != null) {
               info.rangeColorsValue = rangeColorsValue.clone();
            }
         }

         return info;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone RangeOutputVSAssemblyInfo", ex);
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

      writer.print(" face=\"" + face + "\"");
      writer.print(" value=\"" + value + "\"");
      writer.print(" defaultMax=\"" + defMax + "\"");
      writer.print(" defaultMin=\"" + defMin + "\"");
      writer.print(" defaultMajor=\"" + defMajor + "\"");
      writer.print(" defaultMinor=\"" + defMinor + "\"");
      writer.print(" gradient=\"" + isRangeGradient() + "\"");
      writer.print(" gradientValue=\"" + gradientValue.getDValue() + "\"");
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseAttributes(Element elem) {
      super.parseAttributes(elem);

      String str;

      if((str = Tool.getAttribute(elem, "face")) != null) {
         face = Integer.parseInt(str);
      }

      if((str = Tool.getAttribute(elem, "value")) != null) {
         value = Double.parseDouble(str);
      }

      if((str = Tool.getAttribute(elem, "defaultMax")) != null) {
         defMax = Double.parseDouble(str);
      }

      if((str = Tool.getAttribute(elem, "defaultMin")) != null) {
         defMin = Double.parseDouble(str);
      }

      if((str = Tool.getAttribute(elem, "defaultMajor")) != null) {
         defMajor = Double.parseDouble(str);
      }

      if((str = Tool.getAttribute(elem, "defaultMinor")) != null) {
         defMinor = Double.parseDouble(str);
      }

      if((str = getAttributeStr(elem, "gradient", "true")) != null) {
         gradientValue.setDValue(str);
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

      if(majorIncValue.getDValue() != null) {
         writer.print("<majorIncValue>");
         writer.print("<![CDATA[" + majorIncValue.getDValue() + "]]>");
         writer.println("</majorIncValue>");
      }

      if(minorIncValue.getDValue() != null) {
         writer.print("<minorIncValue>");
         writer.print("<![CDATA[" + minorIncValue.getDValue() + "]]>");
         writer.println("</minorIncValue>");
      }

      if(targetValue.getDValue() != null) {
         writer.print("<targetValue>");
         writer.print("<![CDATA[" + targetValue.getDValue() + "]]>");
         writer.println("</targetValue>");
      }

      if(rangeValues != null && rangeValues.length > 0) {
         writer.print("<rangeValues>");

         for(int i = 0; i < rangeValues.length; i++) {
            writer.print("<rangeValue>");
            String dvalue = rangeValues[i].getDValue();
            dvalue = dvalue == null ? "" : dvalue;
            writer.print("<![CDATA[" + dvalue + "]]>");
            writer.print("</rangeValue>");
         }

         writer.println("</rangeValues>");

         writer.print("<ranges>");

         for(int i = 0; i < rangeValues.length; i++) {
            writer.print("<rangeValue>");
            String rvalue = rangeValues[i].getRValue() + "";
            rvalue = rvalue == null ? "" : rvalue;
            writer.print("<![CDATA[" + rvalue + "]]>");
            writer.print("</rangeValue>");
         }

         writer.println("</ranges>");
      }

      Color[] color;

      if(rangeColorsValue != null) {
         color = new Color[rangeColorsValue.length];

         for(int i = 0; i< rangeColorsValue.length; i++) {
            if(rangeColorsValue[i].getDValue() != null &&
               !"".equals(rangeColorsValue[i].getDValue()))
            {
               color[i] = Color.decode(rangeColorsValue[i].getDValue());
            }
         }

         if(color != null && color.length > 0) {
            writer.print("<rangeColorsValue>");

            for(int i = 0; i < color.length; i++) {
               writer.print("<rangeColor>");

               if(color[i] != null) {
                  writer.print("<![CDATA[" + color[i].getRGB() + "]]>");
               }
               else {
                  writer.print("<![CDATA[null]]>");
               }

               writer.print("</rangeColor>");
            }

            writer.println("</rangeColorsValue>");
         }
      }

      color = getRangeColors();

      if(color != null && color.length > 0) {
         writer.print("<rangeColors>");

         for(int i = 0; i < color.length; i++) {
            writer.print("<rangeColor>");

            if(color[i] != null) {
               writer.print("<![CDATA[" + color[i].getRGB() + "]]>");
            }
            else {
               writer.print("<![CDATA[null]]>");
            }

            writer.print("</rangeColor>");
         }

         writer.println("</rangeColors>");
      }
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseContents(Element elem) throws Exception {
      super.parseContents(elem);

      double[] resetValues = getResetValues();
      boolean isReset = resetValues != null;

      maxValue.setDValue(getContentsStr(elem, "max",
         isReset ? resetValues[0] + "" : "100"));
      minValue.setDValue(getContentsStr(elem, "min", "0"));
      majorIncValue.setDValue(getContentsStr(elem, "majorInc",
         isReset ? resetValues[1] + "" : "20"));
      minorIncValue.setDValue(getContentsStr(elem, "minorInc",
         isReset ? resetValues[2] + "" : "5"));
      targetValue.setDValue(getContentsStr(elem, "target",
         isReset ? resetValues[0] + "" : "8"));

      Element rangeValuesNode =
         Tool.getChildNodeByTagName(elem, "rangeValues");
      rangeValuesNode = rangeValuesNode == null ?
         Tool.getChildNodeByTagName(elem, "ranges") : rangeValuesNode;

      if(rangeValuesNode != null) {
         NodeList rangeValuesList =
            Tool.getChildNodesByTagName(rangeValuesNode, "rangeValue");

         if(rangeValuesList != null && rangeValuesList.getLength() > 0) {
            rangeValues = new DynamicValue[rangeValuesList.getLength()];

            for(int i = 0; i < rangeValuesList.getLength(); i++) {
               rangeValues[i] = new DynamicValue(
                  Tool.getValue(rangeValuesList.item(i)), XSchema.DOUBLE);
            }
         }
      }

      Element rangeColorsNode =
         Tool.getChildNodeByTagName(elem, "rangeColorsValue");
      rangeColorsNode = rangeColorsNode == null ?
         Tool.getChildNodeByTagName(elem, "rangeColors") : rangeColorsNode;

      if(rangeColorsNode != null) {
         NodeList rangeColorsList =
            Tool.getChildNodesByTagName(rangeColorsNode, "rangeColor");

         if(rangeColorsList != null && rangeColorsList.getLength() > 0) {
            rangeColorsValue = new DynamicValue2[rangeColorsList.getLength()];

            for(int i = 0; i < rangeColorsList.getLength(); i++) {
               String val = Tool.getValue(rangeColorsList.item(i));
               rangeColorsValue[i] = new DynamicValue2();
               rangeColorsValue[i].setDValue(val == null || val.equals("null") ||
                  val.equals("undefined") || val.equals("NaN")?
                  null : val);
            }
         }
      }
   }

   /**
    * Copy the view part assembly info.
    * @param info the specified viewsheet assembly info.
    * @return <tt>true</tt> if changed, <tt>false</tt> otherwise.
    */
   @Override
   protected boolean copyViewInfo(VSAssemblyInfo info, boolean deep) {
      boolean result = super.copyViewInfo(info, deep);
      RangeOutputVSAssemblyInfo cinfo = (RangeOutputVSAssemblyInfo) info;

      // @by stephenwebster, fix bug1379310816863, add checks for the value and for
      // the default values, to completely restore the prior state of the assembly.
      if(!Tool.equals(value, cinfo.value) || getDoubleValue() != cinfo.getDoubleValue()) {
         value = cinfo.value;
         result = true;
      }

      if(!Tool.equals(maxValue, cinfo.maxValue) || getMax() != cinfo.getMax()) {
         maxValue = cinfo.maxValue;
         // @by larryl, there was code here to call setRValue(null) on all
         // dynamic values. That caused the setting from script to be lost.
         //maxValue.setRValue(null);
         result = true;
      }

      if(!Tool.equals(minValue, cinfo.minValue) || getMin() != cinfo.getMin()) {
         minValue = cinfo.minValue;
         result = true;
      }

      if(!Tool.equals(majorIncValue, cinfo.majorIncValue) ||
         getMajorInc() != cinfo.getMajorInc())
      {
         majorIncValue = cinfo.majorIncValue;
         result = true;
      }

      if(!Tool.equals(minorIncValue, cinfo.minorIncValue) ||
         getMinorInc() != cinfo.getMinorInc())
      {
         minorIncValue = cinfo.minorIncValue;
         result = true;
      }

      if(!Tool.equals(targetValue, cinfo.targetValue) || getTarget() != cinfo.getTarget()) {
         targetValue = cinfo.targetValue;
         result = true;
      }

      if(face != cinfo.face) {
         face = cinfo.face;
         result = true;
      }

      if(!Tool.equals(gradientValue, cinfo.gradientValue) ||
         !Tool.equals(isRangeGradient(), cinfo.isRangeGradient()))
      {
         gradientValue = cinfo.gradientValue;
         result = true;
      }

      if(!Tool.equals(rangeValues, cinfo.rangeValues)) {
         rangeValues = cinfo.rangeValues;
         result = true;
      }

      if(!Tool.equals(rangeColorsValue, cinfo.rangeColorsValue) ||
         !Tool.equals(getRangeColors(), cinfo.getRangeColors()))
      {
         rangeColorsValue = cinfo.rangeColorsValue;
         result = true;
      }

      if(result) {
         try {
            double max = Double.parseDouble(maxValue.getDValue());
            double major = Double.parseDouble(majorIncValue.getDValue());

            setDefaultValueFormat(max, major);
         }
         catch(Exception ex) {
            // ignore if max/major is not set
         }
      }

      return result;
   }

   /**
    * Copy the input data part assembly info.
    * @param info the specified viewsheet assembly info.
    * @return new hint.
    */
   @Override
   protected int copyInputDataInfo(VSAssemblyInfo info, int hint) {
      BindingInfo binding = getBindingInfo();
      hint = super.copyInputDataInfo(info, hint);
      bindingChanged = !Tool.equals(binding, getBindingInfo());

      return hint;
   }

   /**
    * Set the default min/max and increments from the current value.
    */
   public void setDefaultValues(boolean bindingChanged) {
      this.bindingChanged = bindingChanged;
      setDefaultValues();
   }

   /**
    * Set the default min/max and increments from the current value.
    */
   public void setDefaultValues() {
      if(value == 0 || !bindingChanged) {
         return;
      }

      bindingChanged = false;

      // if the values have been set explicitly, don't change
      try {
         if(maxValue.getDValue() != null &&
            defMax != Double.parseDouble(maxValue.getDValue()) ||
            minValue.getDValue() != null &&
            defMin != Double.parseDouble(minValue.getDValue()) ||
            majorIncValue.getDValue() != null &&
            defMajor != Double.parseDouble(majorIncValue.getDValue()) ||
            minorIncValue.getDValue() != null &&
            defMinor != Double.parseDouble(minorIncValue.getDValue()) ||
            targetValue.getDValue() != null &&
            80 != Double.parseDouble(targetValue.getDValue()))
         {
            setDefaultValueFormat(getMax(), getMajorInc());
            return;
         }
      }
      catch(Exception ex) {
         LOG.debug("Failed to set default value format", ex);
         return;
      }

      double[] resetValues = getResetValues();

      if(resetValues == null) {
         return;
      }

      double max, minor, major;

      max = resetValues[0];
      major = resetValues[1];
      minor = resetValues[2];

      // handle the case of negative max by changing it to min
      if(max > 0) {
         maxValue.setDValue(max + "");
         minValue.setDValue("0");
         defMax = max;
         defMin = 0;
      }
      else {
         maxValue.setDValue("0");
         minValue.setDValue(max + "");
         defMax = 0;
         defMin = max;
      }

      majorIncValue.setDValue(major + "");
      minorIncValue.setDValue(minor + "");

      defMajor = major;
      defMinor = minor;
      setDefaultValueFormat(max, major);
   }

   /**
    * Set the default value and tick format.
    */
   private void setDefaultValueFormat(double max, double major) {
      max = Math.abs(max);
      major = Math.abs(major);

      boolean k = max >= 1000L;
      boolean m = max >= 1000000L;
      boolean b = max >= 1000000000L;

      if(major == 0) {
         return;
      }

      // limit loop in case the increment is too small
      for(double v = 0, n = 0; v < max && n < 100; v += major, n++) {
         if((v % 100000L) != 0) {
            k = false;
         }

         if((v % 100000000L) != 0) {
            m = false;
         }

         if((v % 100000000000L) != 0) {
            b = false;
         }
      }

      if(getMin() < 0 && max <= major) {
         k = false;
         m = false;
         b = false;
      }

      VSFormat fmt = getFormat().getDefaultFormat();
      fmt.setFormatValue(null);
      fmt.setFormatExtentValue(null);

      if(b) {
         fmt.setFormatValue("DecimalFormat");
         fmt.setFormatExtentValue("#,##0.#B");
      }
      else if(m) {
         fmt.setFormatValue("DecimalFormat");
         fmt.setFormatExtentValue("#,##0.#M");
      }
      else if(k) {
         fmt.setFormatValue("DecimalFormat");
         fmt.setFormatExtentValue("#,##0.#K");
      }
   }

   /**
    * Set the default vsobject format.
    * @param border true to set border color.
    */
   @Override
   protected void setDefaultFormat(boolean border) {
      super.setDefaultFormat(border);

      // same as chart axis labels
      getFormat().getDefaultFormat().setForegroundValue(GDefaults.DEFAULT_TEXT_COLOR.getRGB() + "");
   }

   /**
    * Reset runtime values.
    */
   @Override
   public void resetRuntimeValues() {
      super.resetRuntimeValues();

      maxValue.setRValue(null);
      minValue.setRValue(null);
      majorIncValue.setRValue(null);
      minorIncValue.setRValue(null);
      targetValue.setRValue(null);
      gradientValue.setRValue(null);

      for(DynamicValue2 rangeColor : rangeColorsValue) {
         rangeColor.setRValue(null);
      }

      for(DynamicValue range : rangeValues) {
         range.setRValue(null);
      }
   }

   private DynamicValue targetValue = new DynamicValue("80", XSchema.DOUBLE);
   private DynamicValue maxValue = new DynamicValue("100", XSchema.DOUBLE);
   private DynamicValue minValue = new DynamicValue("0", XSchema.DOUBLE);
   private DynamicValue majorIncValue = new DynamicValue("20", XSchema.DOUBLE);
   private DynamicValue minorIncValue = new DynamicValue("5", XSchema.DOUBLE);
   private int face;
   private DynamicValue2[] rangeColorsValue = new DynamicValue2[] {
      new DynamicValue2(null, XSchema.COLOR),
      new DynamicValue2(null, XSchema.COLOR),
      new DynamicValue2(null, XSchema.COLOR),
      new DynamicValue2(null, XSchema.COLOR)
   };
   private DynamicValue[] rangeValues = new DynamicValue[] {
      new DynamicValue("35", XSchema.DOUBLE),
      new DynamicValue("70", XSchema.DOUBLE),
      new DynamicValue("100", XSchema.DOUBLE),
   };
   private DynamicValue gradientValue =
      new DynamicValue("true", XSchema.BOOLEAN);
   private double defMax = 100;
   private double defMin = 0;
   private double defMajor = 20;
   private double defMinor = 5;
   private DecimalFormat defFmt = null;
   // runtime
   private double value = 80;
   private boolean bindingChanged = false;

   private static final Logger LOG =
      LoggerFactory.getLogger(RangeOutputVSAssemblyInfo.class);
}
