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
package inetsoft.report.filter;

import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.*;
import inetsoft.uql.asset.ConfirmException;
import inetsoft.util.Tool;
import inetsoft.util.UserMessage;
import inetsoft.util.script.ScriptException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Arrays;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77188: a SummaryFilter processed on an OnDemand worker must hand the worker's own
 * user messages to the thread reading it. The worker used to signal completion before it
 * collected them, and moreRows() read them before waiting.
 *
 * The worker is held with latches, and released only once the reader waits in
 * waitForRow() or has finished reading, so that each case takes one deterministic order.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class SummaryFilterUserMessageTest {
   /** A single moreRows(EOT), the common consumer pattern, gets the worker's message. */
   @Test
   void singleMoreRowsToEndGetsOwnMessage() throws Exception {
      CountDownLatch release = new CountDownLatch(1);
      AtomicReference<Thread> reader = new AtomicReference<>();
      AtomicBoolean once = new AtomicBoolean();

      // the worker adds its message and stops before completing, the reader reaches
      // moreRows() while the worker is still processing
      DefaultTableLens base = new DefaultTableLens(data()) {
         @Override
         public Object getObject(int r, int c) {
            if(r >= 1 && isWorker(reader) && once.compareAndSet(false, true)) {
               Tool.addUserMessage("own warning one-shot");
               await(release);
            }

            return super.getObject(r, c);
         }
      };

      Reader result = readToEnd(createFilter(base), reader, false);
      releaseWhenWaitingOrDone(result, release);
      result.join();

      assertMessage(result.message, "own warning one-shot");
   }

   /** A row-by-row reader gets the message on its last moreRows() call. */
   @Test
   void rowIterationGetsOwnMessage() throws Exception {
      CountDownLatch release = new CountDownLatch(1);
      AtomicReference<Thread> reader = new AtomicReference<>();
      AtomicBoolean once = new AtomicBoolean();

      // collecting the messages blocks in merge() until the reader waits or is done
      DefaultTableLens base = new DefaultTableLens(data()) {
         @Override
         public Object getObject(int r, int c) {
            if(r >= 1 && isWorker(reader) && once.compareAndSet(false, true)) {
               Tool.addUserMessage(new UserMessage("own warning a", ConfirmException.INFO) {
                  @Override
                  public UserMessage merge(UserMessage other) {
                     await(release);
                     return super.merge(other);
                  }
               });
               Tool.addUserMessage("own warning b");
            }

            return super.getObject(r, c);
         }
      };

      Reader result = readToEnd(createFilter(base), reader, true);
      releaseWhenWaitingOrDone(result, release);
      result.join();

      assertEquals(3, result.rows.get(), "header and two groups");
      assertMessage(result.message, "own warning a");
      assertMessage(result.message, "own warning b");
   }

   /** The message of a script exception is published before the completion signal. */
   @Test
   void scriptExceptionMessageReachesReader() throws Exception {
      CountDownLatch release = new CountDownLatch(1);
      AtomicReference<Thread> reader = new AtomicReference<>();

      DefaultTableLens base = new DefaultTableLens(data()) {
         @Override
         public Object getObject(int r, int c) {
            if(r >= 1 && isWorker(reader)) {
               throw new ScriptException("script failed in calc field") {
                  @Override
                  public String getMessage() {
                     // read by SummaryFilter.process(), hold it until the reader waits or
                     // is done
                     if(isWorker(reader) && calledFromProcess()) {
                        await(release);
                     }

                     return super.getMessage();
                  }
               };
            }

            return super.getObject(r, c);
         }
      };

      Reader result = readToEnd(createFilter(base), reader, false);
      releaseWhenWaitingOrDone(result, release);
      result.join();

      assertMessage(result.message, "script failed in calc field");
   }

   /**
    * A message without text used to make the message collection throw (UserMessage.merge).
    * The reader must neither hang nor lose the other message.
    */
   @Test
   void nullTextMessageDoesNotHangReader() throws Exception {
      AtomicReference<Thread> reader = new AtomicReference<>();
      AtomicBoolean once = new AtomicBoolean();

      DefaultTableLens base = new DefaultTableLens(data()) {
         @Override
         public Object getObject(int r, int c) {
            if(r >= 1 && isWorker(reader) && once.compareAndSet(false, true)) {
               // what SummaryFilter/SortFilter add for a script exception without a message
               Tool.addUserMessage((String) null);
               Tool.addUserMessage("own warning after null");
            }

            return super.getObject(r, c);
         }
      };

      Reader result = readToEnd(createFilter(base), reader, false);
      result.join();

      assertMessage(result.message, "own warning after null");
   }

   /** Completion is signalled even if collecting the messages throws. */
   @Test
   void failingMessageCollectionDoesNotHangReader() throws Exception {
      AtomicReference<Thread> reader = new AtomicReference<>();
      AtomicBoolean once = new AtomicBoolean();

      DefaultTableLens base = new DefaultTableLens(data()) {
         @Override
         public Object getObject(int r, int c) {
            if(r >= 1 && isWorker(reader) && once.compareAndSet(false, true)) {
               Tool.addUserMessage(new UserMessage("own warning broken", ConfirmException.INFO) {
                  @Override
                  public UserMessage merge(UserMessage other) {
                     throw new IllegalStateException("broken merge");
                  }
               });
               Tool.addUserMessage("own warning other");
            }

            return super.getObject(r, c);
         }
      };

      SummaryFilter filter = createFilter(base);
      Reader result = readToEnd(filter, reader, false);
      result.join();

      assertEquals(3, filter.getRowCount());
   }

   private static Object[][] data() {
      return new Object[][] { { "g", "v" }, { "a", 1 }, { "a", 2 }, { "b", 3 } };
   }

   private static SummaryFilter createFilter(DefaultTableLens base) {
      return new SummaryFilter(base, new int[] { 0 }, new int[] { 1 }, new SumFormula(), null);
   }

   private static boolean isWorker(AtomicReference<Thread> reader) {
      Thread thread = reader.get();
      return thread != null && Thread.currentThread() != thread;
   }

   private static boolean calledFromProcess() {
      // the innermost filter frame is the catch block in process(), not process0()
      return Arrays.stream(Thread.currentThread().getStackTrace())
         .filter(e -> e.getClassName().equals(SummaryFilter.class.getName()))
         .findFirst()
         .map(e -> e.getMethodName().equals("process") || e.getMethodName().equals("process1"))
         .orElse(false);
   }

   private static void await(CountDownLatch latch) {
      try {
         latch.await(30, TimeUnit.SECONDS);
      }
      catch(InterruptedException ignore) {
         Thread.currentThread().interrupt();
      }
   }

   /**
    * Reads the filter to the end on a new thread, with one moreRows(EOT) or row by row, and
    * keeps the user message the reading thread got.
    */
   private static Reader readToEnd(SummaryFilter filter, AtomicReference<Thread> reader,
                                   boolean rowByRow)
   {
      Reader result = new Reader();
      Thread thread = new Thread(() -> {
         try {
            Tool.clearUserMessage();

            if(rowByRow) {
               for(int r = 0; filter.moreRows(r); r++) {
                  result.rows.incrementAndGet();
               }
            }
            else {
               assertFalse(filter.moreRows(Integer.MAX_VALUE));
            }

            result.message = Tool.getUserMessage();
         }
         catch(Throwable ex) {
            result.failure = ex;
         }
         finally {
            result.done.set(true);
         }
      }, "summary-filter-77188-reader");
      thread.setDaemon(true);
      result.thread = thread;
      reader.set(thread);
      thread.start();
      return result;
   }

   /**
    * Releases the worker once the reader waits for it in waitForRow() or is done reading.
    */
   private static void releaseWhenWaitingOrDone(Reader result, CountDownLatch release) {
      Thread helper = new Thread(() -> {
         awaitCondition(() -> result.done.get() || isWaitingForRow(result.thread));
         release.countDown();
      }, "summary-filter-77188-release");
      helper.setDaemon(true);
      helper.start();
   }

   private static boolean isWaitingForRow(Thread thread) {
      Thread.State state = thread.getState();

      if(state != Thread.State.WAITING && state != Thread.State.TIMED_WAITING) {
         return false;
      }

      return Arrays.stream(thread.getStackTrace())
         .anyMatch(e -> e.getClassName().equals(SummaryFilter.class.getName()) &&
            e.getMethodName().equals("waitForRow"));
   }

   private static void awaitCondition(BooleanSupplier condition) {
      long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);

      while(!condition.getAsBoolean() && System.nanoTime() < end) {
         LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
      }
   }

   private static void assertMessage(UserMessage message, String text) {
      assertNotNull(message, "the reader did not get the filter's own message");
      assertNotNull(message.getMessage());
      assertTrue(message.getMessage().contains(text), message.getMessage());
   }

   private static final class Reader {
      void join() throws InterruptedException {
         thread.join(TimeUnit.SECONDS.toMillis(30));
         assertFalse(thread.isAlive(), "the reader hangs waiting for the filter to complete");

         if(failure != null && !(failure instanceof AssertionError)) {
            fail("the reader failed", failure);
         }

         if(failure instanceof AssertionError) {
            throw (AssertionError) failure;
         }
      }

      Thread thread;
      volatile UserMessage message;
      volatile Throwable failure;
      final AtomicBoolean done = new AtomicBoolean();
      final AtomicInteger rows = new AtomicInteger();
   }
}
