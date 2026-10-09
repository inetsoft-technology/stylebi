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
package inetsoft.sree.internal.cluster.ignite;

import inetsoft.sree.internal.cluster.AffinityCallException;
import inetsoft.sree.internal.cluster.AffinityCallable;
import inetsoft.sree.internal.cluster.Cluster;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteException;
import org.apache.ignite.IgniteMessaging;
import org.apache.ignite.cache.affinity.Affinity;
import org.apache.ignite.cluster.ClusterGroup;
import org.apache.ignite.cluster.ClusterNode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78115: {@code affinityCall()}, {@code affinityCallAsync()}, the response leg of
 * {@code AffinityCallRequestTask.run()}, and {@code sendAffinityFailure()} all sent their
 * single-recipient {@code AffinityCallRequest}/{@code AffinityCallResponse} via the unscoped
 * {@code ignite.message().sendOrdered(AFFINITY_TOPIC, ..., 0)}, which Ignite binds to the
 * <em>entire</em> cluster topology rather than to the one intended recipient. A multi-node
 * {@code sendOrdered()} fails the whole call when any member of that topology is unreachable --
 * even when the real intended recipient is perfectly healthy -- which is how an unrelated,
 * short-lived Ignite client node dying produced the reported
 * "Failed to send the request to node 172.31.10.132:5701 ... node left: ..." even though
 * 172.31.10.132 was never actually unreachable.
 *
 * <p>These tests don't stand up a real multi-node cluster; they verify, with
 * {@link org.mockito.Mockito#verify}, that all four send sites now call
 * {@code ignite.message(ignite.cluster().forNode(<resolved node>))} -- scoped to exactly the
 * intended recipient, matching the class's own pre-existing {@code sendMessage(String,
 * Serializable)} pattern -- rather than the bare, whole-topology {@code ignite.message()}.
 * Against the pre-fix code, {@code verify(ignite, never()).message()} fails because the
 * defective code calls exactly that overload.
 */
@Tag("core")
class IgniteClusterAffinityScopeTest {
   @Test
   void affinityCall_remoteNode_scopesSendToRecipientNodeNotWholeTopology() throws Exception {
      Ignite ignite = mock(Ignite.class);
      org.apache.ignite.IgniteCluster igniteClusterApi = mock(org.apache.ignite.IgniteCluster.class);
      when(ignite.cluster()).thenReturn(igniteClusterApi);

      ClusterNode localNode = mock(ClusterNode.class, "localNode");
      ClusterNode remoteNode = mock(ClusterNode.class, "remoteNode");
      when(localNode.attribute("local.ip.addr")).thenReturn("10.0.0.1");
      when(remoteNode.attribute("local.ip.addr")).thenReturn("10.0.0.2");
      when(igniteClusterApi.localNode()).thenReturn(localNode);

      @SuppressWarnings("unchecked")
      Affinity<Object> affinity = mock(Affinity.class);
      when(ignite.affinity(anyString())).thenReturn(affinity);
      when(affinity.mapKeyToNode(any())).thenReturn(remoteNode);

      ClusterGroup remoteGroup = mock(ClusterGroup.class);
      when(igniteClusterApi.forNode(remoteNode)).thenReturn(remoteGroup);

      IgniteMessaging scopedMessaging = mock(IgniteMessaging.class);
      when(ignite.message(remoteGroup)).thenReturn(scopedMessaging);
      // Force the send to fail synchronously so affinityCall() takes its fast-fail catch
      // branch instead of blocking on a response nobody will ever deliver in this test.
      doThrow(new IgniteException("simulated send failure"))
         .when(scopedMessaging).sendOrdered(any(), any(), anyLong());

      IgniteCluster cluster = newBareCluster(ignite);

      AffinityCallException ex = assertThrows(AffinityCallException.class,
         () -> cluster.affinityCall("myCache", "myKey", () -> "unused"));
      assertNotNull(ex.getCause());

      // The fix: the send must be scoped to exactly the resolved recipient node...
      verify(igniteClusterApi).forNode(remoteNode);
      verify(ignite).message(remoteGroup);
      verify(scopedMessaging).sendOrdered(eq(affinityTopic()), any(), eq(0L));
      // ...and never through the bare, whole-topology overload the bug used.
      verify(ignite, never()).message();
   }

