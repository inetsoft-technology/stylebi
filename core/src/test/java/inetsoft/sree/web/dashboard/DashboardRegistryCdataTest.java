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
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77891, #77892: a dashboard name, creator or description holding {@code ]]>} or a
 * control character was written raw into CDATA, so the registry file could not be read back
 * and every dashboard in it was lost. The registry is saved and loaded through the data space.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DashboardRegistryCdataTest {
   @Autowired
   private ApplicationEventPublisher eventPublisher;

   @Autowired
   private SecurityEngine securityEngine;

   private SecurityTestDataBuilder builder;
   private DashboardRegistry registry;
   private DashboardRegistry loaded;

   @AfterEach
   void tearDown() {
      if(registry != null) {
         registry.clear();
      }

      if(loaded != null) {
         loaded.clear();
      }

      if(builder != null) {
         builder.teardown();
      }
   }

   @Test
   void dashboardNameCreatorAndDescriptionRoundTrip() throws Exception {
      String orgId = "dr77891_org";
      builder = SecurityTestDataBuilder.create().addOrg(orgId + "_name", orgId).setup();
      registry = new DashboardRegistry(orgId, eventPublisher, securityEngine);
      registry.addDashboard("d]]>1__GLOBAL", dashboard("x]]>y", "a]]>b"));
      registry.addDashboard("d2__GLOBAL", dashboard("p\u0001q\tr", "plain"));
      registry.addDashboard("d3__GLOBAL", dashboard("plain", "plain"));
      registry.save();

      loaded = new DashboardRegistry(orgId, eventPublisher, securityEngine);
      loaded.loadDashboard(null);

      assertEquals(3, loaded.getDashboardNames().length);
      VSDashboard first = (VSDashboard) loaded.getDashboard("d]]>1__GLOBAL");
      assertNotNull(first);
      assertEquals("x]]>y", first.getDescription());
      assertEquals("a]]>b", first.getCreatedBy());
      assertEquals("p q\tr", loaded.getDashboard("d2__GLOBAL").getDescription());
      assertEquals("plain", loaded.getDashboard("d3__GLOBAL").getDescription());
   }

   private static VSDashboard dashboard(String description, String createdBy) {
      VSDashboard dashboard = new VSDashboard();
      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
                                        "myvs", null, "host-org");
      ViewsheetEntry viewsheetEntry = new ViewsheetEntry("myvs", null);
      viewsheetEntry.setIdentifier(entry.toIdentifier());
      dashboard.setViewsheet(viewsheetEntry);
      dashboard.setDescription(description);
      dashboard.setCreatedBy(createdBy);
      return dashboard;
   }
}
