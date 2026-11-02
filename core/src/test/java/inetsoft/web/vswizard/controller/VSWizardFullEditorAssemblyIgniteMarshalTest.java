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
package inetsoft.web.vswizard.controller;

import inetsoft.web.vswizard.controller.VSWizardObjectToolbarService.FullEditorAssembly;
import org.apache.ignite.Ignite;
import org.apache.ignite.Ignition;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.internal.IgniteEx;
import org.apache.ignite.marshaller.Marshaller;
import org.apache.ignite.spi.communication.tcp.TcpCommunicationSpi;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;
import org.junit.jupiter.api.*;

import java.io.Serializable;
import java.net.ServerSocket;
import java.nio.file.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78734: the Full Editor result of {@link VSWizardObjectToolbarService#gotoFullEditor}
 * goes back to the calling node inside the affinity call response, which Ignite messaging
 * marshals with the node's BinaryMarshaller. A record cannot be read back by that marshaller
 * (it cannot set final record fields), so the result must survive the round trip.
 */
@Tag("core")
class VSWizardFullEditorAssemblyIgniteMarshalTest {
   @BeforeAll
   static void startIgnite() throws Exception {
      int discoPort = freePort();
      int commPort = freePort();
      TcpDiscoveryVmIpFinder ipFinder = new TcpDiscoveryVmIpFinder();
      ipFinder.setAddresses(Collections.singletonList("127.0.0.1:" + discoPort));
      TcpDiscoverySpi disco = new TcpDiscoverySpi();
      disco.setLocalAddress("127.0.0.1");
      disco.setLocalPort(discoPort);
      disco.setLocalPortRange(0);
      disco.setIpFinder(ipFinder);
      TcpCommunicationSpi comm = new TcpCommunicationSpi();
      comm.setLocalAddress("127.0.0.1");
      comm.setLocalPort(commPort);
      comm.setLocalPortRange(0);

      workDir = Files.createTempDirectory("ignite-78734");
      IgniteConfiguration config = new IgniteConfiguration();
      config.setIgniteInstanceName("bug78734-" + UUID.randomUUID());
      config.setWorkDirectory(workDir.toString());
      config.setLocalHost("127.0.0.1");
      config.setDiscoverySpi(disco);
      config.setCommunicationSpi(comm);
      config.setMetricsLogFrequency(0);
      config.setPeerClassLoadingEnabled(false);
      ignite = Ignition.start(config);
   }

   @AfterAll
   static void stopIgnite() throws Exception {
      if(ignite != null) {
         ignite.close();
      }

      if(workDir != null) {
         try(var paths = Files.walk(workDir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
         }
      }
   }

   @Test
   void theResultSurvivesTheAffinityResponseRoundTrip() throws Exception {
      Marshaller marshaller = ((IgniteEx) ignite).context().marshaller();
      FullEditorAssembly result = new FullEditorAssembly("Recommender__1700000005000", "VSChart");

      // same shape as IgniteCluster.AffinityCallResponse: the result in an Object-typed field
      byte[] bytes = marshaller.marshal(new Response("id", "node", result));
      Response copy = marshaller.unmarshal(bytes, getClass().getClassLoader());

      FullEditorAssembly restored = assertInstanceOf(FullEditorAssembly.class, copy.result);
      assertEquals("Recommender__1700000005000", restored.getAbsoluteName());
      assertEquals("VSChart", restored.getObjectType());
      assertEquals(result, restored);
   }

   private static int freePort() throws Exception {
      try(ServerSocket socket = new ServerSocket(0)) {
         return socket.getLocalPort();
      }
   }

   private static final class Response implements Serializable {
      Response(String id, String recipient, Serializable result) {
         this.id = id;
         this.recipient = recipient;
         this.result = result;
      }

      private final String id;
      private final String recipient;
      private final Serializable result;
   }

   private static Ignite ignite;
   private static Path workDir;
}
