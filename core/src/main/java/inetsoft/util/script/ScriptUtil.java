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
package inetsoft.util.script;

import org.mozilla.javascript.*;

import java.util.Date;

public class ScriptUtil {
   public static Object unwrap(Object obj) {
      if(obj instanceof ConsString) {
         return obj.toString();
      }
      else if(obj instanceof Wrapper) {
         return ((Wrapper) obj).unwrap();
      }

      // convert javascript date to java date
      if(obj instanceof NativeArray) {
         NativeArray narr = (NativeArray) obj;
         Object[] arr = new Object[(int) narr.jsGet_length()];

         for(int i = 0; i < arr.length; i++) {
            arr[i] = unwrap(narr.get(i, narr));
         }

         obj = arr;
      }
      else if(obj instanceof ScriptableObject) {
         ScriptableObject sobj = (ScriptableObject) obj;

         if(sobj.getClassName().equals("Date")) {
            Number num = (Number) sobj.getDefaultValue(Double.TYPE);
            long dateNum = num.longValue();

            // @by stephenwebster, For bug1426196456256
            // Removed legacy code, expecting correct value from the NativeDate
            obj = new Date(dateNum);
         }
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

      return (obj instanceof Undefined) ? null : obj;
   }

   public static Object getScriptValue(Object data) {
      if(data instanceof ScriptableObject) {
         TimeoutContext.enter();
         ScriptableObject sobj = (ScriptableObject) data;

         if(sobj.getClassName().equals("Date")) {
            Number num = (Number) sobj.getDefaultValue(Double.TYPE);
            long dateNum = num.longValue();

            // @by stephenwebster, For bug1426196456256
            // Removed legacy code, expecting correct value from the NativeDate
            return new Date(dateNum);
         }
      }

      return data;
   }

   /**
    * Wrap an object array as native JS array.
    */
   public static NativeArray getNativeArray(Object[] val, Scriptable parentScope) {
      NativeArray arr = new NativeArray(val);
      ScriptRuntime.setBuiltinProtoAndParent(arr, parentScope, TopLevel.Builtins.Array);
      return arr;
   }
}
