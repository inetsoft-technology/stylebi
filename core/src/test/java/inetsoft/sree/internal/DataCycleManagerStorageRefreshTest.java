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

import inetsoft.mv.MVManager;
import inetsoft.sree.RepletRegistryManager;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.schedule.TimeCondition;
import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/*
 * Tier: [integration] - Bug #81275. A data cycle changed on another cluster node reaches this
 * node only as an indexed-storage change event. DataCycleManager.storageRefreshed must reload the
 * changed org's extension tasks in ScheduleManager (node-local cache), once per org, with the
 * org id taken from the changed key, and must never let an exception escape.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, ScheduleTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DataCycleManagerStorageRefreshTest {
   private static final String ORG = "o81275";
   // org ids may contain upper-case letters; the reload must use the key's own case
   private static final String MIXED_ORG = "O81275Mixed";

   @Autowired
   private IndexedStorage storage;

   @Autowired
   private Cluster cluster;

   @Autowired
   private DataSpace dataSpace;

   private ScheduleManager scheduleManager;
   private MVManager mvManager;
   private DataCycleManager manager;

   @BeforeEach
   void setUp() {
      SecurityEngine engine = mock(SecurityEngine.class);
      SecurityProvider provider = mock(SecurityProvider.class);
      when(engine.getSecurityProvider()).thenReturn(provider);
      when(provider.getOrganizationIDs()).thenReturn(new String[] { ORG, MIXED_ORG });

      scheduleManager = mock(ScheduleManager.class);
      mvManager = spy(new MVManager(cluster));
      manager = new DataCycleManager(scheduleManager, storage, engine, cluster, mvManager,
                                     dataSpace, mock(RepletRegistryManager.class));
      clearInvocations(scheduleManager);
   }

   @AfterEach
   void tearDown() {
      System.clearProperty("ScheduleTaskRunner");
   }

   private static String cycleKey(String name, String org) {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.DATA_CYCLE,
                            "/__DATA_CYCLE__" + name, null, org).toIdentifier();
   }

   private static StorageRefreshEvent event(String... keys) {
      List<TimestampIndexChange> changes = new java.util.ArrayList<>();

      for(String key : keys) {
         changes.add(new TimestampIndexChange(key, TimestampIndexChangeType.REMOVE));
      }

      return new StorageRefreshEvent("test", 1L, changes);
   }

   @Test
   void cycleChangeReloadsExtensionsOfThatOrgOnce() {
      manager.storageRefreshed(event(cycleKey("a", ORG), cycleKey("b", ORG)));

      verify(scheduleManager, times(1)).reloadExtensions(ORG);
      verify(scheduleManager, times(1)).reloadExtensions(anyString());
      verify(mvManager).setDefaultCycle(any());
   }

   @Test
   void mixedCaseOrgIdIsPassedUnchanged() {
      manager.storageRefreshed(event(cycleKey("a", MIXED_ORG)));

      verify(scheduleManager).reloadExtensions(MIXED_ORG);
      verify(scheduleManager, never()).reloadExtensions(MIXED_ORG.toLowerCase());
   }

   @Test
   void eachChangedOrgIsReloaded() {
      manager.storageRefreshed(event(cycleKey("a", ORG), cycleKey("b", MIXED_ORG)));

      verify(scheduleManager).reloadExtensions(ORG);
      verify(scheduleManager).reloadExtensions(MIXED_ORG);
   }

   @Test
   void unrelatedKeysDoNotReload() {
      String other = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET,
                                    "/ws1", null, ORG).toIdentifier();
      manager.storageRefreshed(event(other));

      verify(scheduleManager, never()).reloadExtensions(anyString());
   }

   @Test
   void scheduleTaskRunnerSkipsReload() {
      System.setProperty("ScheduleTaskRunner", "true");
      manager.storageRefreshed(event(cycleKey("a", ORG)));

      verify(scheduleManager, never()).reloadExtensions(anyString());
   }

   @Test
   void reloadFailureDoesNotEscape() {
      doThrow(new IllegalStateException("boom")).when(scheduleManager).reloadExtensions(ORG);

      assertDoesNotThrow(() -> manager.storageRefreshed(event(cycleKey("a", ORG))));
      verify(scheduleManager).reloadExtensions(ORG);
   }

   @Test
   void realCycleRemovalIsPickedUpThroughStorageEvent() {
      manager.addCondition("c81275", ORG, TimeCondition.at(1, 0, 0));
      clearInvocations(scheduleManager);
      String key = cycleKey("c81275", ORG);
      manager.removeDataCycle("c81275", ORG);

      manager.storageRefreshed(event(key));

      verify(scheduleManager).reloadExtensions(ORG);
      manager.removeDataCycle("c81275", ORG);
   }
}
