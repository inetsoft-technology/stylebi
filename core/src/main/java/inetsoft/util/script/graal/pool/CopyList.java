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
package inetsoft.util.script.graal.pool;

import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;

import java.util.*;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
 * A host copy of a worksheet script's array passed to a Java method (bug #76960, spec §14.12
 * A3). It is an ordinary List for Java, and reads back into a script as an array.
 * <p>
 * A Java write to it on the script's thread, while the script run that passed it executes, is
 * counted and warned about once per script (bug #77123, C1): with the pool off that write
 * would have reached the script's array, here it does not. Writes a script makes through the
 * copy handed back to it are not counted. Best effort: the List write methods and the
 * iterator writes are covered; a subList's set, replaceAll and removeIf are not.
 */
public final class CopyList extends ArrayList<Object> implements ProxyArray {
   @Override
   public Object get(long index) {
      return get((int) index);
   }

   @Override
   public void set(long index, Value value) {
      int i = (int) index;

      while(size() <= i) {
         super.add(null);
      }

      super.set(i, WsValueCopier.interopElement(value));
   }

   @Override
   public long getSize() {
      return size();
   }

   @Override
   public boolean remove(long index) {
      super.remove((int) index);
      return true;
   }

   // --- Java writes (bug #77123) ---

   @Override
   public Object set(int index, Object element) {
      javaWrite();
      return super.set(index, element);
   }

   @Override
   public boolean add(Object o) {
      javaWrite();
      return super.add(o);
   }

   @Override
   public void add(int index, Object element) {
      javaWrite();
      super.add(index, element);
   }

   @Override
   public boolean addAll(Collection<?> c) {
      javaWrite();
      return super.addAll(c);
   }

   @Override
   public boolean addAll(int index, Collection<?> c) {
      javaWrite();
      return super.addAll(index, c);
   }

   @Override
   public Object remove(int index) {
      javaWrite();
      return super.remove(index);
   }

   @Override
   public boolean remove(Object o) {
      javaWrite();
      return super.remove(o);
   }

   @Override
   public void clear() {
      javaWrite();
      super.clear();
   }

   @Override
   public boolean removeAll(Collection<?> c) {
      javaWrite();
      return super.removeAll(c);
   }

   @Override
   public boolean retainAll(Collection<?> c) {
      javaWrite();
      return super.retainAll(c);
   }

   @Override
   public boolean removeIf(Predicate<? super Object> filter) {
      javaWrite();
      return super.removeIf(filter);
   }

   @Override
   public void replaceAll(UnaryOperator<Object> operator) {
      javaWrite();
      super.replaceAll(operator);
   }

   @Override
   public void sort(Comparator<? super Object> c) {
      javaWrite();
      super.sort(c);
   }

   @Override
   protected void removeRange(int fromIndex, int toIndex) {
      javaWrite();
      super.removeRange(fromIndex, toIndex);
   }

   /**
    * Record the exec that passed this copy to Java. Set once the copy is filled, so its own
    * construction is not a Java write.
    */
   void origin(WsExecContext.Origin origin) {
      this.origin = origin;
   }

   private void javaWrite() {
      WsExecContext.Origin o = origin;

      if(o != null && WsExecContext.copyMutated(o)) {
         origin = null;
      }
   }

   // the exec that passed this copy to Java, until that exec ended or a write was counted
   private transient WsExecContext.Origin origin;
}
