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
import inetsoft.uql.viewsheet.internal.SliderVSAssemblyInfo;

/**
 * The slider viewsheet assembly scriptable in viewsheet scope.
 *
 * @version 10.3
 * @author InetSoft Technology Corp
 */
public class SliderVSAScriptable extends InputVSAScriptable {
   /**
    * Create slider viewsheet assembly scriptable.
    * @param box the specified viewsheet sandbox.
    */
   public SliderVSAScriptable(ViewsheetSandbox box) {
      super(box);
   }

   /**
    * Get the name of the set of objects implemented by this Java class.
    */
   @Override
   public String getClassName() {
      return "SliderVSA";
   }

   /**
    * Add assembly properties.
    */
   @Override
   protected void addProperties() {
      super.addProperties();

      SliderVSAssemblyInfo info = getInfo();

      addProperty("min", "getMin", "setMin", String.class,
         SliderVSAScriptable.class, this);
      addProperty("max", "getMax", "setMax", String.class,
         SliderVSAScriptable.class, this);

      addProperty("increment", "getIncrement", "setIncrement",
                  String.class, info.getClass(), info);
      addProperty("minVisible", "isMinVisible", "setMinVisible", boolean.class,
                  info.getClass(), info);
      addProperty("maxVisible", "isMaxVisible", "setMaxVisible", boolean.class,
                  info.getClass(), info);
      addProperty("tickVisible", "isTickVisible", "setTickVisible",
                  boolean.class, info.getClass(), info);
      addProperty("currentVisible", "isCurrentVisible", "setCurrentVisible",
                  boolean.class, info.getClass(), info);
      addProperty("labelVisible", "isLabelVisible", "setLabelVisible",
                  boolean.class, info.getClass(), info);
      addProperty("snap", "isSnap", "setSnap",
                  boolean.class, info.getClass(), info);
   }

   public String getMax() {
      return getInfo().getMax() + "";
   }

   public void setMax(String max) {
      getInfo().setMax(max);
      checkMax();
   }

   public void setMaxValue(String max) {
      getInfo().setMaxValue(max);
      checkMax();
   }

   private void checkMax() {
      Object val = getInfo().getSelectedObject();

      if(val != null) {
         Double doubleVal = ((Number) val).doubleValue();

         if(doubleVal > getInfo().getMax()) {
            getInfo().setSelectedObject(getInfo().getMax());
         }
      }
   }

   public String getMin() {
      return getInfo().getMin() + "";
   }

   public void setMin(String min) {
      getInfo().setMin(min);
      checkMin();
   }

   public void setMinValue(String min) {
      getInfo().setMinValue(min);
      checkMin();
   }

   private void checkMin() {
      Object val = getInfo().getSelectedObject();

      if(val != null) {
         Double doubleVal = ((Number) val).doubleValue();

         if(doubleVal < getInfo().getMin()) {
            getInfo().setSelectedObject(getInfo().getMin());
         }
      }
   }

   /**
    * Get the assembly info of current slider.
    */
   private SliderVSAssemblyInfo getInfo() {
      if(getVSAssemblyInfo() instanceof SliderVSAssemblyInfo) {
         return (SliderVSAssemblyInfo) getVSAssemblyInfo();
      }

      return new SliderVSAssemblyInfo();
   }
}
