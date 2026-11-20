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

   /** Remove a named member. Returns true if the member existed and was removed. */
   default boolean removeMember(String name) {
      return false;
   }

   /** All member names exposed by this scope. */
   Object[] getMemberKeys();

   /**
    * Bug #81929: true if a script read of an absent member should give JS null rather
    * than undefined, as Rhino did by calling get() for any name. GraalJS reads a member
    * only when {@link ScopeProxy#hasMember} is true, so {@link ScopeProxy} then reports
    * every name as present (except "valueOf") and lets {@link #getMember} return null.
    * The Java {@link #hasMember} contract is unchanged. Side effect: in a script,
    * {@code 'x' in obj} is true for any name.
    */
   default boolean isMissingMemberNull() {
      return false;
   }

   /** Next scope in the lookup chain, or null. */
   default ScriptScope getParentScope() {
      return null;
   }

   /**
    * Bug #77866: the holder of the top-level var stores of the scripts run on this scope,
    * which this scope keeps for as long as it lives, or null to let each engine keep them
    * (until the engine is re-initialized, closed or dropped). A scope made for one
    * evaluation should return one of its own: see {@link ScopeLocals}.
    */
   default ScopeLocals getScopeLocals() {
      return null;
   }
}
