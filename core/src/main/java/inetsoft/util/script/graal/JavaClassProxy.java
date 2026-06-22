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

import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.*;

/**
 * A resolved Java class leaf produced by the {@link LegacyJavaShim}. Wraps the
 * GraalVM host type (from {@code Java.type}) so that static members and {@code
 * new}-construction delegate to GraalVM's own reflection and overload
 * resolution, while adding Rhino's no-{@code new} construction: calling the
 * class as a function ({@code java.awt.Color(0xaed581)}) instantiates it.
 */
public final class JavaClassProxy implements ProxyObject, ProxyExecutable, ProxyInstantiable {
   private final Value hostType;
   private final String className;

   public JavaClassProxy(Value hostType, String className) {
      this.hostType = hostType;
      this.className = className;
   }

   String className() {
      return className;
   }

   @Override
   public Object getMember(String name) {
      // static fields, static methods, and nested types delegate to the host type.
      return hostType.hasMember(name) ? hostType.getMember(name) : null;
   }

   @Override
   public boolean hasMember(String name) {
      return hostType.hasMember(name);
   }

   @Override
   public Object getMemberKeys() {
      return hostType.getMemberKeys().toArray(new String[0]);
   }

   @Override
   public void putMember(String name, Value value) {
      // static field assignment, when the host type permits it.
      if(hostType.hasMember(name)) {
         hostType.putMember(name, value);
      }
   }

   @Override
   public Object execute(Value... arguments) {
      // Rhino allowed construction without `new`: java.awt.Color(0xaed581).
      return hostType.newInstance((Object[]) arguments);
   }

   @Override
   public Object newInstance(Value... arguments) {
      return hostType.newInstance((Object[]) arguments);
   }
}
