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

import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.util.script.ScriptEnv;
import inetsoft.util.script.graal.ScriptScope;
import org.graalvm.polyglot.Value;
import org.mockito.Mockito;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Shared helpers of the worksheet context pool tests (bug #76960).
 */
public final class PoolTestSupport {
   private PoolTestSupport() {
   }

   /**
    * A pooled env with default settings and an empty library, not tied to a server.
    */
   public static WorksheetScriptEnv env() {
      return env(PoolConfig.defaults(), Map.of());
   }

   public static WorksheetScriptEnv env(PoolConfig config, Map<String, String> library) {
      return new WorksheetScriptEnv(config, new InitSnapshot("org1", library));
   }

   /**
    * Compile and run {@code js} on {@code env} with no scope.
    */
   public static Object run(ScriptEnv env, String js) throws Exception {
      return env.exec(env.compile(js), null, null, null);
   }

   /**
    * Run {@code body} on the calling thread while another thread holds a claimed context of
    * {@code env}.
    */
   public static void whileHeldElsewhere(WorksheetScriptEnv env, ThrowingRunnable body)
      throws Exception
   {
      ExecutorService executor = Executors.newSingleThreadExecutor();
      CountDownLatch held = new CountDownLatch(1);
      CountDownLatch done = new CountDownLatch(1);

      try {
         Future<?> holder = executor.submit(() -> {
            try(SlotClaim claim = env.claimSlot()) {
               held.countDown();
               done.await(30, TimeUnit.SECONDS);
            }

            return null;
         });

         if(!held.await(10, TimeUnit.SECONDS)) {
            fail("the holder did not claim a context");
         }

         body.run();
         done.countDown();
         holder.get(10, TimeUnit.SECONDS);
      }
      finally {
         done.countDown();
         executor.shutdownNow();
      }
   }

   /**
    * A sandbox that runs the real sandbox methods without a worksheet (as
    * ConditionFilterBoxResetLockTest does), in the given pool mode.
    */
   public static AssetQuerySandbox poolBox(boolean pool) throws Exception {
      AssetQuerySandbox box = Mockito.mock(AssetQuerySandbox.class, Mockito.CALLS_REAL_METHODS);
      setField(box, "lock", new Object());
      setField(box, "scriptPoolMode", pool);
      return box;
   }

   static void setField(AssetQuerySandbox box, String name, Object value) throws Exception {
      Field field = AssetQuerySandbox.class.getDeclaredField(name);
      field.setAccessible(true);
      field.set(box, value);
   }

   /**
    * Make every read of a lens-owned var's script object at a batch end throw what
    * {@code fault} supplies, or read normally again for {@code null} (Testing #77123, B1
    * residual). Set by reflection: the seam is package-private and not part of the API.
    */
   public static void failOwnedValueReads(java.util.function.Supplier<? extends Throwable> fault)
      throws Exception
   {
      Class<?> codec;

      try {
         codec = Class.forName("inetsoft.util.script.graal.pool.OwnedValueCodec");
      }
      catch(ClassNotFoundException ex) {
         if(fault == null) {
            return;
         }

         throw ex;
      }

      Field field = codec.getDeclaredField("readFault");
      field.setAccessible(true);
      field.set(null, fault);
   }

   /**
    * A counter of the env's pool metrics by name ({@code "HandOffs"} reads getHandOffs()), or
    * -1 if this build has no such counter (Testing #77123, B1 residual part 2: read by
    * reflection so the tests also run, and fail, on a build without it).
    */
   public static long metric(WorksheetScriptEnv env, String name) {
      try {
         return ((Number) PoolMetrics.class.getMethod("get" + name).invoke(env.getMetrics()))
            .longValue();
      }
      catch(ReflectiveOperationException ex) {
         return -1;
      }
   }

   /**
    * Hand off every idle home of the env's pool now, as a take-over or expiry would.
    *
    * @return the homes handed off, or -1 if this build has no homes.
    */
   public static int handOffIdleHomes(WorksheetScriptEnv env) {
      return (int) poolCall(env, "handOffIdleHomes", -1);
   }

   /**
    * @return the homes of the env's pool (slots formula tables' objects live on), or -1.
    */
   public static int homes(WorksheetScriptEnv env) {
      return (int) poolCall(env, "homes", -1);
   }

   /**
    * @return the exclusive homes of the env's pool, or -1.
    */
   public static int exclusiveHomes(WorksheetScriptEnv env) {
      return (int) poolCall(env, "exclusiveHomes", -1);
   }

   /**
    * Run the env's evictor as if the clock read {@code now}.
    */
   public static void evictIdle(WorksheetScriptEnv env, long now) {
      env.pool().evictIdle(now);
   }

   /**
    * Drop every tenant of every slot of the env's pool from the slot's weak tenant map without
    * the pool's purge, as the collection of those formula tables does (Testing #77123, finding
    * G1): a home whose tenants were all collected stays in the pool's homes until a purge.
    */
   public static void collectTenants(WorksheetScriptEnv env) {
      for(Slot slot : env.pool().slots()) {
         slot.clearTenants();
      }
   }

   /**
    * @return the slots of the env's pool that a pool thread is probing now (Testing #77123,
    * finding G1), or 0 if this build has no probes.
    */
   public static int probedSlots(WorksheetScriptEnv env) {
      int n = 0;

      for(Slot slot : env.pool().slots()) {
         try {
            java.lang.reflect.Method m = Slot.class.getDeclaredMethod("isProbed");
            m.setAccessible(true);

            if((Boolean) m.invoke(slot)) {
               n++;
            }
         }
         catch(NoSuchMethodException ex) {
            return 0;
         }
         catch(ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
         }
      }

      return n;
   }

   /**
    * Run {@code hook} on a puller's thread at each retry of its pull while a pool thread
    * probes the home (Testing #77123, finding G1); {@code null} to clear it.
    */
   public static void pullSpinHook(java.util.function.Consumer<Object> hook) {
      OwnedValueCodec.pullSpinHook = hook == null ? null : hook::accept;
   }

   /**
    * Set a test hook of the env's pool: {@code "handOffHook"}, {@code "takeOverHook"},
    * {@code "plainTakeHook"}, {@code "closeIdleHook"} or {@code "giveBackHook"}; {@code null}
    * clears it.
    */
   public static void poolHook(WorksheetScriptEnv env, String name,
                               java.util.function.Consumer<Object> hook)
   {
      java.util.function.Consumer<Slot> h = hook == null ? null : hook::accept;

      switch(name) {
      case "handOffHook" -> env.pool().handOffHook = h;
      case "takeOverHook" -> env.pool().takeOverHook = h;
      case "plainTakeHook" -> env.pool().plainTakeHook = h;
      case "closeIdleHook" -> env.pool().closeIdleHook = h;
      case "giveBackHook" -> env.pool().giveBackHook = h;
      default -> throw new IllegalArgumentException(name);
      }
   }

   /**
    * @return whether {@code slot} (from {@link #currentSlot} or {@link #slots}) is closed.
    */
   public static boolean isClosed(Object slot) {
      return ((Slot) slot).isClosed();
   }

   /**
    * @return the slots of the env's pool now, the primary first.
    */
   public static List<Object> slots(WorksheetScriptEnv env) {
      return new ArrayList<>(env.pool().slots());
   }

   /**
    * @return the exclusive homes of this node, or -1.
    */
   public static int nodeHomes() {
      try {
         return ((Number) PoolMetrics.class.getMethod("nodeHomes").invoke(null)).intValue();
      }
      catch(ReflectiveOperationException ex) {
         return -1;
      }
   }

   private static Object poolCall(WorksheetScriptEnv env, String method, Object missing) {
      try {
         java.lang.reflect.Method m = SlotPool.class.getDeclaredMethod(method);
         m.setAccessible(true);
         return m.invoke(env.pool());
      }
      catch(NoSuchMethodException ex) {
         return missing;
      }
      catch(ReflectiveOperationException ex) {
         throw new IllegalStateException(ex);
      }
   }

   /**
    * A host object a formula calls from getters, traps and setters that must never run: it
    * counts the calls.
    */
   public static final class Probe {
      public void hit() {
         hits.incrementAndGet();
      }

      public int hits() {
         return hits.get();
      }

      private final java.util.concurrent.atomic.AtomicInteger hits =
         new java.util.concurrent.atomic.AtomicInteger();
   }

   /**
    * Swap the slot's clean helper for one whose clean is {@code factory} (a guest function
    * source, applied to {@code args}), keeping its other handles. Caller holds the slot. The
    * one place that builds a CleanHelper reflectively (final review I2): a change to its
    * handles or constructor is fixed here.
    */
   static void injectClean(Slot slot, String factory, Object... args) throws Exception {
      swapHandles(slot, factory, args, true);
   }

   /**
    * Swap the slot's clean helper for one whose paranoid check ({@link CleanHelper#verify})
    * is {@code factory} (a guest function source) applied to the real check's handle and then
    * to {@code args}, keeping its other handles: {@code factory} wraps the real check, e.g. to
    * block in a host call before it (bug #77568). Caller holds the slot.
    */
   static void injectVerify(Slot slot, String factory, Object... args) throws Exception {
      swapHandles(slot, factory, args, false);
   }

   private static void swapHandles(Slot slot, String factory, Object[] args, boolean clean)
      throws Exception
   {
      Field field = Slot.class.getDeclaredField("cleaner");
      field.setAccessible(true);
      CleanHelper real = (CleanHelper) field.get(slot);
      Field cleanField = CleanHelper.class.getDeclaredField("clean");
      Field expect = CleanHelper.class.getDeclaredField("expect");
      Field forget = CleanHelper.class.getDeclaredField("forget");
      Field verifyField = CleanHelper.class.getDeclaredField("verify");
      cleanField.setAccessible(true);
      expect.setAccessible(true);
      forget.setAccessible(true);
      verifyField.setAccessible(true);
      Value made = slot.engine().context().eval("js", factory);
      Value cleanHandle = (Value) cleanField.get(real);
      Value verifyHandle = (Value) verifyField.get(real);

      if(clean) {
         cleanHandle = args.length > 0 ? made.execute(args) : made;
      }
      else {
         Object[] all = new Object[args.length + 1];
         all[0] = verifyHandle;
         System.arraycopy(args, 0, all, 1, args.length);
         verifyHandle = made.execute(all);
      }

      Constructor<CleanHelper> ctor = CleanHelper.class.getDeclaredConstructor(
         Value.class, Value.class, Value.class, Value.class);
      ctor.setAccessible(true);
      field.set(slot, ctor.newInstance(cleanHandle, expect.get(real), forget.get(real),
                                       verifyHandle));
   }

   @FunctionalInterface
   public interface ThrowingRunnable {
      void run() throws Exception;
   }

   /**
    * @return the context this thread's claim on {@code env} holds now, or {@code null}; for
    * {@link #holdElsewhere}.
    */
   public static Object currentSlot(WorksheetScriptEnv env) {
      SlotClaim claim = SlotClaim.current(env.pool());
      return claim == null ? null : claim.peekSlot();
   }

   /**
    * Hold {@code slot} (from {@link #currentSlot}, idle now) on {@code executor}'s thread, as
    * the pool's evictor or a take-over does for a moment, until the returned task is run.
    */
   public static Runnable holdElsewhere(Object slot, ExecutorService executor) throws Exception {
      Slot held = (Slot) slot;
      assertTrue(executor.submit(held::tryAcquire).get(10, TimeUnit.SECONDS), "not idle");
      return () -> {
         try {
            executor.submit(held::release).get(10, TimeUnit.SECONDS);
         }
         catch(Exception ex) {
            throw new IllegalStateException(ex);
         }
      };
   }

   /**
    * A host object a script fires once: its task runs at the first {@link #fire} only.
    */
   public static final class Hook {
      public boolean fire() {
         Runnable run = task;
         task = null;

         if(run != null) {
            run.run();
         }

         return true;
      }

      public volatile Runnable task;
   }

   /**
    * A host object a script calls back into its own env through.
    */
   public static final class Callback {
      public Callback(ScriptEnv env) {
         this.env = env;
      }

      public int put(String name, Object value) {
         env.put(name, value);
         return 1;
      }

      public Object nested(String js) throws Exception {
         return run(env, js);
      }

      public void reset() {
         env.reset();
      }

      /**
       * Park the calling script until {@link #release} counts down.
       */
      public void block() throws InterruptedException {
         entered.countDown();
         release.await(30, TimeUnit.SECONDS);
      }

      public final CountDownLatch entered = new CountDownLatch(1);
      public final CountDownLatch release = new CountDownLatch(1);
      private final ScriptEnv env;
   }

   /**
    * Host methods typed Object/Map/List that keep or mutate their argument.
    */
   public static final class Taker {
      public Object take(Object value) {
         last = value;
         return value;
      }

      public Object takeMap(Map<?, ?> value) {
         last = value;
         return value;
      }

      public Object takeList(List<?> value) {
         last = value;
         return value;
      }

      public void mutateList(List<Object> value) {
         value.add(99);
      }

      public void mutateMap(Map<String, Object> value) {
         value.put("z", 1);
      }

      public volatile Object last;
   }

   /**
    * A plain host scope.
    */
   public static final class MapScope implements ScriptScope {
      @Override
      public Object getMember(String name) {
         return values.get(name);
      }

      @Override
      public boolean hasMember(String name) {
         return values.containsKey(name);
      }

      @Override
      public void putMember(String name, Object value) {
         values.put(name, value);
      }

      @Override
      public Object[] getMemberKeys() {
         return values.keySet().toArray();
      }

      public final Map<String, Object> values = new ConcurrentHashMap<>();
   }
}
