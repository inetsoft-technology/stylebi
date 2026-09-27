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
package inetsoft.sree.web.dashboard;

import inetsoft.sree.ViewsheetEntry;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.sree.security.support.SecurityTestDataBuilder;
import inetsoft.test.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77103: DashboardRegistry's data space change listener reloaded the registry in place
 * (reset() then re-parse) for every change event on its file or an ancestor directory,
 * including the late notification of its own save(). That dropped dashboards added after a save,
 * exposed an empty registry to readers, could persist an empty registry, and could deadlock with
 * a concurrent save() because the listener held the blob read lock (open stream) while waiting
 * for the registry monitor.
 *
 * <p>The listener is invoked directly with a synthetic {@link DataChangeEvent}, which is exactly
 * what a late delivery on the BlobStorageEvent thread does, so each ordering is deterministic.
 * Natural (asynchronous) notifications for the saves in these tests may also arrive at any time;
 * the fixed code must be indifferent to them.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  DashboardRegistryReloadRaceTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DashboardRegistryReloadRaceTest {
   @Autowired
   private DashboardRegistryManager dashboardRegistryManager;

   @Autowired
   private DataSpace dataSpace;

   private SecurityTestDataBuilder builder;

   @AfterEach
   void tearDown() {
      if(builder != null) {
         builder.teardown();
         builder = null;
      }
   }

   // (1) own late save event
   @Test
   void ownLateSaveEvent_keepsUnsavedDashboard() throws Exception {
      DashboardRegistry registry = adminRegistry("dr77103_own");

      registry.addDashboard("A__GLOBAL", newVsDashboard());
      registry.save();
      registry.addDashboard("B__GLOBAL", newVsDashboard()); // not saved yet

      fireOwnEvent(registry); // the save's notification arrives late

      assertNotNull(registry.getDashboard("B__GLOBAL"),
                    "the late notification of our own save must not drop an unsaved dashboard");
      registry.save();
      assertTrue(fileNames(registry).containsAll(List.of("A__GLOBAL", "B__GLOBAL")));
   }

   // (1b) same, for a per-user registry (shares the listener; resolves the global registry)
   @Test
   void ownLateSaveEvent_userRegistry_keepsUnsavedDashboard() throws Exception {
      String orgId = "dr77103_user";
      builder = SecurityTestDataBuilder.create()
         .addOrg("Dr77103User", orgId)
         .addUser("erin", orgId, "password")
         .setup();
      DashboardRegistry registry =
         dashboardRegistryManager.getRegistry(new IdentityID("erin", orgId));

      registry.addDashboard("A", newVsDashboard());
      registry.save();
      registry.addDashboard("B", newVsDashboard());

      fireOwnEvent(registry);

      assertNotNull(registry.getDashboard("A"));
      assertNotNull(registry.getDashboard("B"),
                    "the late notification of our own save must not drop an unsaved dashboard");
   }

   // (2) the listener is in flight while a save() lands (refute A1 / import burst)
   @Test
   void listenerInFlightDuringSave_keepsSavedAndUnsavedDashboards() throws Exception {
      DashboardRegistry registry = adminRegistry("dr77103_toctou");

      registry.addDashboard("A__GLOBAL", newVsDashboard());
      registry.save(); // S1

      Thread listener;

      synchronized(registry) {
         // the notification for S1 is delivered while another thread is inside the registry
         listener = new Thread(() -> fireOwnEvent(registry), "late-S1-event");
         listener.setDaemon(true);
         listener.start();
         awaitBlockedOrDone(listener);

         registry.addDashboard("C__GLOBAL", newVsDashboard());
         registry.save(); // S2
         registry.addDashboard("D__GLOBAL", newVsDashboard()); // unsaved
      }

      listener.join(TimeUnit.SECONDS.toMillis(20));
      assertFalse(listener.isAlive(), "listener must complete");

      assertNotNull(registry.getDashboard("C__GLOBAL"), "saved dashboard C must survive");
      assertNotNull(registry.getDashboard("D__GLOBAL"), "unsaved dashboard D must survive");
      registry.save();
      assertTrue(fileNames(registry).containsAll(List.of("A__GLOBAL", "C__GLOBAL", "D__GLOBAL")));
   }

   // (3) listener vs save(): the listener must not hold the file's read lock while it waits
   // for the registry monitor that save() holds while it waits for the write lock
   @Test
   void listenerConcurrentWithSave_noDeadlock() throws Exception {
      DashboardRegistry registry = adminRegistry("dr77103_deadlock");
      Map<String, Dashboard> big = new LinkedHashMap<>();

      for(int i = 0; i < 3000; i++) {
         big.put("D" + i + "__GLOBAL", newVsDashboard());
      }

      for(int attempt = 0; attempt < 5; attempt++) {
         registry.setDashboardsMap(new LinkedHashMap<>(big));
         registry.save();
         // a different file so that the listener really reloads (and parses a large file)
         writeExternal(registry, "Ext" + attempt + "__GLOBAL", 3000);

         Thread listener = new Thread(() -> fireOwnEvent(registry), "reload-" + attempt);
         listener.setDaemon(true);
         listener.start();

         // Best-effort positioning for the pre-fix code only: there, the listener cleared the map
         // and then held the file open while it waited for the monitor to parse, so an empty map
         // marked the deadlock-prone window. The fixed code takes the monitor before it opens
         // the file and never shows an empty map, so this wait simply times out.
         long end = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(500);

         while(System.nanoTime() < end && listener.isAlive() &&
            registry.getDashboardNames().length != 0)
         {
            Thread.onSpinWait();
         }

         Thread saver = new Thread(() -> {
            synchronized(registry) {
               registry.addDashboard("Saved" + System.nanoTime() + "__GLOBAL", newVsDashboard());

               try {
                  registry.save();
               }
               catch(Exception e) {
                  throw new RuntimeException(e);
               }
            }
         }, "save-" + attempt);
         saver.setDaemon(true);
         saver.start();

         saver.join(TimeUnit.SECONDS.toMillis(20));
         listener.join(TimeUnit.SECONDS.toMillis(20));
         assertFalse(saver.isAlive() || listener.isAlive(),
                     "listener and save() deadlocked (attempt " + attempt + ")");
      }
   }

   // (4) a genuine external/remote change (different bytes) still reloads
   @Test
   void externalChange_reloads() throws Exception {
      DashboardRegistry registry = adminRegistry("dr77103_external");

      registry.addDashboard("A__GLOBAL", newVsDashboard());
      registry.save();
      writeExternal(registry, "Ext__GLOBAL", 0);

      fireOwnEvent(registry);

      assertNotNull(registry.getDashboard("Ext__GLOBAL"), "a foreign write must be reloaded");
      assertNull(registry.getDashboard("A__GLOBAL"), "the registry must reflect the new file");
   }

   // (5) readers never see an empty registry while a reload is in progress
   @Test
   void readersNeverSeeEmptyRegistryDuringReload() throws Exception {
      DashboardRegistry registry = adminRegistry("dr77103_empty");

      registry.addDashboard("Keep__GLOBAL", newVsDashboard());
      registry.save();

      AtomicBoolean stop = new AtomicBoolean();
      AtomicLong misses = new AtomicLong();
      Thread reader = new Thread(() -> {
         while(!stop.get()) {
            if(registry.getDashboard("Keep__GLOBAL") == null) {
               misses.incrementAndGet();
            }
         }
      }, "reader");
      reader.setDaemon(true);
      reader.start();

      try {
         for(int i = 0; i < 30; i++) {
            // every version keeps "Keep" and differs from the previous one, so each fire reloads
            writeExternal(registry, "Keep__GLOBAL", 500 + i);
            fireOwnEvent(registry);
         }
      }
      finally {
         stop.set(true);
         reader.join(TimeUnit.SECONDS.toMillis(10));
      }

      assertNotNull(registry.getDashboard("Keep__GLOBAL"));
      assertEquals(0, misses.get(), "a reader observed the registry without Keep during reload");
   }

   // (6) missing file: absent == absent keeps unsaved adds; a genuine delete still reloads
   @Test
   void missingFile_neverSaved_keepsUnsaved_deletedAfterSave_reloadsEmpty() throws Exception {
      DashboardRegistry registry = adminRegistry("dr77103_missing");
      assertFalse(dataSpace.exists(null, registry.getPath()), "precondition: no file yet");

      registry.addDashboard("U__GLOBAL", newVsDashboard()); // never saved
      fireOwnEvent(registry); // e.g. the portal/<org> directory being created

      assertNotNull(registry.getDashboard("U__GLOBAL"),
                    "an event while the file is still absent must not drop unsaved dashboards");

      registry.save();
      dataSpace.delete(null, registry.getPath());
      fireOwnEvent(registry);

      assertEquals(0, registry.getDashboardNames().length,
                   "a file deleted after it was saved must reload to an empty registry");
   }

   // ── helpers ──

   private DashboardRegistry adminRegistry(String orgId) throws Exception {
      builder = SecurityTestDataBuilder.create().addOrg(orgId + "_name", orgId).setup();
      DashboardRegistry registry = dashboardRegistryManager.getRegistry(orgId);
      assertNotNull(registry);
      assertEquals(orgId, registry.organizationId, "precondition: org id must resolve");
      return registry;
   }

   private static void fireOwnEvent(DashboardRegistry registry) {
      try {
         Field field = DashboardRegistry.class.getDeclaredField("changeListener");
         field.setAccessible(true);
         DataChangeListener listener = (DataChangeListener) field.get(registry);
         String path = registry.getPath();
         int idx = path.lastIndexOf('/');
         listener.dataChanged(new DataChangeEvent(path.substring(0, idx), path.substring(idx + 1),
                                                  System.currentTimeMillis()));
      }
      catch(ReflectiveOperationException e) {
         throw new RuntimeException(e);
      }
   }

   private static void awaitBlockedOrDone(Thread thread) throws InterruptedException {
      long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);

      while(System.nanoTime() < end && thread.isAlive() &&
         thread.getState() != Thread.State.BLOCKED)
      {
         Thread.sleep(1);
      }
   }

   /**
    * Writes a registry file directly through the data space, as another node or an import would.
    */
   private void writeExternal(DashboardRegistry registry, String name, int fillers)
      throws Exception
   {
      StringWriter buffer = new StringWriter();
      PrintWriter writer = new PrintWriter(buffer);
      writer.println("<?xml version=\"1.0\"?>");
      writer.println("<dashboardRegistry>");
      writer.println("<Version>" + FileVersions.DASHBOARD_REGISTRY + "</Version>");
      writeNode(writer, name);

      for(int i = 0; i < fillers; i++) {
         writeNode(writer, "Filler" + i + "__GLOBAL");
      }

      writer.println("</dashboardRegistry>");
      writer.flush();

      try(DataSpace.Transaction tx = dataSpace.beginTransaction();
          OutputStream out = tx.newStream(null, registry.getPath()))
      {
         out.write(buffer.toString().getBytes(StandardCharsets.UTF_8));
         out.flush();
         tx.commit();
      }
   }

   private static void writeNode(PrintWriter writer, String name) {
      writer.println("<node>");
      writer.println("<name><![CDATA[" + name + "]]></name>");
      newVsDashboard().writeXML(writer);
      writer.println("</node>");
   }

   private Set<String> fileNames(DashboardRegistry registry) throws Exception {
      Set<String> names = new HashSet<>();

      try(InputStream in = dataSpace.getInputStream(null, registry.getPath())) {
         assertNotNull(in, "registry file must exist");
         org.w3c.dom.NodeList list =
            Tool.parseXML(in).getDocumentElement().getElementsByTagName("name");

         for(int i = 0; i < list.getLength(); i++) {
            names.add(list.item(i).getTextContent().trim());
         }
      }

      return names;
   }

   private static VSDashboard newVsDashboard() {
      VSDashboard dashboard = new VSDashboard();
      String path = "myvs";
      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
                                        path, null, "host-org");
      ViewsheetEntry viewsheetEntry = new ViewsheetEntry(path, null);
      viewsheetEntry.setIdentifier(entry.toIdentifier());
      dashboard.setViewsheet(viewsheetEntry);
      return dashboard;
   }

   @Configuration
   static class Config {
      @Bean
      public DashboardRegistryManager dashboardRegistryManager(
         ApplicationEventPublisher eventPublisher, SecurityEngine securityEngine,
         inetsoft.uql.asset.DependencyHandler dependencyHandler, DataSpace dataSpace)
      {
         return new DashboardRegistryManager(eventPublisher, securityEngine, dependencyHandler,
                                             dataSpace);
      }
   }
}
