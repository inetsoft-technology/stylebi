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

import inetsoft.util.script.graal.ScriptArrayScope;
import org.rosuda.REngine.RFactor;

public class RFactorScriptable implements ScriptArrayScope {
   public RFactorScriptable(RFactor factor) {
      this.levels = factor.levels();
      this.values = factor.asStrings();
   }

   public String getClassName() {
      return "RFactor";
   }

   @Override
   public boolean hasMember(String name) {
      return "levels".equals(name) || "length".equals(name);
   }

   @Override
   public Object getMember(String name) {
      if("levels".equals(name)) {
         return levels;
      }
      else if("length".equals(name)) {
         return values.length;
      }

      return null;
   }

   @Override
   public void putMember(String name, Object value) {
      // factor members are read-only
   }

   @Override
   public Object[] getMemberKeys() {
      return new Object[] { "levels", "length" };
   }

   @Override
   public long getArraySize() {
      return values.length;
   }

   @Override
   public Object getArrayElement(long index) {
      return index >= 0 && index < values.length ? values[(int) index] : null;
   }

   private final String[] values;
   private final String[] levels;
}
