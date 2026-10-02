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

import inetsoft.util.script.graal.ScriptValueConverter;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * A host copy of a worksheet script's plain object (bug #76960, spec §14.12 A3). It is an
 * ordinary Map for Java, and reads back into a script as an object, so JSON.stringify,
 * Object.keys and String() give main's results; only identity and write-through are lost.
 * Being a proxy, it is never copied again.
 * <p>
 * For a Java-argument copy, a Java write on the script's thread, while the script run that
 * passed it executes, is counted and warned about once per script (bug #77123, C1), as for
 * {@link CopyList}. The script's own writes through the copy handed back to it are not.
 * Best effort: the Map write methods are covered; writes through keySet, values and entrySet
 * are not.
 */
public final class CopyMap extends LinkedHashMap<String, Object> implements ProxyObject {
   /**
    * @param interop {@code true} for a copy made for a Java method argument (Graal default
    *                boxing), {@code false} for one made by ScriptValueConverter (main's shapes).
    */
   CopyMap(boolean interop) {
      this.interop = interop;
   }

   @Override
   public Object getMember(String key) {
      return get(key);
   }

   @Override
   public Object getMemberKeys() {
      return keySet().toArray(new String[0]);
   }

   @Override
   public boolean hasMember(String key) {
      return containsKey(key);
   }

   @Override
   public void putMember(String key, Value value) {
      super.put(key, interop ? WsValueCopier.interopElement(value)
         : ScriptValueConverter.toHostStored(value));
   }

   @Override
   public boolean removeMember(String key) {
      super.remove(key);
      return true;
   }

   // --- Java writes (bug #77123) ---

   @Override
   public Object put(String key, Object value) {
      javaWrite();
      return super.put(key, value);
   }

   @Override
   public void putAll(Map<? extends String, ?> m) {
      javaWrite();
      super.putAll(m);
   }

   @Override
   public Object remove(Object key) {
      javaWrite();
      return super.remove(key);
   }

   @Override
   public boolean remove(Object key, Object value) {
      javaWrite();
      return super.remove(key, value);
   }

   @Override
   public void clear() {
      javaWrite();
      super.clear();
   }

   @Override
   public Object putIfAbsent(String key, Object value) {
      javaWrite();
      return super.putIfAbsent(key, value);
   }

   @Override
   public boolean replace(String key, Object oldValue, Object newValue) {
      javaWrite();
      return super.replace(key, oldValue, newValue);
   }

   @Override
   public Object replace(String key, Object value) {
      javaWrite();
      return super.replace(key, value);
   }

   @Override
   public void replaceAll(BiFunction<? super String, ? super Object, ?> function) {
      javaWrite();
      super.replaceAll(function);
   }

   @Override
   public Object computeIfAbsent(String key, Function<? super String, ?> mappingFunction) {
      javaWrite();
      return super.computeIfAbsent(key, mappingFunction);
   }

   @Override
   public Object computeIfPresent(String key,
                                  BiFunction<? super String, ? super Object, ?> remapping)
   {
      javaWrite();
      return super.computeIfPresent(key, remapping);
   }

   @Override
   public Object compute(String key, BiFunction<? super String, ? super Object, ?> remapping) {
      javaWrite();
      return super.compute(key, remapping);
   }

   @Override
   public Object merge(String key, Object value,
                       BiFunction<? super Object, ? super Object, ?> remapping)
   {
      javaWrite();
      return super.merge(key, value, remapping);
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

   private final boolean interop;
   // the exec that passed this copy to Java, until that exec ended or a write was counted
   private transient WsExecContext.Origin origin;
}
