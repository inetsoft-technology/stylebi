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
package inetsoft.util.script;

import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.SRPrincipal;
import inetsoft.uql.XPrincipal;
import org.mozilla.javascript.*;

import java.security.Principal;
import java.util.*;
import java.util.function.BiFunction;

/**
 * Read-only script view of a {@link Principal}.
 *
 * <p>Scripts receive the session principal (e.g. {@code parameter.__principal__}) so
 * they can inspect the viewer's identity. Wrapped as a plain {@link NativeJavaObject},
 * every public method of the principal's class and its superclasses is callable,
 * including the {@code XPrincipal} setters ({@code setOrgId}, {@code setGroups},
 * {@code setProperty}, ...). The principal handed to scripts is the live instance
 * stored in the HTTP session, so a script could change the identity that later
 * permission and VPM checks trust for the rest of the session. The class shutter
 * cannot prevent this because it gates class names, not individual members.
 * (Bug #77384)
 *
 * <p>This wrapper exposes only an allow-list of read accessors (deny by default) and
 * returns copies of anything mutable. Property assignment and deletion are rejected.
 *
 * <p>{@link #unwrap()} lets a script pass the principal to Java code (script functions
 * and host methods declaring a {@code Principal} parameter) as before, but it hands
 * that code a detached copy ({@link XPrincipal#detachedCopy()}), never the live session
 * instance. Some script-visible Java helpers invoke methods on their arguments
 * reflectively, so the live instance would let a script reach the setters this wrapper
 * hides. Changing the copy never changes the session principal. The copy is equal to
 * the session principal, so permission and session checks treat it the same.
 *
 * <p>A principal that comes back from Java code (for example {@code VpmScope.getUser()})
 * is read-only again, because Rhino wraps every value a host method returns through the
 * context's {@link WrapFactory} ({@link JSFactory}). This reasoning is Rhino-specific:
 * the GraalJS engine does not re-wrap host return values, which is why its counterpart
 * ({@code ReadOnlyPrincipalProxy}) must not be unwrapped for raw host calls.
 */
public class ReadOnlyPrincipalObject extends NativeJavaObject {
   public ReadOnlyPrincipalObject(Scriptable scope, Principal principal, Class<?> staticType) {
      super(scope, principal, staticType);
      this.principal = principal;
   }

   /**
    * Returns a detached copy of the principal for Java code, never the live session
    * instance (see the class comment). Principals that are not {@code XPrincipal}s
    * carry no session state a script could change and are returned as-is.
    */
   @Override
   public Object unwrap() {
      return principal instanceof XPrincipal xp ? xp.detachedCopy() : principal;
   }

   @Override
   public boolean has(String name, Scriptable start) {
      return getAccessor(name) != null || getPropertyAccessor(name) != null;
   }

   @Override
   public boolean has(int index, Scriptable start) {
      return false;
   }

   @Override
   public Object get(String name, Scriptable start) {
      final Accessor method = getAccessor(name);

      if(method != null) {
         return functions.computeIfAbsent(name, k -> new AccessorFunction(k, method, this));
      }

      // bean-style read (e.g. principal.name) of an allow-listed no-arg getter
      final Accessor accessor = getPropertyAccessor(name);

      if(accessor != null) {
         Context cx = Context.getCurrentContext();
         Object value = accessor.fn.apply(principal, new Object[0]);
         Scriptable scope = ScriptableObject.getTopLevelScope(this);
         return cx == null ? value : cx.getWrapFactory().wrap(cx, scope, value, accessor.type);
      }

      return NOT_FOUND;
   }

   @Override
   public Object get(int index, Scriptable start) {
      return NOT_FOUND;
   }

   @Override
   public void put(String name, Scriptable start, Object value) {
      throw readOnly();
   }

   @Override
   public void put(int index, Scriptable start, Object value) {
      throw readOnly();
   }

   @Override
   public void delete(String name) {
      throw readOnly();
   }

   @Override
   public void delete(int index) {
      throw readOnly();
   }

   @Override
   public Object[] getIds() {
      List<Object> ids = new ArrayList<>();

      for(Map.Entry<String, Accessor> e : ACCESSORS.entrySet()) {
         if(e.getValue().appliesTo(principal)) {
            ids.add(e.getKey());
         }
      }

      return ids.toArray();
   }

   private Accessor getAccessor(String name) {
      Accessor accessor = name == null ? null : ACCESSORS.get(name);
      return accessor != null && accessor.appliesTo(principal) ? accessor : null;
   }

   private Accessor getPropertyAccessor(String name) {
      String getter = name == null ? null : PROPERTIES.get(name);
      return getter == null ? null : getAccessor(getter);
   }

   private static EvaluatorException readOnly() {
      return new EvaluatorException("The principal is read-only in script");
   }

   /**
    * Script function invoking one allow-listed accessor on the wrapped principal.
    */
   private static final class AccessorFunction extends BaseFunction {
      AccessorFunction(String name, Accessor accessor, ReadOnlyPrincipalObject owner) {
         this.name = name;
         this.accessor = accessor;
         this.owner = owner;
         ScriptRuntime.setFunctionProtoAndParent(this, ScriptableObject.getTopLevelScope(owner));
      }

      @Override
      public String getFunctionName() {
         return name;
      }

