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
package inetsoft.report.composition.graph;

/*
 * Regression coverage for the webmap.suspend.until multi-tenant leak: one org's Mapbox/Google
 * Maps 402 quota error (AssemblyImageService's WebMapLimitException catch block) used to disable
 * web-map rendering for every organization on the install for 24 hours, because both the write
 * (AssemblyImageService.java) and the read/self-clear (MapGenerator.isWebMap) used the plain,
 * process-global SreeEnv overloads instead of the org-scoped ones. Follows the actAs(orgId)
 * pattern from OrgLifecycleScopedPropertiesIntegrationTest.
 */

import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.OrganizationContextHolder;
import inetsoft.sree.security.SRPrincipal;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.graph.ChartDescriptor;
import inetsoft.uql.viewsheet.graph.MapInfo;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.util.ThreadContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class MapGeneratorWebMapSuspendOrgScopeTest {

   @BeforeEach
   void setUp() {
      // Global (unscoped) web-map service config -- not part of the bug, needed only so
      // isWebMap() has something other than the suspend flag to decide on.
      SreeEnv.setProperty("webmap.service", MapInfo.MAPBOX);
      SreeEnv.setProperty("mapbox.user", "tester");
      SreeEnv.setProperty("mapbox.token", "token");
      SreeEnv.setProperty("mapbox.style", "style");
   }

   @AfterEach
   void tearDown() {
      ThreadContext.setContextPrincipal(null);
      OrganizationContextHolder.setCurrentOrgId(null);

      SreeEnv.setProperty("webmap.service", null);
      SreeEnv.setProperty("mapbox.user", null);
      SreeEnv.setProperty("mapbox.token", null);
      SreeEnv.setProperty("mapbox.style", null);
   }

   @Test
   void orgASuspension_doesNotSuspendOrgB_andExpiresWithoutCrossOrgEffect() {
      String orgA = "webmap_suspend_org_a";
      String orgB = "webmap_suspend_org_b";

      VSChartInfo info = new VSChartInfo();
      info.setFacet(false);
      ChartDescriptor desc = new ChartDescriptor();
      desc.getPlotDescriptor().setWebMap(true);

      // Org A hits the Mapbox/Google Maps quota limit -- mirrors the write in
      // AssemblyImageService's WebMapLimitException catch block.
      actAs(orgA);
      long hours24 = System.currentTimeMillis() + 24 * 60 * 60000L;
      SreeEnv.setProperty("webmap.suspend.until", hours24 + "", true);

      assertFalse(MapGenerator.isWebMap(info, desc, null),
                  "org A must see web maps suspended after its own quota error");

      // Org B never triggered a suspension -- this is the assertion that fails before the fix,
      // when both call sites read/write the plain global key.
      actAs(orgB);
      assertTrue(MapGenerator.isWebMap(info, desc, null),
                 "org B must NOT be suspended by org A's quota error");

      // Org A's suspension expires -- isWebMap() must self-clear without affecting org B.
      actAs(orgA);
      SreeEnv.setProperty("webmap.suspend.until", (System.currentTimeMillis() - 1000) + "", true);
      assertTrue(MapGenerator.isWebMap(info, desc, null),
                 "org A's expired suspension must clear and no longer block web maps");
      assertNull(SreeEnv.getProperty("inetsoft.org." + orgA + ".webmap.suspend.until"),
                 "expiry must self-clear the org-scoped key");

      actAs(orgB);
      assertTrue(MapGenerator.isWebMap(info, desc, null),
                 "org B must remain unaffected by org A's suspension expiry/self-clear");
   }

   private static void actAs(String orgId) {
      ThreadContext.setContextPrincipal(new SRPrincipal(new IdentityID("tester", orgId),
         new IdentityID[0], new String[0], orgId, 1L));
      OrganizationContextHolder.setCurrentOrgId(orgId);
   }
}
