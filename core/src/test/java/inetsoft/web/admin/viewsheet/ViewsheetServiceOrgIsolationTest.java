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
      lenient().when(orgManager.getCurrentOrgID()).thenReturn("orgA");
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
}
