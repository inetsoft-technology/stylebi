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
package inetsoft.web.admin.query;

import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.internal.cluster.MessageEvent;
import inetsoft.sree.schedule.ScheduleClient;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.uql.util.QueryInfo;
import inetsoft.uql.util.XNodeTable;
import inetsoft.uql.util.QueryManager;
import inetsoft.web.admin.monitoring.MonitoringDataService;
import inetsoft.web.cluster.ServerClusterClient;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77059: cancelling queries from EM monitoring must be limited to the
 * current organization, matching the org filter applied to the monitoring list.
 */
@Tag("core")
@ExtendWith(MockitoExtension.class)
class QueryServiceOrgIsolationTest {
   @Mock private ScheduleClient scheduleClient;
   @Mock private ServerClusterClient clusterClient;
   @Mock private MonitoringDataService monitoringDataService;
   @Mock private Cluster cluster;
   @Mock private OrganizationManager orgManager;

   private MockedStatic<OrganizationManager> orgManagerStatic;
   private QueryService service;

   @BeforeEach
   void setUp() {
      orgManagerStatic = mockStatic(OrganizationManager.class);
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
      lenient().when(orgManager.getCurrentOrgID()).thenReturn("orgA");
      service = spy(new QueryService(scheduleClient, clusterClient, monitoringDataService, cluster));
      lenient().doReturn(true).when(service).isLevelQualified(anyString());
   }

   @AfterEach
   void tearDown() {
      XNodeTable.queryMap.clear();
      orgManagerStatic.close();
   }

   private QueryManager addQuery(String id, String ownerOrg) {
      QueryManager qmr = mock(QueryManager.class);
      QueryInfo info = new QueryInfo(id, "t1", "q", new IdentityID("user", ownerOrg),
                                     "asset", 0, new Date());
      info.setQueryManager(qmr);
      XNodeTable.queryMap.put(id, info);
      return qmr;
   }

   @Test
   void destroyLocal_otherOrgQuery_notCancelled() throws Exception {
      QueryManager qmr = addQuery("QUERY1_1_q", "orgB");

      service.destroyClusterQueries(null, new String[] { "QUERY1_1_q" });

      verify(qmr, never()).cancel();
      assertTrue(XNodeTable.queryMap.containsKey("QUERY1_1_q"));
   }

   @Test
   void destroyLocal_sameOrgQuery_cancelled() throws Exception {
      QueryManager qmr = addQuery("QUERY1_1_q", "orgA");

      service.destroyClusterQueries(null, new String[] { "QUERY1_1_q" });

      verify(qmr).cancel();
   }

   @Test
   void destroyRemote_messageCarriesCurrentOrg() throws Exception {
      service.destroyClusterQueries("node-2", new String[] { "QUERY1_1_q" });

      ArgumentCaptor<DestroyQueriesMessage> captor =
         ArgumentCaptor.forClass(DestroyQueriesMessage.class);
      verify(cluster).exchangeMessages(
         eq("node-2"), captor.capture(), eq(DestroyQueriesCompleteMessage.class));
      assertArrayEquals(new String[] { "QUERY1_1_q" }, captor.getValue().getIds());
      assertEquals("orgA", captor.getValue().getOrgID());
   }

   @Test
   void destroyMessage_otherOrgQuery_notCancelled() throws Exception {
      QueryManager qmr = addQuery("QUERY1_1_q", "orgA");
      MessageEvent event = mock(MessageEvent.class);
      when(event.getSender()).thenReturn("node-1");
      when(event.getMessage())
         .thenReturn(new DestroyQueriesMessage(new String[] { "QUERY1_1_q" }, "orgB"));

      service.messageReceived(event);

      verify(qmr, never()).cancel();
   }
}
