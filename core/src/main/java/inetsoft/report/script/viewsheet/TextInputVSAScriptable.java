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
import inetsoft.uql.viewsheet.internal.TextInputVSAssemblyInfo;

/**
 * The TextInput viewsheet assembly scriptable in viewsheet scope.
 *
 * @version 11.2
 * @author InetSoft Technology Corp
 */
public class TextInputVSAScriptable extends InputVSAScriptable {
   /**
    * Create TextInput viewsheet assembly scriptable.
    * @param box the specified viewsheet sandbox.
    */
   public TextInputVSAScriptable(ViewsheetSandbox box) {
      super(box);
   }

   /**
    * Get the name of the set of objects implemented by this Java class.
    */
   @Override
   public String getClassName() {
      return "TextInputVSA";
   }

   /**
    * Add assembly properties.
    */
   @Override
   protected void addProperties() {
      super.addProperties();

      TextInputVSAssemblyInfo info = getInfo();

      addProperty("value", "getValue", "setValue", Object.class,
                  info.getClass(), info);
   }

   /**
    * Get the assembly info of current spinner.
    */
   private TextInputVSAssemblyInfo getInfo() {
      if(getVSAssemblyInfo() instanceof TextInputVSAssemblyInfo) {
         return (TextInputVSAssemblyInfo) getVSAssemblyInfo();
      }

      return new TextInputVSAssemblyInfo();
   }
}
