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
package inetsoft.util.script.graal.pool;

import inetsoft.util.script.graal.GraalJavaScriptEngine;
import inetsoft.util.script.graal.ScriptTimeoutGuard;
import org.graalvm.polyglot.Context;

import java.time.Duration;
import java.util.Map;

/**
 * The engine of one pooled worksheet context (bug #76960): built from its env's init snapshot,
 * counting script errors in its env's map, and dooming its slot when an interrupt could not
 * stop an exec.
 */
final class WsEngine extends GraalJavaScriptEngine {
   WsEngine(InitSnapshot snapshot, Map<Object, Integer> errorCounts) {
      this.snapshot = snapshot;
      this.errorCounts = errorCounts;
   }

   void bind(Slot slot) {
      this.slot = slot;
   }

   Context context() {
      return context;
   }

   ScriptTimeoutGuard.Guard guard(Duration timeout) {
      return timeoutGuard.guard(context, timeout);
   }

   /**
    * Globals were deleted by a clean; drop the name resolution cache.
    */
   void globalsCleaned() {
      invalidateGlobalBindings();
   }

   @Override
   protected Map<String, String> librarySources() {
      return snapshot.getLibrary();
   }

   @Override
   protected Map<Object, Integer> errorCounts() {
      return errorCounts;
   }

   @Override
   protected void resetErrorCounts() {
      // the env owns the counts and clears them on retire (spec §6.9)
   }

   @Override
   protected void onInterruptTimeout() {
      Slot owner = slot;

      if(owner != null) {
         owner.doom();
      }
   }

   private final InitSnapshot snapshot;
   private final Map<Object, Integer> errorCounts;
   private volatile Slot slot;
}
