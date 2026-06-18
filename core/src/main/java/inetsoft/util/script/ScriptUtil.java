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
package inetsoft.util.script;

import inetsoft.util.script.graal.ScriptValueConverter;
import org.graalvm.polyglot.Value;

import java.util.Date;

public class ScriptUtil {
   /**
    * Unwrap a script value into its host (Java) representation. Under GraalJS,
    * values handed back to host code are usually already converted; this
    * method handles any stray polyglot {@link Value}, and preserves the legacy
    * NaN/Infinity -> null behavior.
    */
   public static Object unwrap(Object obj) {
      if(obj instanceof Value) {
         obj = ScriptValueConverter.toHost((Value) obj);
      }

      // Restore the legacy Rhino Wrapper.unwrap() behavior: an XTableArray
      // (the scriptable returned by XUtil.runQuery) unwraps to its underlying
      // XTable so host code can detect/process it as a table. (#75423)
      if(obj instanceof inetsoft.uql.script.XTableArray) {
         return ((inetsoft.uql.script.XTableArray) obj).unwrap();
      }

      // @by larryl, if a calculation generates an invalid result, show null
      // instead of NaN of Infinity. This can be caused by performing a time
      // series comparison and the result fo the first or last item would
      // be meaningless
      if(obj instanceof Double) {
         Double num = (Double) obj;

         if(num.isInfinite() || num.isNaN()) {
            return null;
         }
      }

      return obj;
   }

   public static Object getScriptValue(Object data) {
      if(data instanceof Value) {
         return ScriptValueConverter.toHost((Value) data);
      }

      return data;
   }
}
