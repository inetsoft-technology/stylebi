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
import java.util.function.*;
import java.util.stream.Stream;

/**
 * A {@link LiveView} of a worksheet script's array passed to a Java method (bug #77123). It is
 * a List for Java and reads back into a script as an array. In copy mode every read works on
 * one captured copy, so a reader on another thread never mixes the call-time copy with the
 * final one.
 */
final class LiveList extends AbstractList<Object> implements LiveView, ProxyArray, RandomAccess {
   LiveList(Value guest, CopyList copy, WsExecContext.Frame frame) {
      this.guest = guest;
      this.copy = copy;
      this.frame = frame;
   }

   // --- LiveView ---

   @Override
   public void resnapshot() {
      Value v = guest;

      if(v != null) {
         copy = WsValueCopier.copyList(v);
      }
   }

   @Override
   public void drop() {
      guest = null;
   }

   @Override
   public Value liveGuest() {
      return live() ? guest : null;
   }

   private boolean live() {
      return guest != null && WsExecContext.isLive(frame);
   }

   // --- List: live on the owner thread, else the copy ---

   @Override
   public int size() {
      return live() ? (int) guest.getArraySize() : copy.size();
   }

   @Override
   public Object get(int index) {
      if(live()) {
         Objects.checkIndex(index, (int) guest.getArraySize());
         return LiveView.element(guest.getArrayElement(index), frame);
      }

      return copy.get(index);
   }

   @Override
   public Object set(int index, Object element) {
      if(live()) {
         Object previous = get(index);
         guest.setArrayElement(index, LiveView.toGuest(element));
         return previous;
      }

      return copy.set(index, element);
   }

   @Override
   public void add(int index, Object element) {
      if(live()) {
         int size = (int) guest.getArraySize();
         Objects.checkIndex(index, size + 1);

         if(index == size) {
            guest.setArrayElement(index, LiveView.toGuest(element));
         }
         else {
            guest.invokeMember("splice", index, 0, LiveView.toGuest(element));
         }

         modCount++;
         return;
      }

      copy.add(index, element);
   }

   @Override
   public Object remove(int index) {
      if(live()) {
         Object previous = get(index);
         guest.invokeMember("splice", index, 1);
         modCount++;
         return previous;
      }

      return copy.remove(index);
   }

   @Override
   public void clear() {
      if(live()) {
         guest.invokeMember("splice", 0, guest.getArraySize());
         modCount++;
         return;
      }

      copy.clear();
   }

   // bulk reads and writes in copy mode: one captured copy (refute amendment 5)

   @Override
   public Iterator<Object> iterator() {
      return live() ? super.iterator() : copy.iterator();
   }

   @Override
   public ListIterator<Object> listIterator() {
      return live() ? super.listIterator() : copy.listIterator();
   }

   @Override
   public ListIterator<Object> listIterator(int index) {
      return live() ? super.listIterator(index) : copy.listIterator(index);
   }

   @Override
   public List<Object> subList(int fromIndex, int toIndex) {
      return live() ? super.subList(fromIndex, toIndex) : copy.subList(fromIndex, toIndex);
   }

   @Override
   public Object[] toArray() {
      return live() ? super.toArray() : copy.toArray();
   }

   @Override
   public <T> T[] toArray(T[] a) {
      return live() ? super.toArray(a) : copy.toArray(a);
   }

   @Override
   public void forEach(Consumer<? super Object> action) {
      if(live()) {
         super.forEach(action);
      }
      else {
         copy.forEach(action);
      }
   }

   @Override
   public Spliterator<Object> spliterator() {
      return live() ? super.spliterator() : copy.spliterator();
   }

   @Override
   public Stream<Object> stream() {
      return live() ? super.stream() : copy.stream();
   }

   @Override
   public boolean contains(Object o) {
      return live() ? super.contains(o) : copy.contains(o);
   }

   @Override
   public int indexOf(Object o) {
      return live() ? super.indexOf(o) : copy.indexOf(o);
   }

   @Override
   public int lastIndexOf(Object o) {
      return live() ? super.lastIndexOf(o) : copy.lastIndexOf(o);
   }

   @Override
   public boolean containsAll(Collection<?> c) {
      return live() ? super.containsAll(c) : copy.containsAll(c);
   }

   @Override
   public boolean removeIf(Predicate<? super Object> filter) {
      return live() ? super.removeIf(filter) : copy.removeIf(filter);
   }

   @Override
   public void replaceAll(UnaryOperator<Object> operator) {
      if(live()) {
         super.replaceAll(operator);
      }
      else {
         copy.replaceAll(operator);
      }
   }

   @Override
   public void sort(Comparator<? super Object> c) {
      if(live()) {
         super.sort(c);
      }
      else {
         copy.sort(c);
      }
   }

   @Override
   public boolean equals(Object o) {
      return o == this || (live() ? super.equals(o) : copy.equals(o));
   }

   @Override
   public int hashCode() {
      return live() ? super.hashCode() : copy.hashCode();
   }

   @Override
   public String toString() {
      return live() ? super.toString() : copy.toString();
   }

   // --- ProxyArray: the view read back into a script ---

   @Override
   public Object get(long index) {
      return get((int) index);
   }

   @Override
   public void set(long index, Value value) {
      if(live()) {
         guest.setArrayElement(index, LiveView.toGuest(value));
         return;
      }

      copy.set(index, value);
   }

   @Override
   public long getSize() {
      return size();
   }

   @Override
   public boolean remove(long index) {
      remove((int) index);
      return true;
   }

   /**
    * @return whether this view still holds its guest value, for tests.
    */
   boolean attached() {
      return guest != null;
   }

   // the guest value: set at creation, dropped by its frame; only the owner thread uses it
   private Value guest;
   // replaced once, by the owner thread at exec completion; read by any thread
   private volatile CopyList copy;
   private final WsExecContext.Frame frame;
}
