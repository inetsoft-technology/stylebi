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
import inetsoft.uql.viewsheet.internal.SpinnerVSAssemblyInfo;

/**
 * The spinner viewsheet assembly scriptable in viewsheet scope.
 *
 * @version 10.3
 * @author InetSoft Technology Corp
 */
public class SpinnerVSAScriptable extends InputVSAScriptable {
   /**
    * Create spinner viewsheet assembly scriptable.
    * @param box the specified viewsheet sandbox.
    */
   public SpinnerVSAScriptable(ViewsheetSandbox box) {
      super(box);
   }

   /**
    * Get the name of the set of objects implemented by this Java class.
    */
   @Override
   public String getClassName() {
      return "SpinnerVSA";
   }

   /**
    * Add assembly properties.
    */
   @Override
   protected void addProperties() {
      super.addProperties();

      SpinnerVSAssemblyInfo info = getInfo();

      addProperty("min", "getMin", "setMin", String.class,
                  info.getClass(), info);
      addProperty("max", "getMax", "setMax", String.class,
                  info.getClass(), info);
      addProperty("increment", "getIncrement", "setIncrement",
                  String.class, info.getClass(), info);
   }

   /**
    * Get the assembly info of current spinner.
    */
   private SpinnerVSAssemblyInfo getInfo() {
      if(getVSAssemblyInfo() instanceof SpinnerVSAssemblyInfo) {
         return (SpinnerVSAssemblyInfo) getVSAssemblyInfo();
      }

      return new SpinnerVSAssemblyInfo();
   }
}
