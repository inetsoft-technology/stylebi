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
package inetsoft.sree.web.dashboard;

/*
 * Bug #77748. A change event that a test method queues on the DataSpace's BlobStorageEvent
 * thread can still be queued when that method's context is closed (BlobStorage.close() only
 * shuts the executor down). Delivered while the next method's context is refreshing, after its
 * configuration is parsed and before its bean post-processors are registered, the
 * DashboardRegistry listener's DashboardRegistryManager.getInstance() created that context's
 * dashboardRegistryManager, securityEngine and dataSpace on the event thread, without the
 * post-processors: DashboardRegistryConcurrencyTest then saw a DataSpace that is not the spy, and
 * a SecurityEngine whose @PostConstruct never ran.
 *
 * The first method holds the event thread on a gate and queues a registry change behind it. A
 * bean factory post-processor of the second method's context, which runs in that window, opens
 * the gate and waits for the event thread to finish. The second method then checks that its
 * context was not touched. No step depends on timing, every wait is bounded.
 */

import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.*;
import inetsoft.storage.BlobStorage;
import inetsoft.storage.KeyValueStorage;
import inetsoft.test.*;
import inetsoft.util.ConfigurationContext;
import inetsoft.util.DataSpace;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mockingDetails;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  DashboardRegistryConcurrencyTest.Config.class,
                                  DashboardRegistryStaleEventTest.GateConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@SreeHome
@Tag("core")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DashboardRegistryStaleEventTest {
   @Autowired
   private DashboardRegistryManager registryManager;

   @Autowired
   private DataSpace dataSpace;

   @Autowired
   private SecurityEngine securityEngine;

   @Autowired
   private ApplicationContext applicationContext;

   @AfterEach
   void tearDown() {
      DashboardRegistryTestSupport.quiesce(registryManager, dataSpace);
   }

   @AfterAll
   static void releaseGate() {
      // only if the second method's context never opened it
      if(gate != null) {
         gate.countDown();
      }
   }

   @Test
   @Order(1)
   void registryChangeQueuedBehindBusyEventThread() throws Exception {
      assertTrue(mockingDetails(dataSpace).isSpy(), "the DataSpace bean must be the spy");
      IdentityID user = new IdentityID("dashstale_user",
                                       OrganizationManager.getInstance().getCurrentOrgID());
      // watches the registry file
      DashboardRegistry registry = registryManager.getRegistry(user);

      // records the delivery of the registry change without looking up any bean
      dataSpace.addChangeListener(null, registry.getPath(), e -> {
         deliveredTo = ConfigurationContext.getContext().getApplicationContext();
      });

      String gatePath = SreeEnv.getPath("$(sree.home)/portal/dashstale_gate.txt");
      CountDownLatch held = new CountDownLatch(1);
      gate = new CountDownLatch(1);
      dataSpace.addChangeListener(null, gatePath, e -> {
         if(eventThread == null) {
            eventThread = Thread.currentThread();
            held.countDown();

            try {
               gate.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            }
            catch(InterruptedException ex) {
               Thread.currentThread().interrupt();
            }
         }
      });
      dataSpace.withOutputStream(null, gatePath, out -> out.write('x'));
      assertTrue(held.await(TIMEOUT_SECONDS, TimeUnit.SECONDS),
                 "the BlobStorageEvent thread never reached the gate");

      // The write's event reaches the BlobStorageEvent thread through an OnDemand thread. A
      // key-value listener ordered after BlobStorage's own sees the event once it is queued.
      CountDownLatch queued = new CountDownLatch(1);
      addLastListener(dataSpace, dataSpace.getPath(null, registry.getPath()), queued);

      // queued behind the gate, so it is still queued when this context is closed
      String xml = "<?xml version=\"1.0\"?><dashboardRegistry><Version>13.1</Version>" +
         "</dashboardRegistry>";
      dataSpace.withOutputStream(null, registry.getPath(),
                                 out -> out.write(xml.getBytes(StandardCharsets.UTF_8)));
      assertTrue(queued.await(TIMEOUT_SECONDS, TimeUnit.SECONDS),
                 "the registry change was never queued on the BlobStorageEvent thread");
      releaseInNextContext = true;
   }

   @Test
   @Order(2)
   void nextContext_notTouchedByTheStaleDelivery() {
      assumeTrue(releasedInRefresh, "runs after registryChangeQueuedBehindBusyEventThread only");

      assertAll(
         () -> assertFalse(eventThreadAlive, "the BlobStorageEvent thread did not finish"),
         () -> assertSame(applicationContext, deliveredTo,
                          "the stale change is delivered while this context is refreshing"),
         () -> assertEquals(List.of(), createdByEventThread,
                            "beans created by the BlobStorageEvent thread before the bean " +
                            "post-processors were registered"),
         () -> assertTrue(mockingDetails(dataSpace).isSpy(), "the DataSpace bean must be the spy"),
         () -> assertNotNull(securityEngine.getSecurityProvider(),
                             "the SecurityEngine must be initialized (@PostConstruct)"));
   }

   /**
    * Adds a listener to the key-value storage of the data space's blob storage that is called
    * after the blob storage's own listener, which queues the change on the BlobStorageEvent
    * thread. Both are called one after the other, in hash code order, by one OnDemand thread.
    */
   @SuppressWarnings({ "unchecked", "rawtypes" })
   private static void addLastListener(DataSpace dataSpace, String key, CountDownLatch queued)
      throws ReflectiveOperationException
   {
      Field field = DataSpace.class.getDeclaredField("blobStorage");
      field.setAccessible(true);
      Object blobStorage = field.get(dataSpace);
      Method method = BlobStorage.class.getDeclaredMethod("getStorage");
      method.setAccessible(true);
      KeyValueStorage storage = (KeyValueStorage) method.invoke(blobStorage);

      storage.addListener(new KeyValueStorage.Listener() {
         @Override
         public void entryAdded(KeyValueStorage.Event event) {
            entryUpdated(event);
         }

         @Override
         public void entryUpdated(KeyValueStorage.Event event) {
            if(key.equals(event.getKey())) {
               queued.countDown();
            }
         }

         @Override
         public void entryRemoved(KeyValueStorage.Event event) {
         }

         @Override
         public int hashCode() {
            return Integer.MAX_VALUE;
         }
      });
   }

   @Configuration
   static class GateConfig {
      // runs on the refreshing thread after ConfigurationClassPostProcessor and before
      // registerBeanPostProcessors()
      @Bean
      public static BeanFactoryPostProcessor staleEventGate() {
         return beanFactory -> {
            Thread thread = eventThread;

            if(!releaseInNextContext || thread == null) {
               return;
            }

            releaseInNextContext = false;
            gate.countDown();

            try {
               thread.join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
            }
            catch(InterruptedException e) {
               Thread.currentThread().interrupt();
            }

            eventThreadAlive = thread.isAlive();
            createdByEventThread = BEANS.stream().filter(beanFactory::containsSingleton).toList();
            releasedInRefresh = true;
         };
      }
   }

   // the beans that DashboardRegistryManager.getInstance() creates in a refreshing context
   private static final List<String> BEANS =
      List.of("dashboardRegistryManager", "securityEngine", "dataSpace");
   private static final int TIMEOUT_SECONDS = 30;

   private static volatile CountDownLatch gate;
   private static volatile Thread eventThread;
   private static volatile boolean releaseInNextContext;
   private static volatile boolean releasedInRefresh;
   private static volatile boolean eventThreadAlive;
   private static volatile ApplicationContext deliveredTo;
   private static volatile List<String> createdByEventThread = List.of();
}
