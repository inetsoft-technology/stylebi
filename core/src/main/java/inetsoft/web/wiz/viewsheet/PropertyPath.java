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
package inetsoft.web.wiz.viewsheet;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Reads and writes a dotted path on a property-dialog model.
 *
 * <p>The dialog models nest deeply — setting one boolean on a gauge means
 * {@code gaugeGeneralPaneModel.outputGeneralPaneModel.generalPropPaneModel.basicGeneralPaneModel.visible}
 * — which is why the alias layer exists on top of this. This class is the layer underneath:
 * the raw escape hatch, and what every alias ultimately resolves to.
 *
 * <p><b>Nothing here is ever silently skipped.</b> An unresolvable segment throws and names
 * both the segment and what was available at that level. A path that quietly no-ops is the
 * exact defect this plugin family exists to avoid — it reports success while changing nothing.
 */
public final class PropertyPath {
   private PropertyPath() {
   }

   /** Reads a dotted path, returning null if an intermediate object is absent. */
   public static Object get(Object root, String path) {
      String[] segments = segments(path);
      Object current = root;

      for(int i = 0; i < segments.length; i++) {
         if(current == null) {
            return null;
         }

         current = readOne(current, segments[i], path, i);
      }

      return current;
   }

   /**
    * Writes a dotted path.
    *
    * <p>An absent intermediate is an error rather than something to instantiate: the dialog
    * models are populated by the composer service, so a null pane means the property does not
    * apply to this assembly, and filling one in would fabricate a shape the service never
    * produced.
    */
   public static void set(Object root, String path, Object value) {
      String[] segments = segments(path);
      Object current = root;

      for(int i = 0; i < segments.length - 1; i++) {
         Object next = readOne(current, segments[i], path, i);

         if(next == null) {
            throw new IllegalArgumentException(
               "Cannot set '" + path + "': '" + segments[i] + "' is not present on this " +
               "assembly, so the rest of the path does not exist. That usually means the " +
               "property does not apply to this assembly type.");
         }

         current = next;
      }

      String leaf = segments[segments.length - 1];
      Method setter = setterFor(current.getClass(), leaf);

      if(setter == null) {
         throw new IllegalArgumentException(
            "Cannot set '" + path + "': '" + leaf + "' is not a writable property of " +
            simpleName(current.getClass()) + ". " + available(current.getClass()));
      }

      Class<?> target = setter.getParameterTypes()[0];

      try {
         setter.invoke(current, coerce(value, target, path));
      }
      catch(IllegalAccessException | InvocationTargetException e) {
         throw new IllegalArgumentException(
            "Setting '" + path + "' failed: " + rootMessage(e), e);
      }
   }

   /** The type a path expects, so a registry test can assert the alias declares it correctly. */
   public static Class<?> typeOf(Class<?> rootType, String path) {
      String[] segments = segments(path);
      Class<?> current = rootType;

      for(String segment : segments) {
         Method getter = getterFor(current, segment);

         if(getter == null) {
            throw new IllegalArgumentException(
               "'" + segment + "' does not exist on " + simpleName(current) +
               " (resolving '" + path + "'). " + available(current));
         }

         current = getter.getReturnType();
      }

      return current;
   }

   /** Readable property names at a level, for error messages and discovery. */
   public static List<String> propertiesOf(Class<?> type) {
      Set<String> names = new TreeSet<>();

      for(Method method : type.getMethods()) {
         if(method.getParameterCount() != 0 || method.getDeclaringClass() == Object.class) {
            continue;
         }

         String name = method.getName();

         if(name.startsWith("get") && name.length() > 3) {
            names.add(decapitalize(name.substring(3)));
         }
         else if(name.startsWith("is") && name.length() > 2) {
            names.add(decapitalize(name.substring(2)));
         }
      }

      return new ArrayList<>(names);
   }

