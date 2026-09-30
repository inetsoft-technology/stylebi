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

import inetsoft.report.Size;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A read-only, Map-backed {@link ScriptScope} that exposes a fixed set of named
 * constants to scripts. Replaces the Rhino constant-holder objects built via
 * {@code JavaScriptEngine.addFields(...)}, which reflected the
 * {@code public static final} fields of one or more classes into a scope object
 * (e.g. {@code Chart}, {@code GLine}, {@code StyleConstant}).
 *
 * <p>Member writes are silently ignored. A scope is shared by every script context
 * (see {@link SharedHostObjects}), so it exposes only immutable constant values:
 * strings, boxed primitives and immutable enums (see {@link #isImmutable(Object)}) are
 * handed out as-is (the same object, so identity comparisons keep working); arrays of primitives or of those values, and
 * {@link Size}, are handed out as a fresh copy per read; any other value is not
 * exposed.
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
                  putConstant(field.getName(), field.get(null));
               }
               catch(IllegalAccessException ignore) {
                  // inaccessible field — skip it
               }
            }
         }
      }
   }

   /**
    * Add an extra constant not derived from a reflected field. A value that is not a
    * constant value (see the class comment) is not added, and it removes an earlier
    * member of the same name, as a later class's field replaces it.
    */
   public void putConstant(String name, Object value) {
      if(isImmutable(value)) {
         members.put(name, value);
      }
      else if(isCopyable(value)) {
         members.put(name, copy(value));
      }
      else {
         members.remove(name);
      }
   }

   @Override
   public Object getMember(String name) {
      return copy(members.get(name));
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

   /**
    * True for a value that can be shared as-is: {@code null}, a string, a boxed primitive, or
    * an enum constant that holds no mutable state. An enum constant can carry instance fields,
    * and its methods can read or change a static field, so an enum is accepted only when it is
    * a JDK enum, or when every field declared by its class (and a constant-specific body class),
    * instance or static, other than the enum constants and the synthetic values array, is final
    * and of a primitive or immutable type.
    */
   static boolean isImmutable(Object value) {
      if(value == null || IMMUTABLE_TYPES.contains(value.getClass())) {
         return true;
      }

      return value instanceof Enum<?> e && isImmutableEnum(e.getDeclaringClass(), new HashSet<>());
   }

   private static boolean isImmutableEnum(Class<?> type, Set<Class<?>> visiting) {
      Module module = type.getModule();

      if(module.isNamed() && module.getName().startsWith("java.")) {
         return true;
      }

      if(!visiting.add(type)) {
         // a cycle back to an enum being checked adds no state of its own
         return true;
      }

      List<Class<?>> classes = new ArrayList<>();
      classes.add(type);

      for(Object constant : type.getEnumConstants()) {
         if(constant.getClass() != type) {
            classes.add(constant.getClass());
         }
      }

      for(Class<?> cls : classes) {
         for(Field field : cls.getDeclaredFields()) {
            int mod = field.getModifiers();

            // the enum's own constants and the synthetic values array are not state
            if(field.isEnumConstant() || field.isSynthetic() && Modifier.isStatic(mod) && Modifier.isFinal(mod)) {
               continue;
            }

            // instance and static fields alike: a method can read or change a static
            if(!Modifier.isFinal(mod) || !isImmutableType(field.getType(), visiting)) {
               return false;
            }
         }
      }

      return true;
   }

   private static boolean isImmutableType(Class<?> type, Set<Class<?>> visiting) {
      return type.isPrimitive() || IMMUTABLE_TYPES.contains(type) ||
         type.isEnum() && isImmutableEnum(type, visiting);
   }

   /** True for a mutable constant value that is exposed as a copy per read. */
   private static boolean isCopyable(Object value) {
      if(value instanceof Size) {
         return value.getClass() == Size.class;
      }

      if(value == null || !value.getClass().isArray()) {
         return false;
      }

      if(value.getClass().getComponentType().isPrimitive()) {
         return true;
      }

      for(int i = 0; i < Array.getLength(value); i++) {
         if(!isImmutable(Array.get(value, i))) {
            return false;
         }
      }

      return true;
   }

   /** A copy of a mutable constant value; an immutable value is returned unchanged. */
   private static Object copy(Object value) {
      if(value instanceof Size size) {
         return new Size(size);
      }

      if(value != null && value.getClass().isArray()) {
         int len = Array.getLength(value);
         Object arr = Array.newInstance(value.getClass().getComponentType(), len);
         System.arraycopy(value, 0, arr, 0, len);
         return arr;
      }

      return value;
   }

   private static final Set<Class<?>> IMMUTABLE_TYPES = Set.of(
      String.class, Boolean.class, Character.class, Byte.class, Short.class, Integer.class,
      Long.class, Float.class, Double.class, BigInteger.class, BigDecimal.class);
}
