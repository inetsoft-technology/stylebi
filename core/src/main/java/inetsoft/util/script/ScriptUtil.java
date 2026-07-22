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
      // A JS Date coerced to an Object target (e.g. an element of the Object[][]
      // passed to new DefaultDataSet([["Date","Qty"],[new Date(),200]])) arrives
      // as a foreign polyglot object rather than a Value or a java.util.Date, so
      // the branch above misses it and the date-ness is lost. Recover it here so
      // downstream code (e.g. TimeScale.init) sees a real Date. (#75633)
      else if(obj != null) {
         Date date = ScriptValueConverter.toHostDate(obj);

         if(date != null) {
            return date;
         }
      }

      // Restore the legacy Rhino Wrapper.unwrap() behavior: an XTableArray
      // (the scriptable returned by XUtil.runQuery) unwraps to its underlying
      // XTable so host code can detect/process it as a table. (#75423)
      if(obj instanceof inetsoft.uql.script.XTableArray) {
         return ((inetsoft.uql.script.XTableArray) obj).unwrap();
      }

      // Likewise for a TableArray (the calc/report script table wrapper,
      // e.g. the value returned by a calc cell's data['*@...'] subtable
      // reference). It was also a Rhino Wrapper; unwrap it to its underlying
      // XTable so host code (toList/mapList/etc.) can detect and process it as
      // a table instead of leaving the non-serializable wrapper in a cell
      // value. (#75576 / #75423)
      if(obj instanceof inetsoft.report.script.TableArray) {
         return ((inetsoft.report.script.TableArray) obj).unwrap();
      }

      // Likewise for a CalcRef (the calc-table $name cell reference). It too was
      // a Rhino Scriptable/Wrapper whose getDefaultValue() coerced it to its
      // referenced cell value whenever host code consumed it as data. Under
      // GraalJS the guest->host boundary (ScriptValueConverter.toHost) preserves
      // the live CalcRef so indexing/spec access ($name['*'], $name[-1]) still
      // works, but host utilities that treat a value as plain data (JSObject
      // .split/convert/splitN, called from CALC aggregates like sum($x)/
      // nthLargest($x)) go through this unwrap first. Resolve it here to its
      // referenced value (a scalar or an Object[] of cell values) so those
      // utilities see real data instead of falling back to Object.toString()
      // (which produced "...CalcRef@<hash>" for a bare ref and 0 for numeric
      // aggregates). (#75738)
      if(obj instanceof inetsoft.report.script.formula.CalcRef) {
         return ((inetsoft.report.script.formula.CalcRef) obj).unwrap();
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
