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
import inetsoft.uql.viewsheet.*;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.Undefined;

/**
 * The cube (crosstab and map) viewsheet assembly scriptable in viewsheet scope.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class CubeVSAScriptable extends VSAScriptable {
   /**
    * Create a cube viewsheet assembly scriptable.
    * @param box the specified viewsheet sandbox.
    */
   public CubeVSAScriptable(ViewsheetSandbox box) {
      super(box);
   }

   /**
    * Get the name of the set of objects implemented by this Java class.
    */
   @Override
   public String getClassName() {
      return "CubeVSA";
   }

   /**
    * Get a named property from the object.
    */
   @Override
   public Object get(String name, Scriptable start) {
      Viewsheet vs = box.getViewsheet();
      VSAssembly vassembly = assembly == null ? null :
         (VSAssembly) vs.getAssembly(assembly);

      if(!(vassembly instanceof CubeVSAssembly)) {
         return Undefined.instance;
      }

      return super.get(name, start);
   }

   /**
    * Get an array of property ids.
    */
   @Override
   public Object[] getIds() {
      return new String[0];
   }
}
