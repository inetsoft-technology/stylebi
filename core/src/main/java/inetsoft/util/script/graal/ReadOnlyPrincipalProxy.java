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
package inetsoft.util.script.graal;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.SRPrincipal;
import inetsoft.uql.XPrincipal;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.security.Principal;
import java.util.*;
import java.util.function.BiFunction;

/**
 * Read-only view of a {@link Principal} handed to a script.
 *
 * <p>Scripts receive the session principal (e.g. {@code parameter.__principal__})
 * so they can inspect the viewer's identity. Handed over as a raw host object, its
 * public setters ({@code setOrgId}, {@code setGroups}, {@code setProperty}, ...) and
 * the mutable internals returned by its getters ({@code getGroups()}/
 * {@code getRoles()} arrays, the {@code ClientInfo} holding the user identity) let a
 * script change the live identity that the rest of the session -- permission checks,
 * the query sandbox, VPM -- trusts. GraalJS {@code HostAccess} can deny a whole class
 * but not individual methods, so the principal is instead presented through this
 * proxy, which exposes only an allow-list of read accessors and returns copies of
 * anything mutable. (Bug #77255, Bug #77256)
 *
 * <p>Java code still receives the real principal:
 * {@link ScriptValueConverter#toHost} unwraps the proxy when a script passes it back
 * to one of our script functions ({@code ScriptFunction}, e.g. {@code runQuery}) or
 * stores it in a scope.
 *
 * <p><b>Known limitation:</b> a script that passes {@code parameter.__principal__}
 * straight into a <em>raw</em> host constructor or method that declares a
 * {@code Principal} parameter (e.g. {@code new Java.type('...Principal')(p)}) goes
 * through GraalJS interop, not {@link ScriptValueConverter#toHost}, so it fails
 * overload resolution rather than receiving the target — the same shape as
 * {@link HostBeanProxy}'s host-method limitation. This is intentional: it keeps a
 * script from handing the live principal to arbitrary host code. The supported path
 * is our own script functions, which unwrap it.
 *
 * <p>Unwrapping the proxy for raw host calls (e.g. a GraalJS target-type mapping to
 * {@code Principal} or {@code XPrincipal}) is not supported because host return
 * values are never re-wrapped: any reachable setter/getter pair hands the live
 * principal straight back to the script, with all of its setters. Two concrete
 * round trips are {@code VpmScope.setUser(Principal)}/{@code getUser()} and
 * {@code AssetQuerySandbox.setVPMUser(XPrincipal)}/{@code getVPMUser()} (the latter
 * also mutates the principal it is given). Either would reopen Bug #77255/#77256.
 * (Bug #77361)
 */
public final class ReadOnlyPrincipalProxy implements ProxyObject {
   private ReadOnlyPrincipalProxy(Principal target) {
      this.target = Objects.requireNonNull(target);
   }

   /**
    * Wrap a principal, reusing an existing wrapper for the same principal so script
    * reference equality ({@code ===}) is preserved.
    */
   public static ReadOnlyPrincipalProxy wrap(Principal target) {
      return WRAPPERS.get(target, ReadOnlyPrincipalProxy::new);
   }

   /** The wrapped principal. */
   public Principal getTarget() {
      return target;
   }

   @Override
   public Object getMember(String key) {
      Accessor accessor = ACCESSORS.get(key);

      if(accessor == null || !accessor.appliesTo(target)) {
         return null;
      }

      return (ProxyExecutable) args -> accessor.fn.apply(target, args);
   }

   @Override
   public Object getMemberKeys() {
      List<Object> keys = new ArrayList<>();

      for(Map.Entry<String, Accessor> e : ACCESSORS.entrySet()) {
         if(e.getValue().appliesTo(target)) {
            keys.add(e.getKey());
         }
      }

      return ProxyArray.fromList(keys);
   }

   @Override
   public boolean hasMember(String key) {
      Accessor accessor = ACCESSORS.get(key);
      return accessor != null && accessor.appliesTo(target);
   }

   @Override
   public void putMember(String key, Value value) {
      throw new UnsupportedOperationException("The principal is read-only in script");
   }

