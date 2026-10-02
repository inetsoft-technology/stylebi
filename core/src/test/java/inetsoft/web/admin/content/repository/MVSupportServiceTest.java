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
package inetsoft.web.admin.content.repository;

import inetsoft.mv.MVDef;
import inetsoft.mv.MVManager;
import inetsoft.mv.fs.internal.ClusterUtil;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.internal.cluster.DistributedMap;
import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.sree.security.SecurityEngine;
import org.junit.jupiter.api.*;
import org.mockito.AdditionalAnswers;
import org.mockito.MockedStatic;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77067: MVSupportService.dispose() must only remove entries of the cluster-wide analysis
 * status map for MV names whose definition belongs to the caller's org.
 */
@Tag("core")
class MVSupportServiceTest {
   private static final String CALLER_ORG = "orga";
   private static final String OWN_MV = "vs_T_1727400000000_0_orga";
   private static final String FOREIGN_MV = "vs_T_1727400000001_0_orgb";

   private MVManager mvManager;
   private Map<String, MVSupportService.AnalysisStatus> backing;
   private MVSupportService service;
   private MockedStatic<OrganizationManager> orgManagerStatic;
   private MockedStatic<ClusterUtil> clusterUtilStatic;

   @BeforeEach
   @SuppressWarnings("unchecked")
   void setUp() {
      mvManager = mock(MVManager.class);
      backing = new HashMap<>();
      DistributedMap<String, MVSupportService.AnalysisStatus> map =
         mock(DistributedMap.class, AdditionalAnswers.delegatesTo(backing));
      Cluster cluster = mock(Cluster.class);
      when(cluster.getMap(anyString())).thenAnswer(inv -> map);

      OrganizationManager orgManager = mock(OrganizationManager.class);
      when(orgManager.getCurrentOrgID()).thenReturn(CALLER_ORG);
      orgManagerStatic = mockStatic(OrganizationManager.class);
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
      clusterUtilStatic = mockStatic(ClusterUtil.class);

      service = new MVSupportService(mvManager, mock(SecurityEngine.class),
                                     mock(ScheduleManager.class), cluster);
   }

   @AfterEach
   void tearDown() {
      clusterUtilStatic.close();
      orgManagerStatic.close();
   }

   @Test
   void disposeOwnMVClearsOwnAnalysisEntry() {
      MVDef ownDef = def(OWN_MV);
      when(mvManager.get(OWN_MV, CALLER_ORG)).thenReturn(ownDef);
      backing.put("own-analysis", status(OWN_MV));

      service.dispose(List.of(OWN_MV));

      verify(mvManager).remove(ownDef, false, CALLER_ORG);
      assertFalse(backing.containsKey("own-analysis"));
   }

   @Test
   void disposeForeignNameLeavesOtherOrgAnalysisEntry() {
      when(mvManager.get(FOREIGN_MV, CALLER_ORG)).thenReturn(null);
      backing.put("other-org-analysis", status(FOREIGN_MV));

      service.dispose(List.of(FOREIGN_MV));

      verify(mvManager, never()).remove(any(MVDef.class), anyBoolean(), anyString());
      assertTrue(backing.containsKey("other-org-analysis"));
   }

   @Test
   void disposeMixedListOnlyClearsOwnEntry() {
      MVDef ownDef = def(OWN_MV);
      when(mvManager.get(OWN_MV, CALLER_ORG)).thenReturn(ownDef);
      when(mvManager.get(FOREIGN_MV, CALLER_ORG)).thenReturn(null);
      backing.put("own-analysis", status(OWN_MV));
      backing.put("other-org-analysis", status(FOREIGN_MV));

      service.dispose(List.of(OWN_MV, FOREIGN_MV));

      assertFalse(backing.containsKey("own-analysis"));
      assertTrue(backing.containsKey("other-org-analysis"));
   }

   @Test
   void clusterDeleteRunsBeforeManagerRemove() {
      MVDef ownDef = def(OWN_MV);
      when(mvManager.get(OWN_MV, CALLER_ORG)).thenReturn(ownDef);
      List<String> calls = new ArrayList<>();
      clusterUtilStatic.when(() -> ClusterUtil.deleteClusterMV(OWN_MV))
         .thenAnswer(inv -> calls.add("deleteClusterMV"));
      doAnswer(inv -> calls.add("remove")).when(mvManager).remove(ownDef, false, CALLER_ORG);

      service.dispose(List.of(OWN_MV));

      assertEquals(List.of("deleteClusterMV", "remove"), calls);
   }

   private static MVDef def(String name) {
      MVDef def = mock(MVDef.class);
      when(def.getMVName()).thenReturn(name);
      return def;
   }

   private static MVSupportService.AnalysisStatus status(String mvName) {
      MVSupportService.MVStatus mvStatus = mock(MVSupportService.MVStatus.class);
      MVDef def = def(mvName);
      when(mvStatus.getDefinition()).thenReturn(def);
      return new MVSupportService.AnalysisStatus(
         List.of(), List.of(), Map.of(), List.of(mvStatus), null);
   }
}
