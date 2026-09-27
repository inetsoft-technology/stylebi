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

import inetsoft.sree.security.IdentityID;
import inetsoft.test.*;
import inetsoft.uql.XPrincipal;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77175: user messages ({@code CoreTool.USER_MESSAGE_LOCAL}, a plain ThreadLocal) left on a
 * long-lived {@link ThreadPool} worker by one task must not be visible to the next task on the
 * same worker, which may run for another user or organization.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ThreadPoolUserMessageTest {
   @Test
   void nextTaskForAnotherPrincipalDoesNotSeePreviousTasksMessage() throws Exception {
      ThreadPool pool = new ThreadPool(1, 1, "bug77175a-");
      AtomicReference<String> workerA = new AtomicReference<>();
      AtomicReference<String> workerB = new AtomicReference<>();
      AtomicReference<UserMessage> bobSaw = new AtomicReference<>();

      try {
         runOn(pool, user("alice"), () -> {
            workerA.set(Thread.currentThread().getName());
            Tool.addUserMessage("alice-org secret chart warning");
         });
         runOn(pool, user("bob"), () -> {
            workerB.set(Thread.currentThread().getName());
            bobSaw.set(Tool.getUserMessage());
         });
      }
      finally {
         pool.dispose();
      }

      assertEquals(workerA.get(), workerB.get(), "precondition: both tasks ran on the same worker");
      assertNull(bobSaw.get(), () -> "bob's task saw alice's message: " + bobSaw.get().getMessage());
   }

   @Test
   void plainRunnableDoesNotSeePreviousTasksMessage() throws Exception {
      ThreadPool pool = new ThreadPool(1, 1, "bug77175b-");
      AtomicReference<String> worker1 = new AtomicReference<>();
      AtomicReference<String> worker2 = new AtomicReference<>();
      AtomicReference<UserMessage> saw = new AtomicReference<>();

      try {
         runOn(pool, null, () -> {
            worker1.set(Thread.currentThread().getName());
            Tool.addUserMessage("residue from a plain task");
         });
         runOn(pool, null, () -> {
            worker2.set(Thread.currentThread().getName());
            saw.set(Tool.getUserMessage());
         });
      }
      finally {
         pool.dispose();
      }

      assertEquals(worker1.get(), worker2.get(), "precondition: both tasks ran on the same worker");
      assertNull(saw.get(), () -> "second plain task saw: " + saw.get().getMessage());
   }

   @Test
   void taskStillSeesItsOwnMessageDuringItsRun() throws Exception {
      ThreadPool pool = new ThreadPool(1, 1, "bug77175c-");
      AtomicReference<UserMessage> saw = new AtomicReference<>();

      try {
         runOn(pool, user("bob"), () -> {
            try {
               Tool.addUserMessage("own message");
            }
            finally {
               // readers such as SummaryFilter collect in the task's own finally
               saw.set(Tool.getUserMessage());
            }
         });
      }
      finally {
         pool.dispose();
      }

      assertNotNull(saw.get(), "task must still see the message it added itself");
      assertEquals("own message", saw.get().getMessage());
   }

   private static XPrincipal user(String name) {
      return new XPrincipal(new IdentityID(name, name + "-org"));
   }

   /**
    * Runs {@code body} on {@code pool} and waits for it. With a principal the task is an
    * {@link ThreadPool.AbstractContextRunnable}; without one it is a plain {@link Runnable}.
    */
   private static void runOn(ThreadPool pool, Principal principal, Runnable body)
      throws InterruptedException
   {
      CountDownLatch done = new CountDownLatch(1);
      Runnable task;

      if(principal != null) {
         ThreadPool.AbstractContextRunnable r = new ThreadPool.AbstractContextRunnable() {
            @Override
            public void run() {
               try {
                  body.run();
               }
               finally {
                  done.countDown();
               }
            }
         };
         r.setPrincipal(principal);
         task = r;
      }
      else {
         task = () -> {
            try {
               body.run();
            }
            finally {
               done.countDown();
            }
         };
      }

      pool.add(task);
      assertTrue(done.await(10, TimeUnit.SECONDS), "task did not complete");
   }
}
