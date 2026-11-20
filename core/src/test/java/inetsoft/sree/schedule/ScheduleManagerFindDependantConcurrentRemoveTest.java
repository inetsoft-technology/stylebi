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
package inetsoft.sree.schedule;

import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #81906: the dependency scan of a delete re-reads every task of a snapshot by id, so a task
 * that another delete removed after the snapshot was taken made it throw a NullPointerException.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ScheduleManagerFindDependantConcurrentRemoveTest {
   private static final String HOST = Organization.getDefaultOrganizationID();
   private static final String FOLDER = "Fd81906";
   private static final String PREFIX = "fd81906t";

   @Autowired
   ScheduleManager scheduleManager;

   private Principal savedPrincipal;
   private final List<String> created = new ArrayList<>();

   @BeforeEach
   void setUp() {
      savedPrincipal = ThreadContext.getPrincipal();
   }

   @AfterEach
   void tearDown() throws Exception {
      for(String id : created) {
         if(scheduleManager.getScheduleTask(id, HOST) != null) {
            scheduleManager.removeScheduleTask(id, null);
         }
      }

      created.clear();
      ThreadContext.setContextPrincipal(null);
      ThreadContext.setPrincipal(savedPrincipal);
   }

   // the snapshot holds a task that was removed after it was taken
   @Test
   void snapshotTaskRemovedAfterSnapshot_isSkipped() throws Exception {
      List<String> ids = create(5);
      Vector<ScheduleTask> snapshot = scheduleManager.getScheduleTasks(HOST);
      scheduleManager.removeScheduleTask(ids.get(4), null);

      assertDoesNotThrow(() -> scheduleManager.hasDependency(snapshot, ids.get(0)));
   }

   // a task that really depends on the target is still found next to a removed one
   @Test
   void realDependantStillFound_withRemovedSnapshotTask() throws Exception {
      List<String> ids = create(5);
      ScheduleTask dependant = scheduleManager.getScheduleTask(ids.get(1), HOST);
      dependant.addCondition(new CompletionCondition(ids.get(0)));
      scheduleManager.setScheduleTask(ids.get(1), dependant, folder(), HOST, null);

      Vector<ScheduleTask> snapshot = scheduleManager.getScheduleTasks(HOST);
      scheduleManager.removeScheduleTask(ids.get(4), null);

      assertEquals(ids.get(1), scheduleManager.findDependant(snapshot, ids.get(0)));
   }

   // the delete sequence of the schedule API, run by several threads on different tasks
   @Test
   void concurrentDeletesOfDifferentTasks_allSucceed() throws Exception {
      List<String> ids = create(50);
      int threads = 9;
      ExecutorService pool = Executors.newFixedThreadPool(threads);
      CountDownLatch start = new CountDownLatch(1);
      List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());
      AtomicInteger removed = new AtomicInteger();
      List<Future<?>> futures = new ArrayList<>();

      try {
         for(String id : ids) {
            futures.add(pool.submit(() -> {
               try {
                  start.await();
                  Vector<ScheduleTask> all = scheduleManager.getScheduleTasks(HOST);

                  if(!scheduleManager.hasDependency(all, id)) {
                     scheduleManager.removeScheduleTask(id, null);
                     removed.incrementAndGet();
                  }
               }
               catch(Throwable e) {
                  errors.add(e);
               }
            }));
         }

         start.countDown();

         for(Future<?> f : futures) {
            f.get(60, TimeUnit.SECONDS);
         }
      }
      finally {
         pool.shutdownNow();
         assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
      }

      assertTrue(errors.isEmpty(), errors.toString());
      assertEquals(ids.size(), removed.get());

      for(String id : ids) {
         assertNull(scheduleManager.getScheduleTask(id, HOST), id);
      }
   }

   private List<String> create(int count) throws Exception {
      List<String> ids = new ArrayList<>();

      for(int i = 0; i < count; i++) {
         ScheduleTask task = new ScheduleTask(PREFIX + i);
         task.setOwner(new IdentityID("admin", HOST));
         scheduleManager.setScheduleTask(task.getTaskId(), task, folder(), HOST, null);
         ids.add(task.getTaskId());
         created.add(task.getTaskId());
      }

      return ids;
   }

   private static AssetEntry folder() {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.SCHEDULE_TASK_FOLDER,
                            "/", null, HOST);
   }
}