      @Override
      public Object call(Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
         Object[] jargs = new Object[args.length];

         for(int i = 0; i < args.length; i++) {
            jargs[i] = JavaScriptEngine.unwrap(args[i]);
         }

         Object value = accessor.fn.apply(owner.principal, jargs);
         return cx.getWrapFactory().wrap(cx, scope, value, accessor.type);
      }

      @Override
      public Scriptable construct(Context cx, Scriptable scope, Object[] args) {
         throw readOnly();
      }

      private final String name;
      private final Accessor accessor;
      private final ReadOnlyPrincipalObject owner;
   }

   private static String stringArg(Object[] args) {
      if(args.length == 0 || args[0] == null || args[0] instanceof Undefined) {
         return null;
      }

      return String.valueOf(args[0]);
   }

   private static IdentityID copy(IdentityID id) {
      return id == null ? null : new IdentityID(id.getName(), id.getOrgID());
   }

   private static IdentityID[] copy(IdentityID[] ids) {
      if(ids == null) {
         return null;
      }

      IdentityID[] result = new IdentityID[ids.length];

      for(int i = 0; i < ids.length; i++) {
         result[i] = copy(ids[i]);
      }

      return result;
   }

   private static <T> Enumeration<T> copy(Enumeration<T> names) {
      return names == null ? null : Collections.enumeration(Collections.list(names));
   }

   // A parameter value can be any mutable object a script must not change in place.
   private static Object copyValue(Object value) {
      return XPrincipal.copyParameterValue(value);
   }

   private static XPrincipal x(Principal p) {
      return (XPrincipal) p;
   }

   private static void add(String name, Class<?> owner, Class<?> type,
                           BiFunction<Principal, Object[], Object> fn)
   {
      ACCESSORS.put(name, new Accessor(owner, type, fn));
   }

   private record Accessor(Class<?> owner, Class<?> type,
                           BiFunction<Principal, Object[], Object> fn)
   {
      boolean appliesTo(Principal p) {
         return owner.isInstance(p);
      }
   }

   // Allow-list of read accessors, the same set as the GraalJS ReadOnlyPrincipalProxy
   // on main. Deliberately absent: every setter and mutator (setOrgId, setGroups,
   // setRoles, setProperty, setParameter, setAlias, setUser, setLocale, updateRoles,
   // ...), and the getters that expose live session state or secrets -- getUser()
   // (the mutable ClientInfo), getSession(), getSessionID(), getSecureID() and
   // getDestinationUserName() (which embeds the secure ID).
   private static final Map<String, Accessor> ACCESSORS = new LinkedHashMap<>();
   // bean-style property name -> allow-listed no-arg getter
   private static final Map<String, String> PROPERTIES = new HashMap<>();

   static {
      add("getName", Principal.class, String.class, (p, a) -> p.getName());
      add("toString", Principal.class, String.class, (p, a) -> p.toString());
      add("getIdentityID", XPrincipal.class, IdentityID.class,
          (p, a) -> copy(x(p).getIdentityID()));
      add("getRoles", XPrincipal.class, IdentityID[].class, (p, a) -> copy(x(p).getRoles()));
      add("getGroups", XPrincipal.class, String[].class, (p, a) -> {
         String[] groups = x(p).getGroups();
         return groups == null ? null : groups.clone();
      });
      add("getOrgId", XPrincipal.class, String.class, (p, a) -> x(p).getOrgId());
      add("getCurrentOrgId", XPrincipal.class, String.class, (p, a) -> x(p).getCurrentOrgId());
      add("getAlias", XPrincipal.class, String.class, (p, a) -> x(p).getAlias());
      add("getFullName", XPrincipal.class, String.class, (p, a) -> x(p).getFullName());
      add("toView", XPrincipal.class, String.class, (p, a) -> x(p).toView());
      add("getProperty", XPrincipal.class, String.class,
          (p, a) -> x(p).getProperty(stringArg(a)));
      add("getPropertyNames", XPrincipal.class, Enumeration.class,
          (p, a) -> copy(x(p).getPropertyNames()));
      add("getParameter", XPrincipal.class, Object.class,
          (p, a) -> copyValue(x(p).getParameter(stringArg(a))));
      add("getParameterNames", XPrincipal.class, Enumeration.class,
          (p, a) -> copy((Enumeration<?>) x(p).getParameterNames()));
      add("getLocale", SRPrincipal.class, Locale.class, (p, a) -> ((SRPrincipal) p).getLocale());
      add("getHost", SRPrincipal.class, String.class, (p, a) -> ((SRPrincipal) p).getHost());
      add("getParameterTS", XPrincipal.class, long.class, (p, a) -> {
         String name = stringArg(a);
         return name == null ? 0L : x(p).getParameterTS(name);
      });

      PROPERTIES.put("name", "getName");
      PROPERTIES.put("identityID", "getIdentityID");
      PROPERTIES.put("roles", "getRoles");
      PROPERTIES.put("groups", "getGroups");
      PROPERTIES.put("orgId", "getOrgId");
      PROPERTIES.put("currentOrgId", "getCurrentOrgId");
      PROPERTIES.put("alias", "getAlias");
      PROPERTIES.put("fullName", "getFullName");
      PROPERTIES.put("propertyNames", "getPropertyNames");
      PROPERTIES.put("parameterNames", "getParameterNames");
      PROPERTIES.put("locale", "getLocale");
      PROPERTIES.put("host", "getHost");
   }

   private final transient Principal principal;
   private final transient Map<String, AccessorFunction> functions = new HashMap<>();
}
