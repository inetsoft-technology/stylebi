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
import inetsoft.sree.schedule.CompletionCondition;
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
 * Tier: [integration] - Bug #81817. A cycle holding only association MVs used to list just
 * "Stage 2", whose only condition waited for a Stage 1 task that was never registered, so
 * nothing ever fired it. Stage 2 must then carry the cycle's own time conditions.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, ScheduleTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DataCycleManagerAssociationOnlyCycleTest {
   private static final String ORG = "o81817";
   private static final String CYCLE = "c1";

   @Autowired
   private IndexedStorage storage;

   @Autowired
   private Cluster cluster;

   @Autowired
   private DataSpace dataSpace;

   private MVManager mvManager;
   private DataCycleManager manager;

   @BeforeEach
   void setUp() throws Exception {
      SecurityEngine engine = mock(SecurityEngine.class);
      SecurityProvider provider = mock(SecurityProvider.class);
      when(engine.getSecurityProvider()).thenReturn(provider);
      when(provider.getOrganizationIDs()).thenReturn(new String[] { ORG });
      mvManager = spy(new MVManager(cluster));
      manager = new DataCycleManager(mock(ScheduleManager.class), storage, engine, cluster,
                                     mvManager, dataSpace, mock(RepletRegistryManager.class));
      mvManager.addPropertyChangeListener(manager);
      manager.addCondition(CYCLE, ORG, TimeCondition.at(1, 0, 0));
   }

   @AfterEach
   void tearDown() {
      manager.removeDataCycle(CYCLE, ORG);
   }

   private MVDef mv(boolean association) {
      MVDef def = spy(new MVDef());
      def.setCycle(CYCLE);
      doReturn(association).when(def).isAssociationMV();
      return def;
   }

   private List<ScheduleTask> generate(MVDef... defs) throws Exception {
      doReturn(List.of(defs)).when(mvManager).list(any(String[].class));
      Method handle = MVManager.class.getDeclaredMethod(
         "handleEvent", String.class, String.class, Object.class, Object.class);
      handle.setAccessible(true);
      handle.invoke(mvManager, ORG + "^~~^mvmanager_x", MVManager.MV_CHANGE_EVENT, null, null);
      return manager.getTasks(ORG);
   }

   @Test
   void associationOnlyCycleStage2FiresAtCycleTime() throws Exception {
      List<ScheduleTask> tasks = generate(mv(true));

      assertEquals(1, tasks.size());
      ScheduleTask stage2 = tasks.get(0);
      assertEquals(1, stage2.getConditionCount());
      assertInstanceOf(TimeCondition.class, stage2.getCondition(0));
   }

   @Test
   void baseAndAssociationCycleKeepsCompletionChain() throws Exception {
      List<ScheduleTask> tasks = generate(mv(false), mv(true));

      assertEquals(2, tasks.size());
      ScheduleTask stage2 = tasks.stream()
         .filter(t -> t.getName().endsWith("Stage 2")).findFirst().orElseThrow();
      assertEquals(1, stage2.getConditionCount());
      assertInstanceOf(CompletionCondition.class, stage2.getCondition(0));
   }
}
