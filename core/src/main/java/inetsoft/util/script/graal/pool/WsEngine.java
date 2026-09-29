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
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

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

   /**
    * An env variable was set on this context directly; the let/const reset must never
    * clear it (bug #77181).
    */
   void hostGlobal(String name) {
      markHostGlobal(name);
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
      PoolMetrics.interruptTimedOut();
      Slot owner = slot;

      if(owner != null) {
         owner.doom();
      }
   }

   /**
    * The dedicated Engine of all pooled worksheet contexts (spec §6.8), so their HostAccess
    * leaves the viewsheet and report engines untouched.
    */
   static final Engine SHARED_WS_ENGINE = Engine.newBuilder()
      .allowExperimentalOptions(true)
      .option("engine.WarnInterpreterOnly", "false")
      .build();

   @Override
   protected Engine polyglotEngine() {
      return SHARED_WS_ENGINE;
   }

   @Override
   protected HostAccess hostAccessPolicy() {
      return WsHostAccess.get();
   }

   /**
    * Marks this context as executing on the thread, for the host-boundary conversions and
    * per-context host state; restored on exit so nesting is correct.
    */
   @Override
   protected Object enterExecContext(Object script) {
      return WsExecContext.enter(slot, script);
   }

   @Override
   protected void exitExecContext(Object token) {
      WsExecContext.exit((Slot) token);
   }

   /**
    * A value of another context installed at init is marked foreign (bug #76960, §14.11).
    */
   @Override
   protected void initScope(Map<String, Object> vars) {
      // the intrinsic Date of the fresh context, before any library or user script ran:
      // a lens-owned Date var is rebuilt with it (Testing #77123, B1 residual)
      Value date = context.getBindings("js").getMember("Date");
      // getTime.call bound once: calling it runs the builtin with the value as this, which
      // reads the Date internal slot (a TypeError for any other object) and no script code
      Value getTime = date.getMember("prototype").getMember("getTime");
      dateGetTime = getTime.getMember("call").invokeMember("bind", getTime);
      dateConstructor = date;
      super.initScope(WsValueCopier.markForeign(vars, context));
   }

   /**
    * A value of another context put here is marked foreign (bug #76960, §14.11).
    */
   @Override
   public void put(String name, Object value) {
      super.put(name, WsValueCopier.markForeign(value, context));
   }

   /**
    * @return the Date constructor of this engine's context as created, before any script ran.
    */
   Value dateConstructor() {
      return dateConstructor;
   }

   /**
    * @return the intrinsic {@code Date.prototype.getTime}, bound as {@code getTime.call}:
    *         {@code dateGetTime().execute(v)} gives the time value of the Date {@code v} and
    *         throws a TypeError for anything else, running no script code.
    */
   Value dateGetTime() {
      return dateGetTime;
   }

   private volatile Value dateConstructor;
   private volatile Value dateGetTime;
   private final InitSnapshot snapshot;
   private final Map<Object, Integer> errorCounts;
   private volatile Slot slot;
}
