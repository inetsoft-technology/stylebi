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
package inetsoft.web.admin.ai.cluster;

import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.sree.internal.cluster.DistributedMap;
import inetsoft.uql.XPrincipal;
import inetsoft.util.Tool;
import inetsoft.util.audit.Audit;
import inetsoft.web.admin.ai.AdminAiCallerGuard;
import inetsoft.web.admin.ai.ResolvedPlan;
import inetsoft.web.admin.ai.TaskAuditToken;
import inetsoft.web.admin.cluster.ClusterEnabledModel;
import inetsoft.web.admin.cluster.ClusterService;
import inetsoft.web.cluster.ServerClusterStatus;
import org.junit.jupiter.api.*;
import org.mockito.Answers;
import org.mockito.MockedStatic;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bugs #82187 and #82189: the AI-admin cluster tools must read the cluster's live node list on every
 * request. A node that joins after the services were built must be listed, and a node that has left
 * must no longer be listed, found, previewed or reported Running -- even though its last status-map
 * entry (OK, not paused) is still there.
 *
 * <p>Drives the real {@code ServerClusterClient} over a mock {@link Cluster} whose node set changes
 * between requests, the way Ignite's topology does when a node joins or stops.
 */
@Tag("core")
class ClusterLiveNodeListTest {
   private static final String A = "10.0.0.1";
   private static final String B = "10.0.0.2";

   private final Set<String> nodes = new LinkedHashSet<>();
   private final Map<String, ServerClusterStatus> statusMap = new ConcurrentHashMap<>();
   private Cluster cluster;
   private ClusterService clusterService;
   private MockedStatic<Cluster> clusterStatic;
   private MockedStatic<Tool> tool;
   private MockedStatic<AdminAiCallerGuard> guard;
   private MockedStatic<OrganizationManager> orgManager;
   private final XPrincipal admin = mock(XPrincipal.class);

   @SuppressWarnings("unchecked")
   @BeforeEach void setUp() {
      cluster = mock(Cluster.class, withSettings().strictness(Strictness.LENIENT));
      when(cluster.getClusterNodes()).thenAnswer(inv -> new LinkedHashSet<>(nodes));
      when(cluster.getClusterNodeProperty(anyString(), eq("reportServer"))).thenReturn("true");
      when(cluster.getClusterNodeHost(anyString()))
         .thenAnswer(inv -> ((String) inv.getArgument(0)).split(":")[0]);
      DistributedMap<String, ServerClusterStatus> map = mock(DistributedMap.class,
         withSettings().strictness(Strictness.LENIENT));
      when(map.get(any())).thenAnswer(inv -> statusMap.get(inv.getArgument(0)));
      when(cluster.getMap(anyString())).thenAnswer(inv -> map);

      clusterStatic = mockStatic(Cluster.class);
      clusterStatic.when(Cluster::getInstance).thenReturn(cluster);

      clusterService = mock(ClusterService.class, withSettings().strictness(Strictness.LENIENT));
      when(clusterService.getClusterEnabled())
         .thenReturn(ClusterEnabledModel.builder().enabled(true).pauseEnabled(true).build());

      tool = mockStatic(Tool.class, withSettings().strictness(Strictness.LENIENT)
         .defaultAnswer(Answers.CALLS_REAL_METHODS));
      tool.when(() -> Tool.encryptPassword(anyString()))
         .thenAnswer(inv -> "TKN:" + inv.getArgument(0));
      tool.when(() -> Tool.decryptPassword(anyString()))
         .thenAnswer(inv -> ((String) inv.getArgument(0)).substring(4));

      guard = mockStatic(AdminAiCallerGuard.class, withSettings().strictness(Strictness.LENIENT));
      guard.when(AdminAiCallerGuard::requireBearerAuthenticatedRequest).thenAnswer(inv -> null);
      OrganizationManager manager = mock(OrganizationManager.class,
         withSettings().strictness(Strictness.LENIENT));
      when(manager.isSiteAdmin(any(java.security.Principal.class))).thenReturn(true);
      orgManager = mockStatic(OrganizationManager.class, withSettings().strictness(Strictness.LENIENT));
      orgManager.when(OrganizationManager::getInstance).thenReturn(manager);
   }

   @AfterEach void tearDown() {
      orgManager.close();
      guard.close();
      tool.close();
      clusterStatic.close();
   }

   // ----------------------------------------------------------------- #82187: a node joins later

   @Test void listGetAndPreviewSeeANodeThatJoinedAfterStartup() {
      join(A);
      Services s = services();

      join(B);

      assertEquals(Set.of(A, B), servers(s.controller.listClusterNodes(admin())));
      assertEquals("Running", s.controller.getClusterNode(B, admin()).status());
      ResolvedPlan plan = s.planService.resolve(request(pause(B)));
      assertEquals("Paused", plan.changes().get(0).proposedValue());
   }

