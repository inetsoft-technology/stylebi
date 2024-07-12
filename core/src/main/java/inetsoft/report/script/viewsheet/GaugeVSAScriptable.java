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
package inetsoft.report.script.viewsheet;

import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.viewsheet.internal.GaugeVSAssemblyInfo;

import java.awt.*;

/**
 * The gauge viewsheet assembly scriptable in viewsheet scope.
 *
 * @version 10.3
 * @author InetSoft Technology Corp
 */
public class GaugeVSAScriptable extends RangeOutputVSAScriptable {
   /**
    * Create gauge viewsheet assembly scriptable.
    * @param box the specified viewsheet sandbox.
    */
   public GaugeVSAScriptable(ViewsheetSandbox box) {
      super(box);
   }

   /**
    * Get the name of the set of objects implemented by this Java class.
    */
   @Override
   public String getClassName() {
      return "GaugeVSA";
   }

   /**
    * Add the assembly properties.
    */
   @Override
   protected void addProperties() {
      super.addProperties();

      GaugeVSAssemblyInfo info = getInfo();

      addProperty("labelVisible", "isLabelVisible", "setLabelVisible",
                  boolean.class, info.getClass(), info);
      addProperty("valueFillColor", "getValueFillColor", "setValueFillColor",
                  Color.class, info.getClass(), info);
   }

   /**
    * Get the assembly info of current gauge.
    */
   private GaugeVSAssemblyInfo getInfo() {
      if(getVSAssemblyInfo() instanceof GaugeVSAssemblyInfo) {
         return (GaugeVSAssemblyInfo) getVSAssemblyInfo();
      }

      return new GaugeVSAssemblyInfo();
   }
}
