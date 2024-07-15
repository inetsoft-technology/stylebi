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

import inetsoft.uql.viewsheet.internal.GaugeVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.util.Tool;

/**
 * GaugeVSAssembly represents one gauge assembly contained in a
 * <tt>Viewsheet</tt>.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class GaugeVSAssembly extends OutputVSAssembly {
   /**
    * Constructor.
    */
   public GaugeVSAssembly() {
      super();
   }

   /**
    * Constructor.
    */
   public GaugeVSAssembly(Viewsheet vs, String name) {
      super(vs, name);
   }

   /**
    * Create assembly info.
    * @return the associated assembly info.
    */
   @Override
   protected VSAssemblyInfo createInfo() {
      return new GaugeVSAssemblyInfo();
   }

   /**
    * Get the type.
    * @return the type of the assembly.
    */
   @Override
   public int getAssemblyType() {
      return Viewsheet.GAUGE_ASSET;
   }

   /**
    * Get the max value.
    * @return the max value of the gauge assembly.
    */
   public String getMaxValue() {
      return getGaugeInfo().getMaxValue();
   }

   /**
    * Set the max value.
    * @param val the specified max value.
    */
   public void setMaxValue(String val) {
      getGaugeInfo().setMaxValue(val);
   }

   /**
    * Get the min value.
    * @return the min value of the gauge assembly.
    */
   public String getMinValue() {
      return getGaugeInfo().getMinValue();
   }

   /**
    * Set the min value.
    * @param val the specified min value.
    */
   public void setMinValue(String val) {
      getGaugeInfo().setMinValue(val);
   }

   /**
    * Get the major increment value.
    * @return the major increment value of the assembly.
    */
   public String getMajorIncValue() {
      return getGaugeInfo().getMajorIncValue();
   }

   /**
    * Set the major increment value.
    * @param val the specified major increment value.
    */
   public void setMajorIncValue(String val) {
      getGaugeInfo().setMajorIncValue(val);
   }

   /**
    * Get the minor increment value.
    * @return the minor increment value of the assembly.
    */
   public String getMinorIncValue() {
      return getGaugeInfo().getMinorIncValue();
   }

   /**
    * Set the minor increment value.
    * @param val the specified minor increment value.
    */
   public void setMinorIncValue(String val) {
      getGaugeInfo().setMinorIncValue(val);
   }

   /**
    * Get the range values.
    * @return the range values of the assembly.
    */
   public String[] getRangeValues() {
      return getGaugeInfo().getRangeValues();
   }

   /**
    * Set the range values.
    * @param vals the specified range values.
    */
   public void setRangeValues(String[] val) {
      getGaugeInfo().setRangeValues(val);
   }

   /**
    * Get the face.
    * @return the face of the assembly.
    */
   public int getFace() {
      return getGaugeInfo().getFace();
   }

   /**
    * Set the face.
    * @param face the specified face of the assembly.
    */
   public void setFace(int face) {
      getGaugeInfo().setFace(face);
   }

   /**
    * Get the maximum.
    * @return the maximum of the assembly.
    */
   public double getMax() {
      return getGaugeInfo().getMax();
   }

   /**
    * Get the minimum.
    * @return the minimum of the assembly.
    */
   public double getMin() {
      return getGaugeInfo().getMin();
   }

   /**
    * Get the major increment.
    * @return the major increment of the assembly.
    */
   public double getMajorInc() {
      return getGaugeInfo().getMajorInc();
   }

   /**
    * Get the minor increment.
    * @return the minor increment of the assembly.
    */
   public double getMinorInc() {
      return getGaugeInfo().getMinorInc();
   }

   /**
    * Get the ranges.
    * @return the ranges of the assembly.
    */
   public double[] getRanges() {
      return getGaugeInfo().getRanges();
   }

   /**
    * Set gauge assembly value.
    * @param val the gauge value.
    */
   @Override
   public void setValue(Object val) {
      Double d = Tool.getDoubleData(val);
      getGaugeInfo().setValue(d);
   }

   /**
    * Get gauge assembly value.
    * @return the gauge assembly value.
    */
   @Override
   public Object getValue() {
      return getGaugeInfo().getValue();
   }

   /**
    * Get gauge assembly info.
    * @return the gauge assembly info.
    */
   protected GaugeVSAssemblyInfo getGaugeInfo() {
      return (GaugeVSAssemblyInfo) getInfo();
   }
}
