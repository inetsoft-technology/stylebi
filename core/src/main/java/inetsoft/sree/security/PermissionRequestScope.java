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
package inetsoft.sree.security;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * An opt-in, per-thread scope inside which identity lookups that are pure functions of their
 * arguments are answered once and then reused. It exists for read-only listings that run the same
 * permission check for many entries (such as the repository tree), where every check would
 * otherwise re-read the user, organization and session properties of one and the same principal.
 * <p>
 * Properties of the scope:
 * <ul>
 *    <li>It is only active between {@link #open()} and the close of the returned handle, on the
 *        thread that opened it. With no scope open every {@link #memo} call simply invokes its
 *        supplier, so behavior outside a scope is exactly what it was without this class.</li>
 *    <li>It is nestable: only the outermost handle ends the scope.</li>
 *    <li>It never crosses threads. Work handed to another thread runs without the memo.</li>
 *    <li>Nothing outlives the scope; there is no cache between requests.</li>
 *    <li>Entries are keyed by the identity of the owner (the provider or principal object), the
 *        operation and the argument, so two different providers or two principal instances with
 *        the same name never share an answer.</li>
 *    <li>Values are shared, not copied. Callers inside a scope must treat a returned
 *        {@code User}/{@code Organization} as read-only.</li>
 * </ul>
 */
public final class PermissionRequestScope {
   private PermissionRequestScope() {
   }

   /**
    * Opens a scope on the current thread, or joins the one that is already open.
    */
   public static Handle open() {
      Scope scope = CURRENT.get();

      if(scope == null) {
         scope = new Scope();
         CURRENT.set(scope);
      }

      scope.depth++;
      return new Handle(scope);
   }

   /**
    * Whether a scope is open on the current thread.
    */
   public static boolean isOpen() {
      return CURRENT.get() != null;
   }

   /**
    * Ends any scope on the current thread. Only meant as a backstop for a request that did not
    * close its handle.
    */
   public static void clear() {
      CURRENT.remove();
   }

   /**
    * Answers {@code supplier} once per (owner, operation, argument) while a scope is open, and
    * calls it every time otherwise. A null answer is remembered too; an exception is not.
    */
   @SuppressWarnings("unchecked")
   public static <T> T memo(Object owner, String operation, Object arg, Supplier<T> supplier) {
      Scope scope = CURRENT.get();

      if(scope == null) {
         return supplier.get();
      }

      Key key = new Key(owner, operation, arg);
      Object value = scope.values.get(key);

      if(value == null) {
         value = supplier.get();
         scope.values.put(key, value == null ? NULL : value);
         return (T) value;
      }

      return value == NULL ? null : (T) value;
   }

   /**
    * Forgets everything memoized for {@code owner} in the open scope, if any.
    */
   public static void invalidate(Object owner) {
      Scope scope = CURRENT.get();

      if(scope != null && !scope.values.isEmpty()) {
         scope.values.keySet().removeIf(key -> key.owner == owner);
      }
   }

   /**
    * Closing the handle leaves the scope when it is the outermost one. Closing twice is a no-op.
    */
   public static final class Handle implements AutoCloseable {
      private Handle(Scope scope) {
         this.scope = scope;
      }

      @Override
      public void close() {
         if(closed) {
            return;
         }

         closed = true;

         if(--scope.depth <= 0 && CURRENT.get() == scope) {
            CURRENT.remove();
         }
      }

      private final Scope scope;
      private boolean closed;
   }

   private static final class Scope {
      private final Map<Key, Object> values = new HashMap<>();
      private int depth;
   }

   private static final class Key {
      private Key(Object owner, String operation, Object arg) {
         this.owner = owner;
         this.operation = operation;
         this.arg = arg;
      }

      @Override
      public boolean equals(Object obj) {
         if(!(obj instanceof Key)) {
            return false;
         }

         Key key = (Key) obj;
         return owner == key.owner && operation.equals(key.operation) &&
            Objects.equals(arg, key.arg);
      }

      @Override
      public int hashCode() {
         return 31 * (31 * System.identityHashCode(owner) + operation.hashCode()) +
            Objects.hashCode(arg);
      }

      private final Object owner;
      private final String operation;
      private final Object arg;
   }

   private static final Object NULL = new Object();
   private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();
}
