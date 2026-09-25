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

/**
 * A host copy of a worksheet script's plain object (bug #76960, spec §14.12 A3). It is an
 * ordinary Map for Java, and reads back into a script as an object, so JSON.stringify,
 * Object.keys and String() give main's results; only identity and write-through are lost.
 * Being a proxy, it is never copied again.
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
      put(key, interop ? WsValueCopier.interopElement(value)
         : ScriptValueConverter.toHostStored(value));
   }

   @Override
   public boolean removeMember(String key) {
      remove(key);
      return true;
   }

   private final boolean interop;
}
