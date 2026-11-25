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
package inetsoft.sree.schedule;

import inetsoft.report.internal.LicenseException;
import inetsoft.sree.internal.cluster.Cluster;
import org.junit.jupiter.api.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #82198 -- a second dedicated scheduler node (scheduler=true) was refused with
 * "Only one scheduler instance is allowed with the Community Edition", also on an enterprise
 * server. The one-scheduler-per-cluster rule is kept for every edition, but the message must not
 * name an edition and must tell the administrator which scheduler nodes are running.
 *
 * <p>Drives the real private {@code Scheduler.checkLicense()} on a Scheduler created without its
 * constructor, with the mock {@link Cluster} bean of {@link SchedulerTestHarness}.
 */
@Tag("core")
class SchedulerCheckLicenseTest {
   private SchedulerTestHarness harness;
   private Cluster cluster;
   private Scheduler scheduler;

   @BeforeEach
   void setUp() throws Exception {
      harness = new SchedulerTestHarness();
      // the harness registers its mock Cluster as the Cluster bean
      cluster = Cluster.getInstance();
      scheduler = mock(Scheduler.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
   }

   @AfterEach
   void tearDown() {
      harness.close();
   }

   /** Sets up the cluster nodes; the value is the node's "scheduler" property. */
   private void nodes(Map<String, Boolean> nodes) {
      when(cluster.getClusterNodes()).thenReturn(new LinkedHashSet<>(nodes.keySet()));
      nodes.forEach((node, isScheduler) ->
         when(cluster.<Object>getClusterNodeProperty(node, "scheduler")).thenReturn(isScheduler));
   }

   private void checkLicense() throws Throwable {
      Method method = Scheduler.class.getDeclaredMethod("checkLicense");
      method.setAccessible(true);

      try {
         method.invoke(scheduler);
      }
      catch(InvocationTargetException e) {
         throw e.getCause();
      }
   }

   @Test
   void secondSchedulerNodeIsRefusedWithEditionNeutralMessage() {
      Map<String, Boolean> nodes = new LinkedHashMap<>();
      nodes.put("sched-1", true);
      nodes.put("web-0", false);
      nodes.put("sched-0", true);
      nodes.put("web-1", false);
      nodes(nodes);

      LicenseException ex = assertThrows(LicenseException.class, this::checkLicense);
      String message = ex.getMessage();

      assertFalse(message.contains("Community Edition"), message);
      assertTrue(message.contains("Only one scheduler instance is allowed in a cluster"), message);
      assertTrue(message.contains("2 scheduler instances are running"), message);
      // the running scheduler nodes are named, sorted; web nodes are not
      assertTrue(message.contains("sched-0, sched-1"), message);
      assertFalse(message.contains("web-"), message);
   }

   @Test
   void singleSchedulerNodeWithWebNodesIsAllowed() {
      Map<String, Boolean> nodes = new LinkedHashMap<>();
      nodes.put("sched-0", true);
      nodes.put("web-0", false);
      nodes.put("web-1", false);
      nodes(nodes);

      assertDoesNotThrow(this::checkLicense);
   }

   @Test
   void cloudModeWithoutSchedulerNodesIsAllowed() {
      Map<String, Boolean> nodes = new LinkedHashMap<>();
      nodes.put("web-0", false);
      nodes.put("web-1", false);
      nodes(nodes);

      assertDoesNotThrow(this::checkLicense);
   }
}
