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
package inetsoft.report.composition.execution;

import inetsoft.report.composition.WorksheetService;
import inetsoft.test.*;
import inetsoft.uql.asset.ConfirmException;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.util.Tool;
import inetsoft.util.UserMessage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.*;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Bug #77188: AssetDataCache.getData() reads the exceptions and the user message of its
 * Processor right after join() returns, so the Processor must publish them before it
 * signals completion, and must signal it even if collecting them throws.
 *
 * The Processor runs with a null table, run0() fails fast and the finally block does the
 * publishing under test.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
   AssetDataCacheProcessorUserMessageTest.TestConfig.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class AssetDataCacheProcessorUserMessageTest {
   @Configuration
   static class TestConfig {
      @Bean
      @SuppressWarnings("unchecked")
      public AssetDataCache assetDataCache() {
         return new AssetDataCache(mock(DataSourceRegistry.class), mock(ObjectProvider.class));
      }
   }

   @Test
   void joinSeesExceptionsAndUserMessage() throws Exception {
      AssetDataCache cache = AssetDataCache.getCache();
      Field field = AssetDataCache.class.getDeclaredField("lockEntries");
      field.setAccessible(true);
      Object original = field.get(cache);
      CountDownLatch release = new CountDownLatch(1);
      Exception failure = new Exception("query failed, design lens returned");
      String workerName = "asset-data-cache-77188-processor";

      // the finally block of Processor.run() removes the lock entries right before it
      // collects the results, the query adds its exception there and the worker is held
      // until the caller waits in join() or has read the results
      ThreadLocal<Object> hook = new ThreadLocal<>() {
         @Override
         public void remove() {
            if(Thread.currentThread().getName().equals(workerName)) {
               List<Exception> exceptions = WorksheetService.ASSET_EXCEPTIONS.get();

               if(exceptions != null && !exceptions.contains(failure)) {
                  exceptions.add(failure);
               }

               await(release);
            }

            super.remove();
         }
      };
      field.set(cache, hook);

      try {
         Object processor = newProcessor();
         Thread worker = new Thread(() -> {
            Tool.addUserMessage("query warning a", ConfirmException.WARNING);
            Tool.addUserMessage("query warning b");
            ((Runnable) processor).run();
         }, workerName);
         worker.setDaemon(true);

         Caller caller = new Caller(processor);
         caller.start();
         worker.start();
         releaseWhenJoiningOrDone(caller, release);
         caller.finish();
         worker.join(TimeUnit.SECONDS.toMillis(30));

         assertNotNull(caller.exceptions, "join() returned before the exceptions were published");
         assertTrue(caller.exceptions.contains(failure), caller.exceptions.toString());
         assertMessage(caller.message, "query warning a");
         assertMessage(caller.message, "query warning b");
      }
      finally {
         field.set(cache, original);
      }
   }

   /** A message without text used to make the collection throw before complete(). */
   @Test
   void nullTextMessageDoesNotHangJoin() throws Exception {
      Object processor = newProcessor();
      Thread worker = new Thread(() -> {
         Tool.addUserMessage((String) null);
         Tool.addUserMessage("query warning after null");
         ((Runnable) processor).run();
      }, "asset-data-cache-77188-null");
      worker.setDaemon(true);

      Caller caller = new Caller(processor);
      caller.start();
      worker.start();
      caller.finish();

      assertMessage(caller.message, "query warning after null");
   }

   /** complete() runs even if collecting the user message throws. */
   @Test
   void failingMessageCollectionDoesNotHangJoin() throws Exception {
      Object processor = newProcessor();
      Thread worker = new Thread(() -> {
         Tool.addUserMessage(new UserMessage("query warning broken", ConfirmException.INFO) {
            @Override
            public UserMessage merge(UserMessage other) {
               throw new IllegalStateException("broken merge");
            }
         });
         Tool.addUserMessage("query warning other");

         try {
            ((Runnable) processor).run();
         }
         catch(RuntimeException ignore) {
            // only the join matters here
         }
      }, "asset-data-cache-77188-broken");
      worker.setDaemon(true);

      Caller caller = new Caller(processor);
      caller.start();
      worker.start();
      caller.finish();

      assertNotNull(caller.exceptions, "the exceptions are published before the message");
   }

   private static Object newProcessor() throws Exception {
      Class<?> cls = Class.forName(AssetDataCache.class.getName() + "$Processor");
      Constructor<?> ctor = cls.getDeclaredConstructors()[0];
      ctor.setAccessible(true);
      Class<?>[] types = ctor.getParameterTypes();
      Object[] args = new Object[types.length];

      for(int i = 0; i < args.length; i++) {
         if(types[i] == int.class) {
            args[i] = 0;
         }
         else if(types[i] == long.class) {
            args[i] = 0L;
         }
         else if(types[i] == boolean.class) {
            args[i] = false;
         }
      }

      return ctor.newInstance(args);
   }

   private static Object call(Object processor, String name) throws Exception {
      Method method = processor.getClass().getDeclaredMethod(name);
      method.setAccessible(true);
      return method.invoke(processor);
   }

   private static void await(CountDownLatch latch) {
      try {
         latch.await(30, TimeUnit.SECONDS);
      }
      catch(InterruptedException ignore) {
         Thread.currentThread().interrupt();
      }
   }

   private static void releaseWhenJoiningOrDone(Caller caller, CountDownLatch release) {
      Thread helper = new Thread(() -> {
         awaitCondition(() -> caller.done.get() || isJoining(caller.thread));
         release.countDown();
      }, "asset-data-cache-77188-release");
      helper.setDaemon(true);
      helper.start();
   }

   private static boolean isJoining(Thread thread) {
      Thread.State state = thread.getState();

      if(state != Thread.State.WAITING && state != Thread.State.TIMED_WAITING) {
         return false;
      }

      return Arrays.stream(thread.getStackTrace())
         .anyMatch(e -> e.getClassName().endsWith("AssetDataCache$Processor") &&
            e.getMethodName().equals("join"));
   }

   private static void awaitCondition(BooleanSupplier condition) {
      long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);

      while(!condition.getAsBoolean() && System.nanoTime() < end) {
         LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
      }
   }

   private static void assertMessage(UserMessage message, String text) {
      assertNotNull(message, "join() returned before the user message was published");
      assertNotNull(message.getMessage());
      assertTrue(message.getMessage().contains(text), message.getMessage());
   }

   /** Does what AssetDataCache.getData() does: join, then read the results. */
   private static final class Caller {
      Caller(Object processor) {
         thread = new Thread(() -> {
            try {
               call(processor, "join");
               exceptions = (List<?>) call(processor, "getExceptions");
               message = (UserMessage) call(processor, "getUserMessage");
            }
            catch(Exception ignore) {
            }
            finally {
               done.set(true);
            }
         }, "asset-data-cache-77188-caller");
         thread.setDaemon(true);
      }

      void start() {
         thread.start();
      }

      void finish() throws InterruptedException {
         thread.join(TimeUnit.SECONDS.toMillis(30));
         assertFalse(thread.isAlive(), "join() hangs, complete() was never called");
      }

      final Thread thread;
      volatile List<?> exceptions;
      volatile UserMessage message;
      final AtomicBoolean done = new AtomicBoolean();
   }
}
