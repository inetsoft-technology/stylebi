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

import inetsoft.report.internal.graph.MapData;
import inetsoft.util.script.Calc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * The stateless host objects that {@link GraalJavaScriptEngine} installs as globals, built once
 * per JVM and shared by every Context of every engine instead of rebuilt per Context (Testing
 * #77123, G10: about 40 KB of each pooled worksheet context was these duplicates).
 *
 * <p>Each object is immutable once published and holds nothing of an org, user, sandbox or
 * Context:
 * <ul>
 *    <li>{@link ScriptFunction}: final fields only and no members. Every shared one has a
 *    {@code null} target (it wraps a static method), so invoking it touches no receiver
 *    state, and {@link java.lang.reflect.Method#invoke} is thread-safe.</li>
 *    <li>{@link Calc}: its function map (of such {@code ScriptFunction}s) is filled in the
 *    constructor and only read afterwards, and {@code putMember} is a no-op. Its
 *    {@code setParentScope} is never called on this instance; the engine only reads it (as
 *    {@code CALC} and as the {@code __scope__} proxy's builtin scope). The holder idiom
 *    publishes it safely.</li>
 *    <li>{@link ConstantScope}: filled in the builder below, before
 *    {@link ConcurrentHashMap#computeIfAbsent} publishes it, from the classes'
 *    {@code public static final} values (which are JVM-wide already) and the
 *    {@code MAP_TYPE_*} names of {@link MapData}, whose map list is loaded once per JVM in its
 *    static initializer, not per org. {@code putMember} is a no-op and {@code putConstant} is
 *    called only by that builder.</li>
 * </ul>
 * A script's writes to them ({@code CALC.x = 1}, {@code delete CALC.sum},
 * {@code StyleConstant.Y = 4}) are therefore ignored exactly as with a per-Context instance, and
 * nothing a script does can reach another Context. The guest-side wrappers (the
 * {@link ScopeProxy} from {@link ScriptValueConverter#toGuest}, the global bindings) are still
 * made per Context, so rebinding a global only affects that Context. Objects with per-Context
 * or per-org state (the {@code JavaClassProxy} globals, the {@code __scope__} proxy, the library
 * functions) are not shared.
 */
final class SharedHostObjects {
   private SharedHostObjects() {
   }

   /** The one {@code CALC} object. */
   static Calc calc() {
      return CalcHolder.CALC;
   }

   /**
    * The function for the static method {@code cls.method(params)}; the one per JVM. Its name is
    * the method name, as for a per-Context instance.
    */
   static ScriptFunction function(Class<?> cls, String method, Class<?>... params) {
      return FUNCTIONS.computeIfAbsent(
         cls.getName() + "#" + method + Arrays.toString(params),
         k -> new ScriptFunction(null, cls, method, params));
   }

   /**
    * A function per {@code public static} method declared directly on {@code cls}, in
    * {@code getMethods()} order (installing them in order lets a later same-name entry win, as
    * before); one list per JVM.
    */
   static List<Map.Entry<String, ScriptFunction>> staticFunctions(Class<?> cls) {
      return STATICS.get(cls);
   }

   /** The constant scope of {@code classes} plus the map-type constants; one per JVM. */
   static ConstantScope constants(Class<?>... classes) {
      return CONSTANTS.computeIfAbsent(Arrays.asList(classes.clone()), k -> {
         ConstantScope scope = new ConstantScope(classes);
         installMapTypeConstants(scope);
         return scope;
      });
   }

   /**
    * Register the dynamic {@code MAP_TYPE_<TYPE>} constants (e.g.
    * {@code MAP_TYPE_U.S.} = "U.S.") derived from the installed map data. These
    * are not {@code public static final} fields, so the reflected
    * {@link ConstantScope} does not pick them up; Rhino added them explicitly to
    * both the {@code Chart} and {@code StyleConstant} scopes, so restore them
    * here to keep {@code mapType = Chart["MAP_TYPE_U.S."]} working. (#75679)
    */
   private static void installMapTypeConstants(ConstantScope scope) {
      try {
         for(String type : MapData.getMapTypes()) {
            scope.putConstant("MAP_TYPE_" + type.toUpperCase(), type);
         }
      }
      catch(Throwable ex) {
         LOG.warn("Failed to install map type constants", ex);
      }
   }

   // holder idiom: built on first use, safely published by class initialization
   private static final class CalcHolder {
      private static final Calc CALC = new Calc();
   }

   private static final ConcurrentMap<String, ScriptFunction> FUNCTIONS =
      new ConcurrentHashMap<>();
   private static final ConcurrentMap<List<Class<?>>, ConstantScope> CONSTANTS =
      new ConcurrentHashMap<>();
   private static final ClassValue<List<Map.Entry<String, ScriptFunction>>> STATICS =
      new ClassValue<>() {
         @Override
         protected List<Map.Entry<String, ScriptFunction>> computeValue(Class<?> cls) {
            List<Map.Entry<String, ScriptFunction>> list = new ArrayList<>();

            for(Method m : cls.getMethods()) {
               if(m.getDeclaringClass() != cls || !Modifier.isStatic(m.getModifiers()) ||
                  !Modifier.isPublic(m.getModifiers()))
               {
                  continue;
               }

               list.add(Map.entry(m.getName(), new ScriptFunction(null, m)));
            }

            return List.copyOf(list);
         }
      };

   private static final Logger LOG = LoggerFactory.getLogger(SharedHostObjects.class);
}
