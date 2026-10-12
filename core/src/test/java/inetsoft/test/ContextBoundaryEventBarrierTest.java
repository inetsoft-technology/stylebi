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
package inetsoft.test;

/*
 * Bug #77760. BlobStorage.close() only shuts its event executor down, so a change event that is
 * queued on a closed context's BlobStorageEvent thread still runs later. A listener that looks up
 * a bean when the event arrives, e.g. CSSDictionary calling DataSpace.getDataSpace(), resolved it
 * in the next context once ConfigurationContextInitializer had installed it. During that
 * context's refresh, after the configuration was parsed and before the bean post-processors were
 * registered, the lookup created the next context's dataSpace and its dependencies on the event
 * thread, without the post-processors. The events of the closed contexts must run before the next
 * context is installed.
 *
 * The first method holds its event thread on a gate and queues a change behind it whose listener
 * calls DataSpace.getDataSpace(). A bean factory post-processor of the second method's context,
 * which runs in that window, records whether the change was delivered, opens the gate and waits
 * for the event thread. The gate also opens by itself after GATE_SECONDS, which is what lets the
 * wait for the closed context's events end before this context is installed. Without that wait
 * the test fails as long as the next context reaches its post-processor within GATE_SECONDS; on a
 * very slow runner it can pass without it.
 */

import inetsoft.sree.SreeEnv;
import inetsoft.storage.BlobStorageTestSupport;
import inetsoft.util.ConfigurationContext;
import inetsoft.util.DataSpace;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Set;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  ContextBoundaryEventBarrierTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@SreeHome
@Tag("core")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ContextBoundaryEventBarrierTest {
   @Autowired
   private DataSpace dataSpace;

   @Autowired
   private ApplicationContext applicationContext;

   @AfterAll
   static void releaseGate() {
      if(gate != null) {
         gate.countDown();
      }
   }

   @Test
   @Order(1)
   void lookupQueuedBehindBusyEventThread() throws Exception {
      String gatePath = SreeEnv.getPath("$(sree.home)/portal/barrier77760_gate.txt");
      CountDownLatch held = new CountDownLatch(1);
      gate = new CountDownLatch(1);
      dataSpace.addChangeListener(null, gatePath, e -> {
         if(eventThread == null) {
            eventThread = Thread.currentThread();
            held.countDown();

            try {
               gate.await(GATE_SECONDS, TimeUnit.SECONDS);
            }
            catch(InterruptedException ex) {
               Thread.currentThread().interrupt();
            }
         }
      });
      dataSpace.withOutputStream(null, gatePath, out -> out.write('x'));
      assertTrue(held.await(TIMEOUT_SECONDS, TimeUnit.SECONDS),
                 "the BlobStorageEvent thread never reached the gate");

      // looks up a bean through the configuration context, like CSSDictionary's listener
      String lookupPath = SreeEnv.getPath("$(sree.home)/portal/barrier77760_lookup.txt");
      dataSpace.addChangeListener(null, lookupPath, e -> {
         deliveredTo = ConfigurationContext.getContext().getApplicationContext();

         try {
            DataSpace.getDataSpace();
         }
         catch(Exception ignore) {
            // the closed context, or none, is installed
         }

         delivered = true;
      });

      CountDownLatch queued = new CountDownLatch(1);
      BlobStorageTestSupport.addLastListener(
         dataSpace, dataSpace.getPath(null, lookupPath), queued);
      // queued behind the gate, so it is still queued when this context is closed
      dataSpace.withOutputStream(null, lookupPath, out -> out.write('x'));
      assertTrue(queued.await(TIMEOUT_SECONDS, TimeUnit.SECONDS),
                 "the change was never queued on the BlobStorageEvent thread");
      checkInNextContext = true;
   }

   @Test
   @Order(2)
   void nextContext_installedAfterTheQueuedLookupRan() {
      assumeTrue(checkedInRefresh, "runs after lookupQueuedBehindBusyEventThread only");

      assertAll(
         () -> assertTrue(deliveredBeforeRefresh,
                          "the queued change must be delivered before this context is installed"),
         () -> assertNotSame(applicationContext, deliveredTo,
                             "the queued change must not be delivered in this context"),
         () -> assertFalse(createdByEventThread,
                           "dataSpace was created by the BlobStorageEvent thread before the " +
                           "bean post-processors were registered"),
         () -> assertTrue(PROCESSED.contains("dataSpace"),
                          "dataSpace was not post-processed"));
   }

   @Configuration
   static class Config {
      // records the beans that are post-processed in the context of the second method
      @Bean
      public static BeanPostProcessor barrierMarker() {
         return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
               PROCESSED.add(beanName);
               return bean;
            }
         };
      }

      // runs on the refreshing thread after ConfigurationClassPostProcessor and before
      // registerBeanPostProcessors()
      @Bean
      public static BeanFactoryPostProcessor barrierCheck() {
         return beanFactory -> {
            Thread thread = eventThread;

            if(!checkInNextContext || thread == null) {
               return;
            }

            checkInNextContext = false;
            PROCESSED.clear();
            deliveredBeforeRefresh = delivered;
            gate.countDown();

            try {
               thread.join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
            }
            catch(InterruptedException e) {
               Thread.currentThread().interrupt();
            }

            createdByEventThread = beanFactory.containsSingleton("dataSpace");
            checkedInRefresh = true;
         };
      }
   }

   private static final int GATE_SECONDS = 1;
   private static final int TIMEOUT_SECONDS = 30;
   private static final Set<String> PROCESSED = ConcurrentHashMap.newKeySet();

   private static volatile CountDownLatch gate;
   private static volatile Thread eventThread;
   private static volatile boolean checkInNextContext;
   private static volatile boolean checkedInRefresh;
   private static volatile boolean delivered;
   private static volatile boolean deliveredBeforeRefresh;
   private static volatile boolean createdByEventThread;
   private static volatile ApplicationContext deliveredTo;
}