   // ----------------------------------------------------------------- #82189: a node leaves

   @Test void listGetAndPreviewDropANodeThatLeftTheCluster() {
      join(A);
      join(B);
      Services s = services();
      // queried while alive, so a long-lived client would have cached its address
      assertTrue(s.controller.getClusterNode(B, admin()).reachable());

      leave(B);

      assertEquals(Set.of(A), servers(s.controller.listClusterNodes(admin())));
      ResponseStatusException notFound = assertThrows(ResponseStatusException.class,
         () -> s.controller.getClusterNode(B, admin()));
      assertEquals(HttpStatus.NOT_FOUND, notFound.getStatusCode());
      assertTrue(notFound.getReason().contains("live node list"), notFound.getReason());
      IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
         () -> s.planService.resolve(request(pause(B))));
      assertTrue(refused.getMessage().contains("live node list"), refused.getMessage());
      assertTrue(refused.getMessage().contains("has left the cluster"), refused.getMessage());
   }

   @Test void applyOnANodeThatLeftAfterPreviewIsRefusedWithTheReason() {
      join(A);
      join(B);
      Services s = services();
      ClusterApplyRequest req = applyRequest(s.planService.resolve(request(pause(B))), pause(B));

      leave(B);

      IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
         () -> s.applyService.apply(req, mock(XPrincipal.class)));
      assertTrue(refused.getMessage().contains("has left the cluster"), refused.getMessage());
   }

   @Test void applyWhoseNodeLeavesDuringThePauseFailsSayingItIsNotInTheCluster() throws Exception {
      join(A);
      join(B);
      Services s = services();
      ClusterApplyRequest req = applyRequest(s.planService.resolve(request(pause(B))), pause(B));
      // the node stops while the pause message is in flight
      doAnswer(inv -> {
         leave(B);
         return null;
      }).when(clusterService).pauseServers(any());

      try(MockedStatic<Audit> audit = mockStatic(Audit.class)) {
         audit.when(Audit::getInstance).thenReturn(mock(Audit.class));
         ClusterApplyResult result = s.applyService.apply(req, mock(XPrincipal.class));
         ClusterApplyOutcome outcome = result.results().get(0);

         assertEquals("failed", outcome.status());
         assertEquals("Stopped", outcome.after());
         assertTrue(outcome.error().contains("not in the cluster"), outcome.error());
         assertTrue(outcome.error().contains("could not be confirmed"), outcome.error());
      }
   }

   // ----------------------------------------------------------------- helpers

   private Services services() {
      ClusterClientFactory clients = new ClusterClientFactory(cluster);
      ClusterChangePlanService planService = new ClusterChangePlanService(clusterService, clients);
      ClusterChangesetApplyService applyService =
         new ClusterChangesetApplyService(planService, clusterService, clients);
      AdminClusterController controller =
         new AdminClusterController(planService, applyService, clients);
      return new Services(controller, planService, applyService);
   }

   private record Services(AdminClusterController controller, ClusterChangePlanService planService,
                           ClusterChangesetApplyService applyService)
   {
   }

   private void join(String server) {
      nodes.add(server + ":5701");
      ServerClusterStatus status = new ServerClusterStatus();
      status.setStatus(ServerClusterStatus.Status.OK);
      status.setPaused(false);
      statusMap.put(server, status);
   }

   /** Leaves the node's last status-map entry in place, as a stopped node does. */
   private void leave(String server) {
      nodes.remove(server + ":5701");
   }

   private static Set<String> servers(List<ClusterNodeStatus> list) {
      Set<String> servers = new HashSet<>();
      list.forEach(n -> servers.add(n.server()));
      return servers;
   }

   private XPrincipal admin() {
      return admin;
   }

   private static ClusterChangeRequest pause(String server) {
      ClusterChangeRequest c = new ClusterChangeRequest();
      c.setVerb(ClusterChangeRequest.VERB_PAUSE);
      c.setServer(server);
      return c;
   }

   private static ClusterChangePlanRequest request(ClusterChangeRequest change) {
      ClusterChangePlanRequest req = new ClusterChangePlanRequest();
      req.setTask("task");
      req.setChanges(List.of(change));
      return req;
   }

   private static ClusterApplyRequest applyRequest(ResolvedPlan plan, ClusterChangeRequest change) {
      ClusterApplyRequest req = new ClusterApplyRequest();
      req.setTask("task");
      req.setChanges(List.of(change));
      req.setPlanHash(plan.planHash());
      req.setReviewOutcome("looks good");
      req.setTaskToken(TaskAuditToken.issue(plan.planHash(), "task"));
      return req;
   }
}