   @Test
   void affinityCallAsync_remoteNode_scopesSendToRecipientNodeNotWholeTopology() throws Exception {
      Ignite ignite = mock(Ignite.class);
      org.apache.ignite.IgniteCluster igniteClusterApi = mock(org.apache.ignite.IgniteCluster.class);
      when(ignite.cluster()).thenReturn(igniteClusterApi);

      ClusterNode localNode = mock(ClusterNode.class, "localNode");
      ClusterNode remoteNode = mock(ClusterNode.class, "remoteNode");
      when(localNode.attribute("local.ip.addr")).thenReturn("10.0.0.1");
      when(remoteNode.attribute("local.ip.addr")).thenReturn("10.0.0.2");
      when(igniteClusterApi.localNode()).thenReturn(localNode);

      @SuppressWarnings("unchecked")
      Affinity<Object> affinity = mock(Affinity.class);
      when(ignite.affinity(anyString())).thenReturn(affinity);
      when(affinity.mapKeyToNode(any())).thenReturn(remoteNode);

      ClusterGroup remoteGroup = mock(ClusterGroup.class);
      when(igniteClusterApi.forNode(remoteNode)).thenReturn(remoteGroup);

      IgniteMessaging scopedMessaging = mock(IgniteMessaging.class);
      when(ignite.message(remoteGroup)).thenReturn(scopedMessaging);
      doThrow(new IgniteException("simulated send failure"))
         .when(scopedMessaging).sendOrdered(any(), any(), anyLong());

      IgniteCluster cluster = newBareCluster(ignite);

      Future<String> future = cluster.affinityCallAsync("myCache", "myKey", () -> "unused");
      ExecutionException ex = assertThrows(ExecutionException.class, future::get);
      assertInstanceOf(AffinityCallException.class, ex.getCause());

      verify(igniteClusterApi).forNode(remoteNode);
      verify(ignite).message(remoteGroup);
      verify(scopedMessaging).sendOrdered(eq(affinityTopic()), any(), eq(0L));
      verify(ignite, never()).message();
   }

   @Test
   void sendAffinityFailure_knownSender_scopesSendToThatSenderNode() throws Exception {
      Ignite ignite = mock(Ignite.class);
      org.apache.ignite.IgniteCluster igniteClusterApi = mock(org.apache.ignite.IgniteCluster.class);
      when(ignite.cluster()).thenReturn(igniteClusterApi);

      ClusterNode senderNode = mock(ClusterNode.class, "senderNode");
      when(senderNode.attribute("local.ip.addr")).thenReturn("10.0.0.3");
      when(igniteClusterApi.nodes()).thenReturn(List.of(senderNode));

      ClusterGroup senderGroup = mock(ClusterGroup.class);
      when(igniteClusterApi.forNode(senderNode)).thenReturn(senderGroup);

      IgniteMessaging scopedMessaging = mock(IgniteMessaging.class);
      when(ignite.message(senderGroup)).thenReturn(scopedMessaging);

      IgniteCluster cluster = newBareCluster(ignite);
      Object request = newAffinityCallRequest("id-1", "10.0.0.3:0", "10.0.0.9:0", () -> "unused");

      invokeSendAffinityFailure(cluster, request, new RuntimeException("boom"));

      verify(igniteClusterApi).forNode(senderNode);
      verify(ignite).message(senderGroup);
      verify(scopedMessaging).sendOrdered(eq(affinityTopic()), any(), eq(0L));
      verify(ignite, never()).message();
   }

   @Test
   void sendAffinityFailure_senderAlreadyLeft_dropsResponseInsteadOfBroadcasting() throws Exception {
      Ignite ignite = mock(Ignite.class);
      org.apache.ignite.IgniteCluster igniteClusterApi = mock(org.apache.ignite.IgniteCluster.class);
      when(ignite.cluster()).thenReturn(igniteClusterApi);
      // No nodes at all in the topology -- the sender has already left.
      when(igniteClusterApi.nodes()).thenReturn(Collections.emptyList());

      IgniteCluster cluster = newBareCluster(ignite);
      Object request = newAffinityCallRequest("id-2", "10.0.0.3:0", "10.0.0.9:0", () -> "unused");

      invokeSendAffinityFailure(cluster, request, new RuntimeException("boom"));

      // Must not fall back to the unscoped, whole-topology send either.
      verify(ignite, never()).message();
      verify(ignite, never()).message(any());
   }

