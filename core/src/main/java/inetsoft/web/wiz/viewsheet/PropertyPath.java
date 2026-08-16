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
    * Writes a dotted path, and returns the root the caller must keep using afterward.
    *
    * <p>An absent intermediate is an error rather than something to instantiate: the dialog
    * models are populated by the composer service, so a null pane means the property does not
    * apply to this assembly, and filling one in would fabricate a shape the service never
    * produced.
    *
    * <p><b>The return value matters whenever the root itself is immutable.</b> Every other
    * Immutables case this class handles nests the immutable level under a mutable holder, so a
    * rebuilt child always has somewhere mutable to be written back into and {@code root} itself
    * never changes identity. But a single-segment path directly on an immutable root — the
    * viewsheet-level dialog model's own {@code width}/{@code height}/{@code preview} — has no
    * such holder: the wither produces a genuinely new instance, and nothing above the root
    * exists to absorb it. Returning {@code root} unconditionally would silently drop that write,
    * which is exactly the defect this class exists to avoid. A caller that ignores the return
    * value and keeps the original reference reproduces that defect regardless.
    */
   public static Object set(Object root, String path, Object value) {
      REBUILT_CHILD.remove();

      try {
         writeInto(root, segments(path), 0, path, value);
         Object rebuilt = REBUILT_CHILD.get();
         return rebuilt != null ? rebuilt : root;
      }
      finally {
         REBUILT_CHILD.remove();
      }
   }

   /**
    * Writes {@code value} at {@code segments[depth..]}, rebuilding immutable levels on the way
    * back up.
    *
    * <p>A mutable owner takes {@code setX} and the write stops there. An <b>immutable</b> owner
    * has no setter — only {@code withX} returning a new instance — so the new instance has to be
    * written back into <em>its</em> owner, which may itself be immutable. Hence the recursion:
    * the rebuild propagates upward exactly as far as the immutability does, and stops at the
    * first mutable holder.
    */
   private static void writeInto(Object owner, String[] segments, int depth, String path,
                                 Object value)
   {
      String segment = segments[depth];
      boolean leaf = depth == segments.length - 1;
      Object toWrite = value;

      if(!leaf) {
         Object child = readOne(owner, segment, path, depth);

         if(child == null) {
            throw new IllegalArgumentException(
               "Cannot set '" + path + "': '" + segment + "' is not present on this " +
               "assembly, so the rest of the path does not exist. That usually means the " +
               "property does not apply to this assembly type.");
         }

         Object before = child;
         writeInto(child, segments, depth + 1, path, value);
         Object after = readOne(owner, segment, path, depth);

         // A mutable child was updated in place, so there is nothing to write back. An
         // immutable one is unchanged here — REBUILT_CHILD holds the new instance instead.
         if(REBUILT_CHILD.get() == null || after != before) {
            REBUILT_CHILD.remove();
            return;
         }

         toWrite = REBUILT_CHILD.get();
         REBUILT_CHILD.remove();
      }

      Method setter = setterFor(owner.getClass(), segment);

      if(setter != null) {
         try {
            setter.invoke(owner, leaf ? coerce(toWrite, setter.getParameterTypes()[0], path)
                                      : toWrite);
            return;
         }
         catch(IllegalAccessException | InvocationTargetException e) {
            throw new IllegalArgumentException(
               "Setting '" + path + "' failed: " + rootMessage(e), e);
         }
      }

      Method wither = witherFor(owner.getClass(), segment);

      if(wither == null) {
         throw new IllegalArgumentException(
            "Cannot set '" + path + "': '" + segment + "' is not a writable property of " +
            simpleName(owner.getClass()) + ". " + available(owner.getClass()));
      }

      try {
         Object rebuilt = wither.invoke(
            owner, leaf ? coerce(toWrite, wither.getParameterTypes()[0], path) : toWrite);
         // Hand the new instance to the caller one level up, which writes it into its own owner.
         REBUILT_CHILD.set(rebuilt);
      }
      catch(IllegalAccessException | InvocationTargetException e) {
         throw new IllegalArgumentException(
            "Setting '" + path + "' failed: " + rootMessage(e), e);
      }
   }

   /**
    * Carries a rebuilt immutable instance from a recursion level to its parent.
    *
    * <p>A thread-local rather than a return value because {@code writeInto} is also the public
    * entry point's recursion, and threading an out-parameter through every level would obscure
    * the mutable case, which is by far the common one.
    */
   private static final ThreadLocal<Object> REBUILT_CHILD = new ThreadLocal<>();

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

      String text = String.valueOf(value).trim();

      // Checked before the pass-through below, which would otherwise hand a String straight to a
      // String setter without ever consulting the value domain.
      requireAllowedValue(text, value, target, path);

      if(target.isInstance(value) && !(value instanceof Number && target != value.getClass())) {
         return value;
      }

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

   /**
    * Finds a reader: {@code getX()}, {@code isX()}, or the bare {@code x()} an Immutables model
    * generates. Without the bare form the five Immutables dialog models — image, presenter,
    * table layout, and both viewsheet-level ones — could not be read at all.
    */
   private static Method getterFor(Class<?> type, String property) {
      String suffix = capitalize(property);
      Method bare = null;

      for(Method method : type.getMethods()) {
         if(method.getParameterCount() != 0 || method.getDeclaringClass() == Object.class) {
            continue;
         }

         if(method.getName().equals("get" + suffix) || method.getName().equals("is" + suffix)) {
            return method;
         }

         if(method.getName().equals(property)) {
            bare = method;
         }
      }

      return bare;
   }

   /**
    * Finds an Immutables wither: {@code withX(value)} returning a <b>new</b> instance rather
    * than mutating this one.
    */
   private static Method witherFor(Class<?> type, String property) {
      String name = "with" + capitalize(property);

      for(Method method : type.getMethods()) {
         if(method.getParameterCount() == 1 && method.getName().equals(name)) {
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

   /**
    * Refuses a value outside a String property's closed domain.
    *
    * <p>Some String-typed properties are enums in disguise: StyleBI maps a recognised token to a
    * constant and silently keeps the default for anything else. Passing the raw text through
    * produces the worst outcome available — a clean "ok" for a setting that was never applied.
    * Live, {@code visible: "no"} stored "no" and left the assembly on screen.
    */
   private static void requireAllowedValue(String text, Object value, Class<?> target,
                                           String path)
   {
      if(target != String.class) {
         return;
      }

      Set<String> allowed = CONSTRAINED_STRINGS.get(leafName(path));

      if(allowed != null && allowed.stream().noneMatch(v -> v.equalsIgnoreCase(text))) {
         throw new IllegalArgumentException(
            "'" + path + "' accepts only " + new TreeSet<>(allowed) + "; '" + value + "' is not " +
            "one of them. StyleBI ignores an unrecognised value and keeps the default, so this " +
            "would have reported success without changing anything.");
      }
   }

   /** The last segment of a dotted path — the property's own name. */
   private static String leafName(String path) {
      int dot = path.lastIndexOf('.');
      return dot < 0 ? path : path.substring(dot + 1);
   }

   /**
    * String-typed properties whose value domain is closed.
    *
    * <p>{@code visible} is declared in {@code VSAssemblyInfo} as a {@code DynamicValue} over
    * {@code {"true","show","false","hide","hide on print and export"}}; an unrecognised token is
    * not an error there, it simply leaves the default in place. Without this check the tool
    * reported success for {@code visible: "no"} and the assembly stayed on screen.
    */
   private static final Map<String, Set<String>> CONSTRAINED_STRINGS = Map.of(
      "visible", Set.of("true", "show", "false", "hide", "hide on print and export"));
}
