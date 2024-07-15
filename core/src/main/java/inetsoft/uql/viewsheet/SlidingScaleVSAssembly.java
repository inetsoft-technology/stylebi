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

import inetsoft.uql.viewsheet.internal.SlidingScaleVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.util.Tool;

import java.awt.*;

/**
 * SlidingScaleVSAssembly represents one sliding scale assembly contained in a
 * <tt>Viewsheet</tt>.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class SlidingScaleVSAssembly extends OutputVSAssembly {
   /**
    * Constructor.
    */
   public SlidingScaleVSAssembly() {
      super();
   }

   /**
    * Constructor.
    */
   public SlidingScaleVSAssembly(Viewsheet vs, String name) {
      super(vs, name);
   }

   /**
    * Create assembly info.
    * @return the associated assembly info.
    */
   @Override
   protected VSAssemblyInfo createInfo() {
      return new SlidingScaleVSAssemblyInfo();
   }

   /**
    * Get the type.
    * @return the type of the assembly.
    */
   @Override
   public int getAssemblyType() {
      return Viewsheet.SLIDING_SCALE_ASSET;
   }

   /**
    * Get the max value.
    * @return the max value of the assembly.
    */
   public String getMaxValue() {
      return getSlidingScaleInfo().getMaxValue();
   }

   /**
    * Set the max value.
    * @param val the specified max value.
    */
   public void setMaxValue(String val) {
      getSlidingScaleInfo().setMaxValue(val);
   }

   /**
    * Get the min value.
    * @return the min value of the assembly.
    */
   public String getMinValue() {
      return getSlidingScaleInfo().getMinValue();
   }

   /**
    * Set the min value.
    * @param val the specified min value.
    */
   public void setMinValue(String val) {
      getSlidingScaleInfo().setMinValue(val);
   }

   /**
    * Get the major increment value.
    * @return the major increment value of the assembly.
    */
   public String getMajorIncValue() {
      return getSlidingScaleInfo().getMajorIncValue();
   }

   /**
    * Set the major increment value.
    * @param val the specified major increment value.
    */
   public void setMajorIncValue(String val) {
      getSlidingScaleInfo().setMajorIncValue(val);
   }

   /**
    * Get the minor increment value.
    * @return the minor increment value of the assembly.
    */
   public String getMinorIncValue() {
      return getSlidingScaleInfo().getMinorIncValue();
   }

   /**
    * Set the minor increment value.
    * @param val the specified minor increment value.
    */
   public void setMinorIncValue(String val) {
      getSlidingScaleInfo().setMinorIncValue(val);
   }

   /**
    * Get the range values.
    * @return the range values of the assembly.
    */
   public String[] getRangeValues() {
      return getSlidingScaleInfo().getRangeValues();
   }

   /**
    * Set the range values.
    * @param vals the specified range values.
    */
   public void setRangeValues(String[] val) {
      getSlidingScaleInfo().setRangeValues(val);
   }

   /**
    * Get the face.
    * @return the face of the assembly.
    */
   public int getFace() {
      return getSlidingScaleInfo().getFace();
   }

   /**
    * Set the face.
    * @param face the specified face of the assembly.
    */
   public void setFace(int face) {
      getSlidingScaleInfo().setFace(face);
   }

   /**
    * Get the maximum.
    * @return the maximum of the assembly.
    */
   public double getMax() {
      return getSlidingScaleInfo().getMax();
   }

   /**
    * Get the minimum.
    * @return the minimum of the assembly.
    */
   public double getMin() {
      return getSlidingScaleInfo().getMin();
   }

   /**
    * Get the major increment.
    * @return the major increment of the assembly.
    */
   public double getMajorInc() {
      return getSlidingScaleInfo().getMajorInc();
   }

   /**
    * Get the minor increment.
    * @return the minor increment of the assembly.
    */
   public double getMinorInc() {
      return getSlidingScaleInfo().getMinorInc();
   }

   /**
    * Get the ranges.
    * @return the ranges of the assembly.
    */
   public double[] getRanges() {
      return getSlidingScaleInfo().getRanges();
   }

   /**
    * Get the range colors.
    * @return the range colors of the assembly.
    */
   public Color[] getRangeColors() {
      return getSlidingScaleInfo().getRangeColors();
   }

   /**
    * Set the ranges colors.
    * @param colors the specified range colors.
    */
   public void setRangeColors(Color[] colors) {
      getSlidingScaleInfo().setRangeColors(colors);
   }

   /**
    * Get the style.
    * @return the style of the thermometer assembly.
    */
   public int getStyle() {
      return getSlidingScaleInfo().getStyle();
   }

   /**
    * Set the style.
    * @param style the specified style.
    */
   public void setStyle(int style) {
      getSlidingScaleInfo().setStyle(style);
   }

   /**
    * Set sliding scale assembly value.
    * @param val the sliding scale value.
    */
   @Override
   public void setValue(Object val) {
      Double d = Tool.getDoubleData(val);
      getSlidingScaleInfo().setValue(d);
   }

   /**
    * Get sliding scale assembly value.
    * @return the sliding scale assembly value.
    */
   @Override
   public Object getValue() {
      return getSlidingScaleInfo().getValue();
   }

   /**
    * Get sliding scale assembly info.
    * @return the sliding scale assembly info.
    */
   protected SlidingScaleVSAssemblyInfo getSlidingScaleInfo() {
      return (SlidingScaleVSAssemblyInfo) getInfo();
   }
}
