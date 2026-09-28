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
import inetsoft.mv.trans.UserInfo;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.internal.cluster.DistributedMap;
import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.util.ConfigurationContext;
import inetsoft.web.admin.content.repository.model.CreateUpdateMVRequest;
import org.junit.jupiter.api.*;
import org.mockito.AdditionalAnswers;
import org.mockito.MockedStatic;

import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77260: the MV analysis status map is shared by all organizations and keyed only by the
 * analysis id, so every id lookup must resolve only for the user and org that started the
 * analysis.
 */
@Tag("core")
class MVAnalysisOwnershipTest {
   private static final String ORG_A = "orgA";
   private static final String ORG_B = "orgB";
   private static final String DEFAULT_ORG = "host-org";
   private static final String ID = "analysis-1";

   private Map<String, MVSupportService.AnalysisStatus> backing;
   private Cluster cluster;
   private MVManager mvManager;
   private MVSupportService service;
   private MockedStatic<OrganizationManager> orgManagerStatic;
   private MockedStatic<ConfigurationContext> contextStatic;

   private Principal owner;
   private Principal foreignOrgAdmin;
   private Principal sameOrgOtherUser;
   private Principal ownerInOtherOrg;

   @BeforeEach
   @SuppressWarnings("unchecked")
   void setUp() {
      backing = new HashMap<>();
      DistributedMap<String, MVSupportService.AnalysisStatus> map =
         mock(DistributedMap.class, AdditionalAnswers.delegatesTo(backing));
      cluster = mock(Cluster.class);
      when(cluster.getMap(anyString())).thenAnswer(inv -> map);

      owner = principal("owner~;~" + ORG_B);
      foreignOrgAdmin = principal("admin~;~" + ORG_A);
      sameOrgOtherUser = principal("other~;~" + ORG_B);
      // same principal name, but currently managing another org (site admin switched org)
      ownerInOtherOrg = principal("owner~;~" + ORG_B);

      OrganizationManager orgManager = mock(OrganizationManager.class);
      when(orgManager.getCurrentOrgID()).thenReturn(ORG_B.toLowerCase());
      when(orgManager.getCurrentOrgID(any())).thenAnswer(inv -> {
         Principal p = inv.getArgument(0);

         if(p == owner || p == sameOrgOtherUser) {
            return ORG_B;
         }

         if(p == foreignOrgAdmin || p == ownerInOtherOrg) {
            return ORG_A;
         }

         return DEFAULT_ORG;
      });
      orgManagerStatic = mockStatic(OrganizationManager.class);
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);

      // AnalysisResult reads the status map through the Spring context
      ConfigurationContext context = mock(ConfigurationContext.class);
      when(context.getSpringBean(Cluster.class)).thenReturn(cluster);
      contextStatic = mockStatic(ConfigurationContext.class);
      contextStatic.when(ConfigurationContext::getContext).thenReturn(context);

