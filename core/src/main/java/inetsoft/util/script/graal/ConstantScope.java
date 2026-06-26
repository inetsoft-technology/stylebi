/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A read-only, Map-backed {@link ScriptScope} that exposes a fixed set of named
 * constants to scripts. Replaces the Rhino constant-holder objects built via
 * {@code JavaScriptEngine.addFields(...)}, which reflected the
 * {@code public static final} fields of one or more classes into a scope object
 * (e.g. {@code Chart}, {@code GLine}, {@code StyleConstant}).
 *
 * <p>Member writes are silently ignored (the constants are immutable).
 */
public final class ConstantScope implements ScriptScope {
   private final Map<String, Object> members = new LinkedHashMap<>();

   /**
    * Build a constant scope from the {@code public static final} fields of the
    * given classes. Fields are added in class order; later classes overwrite
    * names from earlier classes, matching Rhino's {@code addFields} behavior.
    */
   public ConstantScope(Class<?>... classes) {
      for(Class<?> cls : classes) {
         if(cls == null) {
            continue;
         }

         for(Field field : cls.getFields()) {
            int mod = field.getModifiers();

            if(Modifier.isPublic(mod) && Modifier.isStatic(mod) && Modifier.isFinal(mod)) {
               try {
                  members.put(field.getName(), field.get(null));
               }
               catch(IllegalAccessException ignore) {
                  // inaccessible field — skip it
               }
            }
         }
      }
   }

   /** Add an extra constant not derived from a reflected field. */
   public void putConstant(String name, Object value) {
      members.put(name, value);
   }

   @Override
   public Object getMember(String name) {
      return members.get(name);
   }

   @Override
   public boolean hasMember(String name) {
      return members.containsKey(name);
   }

   @Override
   public void putMember(String name, Object value) {
      // read-only: ignore writes
   }

   @Override
   public Object[] getMemberKeys() {
      return members.keySet().toArray();
   }
}
