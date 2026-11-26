/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.util.script.graal;

import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;

import java.lang.reflect.Array;

/**
 * Bridges a Java array of {@link ScriptScope}s (e.g. the rows a form table's
 * {@code getFormRows()} returns) to GraalJS. Handed to GraalJS as is, such an array
 * becomes a host array whose elements are raw host objects that bypass
 * {@link ScriptValueConverter#toGuest}, so a script sees their Java methods instead of
 * their script members. This proxy routes each element read through
 * {@code toGuest}, so an element reads the same as a single scope returned by a
 * script function. {@link ScriptValueConverter#toHost} unwraps it back to the
 * original array. (#81904)
 */
final class ScopeArrayProxy implements ProxyArray {
   ScopeArrayProxy(ScriptScope[] array) {
      this.array = array;
   }

   /**
    * @return the wrapped Java array.
    */
   ScriptScope[] getArray() {
      return array;
   }

   @Override
   public Object get(long index) {
      checkIndex(index);
      return ScriptValueConverter.toGuest(array[(int) index]);
   }

   @Override
   public void set(long index, Value value) {
      checkIndex(index);
      // a value the array's component type can't hold is rejected, as for a host array
      Array.set(array, (int) index, ScriptValueConverter.toHostStored(value));
   }

   @Override
   public long getSize() {
      return array.length;
   }

   private void checkIndex(long index) {
      if(index < 0 || index >= array.length) {
         throw new ArrayIndexOutOfBoundsException("Invalid index " + index);
      }
   }

   private final ScriptScope[] array;
}
