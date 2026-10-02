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
package inetsoft.util;

import org.junit.jupiter.api.*;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.concurrent.*;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #76975: {@link ConfigurationContext#awaitSpringBean} must not park on the singleton lock
 * that the thread refreshing the context holds, so that thread can wait for the caller. The
 * refreshing thread requests a bean from {@code onRefresh}, as {@code getWebServerFactory} does
 * in the server, so Spring uses strict singleton locking and holds the lock throughout.
 */
@Tag("core")
class ConfigurationContextAwaitSpringBeanTest {
   @BeforeEach
   void saveContext() {
      previousContext = ConfigurationContext.getContext().getApplicationContext();
   }

   @AfterEach
   void restoreContext() {
      ConfigurationContext.getContext().setApplicationContext(previousContext);
   }

   /**
    * The singleton is created by the refreshing thread after the caller asked for it, and the
    * refreshing thread then waits for the caller while it still holds the lock.
    */
   @Test
   void waitsForSingletonCreatedLaterByRefreshingThread() throws Exception {
      CompletableFuture<Engine> worker = new CompletableFuture<>();
      CompletableFuture<Engine> created = new CompletableFuture<>();

      try(StartupContext context = new StartupContext(() -> {
         Thread thread = startWorker(worker);
         awaitWaiting(thread, worker);
         created.complete(ConfigurationContext.getContext().getApplicationContext()
                             .getBean(Engine.class));
         return waitFor(worker);
      }))
      {
         context.registerBean("engine", Engine.class, Engine::new, bd -> bd.setLazyInit(true));
         refresh(context);

         assertSame(created.get(), context.getBean(STARTER),
                    "the waiting thread did not get the singleton the refreshing thread created");
      }
   }

   /**
    * The caller asks for the singleton whose creation started it, as a service task can while
    * the refreshing thread is still in the {@code IgniteCluster} constructor.
    */
   @Test
   void waitsForSingletonWhoseCreationStartedTheCaller() throws Exception {
      CompletableFuture<Engine> worker = new CompletableFuture<>();

      try(StartupContext context = new StartupContext(() -> {
         Engine engine = ConfigurationContext.getContext().getApplicationContext()
            .getBean(Engine.class);
         assertSame(engine, waitFor(worker),
                    "the waiting thread did not get the singleton that started it");
         return engine;
      }))
      {
         context.registerBean("engine", Engine.class, () -> {
            Thread thread = startWorker(worker);
            awaitWaiting(thread, worker);
            return new Engine();
         }, bd -> bd.setLazyInit(true));
         refresh(context);
      }
   }

   /**
    * After the refresh, a lazy singleton that nobody created yet is looked up, and so created,
    * at once rather than after the timeout.
    */
   @Test
   void looksUpUncreatedLazySingletonAfterRefresh() throws Exception {
      try(AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
         context.registerBean("engine", Engine.class, Engine::new, bd -> bd.setLazyInit(true));
         ConfigurationContext.getContext().setApplicationContext(context);
         context.refresh();
         assertFalse(context.getBeanFactory().containsSingleton("engine"));

         long start = System.nanoTime();
         Engine engine = CompletableFuture.supplyAsync(
               () -> ConfigurationContext.getContext()
                  .awaitSpringBean(Engine.class, 60, TimeUnit.SECONDS))
            .get(10, TimeUnit.SECONDS);
         long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

         assertSame(context.getBean(Engine.class), engine);
         assertTrue(elapsed < 5000L, () -> "the lookup waited " + elapsed + " ms");
      }
   }

   private static void refresh(StartupContext context) {
      ConfigurationContext.getContext().setApplicationContext(context);
      context.refresh();
   }

   private static Thread startWorker(CompletableFuture<Engine> result) {
      Thread thread = new Thread(() -> {
         try {
            result.complete(ConfigurationContext.getContext()
                               .awaitSpringBean(Engine.class, 30, TimeUnit.SECONDS));
         }
         catch(Throwable e) {
            result.completeExceptionally(e);
         }
      }, "cluster-service-bug76975");
      thread.setDaemon(true);
      thread.start();
      return thread;
   }

   /**
    * Waits until the worker waits (on anything) or is done, so the refreshing thread continues
    * only after the worker asked for the bean.
    */
   private static void awaitWaiting(Thread thread, Future<?> result) {
      long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);

      while(System.nanoTime() < end && !result.isDone()) {
         Thread.State state = thread.getState();

         if(state == Thread.State.WAITING || state == Thread.State.TIMED_WAITING) {
            return;
         }

         Thread.onSpinWait();
      }
   }

   /**
    * Waits for the worker as the refreshing thread waits for a service task: bounded, while it
    * holds the singleton lock.
    */
   private static Engine waitFor(Future<Engine> worker) {
      try {
         return worker.get(5, TimeUnit.SECONDS);
      }
      catch(TimeoutException e) {
         throw new AssertionError(
            "the waiting thread did not get the bean while the refreshing thread held the " +
               "singleton lock", e);
      }
      catch(InterruptedException | ExecutionException e) {
         throw new AssertionError(e);
      }
   }

   /**
    * Requests {@link #STARTER} from {@code onRefresh}, before the configuration is frozen.
    */
   private static final class StartupContext extends AnnotationConfigApplicationContext {
      StartupContext(Supplier<Object> starter) {
         registerBean(STARTER, Object.class, starter::get, bd -> bd.setLazyInit(true));
      }

      @Override
      protected void onRefresh() {
         super.onRefresh();
         getBean(STARTER);
      }
   }

   static final class Engine {
   }

   private ApplicationContext previousContext;
   private static final String STARTER = "bug76975Starter";
}