   @Override
   public boolean removeMember(String key) {
      throw new UnsupportedOperationException("The principal is read-only in script");
   }

   @Override
   public String toString() {
      return target.toString();
   }

   private static String stringArg(Value[] args) {
      if(args.length == 0) {
         return null;
      }

      Object value = ScriptValueConverter.toHost(args[0]);
      return value == null ? null : String.valueOf(value);
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

   // A principal parameter value can be any mutable object a script must not be
   // able to change in place: an array, a Collection, a Map or a Date. Return a
   // copy of each of those; immutable scalars (String/Number/Boolean/Character)
   // and anything unrecognized are returned as-is. (An unrecognized mutable type
   // is not defensively copied, but the principal's parameters are populated from
   // request/login values, which are scalars, arrays and collections in practice.)
   private static Object copyValue(Object value) {
      if(value == null) {
         return null;
      }

      if(value.getClass().isArray()) {
         int len = java.lang.reflect.Array.getLength(value);
         Object copy = java.lang.reflect.Array.newInstance(
            value.getClass().getComponentType(), len);
         System.arraycopy(value, 0, copy, 0, len);
         return copy;
      }

      if(value instanceof Map<?, ?> map) {
         return new LinkedHashMap<>(map);
      }

      if(value instanceof Collection<?> col) {
         return new ArrayList<>(col);
      }

      if(value instanceof Date date) {
         return date.clone();
      }

      return value;
   }

   private static void add(String name, Class<?> owner,
                           BiFunction<Principal, Value[], Object> fn)
   {
      ACCESSORS.put(name, new Accessor(owner, fn));
   }

   private static XPrincipal x(Principal p) {
      return (XPrincipal) p;
   }

   private record Accessor(Class<?> owner, BiFunction<Principal, Value[], Object> fn) {
      boolean appliesTo(Principal p) {
         return owner.isInstance(p);
      }
   }

   // Allow-list of read accessors. Deliberately absent: every setter/mutator, and
   // the getters that expose live session state or secrets -- getUser() (the
   // ClientInfo holding the user identity), getSession(), getSessionID(),
   // getSecureID().
   private static final Map<String, Accessor> ACCESSORS = new LinkedHashMap<>();

   static {
      add("getName", Principal.class, (p, a) -> p.getName());
      add("toString", Principal.class, (p, a) -> p.toString());
      add("getIdentityID", XPrincipal.class, (p, a) -> copy(x(p).getIdentityID()));
      add("getRoles", XPrincipal.class, (p, a) -> copy(x(p).getRoles()));
      add("getGroups", XPrincipal.class, (p, a) -> x(p).getGroups().clone());
      add("getOrgId", XPrincipal.class, (p, a) -> x(p).getOrgId());
      add("getCurrentOrgId", XPrincipal.class, (p, a) -> x(p).getCurrentOrgId());
      add("getAlias", XPrincipal.class, (p, a) -> x(p).getAlias());
      add("getFullName", XPrincipal.class, (p, a) -> x(p).getFullName());
      add("toView", XPrincipal.class, (p, a) -> x(p).toView());
      add("getProperty", XPrincipal.class, (p, a) -> x(p).getProperty(stringArg(a)));
      add("getPropertyNames", XPrincipal.class, (p, a) -> new HashSet<>(x(p).getPropertyNames()));
      add("getParameter", XPrincipal.class,
          (p, a) -> copyValue(x(p).getParameter(stringArg(a))));
      add("getParameterNames", XPrincipal.class,
          (p, a) -> new HashSet<>(x(p).getParameterNames()));
      add("getLocale", SRPrincipal.class, (p, a) -> ((SRPrincipal) p).getLocale());
      add("getHost", SRPrincipal.class, (p, a) -> ((SRPrincipal) p).getHost());
      add("getParameterTS", XPrincipal.class, (p, a) -> {
         String name = stringArg(a);
         return name == null ? 0L : x(p).getParameterTS(name);
      });
   }

   // Weak identity-keyed intern table, as in HostBeanProxy.
   private static final Cache<Principal, ReadOnlyPrincipalProxy> WRAPPERS =
      Caffeine.newBuilder().weakKeys().weakValues().build();

   private final Principal target;
}
