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
package inetsoft.report.script.viewsheet;

import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.viewsheet.internal.RangeOutputVSAssemblyInfo;

import java.awt.*;

/**
 * The range output viewsheet assembly scriptable in viewsheet scope.
 *
 * @version 10.3
 * @author InetSoft Technology Corp
 */
public class RangeOutputVSAScriptable extends OutputVSAScriptable {
   /**
    * Create range output viewsheet assembly scriptable.
    * @param box the specified viewsheet sandbox.
    */
   public RangeOutputVSAScriptable(ViewsheetSandbox box) {
      super(box);
   }

   /**
    * Get the name of the set of objects implemented by this Java class.
    */
   @Override
   public String getClassName() {
      return "RangeOutputVSA";
   }

   /**
    * Add the assembly properties.
    */
   @Override
   protected void addProperties() {
      super.addProperties();

      RangeOutputVSAssemblyInfo info = getInfo();

      addProperty("min", "getMin", "setMin",
                  String.class, info.getClass(), info);
      addProperty("max", "getMax", "setMax",
                  String.class, info.getClass(), info);
      addProperty("majorInc", "getMajorInc", "setMajorInc",
                  String.class, info.getClass(), info);
      addProperty("minorInc", "getMinorInc", "setMinorInc",
                  String.class, info.getClass(), info);
      addProperty("rangeGradient", "isRangeGradient", "setRangeGradient",
                  boolean.class, info.getClass(), info);
      addProperty("ranges", "getRanges", "setRanges",
                  String[].class, getClass(), this);
      addProperty("rangeColors", "getRangeColors", "setRangeColors",
                  Color[].class, info.getClass(), info);
   }

   public double[] getRanges() {
      return getInfo().getRanges();
   }

   public void setRanges(String[] val) {
      getInfo().setRanges(val);
   }

   public void setRangesValue(String[] val) {
      getInfo().setRangeValues(val);
   }

   /**
    * Get the suffix of a property, may be "" or [].
    * @param prop the property.
    */
   @Override
   public String getSuffix(Object prop) {
      if("ranges".equals(prop) || "rangeColors".equals(prop)) {
         return "[]";
      }

      return super.getSuffix(prop);
   }

   /**
    * Get the assembly info of range output.
    */
   private RangeOutputVSAssemblyInfo getInfo() {
      if(getVSAssemblyInfo() instanceof RangeOutputVSAssemblyInfo) {
         return (RangeOutputVSAssemblyInfo) getVSAssemblyInfo();
      }

      return new RangeOutputVSAssemblyInfo();
   }
}
