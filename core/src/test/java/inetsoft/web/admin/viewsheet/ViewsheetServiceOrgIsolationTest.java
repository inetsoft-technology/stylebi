/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.web.admin.viewsheet;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.internal.cluster.MessageEvent;
import inetsoft.sree.schedule.ScheduleClient;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.uql.viewsheet.ViewsheetLifecycleMessageChannel;
import inetsoft.uql.XPrincipal;
import inetsoft.web.admin.monitoring.MonitoringDataService;
import inetsoft.web.cluster.ServerClusterClient;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77059: destroying viewsheets from EM monitoring must be limited to the
 * current organization, matching the org filter applied to the monitoring lists.
 */
@Tag("core")
@ExtendWith(MockitoExtension.class)
class ViewsheetServiceOrgIsolationTest {
   @Mock private inetsoft.analytic.composition.ViewsheetService engine;
   @Mock private ScheduleClient scheduleClient;
   @Mock private ServerClusterClient client;
   @Mock private ViewsheetLifecycleMessageChannel lifecycleChannel;
   @Mock private MonitoringDataService monitoringDataService;
   @Mock private Cluster cluster;
   @Mock private OrganizationManager orgManager;

   private MockedStatic<OrganizationManager> orgManagerStatic;
   private ViewsheetService service;

   @BeforeEach
   void setUp() {
      orgManagerStatic = mockStatic(OrganizationManager.class);
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
      lenient().when(orgManager.getCurrentOrgID(any())).thenReturn("orgA");
      service = new ViewsheetService(engine, scheduleClient, client, lifecycleChannel,
                                     monitoringDataService, cluster);
   }

   @AfterEach
   void tearDown() {
      orgManagerStatic.close();
   }

   private void stubSheet(String id, String ownerOrg) {
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      XPrincipal user = mock(XPrincipal.class);
      lenient().when(user.getName()).thenReturn(new IdentityID("user", ownerOrg).convertToKey());
      lenient().when(rvs.getUser()).thenReturn(user);
      lenient().when(engine.sheetExists(id)).thenReturn(true);
      lenient().when(engine.getSheet(eq(id), isNull())).thenReturn(rvs);
   }

   @Test
   void destroyLocal_otherOrgViewsheet_notClosed() throws Exception {
      stubSheet("vs-1", "orgB");

      service.destroyClusterNodeViewsheets(null, new String[] { "vs-1" });

      verify(engine, never()).closeViewsheet(anyString(), any());
   }

   @Test
   void destroyLocal_sameOrgViewsheet_closed() throws Exception {
      stubSheet("vs-1", "orgA");

      service.destroyClusterNodeViewsheets(null, new String[] { "vs-1" });

      verify(engine).closeViewsheet("vs-1", null);
   }

   @Test
   void destroyLocal_mixedOrgs_onlySameOrgClosed() throws Exception {
      stubSheet("vs-1", "orgB");
      stubSheet("vs-2", "orgA");

      service.destroyClusterNodeViewsheets(null, new String[] { "vs-1", "vs-2" });

      verify(engine, never()).closeViewsheet(eq("vs-1"), any());
      verify(engine).closeViewsheet("vs-2", null);
   }

   @Test
   void destroyRemote_messageCarriesCurrentOrg() throws Exception {
      service.destroyClusterNodeViewsheets("node-2", new String[] { "vs-1" });

      ArgumentCaptor<DestroyViewsheetMessage> captor =
         ArgumentCaptor.forClass(DestroyViewsheetMessage.class);
      verify(cluster).sendMessage(eq("node-2"), captor.capture());
      assertArrayEquals(new String[] { "vs-1" }, captor.getValue().getIds());
      assertEquals("orgA", captor.getValue().getOrgID());
   }

   @Test
   void destroyMessage_otherOrgViewsheet_notClosed() throws Exception {
      stubSheet("vs-1", "orgA");
      MessageEvent event = mock(MessageEvent.class);
      when(event.getMessage()).thenReturn(new DestroyViewsheetMessage(new String[] { "vs-1" }, "orgB"));

      service.messageReceived(event);

      verify(engine, never()).closeViewsheet(anyString(), any());
   }

