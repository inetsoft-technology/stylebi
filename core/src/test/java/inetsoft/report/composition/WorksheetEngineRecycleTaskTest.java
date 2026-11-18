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
package inetsoft.report.composition;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.test.*;
import inetsoft.uql.asset.*;
import inetsoft.util.TimedQueue;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.security.Principal;
import java.util.List;
import java.util.Vector;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #81350: the recycle sweep that disposes timed out runtime sheets was only registered by
 * WorksheetEngine.setServer(true), which nothing called after Distributed Sessions, so runtime
 * sheets never expired.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Tag("core")
class WorksheetEngineRecycleTaskTest {
   @Autowired ViewsheetService service;
   @Autowired ConfigurableApplicationContext context;

   @AfterEach
   void removeSheets() {
      WorksheetEngine engine = (WorksheetEngine) service;
      engine.emap.clear();

      for(String id : ids) {
         engine.closeSheet(id, null);
      }

      ids.clear();
   }

   @Test
   @Order(1)
   void springWiredEngineRegistersTheRecycleTask() throws Exception {
      assertNotNull(findRecycleTask(), "no RecycleTask in " + TimedQueue.getStatus());
      assertTrue(service.isServer());
   }

   @Test
   @Order(2)
   void sweepRemovesOnlyTimedOutIdleNonSchedulerSheets() throws Exception {
      WorksheetEngine engine = (WorksheetEngine) service;
      long old = System.currentTimeMillis() - 3600_000L;

      RuntimeWorksheet stale = put("stale", false, old);
      RuntimeWorksheet executing = put("executing", false, old);
      RuntimeWorksheet scheduler = put("scheduler", true, old);
      RuntimeWorksheet fresh = put("fresh", false, System.currentTimeMillis());
      engine.emap.put("executing", new Vector<>());

      assertTrue(stale.isTimeout(), "precondition: stale sheet must be timed out");
      findRecycleTask().run();

      assertThrows(ExpiredSheetException.class, () -> engine.getSheet("stale", null),
                   "timed out sheet must be disposed");
      assertNotNull(engine.getSheet("executing", null), "sheet with a running event is kept");
      assertNotNull(engine.getSheet("scheduler", null), "_scheduler_ sheet is kept");
      assertNotNull(engine.getSheet("fresh", null), "sheet with a recent heartbeat is kept");
   }

   @Test
   @Order(3)
   void closingTheEngineBeanRemovesTheRecycleTask() throws Exception {
      assertNotNull(findRecycleTask());
      // runs the bean's destruction callbacks as closing the context does
      ((org.springframework.beans.factory.support.DefaultListableBeanFactory) context.getBeanFactory()).destroySingleton("viewsheetEngine");
      assertNull(findRecycleTask(), "RecycleTask leaked after the context closed: " +
         TimedQueue.getStatus());
   }

   private RuntimeWorksheet put(String id, boolean scheduler, long heartbeat) {
      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET,
                                        id, null);

      if(scheduler) {
         entry.setProperty("_scheduler_", "true");
      }

      RuntimeWorksheet rws = new RuntimeWorksheet(entry, new Worksheet(), (Principal) null);
      rws.heartbeat = heartbeat;
      rws.setAccessed(heartbeat);
      ((WorksheetEngine) service).putRuntimeSheet(id, rws);
      ids.add(id);
      return rws;
   }

   @SuppressWarnings("unchecked")
   private static TimedQueue.TimedRunnable findRecycleTask() throws Exception {
      Field f = TimedQueue.class.getDeclaredField("queue");
      f.setAccessible(true);
      List<TimedQueue.TimedRunnable> queue = (List<TimedQueue.TimedRunnable>) f.get(null);

      synchronized(queue) {
         return queue.stream()
            .filter(r -> r.getClass().getName().equals(WorksheetEngine.class.getName() +
                                                       "$RecycleTask"))
            .findFirst().orElse(null);
      }
   }

   private final List<String> ids = new java.util.ArrayList<>();
}
