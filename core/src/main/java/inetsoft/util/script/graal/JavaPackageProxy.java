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

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.util.function.Predicate;

/**
 * A lazy Java package navigator (Rhino {@code NativeJavaPackage} analogue) used
 * by the {@link LegacyJavaShim}. Member access either descends into a deeper
 * package or resolves to a {@link JavaClassProxy} when the path names a loadable,
 * allow-listed class. No classpath scanning — resolution is purely lazy.
 *
 * @see LegacyJavaShim#navigate(String, String, Predicate)
 */
public final class JavaPackageProxy implements ProxyObject {
   /** The accumulated package prefix; empty string for the {@code Packages} root. */
   private final String prefix;
   private final Predicate<String> filter;
   private final Context context;

   public JavaPackageProxy(String prefix, Predicate<String> filter, Context context) {
      this.prefix = prefix;
      this.filter = filter;
      this.context = context;
   }

   String prefix() {
      return prefix;
   }

   @Override
   public Object getMember(String name) {
      return LegacyJavaShim.navigate(prefix, name, filter, context);
   }

   @Override
   public boolean hasMember(String name) {
      // Exclude JS-intrinsic names (then, toString, constructor, etc.) so package
      // proxies are not mistaken for thenables by GraalJS's Promise machinery.
      return LegacyJavaShim.isEnabled() && LegacyJavaShim.isResolvableName(name);
   }

   @Override
   public Object getMemberKeys() {
      // packages cannot be enumerated without a classpath scan.
      return new String[0];
   }

   @Override
   public void putMember(String name, Value value) {
      // package roots are read-only.
   }
}
