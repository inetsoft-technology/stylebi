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
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.ComboBoxVSAssemblyInfo;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.Undefined;

import java.sql.Timestamp;

/**
 * The comboBox viewsheet assembly scriptable in viewsheet scope.
 *
 * @version 10.3
 * @author InetSoft Technology Corp
 */
public class ComboBoxVSAScriptable extends InputVSAScriptable {
   /**
    * Create comboBox viewsheet assembly scriptable.
    * @param box the specified viewsheet sandbox.
    */
   public ComboBoxVSAScriptable(ViewsheetSandbox box) {
      super(box);
   }

   /**
    * Get the name of the set of objects implemented by this Java class.
    */
   @Override
   public String getClassName() {
      return "ComboBoxVSA";
   }

   @Override
   protected void addProperties() {
      super.addProperties();

      ComboBoxVSAssemblyInfo info = getInfo();

      addProperty("serverTimeZone", "isServerTimeZone", "setServerTimeZone",
                  boolean.class, info.getClass(), info);

      addProperty("minDate", "getMinDate", "setMinDate",
                  Timestamp.class, info.getClass(), info);

      addProperty("maxDate", "getMaxDate", "setMaxDate",
                  Timestamp.class, info.getClass(), info);
   }

   /**
    * Get a named property from the object.
    */
   @Override
   public Object get(String name, Scriptable start) {
      Viewsheet vs = box.getViewsheet();
      VSAssembly vassembly = assembly == null ? null :
         (VSAssembly) vs.getAssembly(assembly);

      if(!(vassembly instanceof ComboBoxVSAssembly)) {
         return Undefined.instance;
      }

      return super.get(name, start);
   }

   /**
    * Indicate whether or not a named property is defined in an object.
    */
   @Override
   public boolean has(String name, Scriptable start) {
      Viewsheet vs = box.getViewsheet();
      VSAssembly vassembly = assembly == null ? null :
         (VSAssembly) vs.getAssembly(assembly);

      if(!(vassembly instanceof ComboBoxVSAssembly)) {
         return false;
      }

      return super.has(name, start);
   }

   /**
    * Get the assembly info of current slider.
    */
   private ComboBoxVSAssemblyInfo getInfo() {
      if(getVSAssemblyInfo() instanceof ComboBoxVSAssemblyInfo) {
         return (ComboBoxVSAssemblyInfo) getVSAssemblyInfo();
      }

      return new ComboBoxVSAssemblyInfo();
   }
}