   /**
    * Coerces a JSON-shaped value onto the setter's type.
    *
    * <p>Forgiving where the intent is unambiguous — {@code "true"} for a boolean, a numeric
    * string for a number, an enum token in any case — and loud otherwise. An LLM writing JSON
    * produces these spellings constantly, and rejecting them would be pedantry; guessing at
    * anything beyond them would not.
    */
   static Object coerce(Object value, Class<?> target, String path) {
      if(value == null) {
         if(target.isPrimitive()) {
            throw new IllegalArgumentException(
               "'" + path + "' is a " + target.getName() + " and cannot be set to null.");
         }

         return null;
      }

      if(target.isInstance(value) && !(value instanceof Number && target != value.getClass())) {
         return value;
      }

      String text = String.valueOf(value).trim();

      if(target == boolean.class || target == Boolean.class) {
         if("true".equalsIgnoreCase(text)) {
            return Boolean.TRUE;
         }

         if("false".equalsIgnoreCase(text)) {
            return Boolean.FALSE;
         }

         throw new IllegalArgumentException(
            "'" + path + "' is a boolean; '" + value + "' is not true or false. Spellings " +
            "like \"yes\" are refused rather than guessed at, because guessing wrong here " +
            "renders a setting that looks applied and is not.");
      }

      try {
         if(target == int.class || target == Integer.class) {
            return (int) Double.parseDouble(text);
         }

         if(target == long.class || target == Long.class) {
            return (long) Double.parseDouble(text);
         }

         if(target == double.class || target == Double.class) {
            return Double.parseDouble(text);
         }

         if(target == float.class || target == Float.class) {
            return (float) Double.parseDouble(text);
         }
      }
      catch(NumberFormatException e) {
         throw new IllegalArgumentException(
            "'" + path + "' is a " + simpleName(target) + "; '" + value + "' is not a number.");
      }

      if(target == String.class) {
         return text;
      }

      if(target.isEnum()) {
         for(Object constant : target.getEnumConstants()) {
            if(String.valueOf(constant).equalsIgnoreCase(text)) {
               return constant;
            }
         }

         throw new IllegalArgumentException(
            "'" + path + "' does not accept '" + value + "'. Valid values: " +
            Arrays.toString(target.getEnumConstants()) + ".");
      }

      throw new IllegalArgumentException(
         "'" + path + "' expects " + simpleName(target) + ", which '" + value + "' is not.");
   }

   // ── reflection helpers ────────────────────────────────────────────────────

   private static String[] segments(String path) {
      if(path == null || path.isBlank()) {
         throw new IllegalArgumentException("A property path cannot be empty.");
      }

      return path.split("\\.");
   }

   private static Object readOne(Object target, String segment, String path, int index) {
      Method getter = getterFor(target.getClass(), segment);

      if(getter == null) {
         throw new IllegalArgumentException(
            "'" + segment + "' (in '" + path + "', segment " + (index + 1) + ") is not a " +
            "property of " + simpleName(target.getClass()) + ". " +
            available(target.getClass()));
      }

      try {
         return getter.invoke(target);
      }
      catch(IllegalAccessException | InvocationTargetException e) {
         throw new IllegalArgumentException("Reading '" + path + "' failed: " + rootMessage(e), e);
      }
   }

   private static Method getterFor(Class<?> type, String property) {
      String suffix = capitalize(property);

      for(Method method : type.getMethods()) {
         if(method.getParameterCount() != 0) {
            continue;
         }

         if(method.getName().equals("get" + suffix) || method.getName().equals("is" + suffix)) {
            return method;
         }
      }

      return null;
   }

   private static Method setterFor(Class<?> type, String property) {
      String name = "set" + capitalize(property);

      for(Method method : type.getMethods()) {
         if(method.getParameterCount() == 1 && method.getName().equals(name)) {
            return method;
         }
      }

      return null;
   }

   /** Names the nearest properties, since a wrong segment is nearly always a near miss. */
   private static String available(Class<?> type) {
      List<String> properties = propertiesOf(type);

      if(properties.isEmpty()) {
         return "It has no readable properties.";
      }

      if(properties.size() > 25) {
         return "It has " + properties.size() + " properties; call list_assembly_properties " +
            "for the vocabulary.";
      }

      return "Available: " + String.join(", ", properties) + ".";
   }

   private static String rootMessage(Exception e) {
      Throwable cause = e instanceof InvocationTargetException invocation && invocation.getCause() != null
         ? invocation.getCause() : e;
      return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
   }

   private static String simpleName(Class<?> type) {
      return type.getSimpleName();
   }

   private static String capitalize(String value) {
      return value.isEmpty() ? value
         : Character.toUpperCase(value.charAt(0)) + value.substring(1);
   }

   private static String decapitalize(String value) {
      return value.isEmpty() ? value
         : Character.toLowerCase(value.charAt(0)) + value.substring(1);
   }
}
