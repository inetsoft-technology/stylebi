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
package inetsoft.uql.asset.internal;

import inetsoft.sree.internal.cluster.ignite.IgniteUtils;
import inetsoft.sree.security.IdentityID;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.Ignition;
import org.apache.ignite.binary.*;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.internal.IgniteEx;
import org.apache.ignite.internal.binary.BinaryTypeImpl;
import org.apache.ignite.spi.communication.tcp.TcpCommunicationSpi;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #80463: AssetFolder wrote one binary field per child, so Ignite registered a new binary
 * schema for every distinct child count. The axis is the number of children of a folder.
 * Uses a hermetic embedded Ignite node (loopback, free ports, no persistence).
 */
@Tag("core")
class AssetFolderBinaryTest {
   private static final String TYPE_NAME = AssetFolder.class.getName();
   private static Ignite ignite;
   private static IgniteCache<String, Object> cache;

   @BeforeAll
   static void startIgnite(@TempDir Path work) throws Exception {
      int discoveryPort = freePort();
      int commPort = freePort();
      IgniteConfiguration cfg = new IgniteConfiguration();
      cfg.setIgniteInstanceName("assetFolderBinaryTest");
      cfg.setMetricsLogFrequency(0);
      cfg.setPeerClassLoadingEnabled(false);
      cfg.setWorkDirectory(work.toString());
      cfg.setClientConnectorConfiguration(null);
      cfg.setConnectorConfiguration(null);

      TcpDiscoverySpi discovery = new TcpDiscoverySpi();
      TcpDiscoveryVmIpFinder finder = new TcpDiscoveryVmIpFinder();
      finder.setAddresses(List.of("127.0.0.1:" + discoveryPort));
      discovery.setIpFinder(finder);
      discovery.setLocalAddress("127.0.0.1");
      discovery.setLocalPort(discoveryPort);
      discovery.setLocalPortRange(0);
      cfg.setDiscoverySpi(discovery);

      TcpCommunicationSpi comm = new TcpCommunicationSpi();
      comm.setLocalAddress("127.0.0.1");
      comm.setLocalPort(commPort);
      comm.setLocalPortRange(0);
      cfg.setCommunicationSpi(comm);

      IgniteUtils.configBinaryTypes(cfg);
      // let the legacy stand-in share the type id of AssetFolder
      cfg.getBinaryConfiguration().setNameMapper(new BinaryBasicNameMapper() {
         @Override
         public String typeName(String clsName) {
            return clsName.endsWith("$LegacyFolder") ? TYPE_NAME : super.typeName(clsName);
         }
      });

      ignite = Ignition.start(cfg);
      cache = ignite.getOrCreateCache("assetFolderBinaryTest");
   }

   @AfterAll
   static void stopIgnite() {
      if(ignite != null) {
         Ignition.stop(ignite.name(), true);
         ignite = null;
         cache = null;
      }
   }

   @Test
   void schemaCountStaysOneAcrossChildCounts() throws Exception {
      // other tests register schemas under the same type id (legacy layout), so compare the
      // number of schemas added by these puts rather than the absolute count
      int before = schemaCount();

      for(int n = 0; n <= 50; n++) {
         cache.put("k" + n, folder(n));
      }

      int added = schemaCount() - before;
      assertTrue(added <= 1, "at most one new binary schema regardless of child count, added "
         + added);
   }

   @Test
   void roundTripKeepsEntries() {
      for(int n : new int[] { 0, 1, 4, 7, 50 }) {
         AssetFolder source = folder(n);
         source.setOwner(new IdentityID("owner" + n, "org"));
         cache.put("rt" + n, source);
         AssetFolder back = (AssetFolder) cache.get("rt" + n);

         assertEquals(n, back.getEntries().length);
         assertEquals(new HashSet<>(Arrays.asList(source.getEntries())),
                      new HashSet<>(Arrays.asList(back.getEntries())));
         assertEquals(source.getOwner(), back.getOwner());
      }
   }

   @Test
   void readsLegacyLayoutWithRepeatedEntryFields() {
      // register the new layout first, then add old-format objects of the same type
      cache.put("new", folder(2));

      for(int n : new int[] { 0, 1, 4, 7 }) {
         LegacyFolder legacy = new LegacyFolder();
         legacy.entries.addAll(Arrays.asList(folder(n).getEntries()));
         cache.put("legacy" + n, legacy);

         Object value = cache.get("legacy" + n);
         AssetFolder back = value instanceof LegacyFolder ?
            ((LegacyFolder) value).result : (AssetFolder) value;

         assertNotNull(back);
         assertEquals(n, back.getEntries().length, "legacy folder with " + n + " children");
         assertEquals(new HashSet<>(legacy.entries),
                      new HashSet<>(Arrays.asList(back.getEntries())));
      }
   }

   private static AssetFolder folder(int n) {
      AssetFolder folder = new AssetFolder();

      for(int i = 0; i < n; i++) {
         folder.addEntry(new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
                                        "dash" + i, new IdentityID("u", "org")));
      }

      return folder;
   }

   private static int schemaCount() throws Exception {
      IgniteEx ex = (IgniteEx) ignite;
      int typeId = ex.context().cacheObjects().typeId(TYPE_NAME);
      BinaryTypeImpl type = (BinaryTypeImpl) ex.context().cacheObjects().metadata(typeId);

      if(type == null) {
         return 0;
      }

      Object meta = type.metadata();
      var method = meta.getClass().getDeclaredMethod("schemas");
      method.setAccessible(true);
      return ((Collection<?>) method.invoke(meta)).size();
   }

   private static int freePort() throws Exception {
      try(ServerSocket socket = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))) {
         return socket.getLocalPort();
      }
   }

   /** Replicates the pre-fix writer: one "entry" field per child. */
   public static final class LegacyFolder implements Binarylizable {
      final List<AssetEntry> entries = new ArrayList<>();
      AssetFolder result;

      @Override
      public void writeBinary(BinaryWriter writer) throws BinaryObjectException {
         writer.writeObject("owner", (IdentityID) null);
         writer.writeInt("size", entries.size());

         for(AssetEntry entry : entries) {
            writer.writeObject("entry", entry);
         }
      }

      @Override
      public void readBinary(BinaryReader reader) throws BinaryObjectException {
         result = new AssetFolder();
         result.readBinary(reader);
      }
   }
}
