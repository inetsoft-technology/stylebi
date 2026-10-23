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
package inetsoft.web.admin.server;

/*
 * Test strategy
 *
 * The service is built with its constructor only (init() is not called), with a mocked
 * Cluster and NodeProtector, so no Spring bean or ConfigurationContext is reached.
 *
 * Behavioral guarantees covered:
 *
 * [G1] Bug #78571: a true -> false map event for the local node unprotects the node even
 *      when the protector's read-back says false (the AWS read-back always did).
 * [G2] Bug #78571: a true -> false map event unprotects the node even when the protector's
 *      read-back fails.
 * [G3] A false -> true map event protects the node even when the read-back already says
 *      true, so the protector's own state (e.g. AWS renewal) follows the map.
 * [G4] Events for other nodes are ignored.
 * [G5] While single-instance protection holds (minimum uptime not expired), a map false is
 *      not sent to the protector.
 * [G6] Bug #78652: a value the coordinator stored for the local node before the listener was
 *      registered (the node was still starting) is applied at startup, true and false.
 * [G7] No stored value for the local node leaves the protector untouched.
 * [G8] Bug #78652: startup registers the map listener first, then applies the stored value,
 *      so a value put in between reaches the node through one of the two.
 */

import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.internal.cluster.DistributedMap;
import inetsoft.sree.internal.cluster.DistributedScheduledExecutorService;
import inetsoft.sree.internal.cluster.EntryEvent;
import inetsoft.util.config.ClusterConfig;
import org.mockito.InOrder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

@Tag("core")
class NodeProtectionServiceTest {
   @BeforeEach
   void setUp() {
      cluster = mock(Cluster.class);
      when(cluster.getLocalMember()).thenReturn(LOCAL);
      protector = mock(NodeProtector.class);
      service = new NodeProtectionService(cluster);
      ReflectionTestUtils.setField(service, "nodeProtector", protector);
   }

   // [G1]
   @Test
   void mapFalseUnprotectsWhenReadBackIsFalse() {
      when(protector.getNodeProtection()).thenReturn(false);

      service.entryAdded(event(LOCAL, null, true));
      service.entryUpdated(event(LOCAL, true, false));

      verify(protector).updateNodeProtection(true);
      verify(protector).updateNodeProtection(false);
   }

   // [G2]
   @Test
   void mapFalseUnprotectsWhenReadBackFails() {
      when(protector.getNodeProtection()).thenThrow(new IllegalStateException("agent error"));

      service.entryUpdated(event(LOCAL, true, false));

      verify(protector).updateNodeProtection(false);
   }

   // [G3]
   @Test
   void mapTrueProtectsWhenReadBackIsAlreadyTrue() {
      when(protector.getNodeProtection()).thenReturn(true);

      service.entryUpdated(event(LOCAL, false, true));

      verify(protector).updateNodeProtection(true);
   }

   // [G4]
   @Test
   void otherNodeEventIsIgnored() {
      service.entryUpdated(event("other-node", true, false));

      verify(protector, never()).updateNodeProtection(anyBoolean());
   }

   // [G5]
   @Test
   void singleInstanceProtectionIsKept() {
      ReflectionTestUtils.setField(service, "minUptime", Duration.ofDays(365));
      ReflectionTestUtils.setField(service, "instanceProtected", true);

      service.entryUpdated(event(LOCAL, true, false));

      verify(protector, never()).updateNodeProtection(anyBoolean());
   }

   // [G6]
   @Test
   void storedTrueIsAppliedAtStartup() {
      stored(true);

      service.applyStoredProtection();

      verify(protector).updateNodeProtection(true);
      verify(protector, never()).updateNodeProtection(false);
   }

   // [G6]
   @Test
   void storedFalseIsAppliedAtStartup() {
      stored(false);

      service.applyStoredProtection();

      verify(protector).updateNodeProtection(false);
   }

   // [G7]
   @Test
   void missingStoredValueIsIgnoredAtStartup() {
      stored(null);

      service.applyStoredProtection();

      verify(protector, never()).updateNodeProtection(anyBoolean());
   }

   // [G8]
   @Test
   void startupRegistersListenerThenAppliesStoredValue() {
      stored(true);
      when(cluster.getScheduledExecutor()).thenReturn(mock(DistributedScheduledExecutorService.class));

      service.startCoordination(mock(ClusterConfig.class));

      InOrder order = inOrder(cluster, protector);
      order.verify(cluster).addReplicatedMapListener(NodeProtectionService.NODE_PROTECTION_MAP, service);
      order.verify(cluster).getReplicatedMap(NodeProtectionService.NODE_PROTECTION_MAP);
      order.verify(protector).updateNodeProtection(true);
   }

   @SuppressWarnings("unchecked")
   private void stored(Boolean value) {
      DistributedMap<String, Boolean> map = mock(DistributedMap.class);
      when(map.get(LOCAL)).thenReturn(value);
      when(cluster.<String, Boolean>getReplicatedMap(NodeProtectionService.NODE_PROTECTION_MAP))
         .thenReturn(map);
   }

   private static EntryEvent<String, Boolean> event(String key, Boolean oldValue, Boolean value) {
      return new EntryEvent<>(NodeProtectionService.NODE_PROTECTION_MAP, key, oldValue, value);
   }

   private static final String LOCAL = "local-node";
   private Cluster cluster;
   private NodeProtector protector;
   private NodeProtectionService service;
}