      mvManager = mock(MVManager.class);
      service = new MVSupportService(mvManager, mock(SecurityEngine.class),
                                     mock(ScheduleManager.class), cluster);
   }

   @AfterEach
   void tearDown() {
      contextStatic.close();
      orgManagerStatic.close();
   }

   @Test
   void foreignOrgCallerIsRefusedOnEveryLookup() {
      backing.put(ID, stampedStatus(ORG_B, owner.getName(), List.of()));

      assertThrows(IllegalStateException.class,
                   () -> service.getAnalysisResult(ID, foreignOrgAdmin));
      assertThrows(IllegalStateException.class,
                   () -> service.getMVStatusList(ID, foreignOrgAdmin));
   }

   @Test
   void ownerCanReadOwnAnalysis() {
      backing.put(ID, stampedStatus(ORG_B, owner.getName(), List.of()));

      MVSupportService.AnalysisResult result = service.getAnalysisResult(ID, owner);

      assertEquals(ID, result.getId());
      assertEquals(1, result.getExceptions().size());
      assertTrue(service.getMVStatusList(ID, owner).isEmpty());
   }

   @Test
   void otherUserInSameOrgIsRefused() {
      backing.put(ID, stampedStatus(ORG_B, owner.getName(), List.of()));

      assertThrows(IllegalStateException.class,
                   () -> service.getAnalysisResult(ID, sameOrgOtherUser));
   }

   @Test
   void sameUserManagingAnotherOrgIsRefused() {
      backing.put(ID, stampedStatus(ORG_B, owner.getName(), List.of()));

      assertThrows(IllegalStateException.class,
                   () -> service.getAnalysisResult(ID, ownerInOtherOrg));
   }

   @Test
   void orgComparisonIgnoresCase() {
      backing.put(ID, stampedStatus(ORG_B.toLowerCase(), owner.getName(), List.of()));

      assertEquals(ID, service.getAnalysisResult(ID, owner).getId());
   }

   @Test
   void unstampedStatusIsRefused() {
      backing.put(ID, new MVSupportService.AnalysisStatus(
         List.of(), new ArrayList<>(), new HashMap<>(), List.of(), null));

      assertThrows(IllegalStateException.class, () -> service.getAnalysisResult(ID, owner));
   }

   @Test
   void missingAnalysisIsRefusedLikeForeignOne() {
      IllegalStateException missing = assertThrows(
         IllegalStateException.class, () -> service.getAnalysisResult("nope", owner));
      backing.put(ID, stampedStatus(ORG_B, owner.getName(), List.of()));
      IllegalStateException foreign = assertThrows(
         IllegalStateException.class, () -> service.getAnalysisResult(ID, foreignOrgAdmin));

      assertEquals(missing.getMessage(), foreign.getMessage());
   }

   @Test
   void nullPrincipalAnalysisIsReadableByNullPrincipalOnly() {
      backing.put(ID, stampedStatus(DEFAULT_ORG, null, List.of()));

      assertEquals(ID, service.getAnalysisResult(ID, null).getId());
      assertThrows(IllegalStateException.class, () -> service.getAnalysisResult(ID, owner));
   }

   @Test
   void analyzeStampsOwnerBeforeReturningId() throws Exception {
      MVSupportService.AnalysisResult started = service.analyze(new String[0], owner);

      assertEquals(started.getId(), service.getAnalysisResult(started.getId(), owner).getId());
      assertThrows(IllegalStateException.class,
                   () -> service.getAnalysisResult(started.getId(), foreignOrgAdmin));
   }

   @Test
   void analyzeWithNullPrincipalIsReadableWithNullPrincipal() throws Exception {
      // security off: no security provider and no principal
      MVSupportService.AnalysisResult started = service.analyze(new String[0], null);

      assertEquals(started.getId(), service.getAnalysisResult(started.getId(), null).getId());
   }

   @Test
   void setDataCycleKeepsOwnerAccess() {
      MVDef def = mock(MVDef.class);
      when(def.getName()).thenReturn("mv1");
      MVSupportService.MVStatus mvStatus = mock(MVSupportService.MVStatus.class);
      when(mvStatus.getDefinition()).thenReturn(def);
      backing.put(ID, stampedStatus(ORG_B, owner.getName(), List.of(mvStatus)));

      service.setDataCycle(List.of("mv1"), service.getAnalysisResult(ID, owner), "cycle1", ORG_B);

      MVSupportService.AnalysisStatus rewritten = backing.get(ID);
      assertEquals(ORG_B, rewritten.getOrgId());
      assertEquals(owner.getName(), rewritten.getOwner());
      assertEquals(1, service.getMVStatusList(ID, owner).size());
      assertThrows(IllegalStateException.class,
                   () -> service.getAnalysisResult(ID, foreignOrgAdmin));
   }

   @Test
   void mvServiceCheckStatusAndCreateRefuseForeignCaller() throws Throwable {
      backing.put(ID, stampedStatus(ORG_B, owner.getName(), List.of()));
      MVService mvService = new MVService(
         mock(ContentRepositoryTreeService.class), service, cluster, mvManager,
         mock(inetsoft.sree.internal.DataCycleManager.class), mock(SecurityEngine.class),
         mock(inetsoft.mv.data.MVStorage.class));
      CreateUpdateMVRequest request = mock(CreateUpdateMVRequest.class);

      assertThrows(IllegalStateException.class,
                   () -> mvService.checkAnalyzeStatus(ID, foreignOrgAdmin));
      assertThrows(IllegalStateException.class,
                   () -> mvService.create("create-1", ID, request, foreignOrgAdmin));
      assertFalse(backing.containsKey("create-1"), "no background create job may be started");
   }

   @Test
   void controllerExceptionsEndpointRefusesForeignCaller() {
      backing.put(ID, stampedStatus(ORG_B, owner.getName(), List.of()));
      MVController controller = new MVController(
         mock(MVService.class), service, mock(inetsoft.sree.security.SecurityProvider.class),
         mock(SecurityEngine.class));

      assertThrows(IllegalStateException.class, () -> controller.setCycle(ID, foreignOrgAdmin));
      assertEquals(1, controller.setCycle(ID, owner).exceptions().size());
   }

   private static MVSupportService.AnalysisStatus stampedStatus(
      String orgId, String ownerName, List<MVSupportService.MVStatus> results)
   {
      List<UserInfo> exceptions = new ArrayList<>();
      exceptions.add(new UserInfo("OrgB/SecretSheet", "", "org B failure detail"));
      return new MVSupportService.AnalysisStatus(
         new ArrayList<>(), exceptions, new HashMap<>(), results, null, orgId, ownerName);
   }

   private static Principal principal(String name) {
      Principal principal = mock(Principal.class);
      when(principal.getName()).thenReturn(name);
      return principal;
   }
}
