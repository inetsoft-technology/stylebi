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

/**
 * A scriptable scope object. Replaces the use of Rhino's Scriptable for
 * objects that expose named members to scripts. Implementations are bridged
 * to GraalJS by {@link ScopeProxy}.
 */
public interface ScriptScope {
   /** Get a named member, or null/Undefined sentinel if absent. */
   Object getMember(String name);

   /** True if this scope defines the named member. */
   boolean hasMember(String name);

   /** Define or replace a named member. */
   void putMember(String name, Object value);

   /** Remove a named member (no-op if unsupported). */
   default void removeMember(String name) {
   }

   /** All member names exposed by this scope. */
   Object[] getMemberKeys();

   /** Next scope in the lookup chain, or null. */
   default ScriptScope getParentScope() {
      return null;
   }
}
