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

import inetsoft.uql.viewsheet.internal.CylinderVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.util.Tool;

/**
 * CylinderVSAssembly represents one cylinder assembly contained in a
 * <tt>Viewsheet</tt>.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class CylinderVSAssembly extends OutputVSAssembly {
   /**
    * Constructor.
    */
   public CylinderVSAssembly() {
      super();
   }

   /**
    * Constructor.
    */
   public CylinderVSAssembly(Viewsheet vs, String name) {
      super(vs, name);
   }

   /**
    * Create assembly info.
    * @return the associated assembly info.
    */
   @Override
   protected VSAssemblyInfo createInfo() {
      return new CylinderVSAssemblyInfo();
   }

   /**
    * Get the type.
    * @return the type of the assembly.
    */
   @Override
   public int getAssemblyType() {
      return Viewsheet.CYLINDER_ASSET;
   }

   /**
    * Get the max value.
    * @return the max value of the assembly.
    */
   public String getMaxValue() {
      return getCylinderInfo().getMaxValue();
   }

   /**
    * Set the max value.
    * @param val the specified max value.
    */
   public void setMaxValue(String val) {
      getCylinderInfo().setMaxValue(val);
   }

   /**
    * Get the min value.
    * @return the min value of the assembly.
    */
   public String getMinValue() {
      return getCylinderInfo().getMinValue();
   }

   /**
    * Set the min value.
    * @param val the specified min value.
    */
   public void setMinValue(String val) {
      getCylinderInfo().setMinValue(val);
   }

   /**
    * Get the major increment value.
    * @return the major increment value of the assembly.
    */
   public String getMajorIncValue() {
      return getCylinderInfo().getMajorIncValue();
   }

   /**
    * Set the major increment value.
    * @param val the specified major increment value.
    */
   public void setMajorIncValue(String val) {
      getCylinderInfo().setMajorIncValue(val);
   }

   /**
    * Get the minor increment value.
    * @return the minor increment value of the assembly.
    */
   public String getMinorIncValue() {
      return getCylinderInfo().getMinorIncValue();
   }

   /**
    * Set the minor increment value.
    * @param val the specified minor increment value.
    */
   public void setMinorIncValue(String val) {
      getCylinderInfo().setMinorIncValue(val);
   }

   /**
    * Get the range values.
    * @return the range values of the assembly.
    */
   public String[] getRangeValues() {
      return getCylinderInfo().getRangeValues();
   }

   /**
    * Set the range values.
    * @param vals the specified range values.
    */
   public void setRangeValues(String[] val) {
      getCylinderInfo().setRangeValues(val);
   }

   /**
    * Get the face.
    * @return the face of the assembly.
    */
   public int getFace() {
      return getCylinderInfo().getFace();
   }

   /**
    * Set the face.
    * @param face the specified face of the assembly.
    */
   public void setFace(int face) {
      getCylinderInfo().setFace(face);
   }

   /**
    * Get the maximum.
    * @return the maximum of the assembly.
    */
   public double getMax() {
      return getCylinderInfo().getMax();
   }

   /**
    * Get the minimum.
    * @return the minimum of the assembly.
    */
   public double getMin() {
      return getCylinderInfo().getMin();
   }

   /**
    * Get the major increment.
    * @return the major increment of the assembly.
    */
   public double getMajorInc() {
      return getCylinderInfo().getMajorInc();
   }

   /**
    * Get the minor increment.
    * @return the minor increment of the assembly.
    */
   public double getMinorInc() {
      return getCylinderInfo().getMinorInc();
   }

   /**
    * Get the ranges.
    * @return the ranges of the assembly.
    */
   public double[] getRanges() {
      return getCylinderInfo().getRanges();
   }

   /**
    * Set cylinder assembly value.
    * @param val the cylinder value.
    */
   @Override
   public void setValue(Object val) {
      Double d = Tool.getDoubleData(val);
      getCylinderInfo().setValue(d);
   }

   /**
    * Get cylinder assembly value.
    * @return the cylinder assembly value.
    */
   @Override
   public Object getValue() {
      return getCylinderInfo().getValue();
   }

   /**
    * Get cylinder assembly info.
    * @return the cylinder assembly info.
    */
   protected CylinderVSAssemblyInfo getCylinderInfo() {
      return (CylinderVSAssemblyInfo) getInfo();
   }
}
