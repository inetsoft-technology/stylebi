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

import java.io.Serial;
import java.io.Serializable;
import java.util.*;
import java.util.function.*;
import java.util.stream.Stream;

/**
 * A {@link LiveView} of a worksheet script's array passed to a Java method (bug #77123). It is
 * a List for Java and reads back into a script as an array. In copy mode every read works on
 * one captured copy, so a reader on another thread never mixes two copies; while the exec is
 * open that copy is read-only (see {@link LiveView}).
 */
final class LiveList extends AbstractList<Object>
   implements LiveView, ProxyArray, RandomAccess, Serializable
{
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
         stale = false;
      }
   }

   @Override
   public void refresh() {
      if(live()) {
         copy = WsValueCopier.copyList(guest);
         stale = false;
      }
   }

   @Override
   public void rebind(Object copied) {
      if(copied instanceof CopyList list && list != copy) {
         copy = list;
         stale = false;
      }
   }

   @Override
   public Object currentCopy() {
      return copy;
   }

   @Override
   public void drop() {
      guest = null;
      frame = null;
   }

   @Override
   public Value liveGuest() {
      return live() ? guest : null;
   }

   private boolean live() {
      WsExecContext.Frame f = frame;
      return guest != null && f != null && WsExecContext.isLive(f);
   }

   /**
    * @return the copy for a read in copy mode: read-only while the exec is open, and an error
    *         then if it could not follow a live write.
    */
   private List<Object> readCopy() {
      CopyList c = copy;

      if(WsExecContext.isOpen(frame)) {
         if(stale) {
            throw LiveView.busy();
         }

         return Collections.unmodifiableList(c);
      }

      return c;
   }

   /**
    * @return the copy for a write in copy mode: only once the exec that made the view ended.
    */
   private CopyList writeCopy() {
      if(WsExecContext.isOpen(frame)) {
         throw LiveView.busy();
      }

      return copy;
   }

   /**
    * Apply a live write to the copy too, so other threads see it; a copy that cannot follow
    * (it fell behind the guest) is marked stale, and reading it then fails.
    */
   private void mirror(Consumer<CopyList> write) {
      try {
         write.accept(copy);
      }
      catch(RuntimeException ex) {
         stale = true;
      }
   }

   private Object liveElement(int index) {
      CopyList c = copy;
      Object copied = !stale && index < c.size() ? c.get(index) : null;
      return LiveView.element(guest.getArrayElement(index), frame, copied);
   }

   // --- List: live on the owner thread, else the copy ---

   @Override
   public int size() {
      return live() ? (int) guest.getArraySize() : readCopy().size();
   }

   @Override
   public Object get(int index) {
      if(live()) {
         Objects.checkIndex(index, (int) guest.getArraySize());
         return liveElement(index);
      }

      return readCopy().get(index);
   }

   @Override
   public Object set(int index, Object element) {
      if(live()) {
         Object previous = get(index);
         guest.setArrayElement(index, LiveView.toGuest(element));
         mirror(c -> c.set(index, LiveView.toCopy(element)));
         return previous;
      }

      return writeCopy().set(index, element);
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

         mirror(c -> c.add(index, LiveView.toCopy(element)));
         modCount++;
         return;
      }

      writeCopy().add(index, element);
   }

   @Override
   public Object remove(int index) {
      if(live()) {
         Object previous = get(index);
         guest.invokeMember("splice", index, 1);
         mirror(c -> c.remove(index));
         modCount++;
         return previous;
      }

      return writeCopy().remove(index);
   }

   @Override
   public void clear() {
      if(live()) {
         guest.invokeMember("splice", 0, guest.getArraySize());
         mirror(ArrayList::clear);
         modCount++;
         return;
      }

      writeCopy().clear();
   }

   // bulk reads and writes in copy mode: one captured copy (refute amendment 5)

   @Override
   public Iterator<Object> iterator() {
      return live() ? super.iterator() : readCopy().iterator();
   }

   @Override
   public ListIterator<Object> listIterator() {
      return live() ? super.listIterator() : readCopy().listIterator();
   }

   @Override
   public ListIterator<Object> listIterator(int index) {
      return live() ? super.listIterator(index) : readCopy().listIterator(index);
   }

   @Override
   public List<Object> subList(int fromIndex, int toIndex) {
      return live() ? super.subList(fromIndex, toIndex) : readCopy().subList(fromIndex, toIndex);
   }

   @Override
   public Object[] toArray() {
      return live() ? super.toArray() : readCopy().toArray();
   }

   @Override
   public <T> T[] toArray(T[] a) {
      return live() ? super.toArray(a) : readCopy().toArray(a);
   }

   @Override
   public void forEach(Consumer<? super Object> action) {
      if(live()) {
         super.forEach(action);
      }
      else {
         readCopy().forEach(action);
      }
   }

   @Override
   public Spliterator<Object> spliterator() {
      return live() ? super.spliterator() : readCopy().spliterator();
   }

   @Override
   public Stream<Object> stream() {
      return live() ? super.stream() : readCopy().stream();
   }

   @Override
   public Stream<Object> parallelStream() {
      return live() ? super.parallelStream() : readCopy().parallelStream();
   }

   @Override
   public boolean contains(Object o) {
      return live() ? super.contains(o) : readCopy().contains(o);
   }

   @Override
   public int indexOf(Object o) {
      return live() ? super.indexOf(o) : readCopy().indexOf(o);
   }

   @Override
   public int lastIndexOf(Object o) {
      return live() ? super.lastIndexOf(o) : readCopy().lastIndexOf(o);
   }

   @Override
   public boolean containsAll(Collection<?> c) {
      return live() ? super.containsAll(c) : readCopy().containsAll(c);
   }

   @Override
   public boolean removeIf(Predicate<? super Object> filter) {
      return live() ? super.removeIf(filter) : writeCopy().removeIf(filter);
   }

   @Override
   public void replaceAll(UnaryOperator<Object> operator) {
      if(live()) {
         super.replaceAll(operator);
      }
      else {
         writeCopy().replaceAll(operator);
      }
   }

   @Override
   public void sort(Comparator<? super Object> c) {
      if(live()) {
         super.sort(c);
      }
      else {
         writeCopy().sort(c);
      }
   }

   @Override
   public boolean equals(Object o) {
      return o == this || (live() ? super.equals(o) : readCopy().equals(o));
   }

   @Override
   public int hashCode() {
      return live() ? super.hashCode() : readCopy().hashCode();
   }

   @Override
   public String toString() {
      return live() ? super.toString() : readCopy().toString();
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
         int i = (int) index;

         mirror(c -> {
            Object e = LiveView.toCopy(value);

            while(c.size() <= i) {
               c.add(null);
            }

            c.set(i, e);
         });

         return;
      }

      writeCopy().set(index, value);
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
    * Serialized as its current state, a {@link CopyList}, as today's copy was.
    */
   @Serial
   private Object writeReplace() {
      return live() ? WsValueCopier.copyList(guest) : copy;
   }

   /**
    * @return whether this view still holds its guest value, for tests.
    */
   boolean attached() {
      return guest != null;
   }

   // the guest value: set at creation, dropped by its frame; only the owner thread uses it
   private transient Value guest;
   // re-made by the owner thread at each pass and at exec completion, and written by the
   // owner's live writes; read by any thread
   private transient volatile CopyList copy;
   // the copy could not follow a live write: reading it while the exec is open fails
   private transient volatile boolean stale;
   // dropped with the guest value, so a kept view pins no slot, context or thread
   private transient volatile WsExecContext.Frame frame;
}
