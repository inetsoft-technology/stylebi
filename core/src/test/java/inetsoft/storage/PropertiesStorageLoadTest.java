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
package inetsoft.storage;

import inetsoft.sree.PropertiesEngine;
import inetsoft.sree.internal.cluster.*;
import inetsoft.test.TestKeyValueEngine;
import inetsoft.util.ConfigurationContext;
import inetsoft.util.FileSystemService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.NestedExceptionUtils;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Bug #76975: {@code PropertiesEngine} must not start with a {@code sreeProperties} store whose
 * load did not complete, because the node would then start with security off and could replace
 * the stored keys. It retries the load once, and fails if the retry does not complete either, so
 * the startup fails instead.
 */
@Tag("core")
class PropertiesStorageLoadTest {
   @BeforeEach
   void saveContext() {
      previousContext = ConfigurationContext.getContext().getApplicationContext();
   }

   @AfterEach
   void restoreContext() {
      ConfigurationContext.getContext().setApplicationContext(previousContext);
   }

   @Test
   void startupFailsWhenThePropertiesCannotBeLoaded() {
      LoadTimesOutCluster cluster = new LoadTimesOutCluster(Integer.MAX_VALUE);

      try(AnnotationConfigApplicationContext context = createContext(cluster)) {
         context.refresh();
         // PropertiesEngine is lazy, the server creates it while it creates SecurityEngine
         BeanCreationException e = assertThrows(
            BeanCreationException.class, () -> context.getBean(PropertiesEngine.class));
         Throwable cause = NestedExceptionUtils.getMostSpecificCause(e);
         assertInstanceOf(IllegalStateException.class, cause, () -> "unexpected cause: " + cause);
         assertTrue(cause.getMessage().contains(STORE), cause::getMessage);
         assertEquals(2, cluster.timedOutLoads.get(), "the load was not retried exactly once");
      }
   }

   @Test
   void startsWithTheStoredPropertiesWhenTheRetriedLoadCompletes() {
      LoadTimesOutCluster cluster = new LoadTimesOutCluster(1);

      try(AnnotationConfigApplicationContext context = createContext(cluster)) {
         context.refresh();
         PropertiesEngine engine = context.getBean(PropertiesEngine.class);

         assertEquals(1, cluster.timedOutLoads.get(), "the first load did not time out");
         assertEquals("true", engine.getPropertyFromStorage("security.enabled"),
                      "the stored properties were not loaded by the retry");
      }
   }

   @SuppressWarnings("unchecked")
   private static AnnotationConfigApplicationContext createContext(Cluster cluster) {
      KeyValueEngine engine = new TestKeyValueEngine();
      engine.put(STORE, "security.enabled", "true");

      AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
      context.registerBean("cluster", Cluster.class, () -> cluster);
      context.registerBean("keyValueEngine", KeyValueEngine.class, () -> engine);
      context.registerBean(
         KeyValueStorageManager.class, () -> new KeyValueStorageManager(engine, cluster));
      context.registerBean(
         PropertiesEngine.class,
         () -> new PropertiesEngine(
            context.getBean(KeyValueStorageManager.class), mock(FileSystemService.class),
            context, mock(ObjectProvider.class)));
      ConfigurationContext.getContext().setApplicationContext(context);
      return context;
   }

   /**
    * Times out the given number of {@code sreeProperties} loads as the 3-minute wait in
    * {@code LocalKeyValueStorage} does, and runs the later ones.
    */
   private static final class LoadTimesOutCluster extends MockCluster {
      LoadTimesOutCluster(int timeouts) {
         this.timeouts = timeouts;
      }

      @Override
      public Future<?> submit(String serviceId, SingletonRunnableTask task) {
         if(task instanceof LoadKeyValueTask<?> load && STORE.equals(load.getId()) &&
            timedOutLoads.get() < timeouts)
         {
            timedOutLoads.incrementAndGet();
            return new TimedOutFuture();
         }

         return super.submit(serviceId, task);
      }

      private final int timeouts;
      private final AtomicInteger timedOutLoads = new AtomicInteger();
   }

   private static final class TimedOutFuture extends CompletableFuture<Void> {
      TimedOutFuture() {
         completeExceptionally(new TimeoutException());
      }

      @Override
      public Void get(long timeout, TimeUnit unit) throws TimeoutException {
         throw new TimeoutException();
      }
   }

   private ApplicationContext previousContext;
   private static final String STORE = "sreeProperties";
}