   /**
    * Bug #77266: the no-arg getCurrentOrgID() lower-cases the org id while the org id of
    * a viewsheet owner is case-preserved, so a mixed-case org could neither list nor
    * destroy its own viewsheets.
    */
   private void useMixedCaseOrg() {
      lenient().when(orgManager.getCurrentOrgID()).thenReturn("mixedorg");
      lenient().when(orgManager.getCurrentOrgID(any())).thenReturn("MixedOrg");
   }

   private ViewsheetModel model(String id, String ownerOrg) {
      IdentityID owner = new IdentityID("user", ownerOrg);
      return ViewsheetModel.builder()
         .id(id).state(ViewsheetModel.State.OPEN).user(owner).monitorUser(owner)
         .dateCreated(0L).dateAccessed(0L).build();
   }

   @Test
   void destroyLocal_mixedCaseOrgViewsheet_closed() throws Exception {
      useMixedCaseOrg();
      stubSheet("vs-1", "MixedOrg");

      service.destroyClusterNodeViewsheets(null, new String[] { "vs-1" });

      verify(engine).closeViewsheet("vs-1", null);
   }

   @Test
   void destroyLocal_mixedCaseOrg_otherOrgViewsheetNotClosed() throws Exception {
      useMixedCaseOrg();
      stubSheet("vs-1", "OtherOrg");

      service.destroyClusterNodeViewsheets(null, new String[] { "vs-1" });

      verify(engine, never()).closeViewsheet(anyString(), any());
   }

   @Test
   void destroyRemote_mixedCaseOrg_messageCarriesCasePreservedOrg() throws Exception {
      useMixedCaseOrg();

      service.destroyClusterNodeViewsheets("node-2", new String[] { "vs-1" });

      ArgumentCaptor<DestroyViewsheetMessage> captor =
         ArgumentCaptor.forClass(DestroyViewsheetMessage.class);
      verify(cluster).sendMessage(eq("node-2"), captor.capture());
      assertEquals("MixedOrg", captor.getValue().getOrgID());
   }

   @Test
   void destroyMessage_mixedCaseOrgViewsheet_closed() throws Exception {
      stubSheet("vs-1", "MixedOrg");
      MessageEvent event = mock(MessageEvent.class);
      when(event.getMessage())
         .thenReturn(new DestroyViewsheetMessage(new String[] { "vs-1" }, "MixedOrg"));

      service.messageReceived(event);

      verify(engine).closeViewsheet("vs-1", null);
   }

   @Test
   void getViewsheets_mixedCaseOrg_listsOwnOrgOnly() {
      useMixedCaseOrg();
      when(scheduleClient.isCloud()).thenReturn(true);
      ViewsheetMetrics metrics = mock(ViewsheetMetrics.class);
      when(metrics.activeViewsheets()).thenReturn(
         java.util.List.of(model("own", "MixedOrg"), model("other", "OtherOrg")));
      when(client.getMetrics(any(), eq("node-1"))).thenReturn(metrics);

      java.util.List<ViewsheetModel> result =
         service.getViewsheets(ViewsheetModel.State.OPEN, "node-1");

      assertEquals(1, result.size());
      assertEquals("own", result.get(0).id());
   }

   @Test
   void getOpenViewsheets_mixedCaseOrg_listsOwnOrgOnly() {
      useMixedCaseOrg();
      when(scheduleClient.isCloud()).thenReturn(true);
      ViewsheetMetrics metrics = mock(ViewsheetMetrics.class);
      when(metrics.activeViewsheets()).thenReturn(
         java.util.List.of(model("own", "MixedOrg"), model("other", "OtherOrg")));
      when(client.getMetrics(any(), eq("node-1"))).thenReturn(metrics);

      assertEquals(1, service.getOpenViewsheets("node-1", null).size());
   }
}
