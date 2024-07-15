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
package com.inetsoft.connectors.script;

import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.rosuda.REngine.RFactor;

public class RFactorScriptable extends ScriptableObject {
   public RFactorScriptable(RFactor factor) {
      this.levels = factor.levels();
      this.values = factor.asStrings();
   }

   @Override
   public String getClassName() {
      return "RFactor";
   }

   @Override
   public boolean has(String name, Scriptable start) {
      return "levels".equals(name) || "length".equals(name) || super.has(name, start);
   }

   @Override
   public boolean has(int index, Scriptable start) {
      return index < values.length;
   }

   @Override
   public Object get(String name, Scriptable start) {
      if("levels".equals(name)) {
         return levels;
      }
      else if("length".equals(name)) {
         return values.length;
      }

      return super.get(name, start);
   }

   @Override
   public Object get(int index, Scriptable start) {
      return index < values.length ? values[index] : Scriptable.NOT_FOUND;
   }

   private final String[] values;
   private final String[] levels;
}
