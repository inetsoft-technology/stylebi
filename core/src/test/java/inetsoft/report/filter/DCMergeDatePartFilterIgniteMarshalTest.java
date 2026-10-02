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

package inetsoft.report.filter;

import inetsoft.graph.data.DefaultDataSet;
import inetsoft.report.lens.DataSetTable;
import inetsoft.test.*;
import inetsoft.uql.XTable;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.viewsheet.VSDimensionRef;
import inetsoft.uql.viewsheet.XDimensionRef;
import inetsoft.web.viewsheet.model.table.BaseTableCellModel;
import inetsoft.web.viewsheet.service.CommandDispatcherService;
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

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static inetsoft.test.XTableUtil.date;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77196: a date-comparison crosstab cell (MergePartCell) reaches
 * LoadTableDataCommand.cellData, and CommandDispatcherService forwards the live
 * command to the websocket node through Ignite messaging, which uses the
 * BinaryMarshaller. The marshalled message must not carry the table.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class DCMergeDatePartFilterIgniteMarshalTest {
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

      workDir = Files.createTempDirectory("ignite-77196");
      IgniteConfiguration config = new IgniteConfiguration();
      config.setIgniteInstanceName("bug77196-" + UUID.randomUUID());
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
   public void forwardedCommandDoesNotCarryTable() throws Exception {
      Marshaller marshaller = ((IgniteEx) ignite).context().marshaller();
      ClassLoader loader = getClass().getClassLoader();

      Fixture small = new Fixture(12);
      Fixture large = new Fixture(20_000);
      byte[] smallBytes = marshaller.marshal(small.message());
      byte[] largeBytes = marshaller.marshal(large.message());

      // The message size must depend on the number of cells, not on the table rows.
      assertTrue(largeBytes.length < 64 * 1024,
                 "marshalled message is " + largeBytes.length + " bytes for a " +
                 large.base.getRowCount() + "-row table");
      assertTrue(largeBytes.length < smallBytes.length * 2,
                 "message grows with the table: " + smallBytes.length + " -> " +
                 largeBytes.length + " bytes");

      CommandDispatcherService.CommandMessage copy = marshaller.unmarshal(largeBytes, loader);
      BaseTableCellModel[] cells = (BaseTableCellModel[]) copy.getPayload();
      assertEquals(large.cells.length, cells.length);
      assertNoTableReachable(copy);

      for(int i = 0; i < cells.length; i++) {
         Object original = large.cells[i].getCellData();
         Object restored = cells[i].getCellData();
         assertInstanceOf(DCMergeDatePartFilter.MergePartCell.class, restored);
         DCMergeDatePartFilter.MergePartCell o = (DCMergeDatePartFilter.MergePartCell) original;
         DCMergeDatePartFilter.MergePartCell r = (DCMergeDatePartFilter.MergePartCell) restored;
         assertEquals(o, r);
         assertEquals(o.hashCode(), r.hashCode());
         assertEquals(0, o.compareTo(r));
         assertEquals(o.toString(), r.toString());
         assertEquals(large.cells[i].getCellLabel(), cells[i].getCellLabel());
         assertEquals(o.getOriginalData(), r.getOriginalData());
         assertEquals(o.getDateGroupValue(), r.getDateGroupValue());
         assertEquals(o.getPartRef().getFullName(), r.getPartRef().getFullName());
         assertEquals(o.getMergedRefs().size(), r.getMergedRefs().size());
      }
   }

   private static void assertNoTableReachable(Object root) throws IllegalAccessException {
      Map<Object, Boolean> seen = new IdentityHashMap<>();
      Deque<Object[]> stack = new ArrayDeque<>();
      stack.push(new Object[]{ root, "root" });

      while(!stack.isEmpty()) {
         Object[] item = stack.pop();
         Object obj = item[0];

         if(obj == null || seen.put(obj, Boolean.TRUE) != null) {
            continue;
         }

         assertFalse(obj instanceof XTable || obj instanceof DCMergeDatePartFilter,
                     "table reachable after unmarshal via " + item[1]);
         Class<?> cls = obj.getClass();

         if(cls.isArray()) {
            if(!cls.getComponentType().isPrimitive()) {
               for(int i = 0; i < Array.getLength(obj); i++) {
                  stack.push(new Object[]{ Array.get(obj, i), item[1] + "[" + i + "]" });
               }
            }

            continue;
         }

         if(obj instanceof Collection<?> coll) {
            for(Object o : coll) {
               stack.push(new Object[]{ o, item[1] + "[*]" });
            }

            continue;
         }

         if(obj instanceof Map<?, ?> map) {
            for(Map.Entry<?, ?> e : map.entrySet()) {
               stack.push(new Object[]{ e.getKey(), item[1] + "{key}" });
               stack.push(new Object[]{ e.getValue(), item[1] + "{value}" });
            }

            continue;
         }

         if(cls.getName().startsWith("java.")) {
            continue;
         }

         for(Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
            for(Field f : c.getDeclaredFields()) {
               if(Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) {
                  continue;
               }

               f.setAccessible(true);
               stack.push(new Object[]{ f.get(obj), item[1] + "." + f.getName() });
            }
         }
      }
   }

   private static int freePort() throws Exception {
      try(ServerSocket socket = new ServerSocket(0)) {
         return socket.getLocalPort();
      }
   }

   private static final class Fixture {
      Fixture(int rowCount) {
         List<Object[]> rows = new ArrayList<>();
         rows.add(new Object[]{ "Year(date)", "WeekOfYear(date)", "date", "Sum(qty)" });

         for(int i = 0; i < rowCount; i++) {
            int month = i % 12 + 1;
            int week = i % 3 + 4;
            String mm = month < 10 ? "0" + month : Integer.toString(month);
            rows.add(new Object[]{ 2021, month * 10 + week, date("2021-" + mm + "-15"), i });
         }

         base = new DataSetTable(new DefaultDataSet(rows.toArray(new Object[0][])));
         VSDimensionRef yearRef = new VSDimensionRef();
         yearRef.setDataRef(new AttributeRef("Year(date)"));
         VSDimensionRef partRef = new VSDimensionRef();
         partRef.setDataRef(new AttributeRef("WeekOfYear(date)"));
         VSDimensionRef dateGroupRef = new VSDimensionRef();
         dateGroupRef.setDataRef(new AttributeRef("date"));
         List<XDimensionRef> extras = new ArrayList<>();
         extras.add(yearRef);
         DCMergeDatePartFilter filter =
            new DCMergeDatePartFilter(base, extras, partRef, dateGroupRef, null);

         // like a crosstab page: a fixed number of header cells regardless of table size
         cells = new BaseTableCellModel[12];

         for(int i = 0; i < cells.length; i++) {
            cells[i] = BaseTableCellModel.createSimpleCell(filter, base.getHeaderRowCount() + i, 1);
            assertInstanceOf(DCMergeDatePartFilter.MergePartCell.class, cells[i].getCellData());
         }
      }

      CommandDispatcherService.CommandMessage message() {
         return new CommandDispatcherService.CommandMessage(
            "id", "admin", "/commands", cells, new HashMap<>());
      }

      final DataSetTable base;
      final BaseTableCellModel[] cells;
   }

   private static Ignite ignite;
   private static Path workDir;
}
