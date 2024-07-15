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

import org.apache.commons.lang3.ArrayUtils;
import org.mozilla.javascript.NativeJavaArray;
import org.mozilla.javascript.Scriptable;

import java.util.Arrays;

/**
 * NativeJavaArray's includes doesn't work. Fix it in this class.
 */
public class NativeJavaArray2 extends NativeJavaArray {
   public NativeJavaArray2(Object[] arr, Scriptable scope) {
      super(scope, arr);
      this.arr = arr;
   }

   @Override
   public Object get(String name, Scriptable scope) {
      if("includes".equals(name)) {
         if(includesFunc == null) {
            includesFunc = new FunctionObject2(scope, NativeJavaArray2.class, "includes", Object.class);
         }

         return includesFunc;
      }

      return super.get(name, scope);
   }

   public boolean includes(Object val) {
      if(val instanceof Number) {
         double dval = ((Number) val).doubleValue();
         // ignore number type
         return Arrays.stream(arr)
            .anyMatch(a -> a instanceof Number && ((Number) a).doubleValue() == dval);
      }

      return ArrayUtils.contains(arr, val);
   }

   private Object[] arr;
   private FunctionObject2 includesFunc;
}
