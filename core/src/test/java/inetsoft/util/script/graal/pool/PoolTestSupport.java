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
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;

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

   @FunctionalInterface
   public interface ThrowingRunnable {
      void run() throws Exception;
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
