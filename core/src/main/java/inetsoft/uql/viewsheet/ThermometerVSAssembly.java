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

import inetsoft.uql.viewsheet.internal.ThermometerVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.util.Tool;

/**
 * ThermometerVSAssembly represents one thermometer assembly contained in a
 * <tt>Viewsheet</tt>.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class ThermometerVSAssembly extends OutputVSAssembly {
   /**
    * Constructor.
    */
   public ThermometerVSAssembly() {
      super();
   }

   /**
    * Constructor.
    */
   public ThermometerVSAssembly(Viewsheet vs, String name) {
      super(vs, name);
   }

   /**
    * Get the type.
    * @return the type of the assembly.
    */
   @Override
   public int getAssemblyType() {
      return Viewsheet.THERMOMETER_ASSET;
   }

   /**
    * Create assembly info.
    * @return the associated assembly info.
    */
   @Override
   protected VSAssemblyInfo createInfo() {
      return new ThermometerVSAssemblyInfo();
   }

   /**
    * Get the max value.
    * @return the max value of the assembly.
    */
   public String getMaxValue() {
      return getThermometerInfo().getMaxValue();
   }

   /**
    * Set the max value.
    * @param val the specified max value.
    */
   public void setMaxValue(String val) {
      getThermometerInfo().setMaxValue(val);
   }

   /**
    * Get the min value.
    * @return the min value of the assembly.
    */
   public String getMinValue() {
      return getThermometerInfo().getMinValue();
   }

   /**
    * Set the min value.
    * @param val the specified min value.
    */
   public void setMinValue(String val) {
      getThermometerInfo().setMinValue(val);
   }

   /**
    * Get the major increment value.
    * @return the major increment value of the assembly.
    */
   public String getMajorIncValue() {
      return getThermometerInfo().getMajorIncValue();
   }

   /**
    * Set the major increment value.
    * @param val the specified major increment value.
    */
   public void setMajorIncValue(String val) {
      getThermometerInfo().setMajorIncValue(val);
   }

   /**
    * Get the minor increment value.
    * @return the minor increment value of the assembly.
    */
   public String getMinorIncValue() {
      return getThermometerInfo().getMinorIncValue();
   }

   /**
    * Set the minor increment value.
    * @param val the specified minor increment value.
    */
   public void setMinorIncValue(String val) {
      getThermometerInfo().setMinorIncValue(val);
   }

   /**
    * Get the range values.
    * @return the range values of the assembly.
    */
   public String[] getRangeValues() {
      return getThermometerInfo().getRangeValues();
   }

   /**
    * Set the range values.
    * @param vals the specified range values.
    */
   public void setRangeValues(String[] val) {
      getThermometerInfo().setRangeValues(val);
   }

   /**
    * Get the face.
    * @return the face of the assembly.
    */
   public int getFace() {
      return getThermometerInfo().getFace();
   }

   /**
    * Set the face.
    * @param face the specified face of the assembly.
    */
   public void setFace(int face) {
      getThermometerInfo().setFace(face);
   }

   /**
    * Get the maximum.
    * @return the maximum of the assembly.
    */
   public double getMax() {
      return getThermometerInfo().getMax();
   }

   /**
    * Get the minimum.
    * @return the minimum of the assembly.
    */
   public double getMin() {
      return getThermometerInfo().getMin();
   }

   /**
    * Get the major increment.
    * @return the major increment of the assembly.
    */
   public double getMajorInc() {
      return getThermometerInfo().getMajorInc();
   }

   /**
    * Get the minor increment.
    * @return the minor increment of the assembly.
    */
   public double getMinorInc() {
      return getThermometerInfo().getMinorInc();
   }

   /**
    * Get the ranges.
    * @return the ranges of the assembly.
    */
   public double[] getRanges() {
      return getThermometerInfo().getRanges();
   }

   /**
    * Get the style.
    * @return the style of the thermometer assembly.
    */
   public int getStyle() {
      return getThermometerInfo().getStyle();
   }

   /**
    * Set the style.
    * @param style the specified style.
    */
   public void setStyle(int style) {
      getThermometerInfo().setStyle(style);
   }

   /**
    * Set thermometer assembly value.
    * @param val the thermometer value.
    */
   @Override
   public void setValue(Object val) {
      Double d = Tool.getDoubleData(val);
      getThermometerInfo().setValue(d);
   }

   /**
    * Get thermometer assembly value.
    * @return the thermometer assembly value.
    */
   @Override
   public Object getValue() {
      return getThermometerInfo().getValue();
   }

   /**
    * Get thermometer assembly info.
    * @return the thermometer assembly info.
    */
   protected ThermometerVSAssemblyInfo getThermometerInfo() {
      return (ThermometerVSAssemblyInfo) getInfo();
   }
}
