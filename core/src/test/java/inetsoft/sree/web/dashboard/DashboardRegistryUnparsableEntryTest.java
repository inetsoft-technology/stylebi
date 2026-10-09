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
import inetsoft.sree.security.SecurityEngine;
import inetsoft.sree.security.support.SecurityTestDataBuilder;
import inetsoft.test.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.util.DataSpace;
import inetsoft.util.FileVersions;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78103: a dashboard entry whose {@code <dashboard>} body fails to parse (e.g. a viewsheet
 * entry without {@code <path>}, Bug #77603, or a non-numeric {@code <created>}/{@code <modified>})
 * used to be silently and permanently erased from the registry file the next time the file was
 * rewritten: by an ordinary edit of a *different* dashboard through {@link DashboardRegistry#update}
 * (behind {@code putDashboard}/{@code updateDashboard}/{@code renameEntry}/{@code removeEntry}), or
 * even by nothing more than the first load of an older-version file, which unconditionally fires
 * {@code savePorted()}. The fix keeps the entry's raw {@code <dashboard>} node (as an
 * {@link UnparsableDashboardEntry}) so it round-trips through both rewrite paths unchanged, while
 * staying excluded from {@link DashboardRegistry#getDashboard}/{@link DashboardRegistry#getDashboardNames}
 * and remaining renamable/removable by name, in its original position in the file.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DashboardRegistryUnparsableEntryTest {
   @Autowired
   private ApplicationEventPublisher eventPublisher;

   @Autowired
   private SecurityEngine securityEngine;

   @Autowired
   private DataSpace dataSpace;

   private SecurityTestDataBuilder builder;
   private DashboardRegistry registry;

   @AfterEach
   void tearDown() {
      if(registry != null) {
         registry.clear();
      }

      if(builder != null) {
         builder.teardown();
      }
   }

   /**
    * Rewrite path (a) from the diagnosis: an ordinary edit of a *different* dashboard, going
    * through {@code update(...)}/{@code putDashboard}, must not drop the broken entry.
    */
   @Test
   void ordinaryEditOfAnotherDashboardKeepsUnparsableEntry() throws Exception {
      String orgId = "dr78103_a_org";
      builder = SecurityTestDataBuilder.create().addOrg(orgId + "_name", orgId).setup();
      registry = new DashboardRegistry(orgId, eventPublisher, securityEngine);
      writeRegistryFile(registry.getPath(), FileVersions.DASHBOARD_REGISTRY);

      registry.loadDashboard(null);

      // the broken entry must never be shown to an ordinary caller, same as before the fix
      assertNull(registry.getDashboard("Broken__GLOBAL"));
      assertEquals(2, registry.getDashboardNames().length);

      // the current version needs no port, so nothing has rewritten the file yet
      String beforeEdit = readRegistryFile(registry.getPath());
      assertTrue(beforeEdit.contains("Broken__GLOBAL"),
                 "raw file must still have the broken node before any edit");

      // an ordinary edit of a *different*, unrelated dashboard
      registry.putDashboard("Fourth__GLOBAL", dashboard());

      String afterEdit = readRegistryFile(registry.getPath());
      assertTrue(afterEdit.contains("Broken__GLOBAL"),
                 "the broken entry must survive an unrelated putDashboard rewrite (Bug #78103)");
      assertTrue(afterEdit.contains("1^128^__NULL__^myvs^host-org"),
                 "the broken entry's raw body must be preserved, not just its name");
      assertNull(registry.getDashboard("Broken__GLOBAL"),
                 "still excluded from ordinary lookup after the rewrite");
      assertFalse(List.of(registry.getDashboardNames()).contains("Broken__GLOBAL"),
                  "still excluded from the listing after the rewrite");

      // original order preserved: First, Broken, Third, then Fourth appended at the end
      assertOrder(afterEdit, "First__GLOBAL", "Broken__GLOBAL", "Third__GLOBAL", "Fourth__GLOBAL");
   }

   /**
    * Rewrite path (b) from the diagnosis: the very first load of an older-version file fires
    * {@code savePorted()} unconditionally, with no caller-supplied edit, and must not drop the
    * broken entry either.
    */
   @Test
   void firstLoadOfOlderVersionFileKeepsUnparsableEntryWhilePorting() throws Exception {
      String orgId = "dr78103_b_org";
      builder = SecurityTestDataBuilder.create().addOrg(orgId + "_name", orgId).setup();
      registry = new DashboardRegistry(orgId, eventPublisher, securityEngine);
      writeRegistryFile(registry.getPath(), "9.4");

      String beforeLoad = readRegistryFile(registry.getPath());
      assertTrue(beforeLoad.contains("Broken__GLOBAL"));
      assertTrue(beforeLoad.contains("<Version>9.4</Version>"));

      // nothing beyond the bare load - savePorted() fires internally because of the version
      // mismatch
      registry.loadDashboard(null);

      String afterLoad = readRegistryFile(registry.getPath());
      assertTrue(afterLoad.contains("<Version>" + FileVersions.DASHBOARD_REGISTRY + "</Version>"),
                 "the file must be ported to the current version");
      assertTrue(afterLoad.contains("Broken__GLOBAL"),
                 "the broken entry must survive the version-port rewrite (Bug #78103)");
      assertTrue(afterLoad.contains("1^128^__NULL__^myvs^host-org"),
                 "the broken entry's raw body must be preserved, not just its name");
      assertNull(registry.getDashboard("Broken__GLOBAL"));
      assertEquals(2, registry.getDashboardNames().length);
      assertOrder(afterLoad, "First__GLOBAL", "Broken__GLOBAL", "Third__GLOBAL");
   }

   /**
    * Per 02-refute.md / 03-fix.md: a by-name rename of the broken entry (the file's {@code <name>}
    * is readable even when {@code <dashboard>} isn't) must still work coherently.
    */
   @Test
   void renameEntryRenamesUnparsableEntryByName() throws Exception {
      String orgId = "dr78103_c_org";
      builder = SecurityTestDataBuilder.create().addOrg(orgId + "_name", orgId).setup();
      registry = new DashboardRegistry(orgId, eventPublisher, securityEngine);
      writeRegistryFile(registry.getPath(), FileVersions.DASHBOARD_REGISTRY);
      registry.loadDashboard(null);

      assertTrue(registry.renameEntry("Broken__GLOBAL", "Renamed__GLOBAL"),
                 "a by-name rename of the broken entry must succeed");

      String afterRename = readRegistryFile(registry.getPath());
      assertFalse(afterRename.contains("Broken__GLOBAL"), "the old name must be gone");
      assertTrue(afterRename.contains("Renamed__GLOBAL"),
                 "the raw node must survive under the new name");
      assertTrue(afterRename.contains("1^128^__NULL__^myvs^host-org"),
                 "the raw body must be preserved across the rename");
      assertTrue(afterRename.contains("First__GLOBAL") && afterRename.contains("Third__GLOBAL"),
                 "the other dashboards must be unaffected");
      // still excluded from ordinary lookup/listing under its new name too
      assertNull(registry.getDashboard("Renamed__GLOBAL"));
      assertEquals(2, registry.getDashboardNames().length);
   }

   /**
    * Per 02-refute.md / 03-fix.md: a by-name removal of the broken entry must still work
    * coherently (it must actually be removable, not stuck forever because it round-trips).
    */
   @Test
   void removeEntryRemovesUnparsableEntryByName() throws Exception {
      String orgId = "dr78103_d_org";
      builder = SecurityTestDataBuilder.create().addOrg(orgId + "_name", orgId).setup();
      registry = new DashboardRegistry(orgId, eventPublisher, securityEngine);
      writeRegistryFile(registry.getPath(), FileVersions.DASHBOARD_REGISTRY);
      registry.loadDashboard(null);

      registry.removeEntry("Broken__GLOBAL");

      String afterRemove = readRegistryFile(registry.getPath());
      assertFalse(afterRemove.contains("Broken__GLOBAL"),
                  "a deliberate by-name removal must actually remove the broken entry");
      assertTrue(afterRemove.contains("First__GLOBAL") && afterRemove.contains("Third__GLOBAL"),
                 "the other dashboards must be unaffected");
      assertEquals(2, registry.getDashboardNames().length);
   }

   private void writeRegistryFile(String path, String version) throws Exception {
      StringWriter buffer = new StringWriter();
      PrintWriter writer = new PrintWriter(buffer);
      writer.println("<?xml version=\"1.0\"?>");
      writer.println("<dashboardRegistry>");
      writer.println("<Version>" + version + "</Version>");
      writeNode(writer, "First__GLOBAL");
      // the reporter's / #77603's corrupt dashboard: a viewsheet entry with no <path>
      writer.println("<node>");
      writer.println("<name><![CDATA[Broken__GLOBAL]]></name>");
      writer.println("<dashboard class=\"inetsoft.sree.web.dashboard.VSDashboard\">");
      writer.println("<entry type=\"64\" identifier=\"1^128^__NULL__^myvs^host-org\"></entry>");
      writer.println("</dashboard>");
      writer.println("</node>");
      writeNode(writer, "Third__GLOBAL");
      writer.println("</dashboardRegistry>");
      writer.flush();

      try(DataSpace.Transaction tx = dataSpace.beginTransaction();
          OutputStream out = tx.newStream(null, path))
      {
         out.write(buffer.toString().getBytes(StandardCharsets.UTF_8));
         out.flush();
         tx.commit();
      }
   }

   private static void writeNode(PrintWriter writer, String name) {
      writer.println("<node>");
      writer.println("<name><![CDATA[" + name + "]]></name>");
      dashboard().writeXML(writer);
      writer.println("</node>");
   }

   private static VSDashboard dashboard() {
      VSDashboard dashboard = new VSDashboard();
      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
                                        "myvs", null, "host-org");
      ViewsheetEntry viewsheetEntry = new ViewsheetEntry("myvs", null);
      viewsheetEntry.setIdentifier(entry.toIdentifier());
      dashboard.setViewsheet(viewsheetEntry);
      return dashboard;
   }

   private String readRegistryFile(String path) throws IOException {
      try(InputStream in = dataSpace.getInputStream(null, path)) {
         return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
      }
   }

   private static void assertOrder(String content, String... namesInOrder) {
      int last = -1;

      for(String name : namesInOrder) {
         int idx = content.indexOf(name);
         assertTrue(idx >= 0, name + " must appear in the file");
         assertTrue(idx > last, name + " must appear after the previous name, preserving order");
         last = idx;
      }
   }
}
