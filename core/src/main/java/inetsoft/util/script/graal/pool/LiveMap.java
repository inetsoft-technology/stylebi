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
import org.graalvm.polyglot.proxy.ProxyObject;

import java.io.Serial;
import java.io.Serializable;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * A {@link LiveView} of a worksheet script's plain object passed to a Java method (bug
 * #77123). It is a Map of the object's own keys for Java and reads back into a script as an
 * object. In copy mode every read works on one captured copy; while the exec is open that
 * copy is read-only (see {@link LiveView}).
 */
final class LiveMap extends AbstractMap<String, Object>
   implements LiveView, ProxyObject, Serializable
{
   LiveMap(Value guest, CopyMap copy, WsExecContext.Frame frame) {
      this.guest = guest;
      this.copy = copy;
      this.frame = frame;
   }

   // --- LiveView ---

   @Override
   public void resnapshot() {
      Value v = guest;

      if(v != null) {
         copy = WsValueCopier.copyMap(v);
         stale = false;
      }
   }

   @Override
   public void refresh() {
      if(live()) {
         copy = WsValueCopier.copyMap(guest);
         stale = false;
      }
   }

   @Override
   public void rebind(Object copied) {
      if(copied instanceof CopyMap map && map != copy) {
         copy = map;
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
   private Map<String, Object> readCopy() {
      CopyMap c = copy;

      if(WsExecContext.isOpen(frame)) {
         if(stale) {
            throw LiveView.busy();
         }

         return Collections.unmodifiableMap(c);
      }

      return c;
   }

   /**
    * @return the copy for a write in copy mode: only once the exec that made the view ended.
    */
   private CopyMap writeCopy() {
      if(WsExecContext.isOpen(frame)) {
         throw LiveView.busy();
      }

      return copy;
   }

   /**
    * Apply a live write to the copy too, so other threads see it; a copy that cannot follow
    * is marked stale, and reading it then fails.
    */
   private void mirror(Consumer<CopyMap> write) {
      try {
         write.accept(copy);
      }
      catch(RuntimeException ex) {
         stale = true;
      }
   }

   /**
    * @return the member {@code key} of the live guest when it is an own member, else null;
    *         an inherited member (toString, constructor, ...) is not a key, as in the copy.
    */
   private Value member(Object key) {
      String name = String.valueOf(key);
      Value m = guest.getMember(name);

      if(m == null || m.isNumber() || m.isString() || m.isBoolean()) {
         return m;
      }

      // a function or non-plain value is either inherited (not a key) or an own member the
      // script added after the call, which is rejected as the copy would have
      if(m.canExecute() || WsValueCopier.isNonPlainObject(m)) {
         return guest.getMemberKeys().contains(name) ? m : null;
      }

      return m;
   }

   private Object liveGet(Object key) {
      Value m = member(key);

      if(m == null) {
         return null;
      }

      Object copied = stale ? null : copy.get(key);
      return LiveView.element(m, frame, copied);
   }

   // --- Map: live on the owner thread, else the copy ---

   @Override
   public int size() {
      return live() ? guest.getMemberKeys().size() : readCopy().size();
   }

   @Override
   public boolean isEmpty() {
      return live() ? guest.getMemberKeys().isEmpty() : readCopy().isEmpty();
   }

   @Override
   public Object get(Object key) {
      return live() ? liveGet(key) : readCopy().get(key);
   }

   @Override
   public boolean containsKey(Object key) {
      return live() ? member(key) != null : readCopy().containsKey(key);
   }

   @Override
   public Object put(String key, Object value) {
      if(live()) {
         Object previous = get(key);
         guest.putMember(key, LiveView.toGuest(value));
         mirror(c -> c.put(key, LiveView.toCopy(value)));
         return previous;
      }

      return writeCopy().put(key, value);
   }

   @Override
   public Object remove(Object key) {
      if(live()) {
         Object previous = get(key);
         guest.removeMember(String.valueOf(key));
         mirror(c -> c.remove(String.valueOf(key)));
         return previous;
      }

      return writeCopy().remove(key);
   }

   @Override
   public void clear() {
      if(live()) {
         for(String key : new ArrayList<>(guest.getMemberKeys())) {
            guest.removeMember(key);
         }

         mirror(LinkedHashMap::clear);
         return;
      }

      writeCopy().clear();
   }

   @Override
   public Set<Entry<String, Object>> entrySet() {
      return live() ? new LiveEntries() : readCopy().entrySet();
   }

   // bulk reads in copy mode: one captured copy (refute amendment 5)

   @Override
   public Set<String> keySet() {
      return live() ? super.keySet() : readCopy().keySet();
   }

   @Override
   public Collection<Object> values() {
      return live() ? super.values() : readCopy().values();
   }

   @Override
   public boolean containsValue(Object value) {
      return live() ? super.containsValue(value) : readCopy().containsValue(value);
   }

   @Override
   public void forEach(BiConsumer<? super String, ? super Object> action) {
      if(live()) {
         super.forEach(action);
      }
      else {
         readCopy().forEach(action);
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

   // --- ProxyObject: the view read back into a script ---

   @Override
   public Object getMember(String key) {
      return get(key);
   }

   @Override
   public Object getMemberKeys() {
      return live() ? guest.getMemberKeys().toArray(new String[0])
         : readCopy().keySet().toArray(new String[0]);
   }

   @Override
   public boolean hasMember(String key) {
      return containsKey(key);
   }

   @Override
   public void putMember(String key, Value value) {
      if(live()) {
         guest.putMember(key, LiveView.toGuest(value));
         mirror(c -> c.put(key, LiveView.toCopy(value)));
         return;
      }

      writeCopy().putMember(key, value);
   }

   @Override
   public boolean removeMember(String key) {
      remove(key);
      return true;
   }

   /**
    * Serialized as its current state, a {@link CopyMap}, as today's copy was.
    */
   @Serial
   private Object writeReplace() {
      return live() ? WsValueCopier.copyMap(guest) : copy;
   }

   /**
    * @return whether this view still holds its guest value, for tests.
    */
   boolean attached() {
      return guest != null;
   }

   /**
    * The live entries: the guest's own keys when iterated live; writes go through the view.
    * An iterator taken when the view is not live on the calling thread (another thread, or
    * after the exec) iterates the copy and never touches the guest value (review I2).
    */
   private final class LiveEntries extends AbstractSet<Entry<String, Object>> {
      @Override
      public Iterator<Entry<String, Object>> iterator() {
         if(!live()) {
            return readCopy().entrySet().iterator();
         }

         Iterator<String> keys = new ArrayList<>(guest.getMemberKeys()).iterator();

         return new Iterator<>() {
            @Override
            public boolean hasNext() {
               return keys.hasNext();
            }

            @Override
            public Entry<String, Object> next() {
               String key = keys.next();
               last = key;

               // get() re-checks liveness: consumed elsewhere, it reads the copy
               return new SimpleEntry<>(key, get(key)) {
                  @Override
                  public Object setValue(Object value) {
                     put(key, value);
                     return super.setValue(value);
                  }
               };
            }

            @Override
            public void remove() {
               if(last == null) {
                  throw new IllegalStateException();
               }

               LiveMap.this.remove(last);
               last = null;
            }

            private String last;
         };
      }

      @Override
      public int size() {
         return LiveMap.this.size();
      }
   }

   // the guest value: set at creation, dropped by its frame; only the owner thread uses it
   private transient Value guest;
   // re-made by the owner thread at each pass and at exec completion, and written by the
   // owner's live writes; read by any thread
   private transient volatile CopyMap copy;
   // the copy could not follow a live write: reading it while the exec is open fails
   private transient volatile boolean stale;
   // dropped with the guest value, so a kept view pins no slot, context or thread
   private transient volatile WsExecContext.Frame frame;
}