   @Test
   void affinityCallRequestTask_run_scopesResponseToSenderNode() throws Exception {
      Ignite ignite = mock(Ignite.class);
      org.apache.ignite.IgniteCluster igniteClusterApi = mock(org.apache.ignite.IgniteCluster.class);
      when(ignite.cluster()).thenReturn(igniteClusterApi);

      ClusterNode senderNode = mock(ClusterNode.class, "senderNode");
      when(senderNode.attribute("local.ip.addr")).thenReturn("10.0.0.3");
      when(igniteClusterApi.nodes()).thenReturn(List.of(senderNode));

      ClusterGroup senderGroup = mock(ClusterGroup.class);
      when(igniteClusterApi.forNode(senderNode)).thenReturn(senderGroup);

      IgniteMessaging scopedMessaging = mock(IgniteMessaging.class);
      when(ignite.message(senderGroup)).thenReturn(scopedMessaging);

      IgniteCluster cluster = newBareCluster(ignite);
      AffinityCallable<String> job = () -> "ok";
      Object request = newAffinityCallRequest("id-3", "10.0.0.3:0", "10.0.0.9:0", job);
      Runnable task = newAffinityCallRequestTask(request);

      try(MockedStatic<Cluster> clusterStatic = mockStatic(Cluster.class)) {
         clusterStatic.when(Cluster::getInstance).thenReturn(cluster);
         task.run();
      }

      verify(igniteClusterApi).forNode(senderNode);
      verify(ignite).message(senderGroup);
      verify(scopedMessaging).sendOrdered(eq(affinityTopic()), any(), eq(0L));
      verify(ignite, never()).message();
   }

   @Test
   void affinityCallRequestTask_run_senderAlreadyLeft_dropsResponseInsteadOfBroadcasting()
      throws Exception
   {
      Ignite ignite = mock(Ignite.class);
      org.apache.ignite.IgniteCluster igniteClusterApi = mock(org.apache.ignite.IgniteCluster.class);
      when(ignite.cluster()).thenReturn(igniteClusterApi);
      when(igniteClusterApi.nodes()).thenReturn(Collections.emptyList());

      IgniteCluster cluster = newBareCluster(ignite);
      AffinityCallable<String> job = () -> "ok";
      Object request = newAffinityCallRequest("id-4", "10.0.0.3:0", "10.0.0.9:0", job);
      Runnable task = newAffinityCallRequestTask(request);

      try(MockedStatic<Cluster> clusterStatic = mockStatic(Cluster.class)) {
         clusterStatic.when(Cluster::getInstance).thenReturn(cluster);
         task.run();
      }

      verify(ignite, never()).message();
      verify(ignite, never()).message(any());
   }

   /**
    * Creates an {@code IgniteCluster} instance without running its real constructor (which
    * would try to join a real Ignite cluster) and without stubbing any of its own methods --
    * it is a real object with only the {@code ignite} and {@code affinityFutures} fields
    * populated, so calling an unstubbed method like {@code affinityCall()} runs the actual
    * production code being tested.
    */
   private static IgniteCluster newBareCluster(Ignite ignite) throws Exception {
      IgniteCluster cluster = mock(IgniteCluster.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
      setField(cluster, "ignite", ignite);
      setField(cluster, "affinityFutures", new ConcurrentHashMap<>());
      return cluster;
   }

   private static void setField(Object target, String name, Object value) throws Exception {
      Field field = IgniteCluster.class.getDeclaredField(name);
      field.setAccessible(true);
      field.set(target, value);
   }

   private static String affinityTopic() throws Exception {
      Field field = IgniteCluster.class.getDeclaredField("AFFINITY_TOPIC");
      field.setAccessible(true);
      return (String) field.get(null);
   }

   @SuppressWarnings("unchecked")
   private static <T> Object newAffinityCallRequest(
      String id, String sender, String recipient, AffinityCallable<T> callable) throws Exception
   {
      Class<?> requestClass =
         Class.forName("inetsoft.sree.internal.cluster.ignite.IgniteCluster$AffinityCallRequest");
      Constructor<?> ctor = requestClass.getDeclaredConstructor(
         String.class, String.class, String.class, AffinityCallable.class);
      ctor.setAccessible(true);
      return ctor.newInstance(id, sender, recipient, callable);
   }

   private static Runnable newAffinityCallRequestTask(Object request) throws Exception {
      Class<?> requestClass =
         Class.forName("inetsoft.sree.internal.cluster.ignite.IgniteCluster$AffinityCallRequest");
      Class<?> taskClass =
         Class.forName("inetsoft.sree.internal.cluster.ignite.IgniteCluster$AffinityCallRequestTask");
      Constructor<?> ctor = taskClass.getDeclaredConstructor(requestClass);
      ctor.setAccessible(true);
      return (Runnable) ctor.newInstance(request);
   }

   private static void invokeSendAffinityFailure(
      IgniteCluster cluster, Object request, Throwable error) throws Exception
   {
      Class<?> requestClass =
         Class.forName("inetsoft.sree.internal.cluster.ignite.IgniteCluster$AffinityCallRequest");
      Method method = IgniteCluster.class.getDeclaredMethod(
         "sendAffinityFailure", requestClass, Throwable.class);
      method.setAccessible(true);
      method.invoke(cluster, request, error);
   }
}
