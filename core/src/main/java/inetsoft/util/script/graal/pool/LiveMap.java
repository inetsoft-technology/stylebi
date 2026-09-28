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

import java.util.*;
import java.util.function.BiConsumer;

/**
 * A {@link LiveView} of a worksheet script's plain object passed to a Java method (bug
 * #77123). It is a Map of the object's own keys for Java and reads back into a script as an
 * object. In copy mode every read works on one captured copy.
 */
final class LiveMap extends AbstractMap<String, Object> implements LiveView, ProxyObject {
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

   /**
    * @return the member {@code key} of the live guest when it is an own member, else null;
    *         an inherited member (toString, constructor, ...) is not a key, as in the copy.
    */
   private Value member(Object key) {
      String name = String.valueOf(key);
      Value m = guest.getMember(name);

      if(m == null) {
         return null;
      }

      // a function or non-plain value is either inherited (not a key) or an own member the
      // script added after the call, which is rejected as the copy would have
      if(m.canExecute() || WsValueCopier.isNonPlainObject(m)) {
         return guest.getMemberKeys().contains(name) ? m : null;
      }

      return m;
   }

   // --- Map: live on the owner thread, else the copy ---

   @Override
   public int size() {
      return live() ? guest.getMemberKeys().size() : copy.size();
   }

   @Override
   public boolean isEmpty() {
      return live() ? guest.getMemberKeys().isEmpty() : copy.isEmpty();
   }

   @Override
   public Object get(Object key) {
      if(live()) {
         Value m = member(key);
         return m == null ? null : LiveView.element(m, frame);
      }

      return copy.get(key);
   }

   @Override
   public boolean containsKey(Object key) {
      return live() ? member(key) != null : copy.containsKey(key);
   }

   @Override
   public Object put(String key, Object value) {
      if(live()) {
         Object previous = get(key);
         guest.putMember(key, LiveView.toGuest(value));
         return previous;
      }

      return copy.put(key, value);
   }

   @Override
   public Object remove(Object key) {
      if(live()) {
         Object previous = get(key);
         guest.removeMember(String.valueOf(key));
         return previous;
      }

      return copy.remove(key);
   }

   @Override
   public void clear() {
      if(live()) {
         for(String key : new ArrayList<>(guest.getMemberKeys())) {
            guest.removeMember(key);
         }

         return;
      }

      copy.clear();
   }

   @Override
   public Set<Entry<String, Object>> entrySet() {
      return live() ? new LiveEntries() : copy.entrySet();
   }

   // bulk reads in copy mode: one captured copy (refute amendment 5)

   @Override
   public Set<String> keySet() {
      return live() ? super.keySet() : copy.keySet();
   }

   @Override
   public Collection<Object> values() {
      return live() ? super.values() : copy.values();
   }

   @Override
   public boolean containsValue(Object value) {
      return live() ? super.containsValue(value) : copy.containsValue(value);
   }

   @Override
   public void forEach(BiConsumer<? super String, ? super Object> action) {
      if(live()) {
         super.forEach(action);
      }
      else {
         copy.forEach(action);
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

   // --- ProxyObject: the view read back into a script ---

   @Override
   public Object getMember(String key) {
      return get(key);
   }

   @Override
   public Object getMemberKeys() {
      return live() ? guest.getMemberKeys().toArray(new String[0]) : copy.getMemberKeys();
   }

   @Override
   public boolean hasMember(String key) {
      return containsKey(key);
   }

   @Override
   public void putMember(String key, Value value) {
      if(live()) {
         guest.putMember(key, LiveView.toGuest(value));
         return;
      }

      copy.putMember(key, value);
   }

   @Override
   public boolean removeMember(String key) {
      remove(key);
      return true;
   }

   /**
    * @return whether this view still holds its guest value, for tests.
    */
   boolean attached() {
      return guest != null;
   }

   /**
    * The live entries: the guest's own keys when iterated; writes go to the guest.
    */
   private final class LiveEntries extends AbstractSet<Entry<String, Object>> {
      @Override
      public Iterator<Entry<String, Object>> iterator() {
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
   private Value guest;
   // replaced once, by the owner thread at exec completion; read by any thread
   private volatile CopyMap copy;
   private final WsExecContext.Frame frame;
}
