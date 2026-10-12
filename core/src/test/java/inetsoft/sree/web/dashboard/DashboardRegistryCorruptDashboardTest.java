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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
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
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.*;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77603: a viewsheet entry without {@code <path>} is refused when it is parsed. In a
 * registry file, the refusal of one dashboard must not drop the dashboards after it: that
 * dashboard is skipped with a warning and the others keep loading.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DashboardRegistryCorruptDashboardTest {
   @Autowired
   private ApplicationEventPublisher eventPublisher;

   @Autowired
   private SecurityEngine securityEngine;

   @Autowired
   private DataSpace dataSpace;

   private SecurityTestDataBuilder builder;
   private DashboardRegistry registry;
   private Logger logger;
   private ListAppender<ILoggingEvent> appender;

   @BeforeEach
   void setUp() {
      logger = (Logger) LoggerFactory.getLogger(DashboardRegistry.class);
      appender = new ListAppender<>();
      appender.start();
      logger.addAppender(appender);
   }

   @AfterEach
   void tearDown() {
      logger.detachAppender(appender);

      if(registry != null) {
         registry.clear();
      }

      if(builder != null) {
         builder.teardown();
      }
   }

   @Test
   void dashboardWithoutPathIsSkippedAndOthersLoad() throws Exception {
      String orgId = "dr77603_org";
      builder = SecurityTestDataBuilder.create().addOrg(orgId + "_name", orgId).setup();
      registry = new DashboardRegistry(orgId, eventPublisher, securityEngine);
      writeRegistryFile(registry.getPath());

      registry.loadDashboard(null);

      assertNotNull(registry.getDashboard("First__GLOBAL"), "the first dashboard must load");
      assertNull(registry.getDashboard("Broken__GLOBAL"),
                 "the dashboard without <path> must be skipped");
      assertNotNull(registry.getDashboard("Third__GLOBAL"),
                    "a dashboard after the corrupt one must still load");
      assertEquals(2, registry.getDashboardNames().length);

      assertTrue(appender.list.stream().anyMatch(e ->
                    e.getLevel() == Level.WARN &&
                    e.getFormattedMessage().contains("Broken__GLOBAL") &&
                    e.getFormattedMessage().contains("missing <path>")),
                 "a warning must name the skipped dashboard and the reason");
   }

   private void writeRegistryFile(String path) throws Exception {
      StringWriter buffer = new StringWriter();
      PrintWriter writer = new PrintWriter(buffer);
      writer.println("<?xml version=\"1.0\"?>");
      writer.println("<dashboardRegistry>");
      writer.println("<Version>" + FileVersions.DASHBOARD_REGISTRY + "</Version>");
      writeNode(writer, "First__GLOBAL");
      // the reporter's corrupt dashboard: a viewsheet entry with no <path>
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
      VSDashboard dashboard = new VSDashboard();
      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
                                        "myvs", null, "host-org");
      ViewsheetEntry viewsheetEntry = new ViewsheetEntry("myvs", null);
      viewsheetEntry.setIdentifier(entry.toIdentifier());
      dashboard.setViewsheet(viewsheetEntry);

      writer.println("<node>");
      writer.println("<name><![CDATA[" + name + "]]></name>");
      dashboard.writeXML(writer);
      writer.println("</node>");
   }
}
