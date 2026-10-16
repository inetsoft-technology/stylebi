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
package inetsoft.web.wiz.viewsheet;

import inetsoft.sree.internal.cluster.ignite.IgniteUtils;
import inetsoft.test.*;
import inetsoft.web.service.HighlightService.DateComparisonFields;
import org.apache.ignite.Ignite;
import org.apache.ignite.Ignition;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.internal.IgniteEx;
import org.apache.ignite.marshaller.Marshaller;
import org.apache.ignite.spi.communication.tcp.TcpCommunicationSpi;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.*;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78540: {@code HighlightDialogService.getDateComparisonFields} is a
 * {@code @ClusterProxyMethod}, so when the viewsheet lives on another node its result crosses
 * Ignite's binary marshaller. It must arrive intact and still match case-insensitively, or every
 * {@code set_highlight} on that node fails (a record cannot be marshalled on JDK 21) or a
 * lower-cased derived field slips past the guard (a comparator-ordered set losing its comparator).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class DateComparisonFieldsMarshalTest {
   private static final String DERIVED =
      "Change from previous year of Year(ORDER_DATE): Sum(DISCOUNT).1";
   private static final String AMBIGUOUS = "Sum(DISCOUNT).1";

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

      workDir = Files.createTempDirectory("ignite-78540");
      IgniteConfiguration config = new IgniteConfiguration();
      config.setIgniteInstanceName("bug78540-" + UUID.randomUUID());
      config.setWorkDirectory(workDir.toString());
      config.setLocalHost("127.0.0.1");
      config.setDiscoverySpi(disco);
      config.setCommunicationSpi(comm);
      config.setMetricsLogFrequency(0);
      config.setPeerClassLoadingEnabled(false);
      // as IgniteCluster configures it
      IgniteUtils.configBinaryTypes(config);
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

   /** The result as the classifier builds it: case-insensitive sets of listed names. */
   private static DateComparisonFields fields() {
      Set<String> derived = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
      derived.add(DERIVED);
      Set<String> ambiguous = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
      ambiguous.add(AMBIGUOUS);
      return new DateComparisonFields(derived, ambiguous);
   }

   private static AssemblyHighlightService.Highlight highlightOn(String field) {
      return new AssemblyHighlightService.Highlight(
         "h", "#ff0000", null, null,
         List.of(new ConditionVocabulary.Clause(field, ">", List.of(0), null, false, false, 0)),
         false);
   }

   private static void assertStillGuards(DateComparisonFields fields) {
      for(String field : List.of(DERIVED, DERIVED.toLowerCase(), AMBIGUOUS.toLowerCase())) {
         Exception e = assertThrows(IllegalArgumentException.class,
            () -> AssemblyHighlightService.requireNoDerivedConditionField(
               highlightOn(field), fields), field);
         assertTrue(e.getMessage().contains("'" + field + "'"), e.getMessage());
      }

      assertDoesNotThrow(() -> AssemblyHighlightService.requireNoDerivedConditionField(
         highlightOn("Sum(DISCOUNT)"), fields));
   }

   @Test
   void survivesJavaSerialization() throws Exception {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();

      try(ObjectOutputStream out = new ObjectOutputStream(bytes)) {
         out.writeObject(fields());
      }

      try(ObjectInputStream in =
             new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray())))
      {
         assertStillGuards((DateComparisonFields) in.readObject());
      }
   }

   @Test
   void survivesIgniteMarshaller() throws Exception {
      Marshaller marshaller = ((IgniteEx) ignite).context().marshaller();
      byte[] bytes = marshaller.marshal(fields());
      DateComparisonFields loaded = marshaller.unmarshal(bytes, getClass().getClassLoader());

      assertStillGuards(loaded);
   }

   @Test
   void noneSurvivesIgniteMarshaller() throws Exception {
      Marshaller marshaller = ((IgniteEx) ignite).context().marshaller();
      DateComparisonFields loaded = marshaller.unmarshal(
         marshaller.marshal(DateComparisonFields.NONE), getClass().getClassLoader());

      assertDoesNotThrow(() -> AssemblyHighlightService.requireNoDerivedConditionField(
         highlightOn(DERIVED), loaded));
   }

   private static int freePort() throws IOException {
      try(ServerSocket socket = new ServerSocket(0)) {
         return socket.getLocalPort();
      }
   }

   private static Ignite ignite;
   private static Path workDir;
}
