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
package inetsoft.report.script.viewsheet;

import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.viewsheet.internal.RadioButtonVSAssemblyInfo;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.Undefined;

/**
 * The radio button viewsheet assembly scriptable in viewsheet scope.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class RadioButtonVSAScriptable extends InputVSAScriptable
   implements CompositeVSAScriptable
{
   /**
    * Create an input viewsheet assembly scriptable.
    * @param box the specified viewsheet sandbox.
    */
   public RadioButtonVSAScriptable(ViewsheetSandbox box) {
      super(box);

      cellValue = NULL;
   }

   /**
    * Get the name of the set of objects implemented by this Java class.
    */
   @Override
   public String getClassName() {
      return "RadioButtonVSA";
   }

   /**
    * Get the cell value.
    * @return the cell value, <tt>NULL</tt> no cell value.
    */
   @Override
   public Object getCellValue() {
      return cellValue;
   }

   /**
    * Set the cell value.
    * @param val the specified cell value, <tt>NULL</tt> clear cell value.
    */
   @Override
   public void setCellValue(Object val) {
      this.cellValue = val;
   }

   /**
    * Get a named property from the object.
    */
   @Override
   public Object get(String name, Scriptable start) {
      if(!(getVSAssemblyInfo() instanceof RadioButtonVSAssemblyInfo)) {
         return Undefined.instance;
      }

      if(cellValue != NULL && name.equals("value")) {
         return cellValue;
      }

      return super.get(name, start);
   }

   /**
    * Indicate whether or not a named property is defined in an object.
    */
   @Override
   public boolean has(String name, Scriptable start) {
      if(!(getVSAssemblyInfo() instanceof RadioButtonVSAssemblyInfo)) {
         return false;
      }

      if(cellValue != NULL && name.equals("value")) {
         return true;
      }

      return super.has(name, start);
   }

   /**
    * Add assembly properties.
    */
   @Override
   protected void addProperties() {
      super.addProperties();

      RadioButtonVSAssemblyInfo info = getInfo();

      addProperty("title", "getTitle", "setTitle", String.class,
                  info.getClass(), info);
      addProperty("titleVisible", "isTitleVisible", "setTitleVisible",
                  boolean.class, info.getClass(), info);
      addProperty("value", null);
   }

   /**
    * Get the assembly info of current radio button.
    */
   private RadioButtonVSAssemblyInfo getInfo() {
      if(getVSAssemblyInfo() instanceof RadioButtonVSAssemblyInfo) {
         return (RadioButtonVSAssemblyInfo) getVSAssemblyInfo();
      }

      return new RadioButtonVSAssemblyInfo();
   }

   private Object cellValue;
}
