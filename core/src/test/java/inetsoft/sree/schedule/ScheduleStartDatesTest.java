/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

/*
 * Test strategy
 *
 * The scheduler status table is built from ScheduleClient.getScheduleStartDates(). In a cloud
 * runner cluster every node runs its own scheduler, so the CloudRunnerServerScheduleClient lists
 * all live non-client nodes (a stopped one with a null start time), not only the node that answered.
 * Cluster and ScheduleServer are mocks; the node properties are scripted per node.
 *
 * Coverage scope:
 *   [two nodes, serving = A]   rows A, B; A first
 *   [two nodes, serving = B]   same row set; B first
 *   [remote not started]       a remote node without the property is listed with a null date
 *                              (Not ready), the same rows from every serving node
 *   [local not started]        the serving node is still listed, with a null start time
 *   [client member]            client members are never listed (getClusterNodes() excludes them)
 *   [remote start time]        the remote row carries that node's own start time, not the
 *                              serving node's
 *   [plain client]             the base class maps getScheduleServers() through
 *                              getScheduleStartDate(server), unchanged
 */

import inetsoft.sree.internal.cluster.Cluster;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("core")
class ScheduleStartDatesTest {
   private static final String NODE_A = "node-a";
   private static final String NODE_B = "node-b";
   private static final String HOST_A = "172.22.0.3";
   private static final String HOST_B = "172.22.0.4";
   private static final long START_A = 1_700_000_000_000L;
   private static final long START_B = 1_700_000_500_000L;

   private Cluster cluster;
   private ScheduleServer scheduleServer;

   @BeforeEach
   void setUp() {
      cluster = mock(Cluster.class);
      scheduleServer = mock(ScheduleServer.class);
      when(cluster.getClusterNodeHost(NODE_A)).thenReturn(HOST_A);
      when(cluster.getClusterNodeHost(NODE_B)).thenReturn(HOST_B);
      when(cluster.getClusterNodes()).thenReturn(new LinkedHashSet<>(List.of(NODE_A, NODE_B)));
      when(cluster.getClusterNodeProperty(NODE_A, Scheduler.SCHEDULER_START_TIME))
         .thenReturn(START_A);
      when(cluster.getClusterNodeProperty(NODE_B, Scheduler.SCHEDULER_START_TIME))
         .thenReturn(START_B);
   }

   /** Makes {@code local} the serving node, with its in-process scheduler started at the time. */
   private CloudRunnerServerScheduleClient servedBy(String local, Long localStart)
      throws Exception
   {
      when(cluster.getLocalMember()).thenReturn(local);
      when(scheduleServer.isLocalServerRunning()).thenReturn(localStart != null);

      if(localStart != null) {
         when(scheduleServer.getStartTime()).thenReturn(new Date(localStart));
      }

      return new CloudRunnerServerScheduleClient(scheduleServer, cluster);
   }

   @Test
   void listsBothNodesWithServingNodeFirstWhenAServes() throws Exception {
      Map<String, Date> dates = servedBy(NODE_A, START_A).getScheduleStartDates();

      assertEquals(List.of(HOST_A, HOST_B), new ArrayList<>(dates.keySet()));
      assertEquals(new Date(START_A), dates.get(HOST_A));
      assertEquals(new Date(START_B), dates.get(HOST_B));
   }

   @Test
   void listsTheSameRowsWithServingNodeFirstWhenBServes() throws Exception {
      Map<String, Date> dates = servedBy(NODE_B, START_B).getScheduleStartDates();

      assertEquals(List.of(HOST_B, HOST_A), new ArrayList<>(dates.keySet()));
      assertEquals(new Date(START_A), dates.get(HOST_A));
      assertEquals(new Date(START_B), dates.get(HOST_B));
   }

   @Test
   void rowSetDoesNotDependOnTheServingNode() throws Exception {
      Map<String, Date> servedByA = servedBy(NODE_A, START_A).getScheduleStartDates();
      Map<String, Date> servedByB = servedBy(NODE_B, START_B).getScheduleStartDates();

      assertEquals(servedByA, servedByB);
   }

   @Test
   void remoteNodeWithoutStartTimeIsListedAsNotReady() throws Exception {
      when(cluster.getClusterNodeProperty(NODE_B, Scheduler.SCHEDULER_START_TIME))
         .thenReturn(null);

      Map<String, Date> dates = servedBy(NODE_A, START_A).getScheduleStartDates();

      assertEquals(List.of(HOST_A, HOST_B), new ArrayList<>(dates.keySet()));
      assertEquals(new Date(START_A), dates.get(HOST_A));
      assertTrue(dates.containsKey(HOST_B));
      assertNull(dates.get(HOST_B));
   }

