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
package inetsoft.sree.internal;

import inetsoft.mv.MVDef;
import inetsoft.mv.MVManager;
import inetsoft.sree.RepletRegistryManager;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.schedule.ScheduleTask;
import inetsoft.sree.schedule.TimeCondition;
import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.util.DataSpace;
import inetsoft.util.IndexedStorage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/*
 * Tier: [integration] - Bug #79265. A data cycle deleted while DataCycleManager.generateTasks
 * runs (triggered by MVManager's mv_change event) used to throw ArrayIndexOutOfBoundsException
 * "0 >= 0" from the getConditionCount/getCondition pair and abort the regeneration. Uses the
 * real DataCycleManager, real MVManager.handleEvent and a Mockito spy on the real
 * BlobIndexedStorage whose contains() reports the cycle as gone from a chosen call on.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, ScheduleTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DataCycleManagerGenerateTasksRaceTest {
   private static final String ORG = "o79265";
   private static final String CYCLE = "c1";

   @Autowired
   private IndexedStorage realStorage;

   @Autowired
   private Cluster cluster;

   @Autowired
   private DataSpace dataSpace;

   private IndexedStorage storage;
   private MVManager mvManager;
   private DataCycleManager manager;
   private final AtomicInteger cycleContainsCalls = new AtomicInteger();
   // the cycle reads as present for this many contains() calls on its key, then as gone
   private volatile int presentFor = Integer.MAX_VALUE;

   @BeforeEach
   void setUp() throws Exception {
      storage = spy(realStorage);
      doAnswer(inv -> {
         String key = inv.getArgument(0);

         if(key != null && key.contains(CYCLE) && cycleContainsCalls.incrementAndGet() > presentFor) {
            return false;
         }

         return inv.callRealMethod();
      }).when(storage).contains(any(String.class), eq(ORG));

      SecurityEngine engine = mock(SecurityEngine.class);
      SecurityProvider provider = mock(SecurityProvider.class);
      when(engine.getSecurityProvider()).thenReturn(provider);
      when(provider.getOrganizationIDs()).thenReturn(new String[] { ORG });

      mvManager = spy(new MVManager(cluster));
      MVDef def = new MVDef();
      def.setCycle(CYCLE);
      doReturn(List.of(def)).when(mvManager).list(any(String[].class));

      manager = new DataCycleManager(mock(ScheduleManager.class), storage, engine, cluster,
                                     mvManager, dataSpace, mock(RepletRegistryManager.class));
      mvManager.addPropertyChangeListener(manager);
      manager.addCondition(CYCLE, ORG, TimeCondition.at(1, 0, 0));
      cycleContainsCalls.set(0);
   }

   @AfterEach
   void tearDown() throws Exception {
      presentFor = Integer.MAX_VALUE;
      manager.removeDataCycle(CYCLE, ORG);
   }

   @Test
   void stableCycleYieldsTaskWithItsCondition() throws Exception {
      fireMvChange();

      List<ScheduleTask> tasks = manager.getTasks(ORG);
      assertEquals(1, tasks.size());
      assertEquals(1, tasks.get(0).getConditionCount());
   }

   /** The cycle is deleted before generateTasks reads it again: no exception, no task. */
   @Test
   void cycleGoneBeforeReadIsSkipped() throws Exception {
      presentFor = 0;
      fireMvChange();

      assertTrue(manager.getTasks(ORG).isEmpty(), "no task or stale entry for a vanished cycle");
   }

   /**
    * The old count-then-index sequence: the cycle reads as present for the first two reads and
    * gone for the third (what getCycleInfo, getConditionCount, getCondition used to see). Must
    * not throw out of MVManager.handleEvent's listener nor leave a half-built task.
    */
   @Test
   void cycleVanishingMidPassDoesNotAbortRegeneration() throws Exception {
      presentFor = 2;
      fireMvChange();

      for(ScheduleTask task : manager.getTasks(ORG)) {
         assertEquals(1, task.getConditionCount(), "a generated task is built from one read");
      }

      // a following pass with the cycle really gone leaves no stale task behind
      presentFor = 0;
      fireMvChange();
      assertTrue(manager.getTasks(ORG).isEmpty());
   }

   @Test
   void propertyChangeDoesNotThrowWhenCycleVanishes() {
      presentFor = 2;
      assertDoesNotThrow(() -> manager.propertyChange(
         new java.beans.PropertyChangeEvent(source(), MVManager.MV_CHANGE_EVENT, null, null)));
   }

   private static String source() {
      return ORG + "^~~^mvmanager_x";
   }

   private void fireMvChange() throws Exception {
      Method handle = MVManager.class.getDeclaredMethod(
         "handleEvent", String.class, String.class, Object.class, Object.class);
      handle.setAccessible(true);
      handle.invoke(mvManager, source(), MVManager.MV_CHANGE_EVENT, null, null);
   }
}
