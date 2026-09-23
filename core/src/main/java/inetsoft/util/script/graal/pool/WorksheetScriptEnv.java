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

import inetsoft.util.audit.ExecutionBreakDownRecord;
import inetsoft.util.profile.ProfileUtils;
import inetsoft.util.script.FormulaContext;
import inetsoft.util.script.ScriptSpan;
import inetsoft.util.script.graal.GraalJavaScriptEngine;
import inetsoft.util.script.graal.GraalJavaScriptEnv;
import inetsoft.util.script.graal.ScriptScope;

import java.util.*;
import java.util.concurrent.locks.Lock;

/**
 * The worksheet script environment of a sandbox in context pool mode (bug #76960, spec §4).
 * Its variables live in an {@link EnvState}; its scripts run on pooled contexts reached through
 * per-thread {@link SlotClaim}s, so no thread ever waits for another thread's context. Every
 * base method that could touch an engine or {@code vars} is overridden (M6); the base class's
 * engine and {@code vars} are never used.
 *
 * <p>Only {@code AssetQuerySandbox.getScriptEnv()} creates it, for a sandbox built with
 * {@code script.ws.contextPool} on. Viewsheet and report envs are unaffected.
 */
public class WorksheetScriptEnv extends GraalJavaScriptEnv {
   public WorksheetScriptEnv(PoolConfig config) {
      this(config, InitSnapshot.capture());
   }

   WorksheetScriptEnv(PoolConfig config, InitSnapshot snapshot) {
      this.config = config;
      this.snapshot = snapshot;
      this.pool = new SlotPool(new Source(), config, metrics);
   }

   /**
    * Monitor-free: makes sure a primary context exists, never waiting (spec §4.2 step 4).
    */
   @Override
   public void init() {
      pool.ensurePrimary();
   }

   /**
    * The contexts are rebuilt from the variables at their next checkout, as main rebuilt its
    * one Context with the same vars.
    */
   @Override
   public void reset() {
      retire();
   }

   /**
    * Retire every context of this env without waiting (spec §4.5). Called by the sandbox when
    * it drops this env; lenses built on it keep working on fresh contexts.
    */
   public void retire() {
      errorCounts.clear();
      pool.retire();
   }

   /**
    * A no-op apart from {@link #init()}, as on main.
    */
   @Override
   public void setParent(Object scope) {
      init();
   }

   /**
    * A no-op apart from {@link #init()}, as on main.
    */
   @Override
   public void addTopLevelParentScope(Object child) {
      init();
   }

   @Override
   public Object compile(String cmd) throws Exception {
      return compile(cmd, false);
   }

   @Override
   public Object compile(String cmd, boolean fieldOnly) throws Exception {
      try(SlotClaim claim = SlotClaim.acquire(pool, false)) {
         return claim.slot().engine().compile(cmd, fieldOnly);
      }
   }

   @Override
   public Object exec(Object script, Object scope, Object rscope, Object target)
      throws Exception
   {
      // for Feature #26586, add javascript execution time record for current report.
      return ProfileUtils.addExecutionBreakDownRecord(target,
         ExecutionBreakDownRecord.JAVASCRIPT_PROCESSING_CYCLE, args -> {
            return doExec(script, scope, rscope);
         });
   }

   private Object doExec(Object script, Object scope, Object rscope) throws Exception {
      ScriptScope scriptScope = (scope instanceof ScriptScope) ? (ScriptScope) scope : null;

      if(scriptScope != null) {
         FormulaContext.pushScope(scriptScope);
      }

      try(SlotClaim claim = SlotClaim.acquire(pool, false)) {
         metrics.executed();
         return claim.slot().engine().exec(script, scope, rscope);
      }
      finally {
         if(scriptScope != null) {
            FormulaContext.popScope();
         }
      }
   }

   @Override
   public void checkFunction(String name, String cmd) throws Exception {
      try(SlotClaim claim = SlotClaim.acquire(pool, false)) {
         claim.slot().engine().checkFunction(name, cmd);
      }
   }

   @Override
   public Object[] getIds(Object id, Object scope, boolean parent) {
      try(SlotClaim claim = SlotClaim.acquire(pool, false)) {
         return claim.slot().engine().getMemberKeys();
      }
   }

   @Override
   public Object[] getDisplayNames(Object id, Object scope, boolean parent) {
      return getIds(id, scope, parent);
   }

   @Override
   public Object[] getNames(Object id, Object scope, boolean parent) {
      return getIds(id, scope, parent);
   }

   /**
    * @return {@code null}, as on main; needs no context.
    */
   @Override
   public Object getScope(Object id, Object scope) {
      return null;
   }

   /**
    * Writes the env's variables only. If this thread has a claimed context of this env, the
    * write is also applied to it now, which never waits because the thread owns it (N2).
    */
   @Override
   public void put(String name, Object obj) {
      state.put(name, obj);
      Slot own = ownSlot();

      if(own != null) {
         own.applyOwn(name, obj);
      }
   }

   /**
    * Reads the env's variables; no worksheet code reads a context's globals back (spec §5.1).
    */
   @Override
   public Object get(String name) {
      return state.get(name);
   }

   @Override
   public void remove(String name) {
      state.remove(name);
      Slot own = ownSlot();

      if(own != null) {
         own.removeOwn(name);
      }
   }

   /**
    * @return {@code null}: a pooled context is never waited for, so callers must not order
    * locks against one (spec §5.1, §7).
    */
   @Override
   public Lock getExecutionLock() {
      return null;
   }

   @Override
   public void setSQL(boolean sql) {
      this.sql = sql;
   }

   @Override
   public boolean isSQL() {
      return sql;
   }

   /**
    * A lazy claim: it takes a context only when a script in the span needs one.
    */
   @Override
   public ScriptSpan openSpan() {
      return SlotClaim.acquire(pool, true);
   }

   /**
    * Claim a context now, for a caller that must hold one across calls (the R connector, the
    * lock-cycle suite). Close it in a try-with-resources.
    */
   public SlotClaim claimSlot() {
      return SlotClaim.acquire(pool, false);
   }

   /**
    * @return the primary context's engine, for diagnostics and tests; it may be retired at
    * any time.
    */
   public GraalJavaScriptEngine primaryEngine() {
      pool.ensurePrimary();
      Slot primary = pool.primary();
      return primary == null ? null : primary.engine();
   }

   public PoolConfig getConfig() {
      return config;
   }

   public PoolMetrics getMetrics() {
      return metrics;
   }

   /**
    * @return the version of this env's variables, which changes on every put or remove.
    */
   public long getStateVersion() {
      return state.snapshot().version();
   }

   EnvState state() {
      return state;
   }

   Map<Object, Integer> errorCounts() {
      return errorCounts;
   }

   SlotPool pool() {
      return pool;
   }

   private Slot ownSlot() {
      SlotClaim claim = SlotClaim.current(pool);
      return claim == null ? null : claim.peekSlot();
   }

   private final class Source implements SlotSource {
      @Override
      public EnvState state() {
         return state;
      }

      @Override
      public boolean isSQL() {
         return sql;
      }

      @Override
      public Slot create(long epoch) throws Exception {
         return Slot.create(snapshot, state.snapshot(), epoch, sql, errorCounts, metrics);
      }
   }

   private final PoolConfig config;
   private final InitSnapshot snapshot;
   private final EnvState state = new EnvState();
   private final PoolMetrics metrics = new PoolMetrics();
   // spec §6.9: owned by the env for all its contexts, cleared on retire (M3)
   private final Map<Object, Integer> errorCounts =
      Collections.synchronizedMap(new WeakHashMap<>());
   private final SlotPool pool;
}