   @Test
   void stoppedRemoteNodeGivesTheSameRowsFromEveryServingNode() throws Exception {
      when(cluster.getClusterNodeProperty(NODE_B, Scheduler.SCHEDULER_START_TIME))
         .thenReturn(null);

      Map<String, Date> servedByA = servedBy(NODE_A, START_A).getScheduleStartDates();
      Map<String, Date> servedByB = servedBy(NODE_B, null).getScheduleStartDates();

      Map<String, Date> expected = new HashMap<>();
      expected.put(HOST_A, new Date(START_A));
      expected.put(HOST_B, null);
      assertEquals(expected, servedByA);
      assertEquals(expected, servedByB);
   }

   @Test
   void remoteNodeWithoutHostIsNotListed() throws Exception {
      when(cluster.getClusterNodeHost(NODE_B)).thenReturn(null);

      Map<String, Date> dates = servedBy(NODE_A, START_A).getScheduleStartDates();

      assertEquals(List.of(HOST_A), new ArrayList<>(dates.keySet()));
   }

   @Test
   void departedNodeWithLeftoverPropertyIsNotListed() throws Exception {
      String departed = "node-gone";
      when(cluster.getClusterNodeHost(departed)).thenReturn("172.22.0.8");
      when(cluster.getClusterNodeProperty(departed, Scheduler.SCHEDULER_START_TIME))
         .thenReturn(START_B);

      Map<String, Date> dates = servedBy(NODE_A, START_A).getScheduleStartDates();

      assertFalse(dates.containsKey("172.22.0.8"));
      assertEquals(2, dates.size());
   }

   @Test
   void sameHostCollisionPrefersTheNonNullStartTimeInEitherOrder() throws Exception {
      String stopped = "node-b-stopped";
      String running = "node-b-running";
      when(cluster.getClusterNodeHost(stopped)).thenReturn(HOST_B);
      when(cluster.getClusterNodeHost(running)).thenReturn(HOST_B);
      when(cluster.getClusterNodeProperty(stopped, Scheduler.SCHEDULER_START_TIME))
         .thenReturn(null);
      when(cluster.getClusterNodeProperty(running, Scheduler.SCHEDULER_START_TIME))
         .thenReturn(START_B);

      for(List<String> order : List.of(List.of(NODE_A, stopped, running),
                                       List.of(NODE_A, running, stopped)))
      {
         when(cluster.getClusterNodes()).thenReturn(new LinkedHashSet<>(order));
         Map<String, Date> dates = servedBy(NODE_A, START_A).getScheduleStartDates();

         assertEquals(2, dates.size());
         assertEquals(new Date(START_B), dates.get(HOST_B));
      }
   }

   @Test
   void servingNodeWhoseSchedulerIsNotStartedIsStillListedAsNotReady() throws Exception {
      Map<String, Date> dates = servedBy(NODE_A, null).getScheduleStartDates();

      assertEquals(List.of(HOST_A, HOST_B), new ArrayList<>(dates.keySet()));
      assertNull(dates.get(HOST_A));
      assertEquals(new Date(START_B), dates.get(HOST_B));
   }

   @Test
   void clientMembersAreNotListed() throws Exception {
      String client = "cloud-runner-task";
      when(cluster.getClusterNodeHost(client)).thenReturn("172.22.0.9");
      when(cluster.getClusterNodeProperty(client, Scheduler.SCHEDULER_START_TIME))
         .thenReturn(START_B);
      // the default getClusterNodes() excludes clients, as the production cluster does
      when(cluster.getClusterNodes()).thenReturn(new LinkedHashSet<>(List.of(NODE_A, NODE_B)));
      when(cluster.getClusterNodes(true))
         .thenReturn(new LinkedHashSet<>(List.of(NODE_A, NODE_B, client)));

      Map<String, Date> dates = servedBy(NODE_A, START_A).getScheduleStartDates();

      assertFalse(dates.containsKey("172.22.0.9"));
      assertEquals(2, dates.size());
   }

   @Test
   void remoteRowCarriesTheRemoteNodesOwnStartTime() throws Exception {
      // the serving node started long after the remote one; the remote row must not take the
      // serving node's in-process start time
      Map<String, Date> dates = servedBy(NODE_B, START_B + 999_000L).getScheduleStartDates();

      assertEquals(new Date(START_A), dates.get(HOST_A));
      assertEquals(new Date(START_B + 999_000L), dates.get(HOST_B));
   }

   @Test
   void plainClientMapsScheduleServersThroughStartDate() {
      Date dateA = new Date(START_A);
      ScheduleClient plain = new ScheduleClient(cluster) {
         @Override
         public String[] getScheduleServers() {
            return new String[] { HOST_A, HOST_B, HOST_A };
         }

         @Override
         public Date getScheduleStartDate(String server) {
            return HOST_A.equals(server) ? dateA : null;
         }
      };

      Map<String, Date> dates = plain.getScheduleStartDates();

      assertEquals(List.of(HOST_A, HOST_B), new ArrayList<>(dates.keySet()));
      assertEquals(dateA, dates.get(HOST_A));
      assertNull(dates.get(HOST_B));
   }
}
