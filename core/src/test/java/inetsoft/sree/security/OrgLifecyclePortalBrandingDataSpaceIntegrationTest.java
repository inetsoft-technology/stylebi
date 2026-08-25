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
package inetsoft.sree.security;

/*
 * P1 (test plan, reports/test-team/2026-08-25_bug-75800/plan.md) -- real-DataSpace companion to
 * OrgLifecyclePortalBrandingTest's mock-only logo/favicon copy/rename tests (TC-01..TC-04). Those
 * mock-based tests can only prove that addLogoEntry/addFaviconEntry/removeLogoEntry/
 * removeFaviconEntry were *invoked* with the expected map-value strings; they cannot prove actual
 * bytes land at the destination path the map value claims, because dataSpace.getInputStream()/
 * withOutputStream() run against a real DataSpace bean regardless (PortalThemesManager itself is
 * the only mocked collaborator). This file swaps in a real, non-mocked PortalThemesManager so the
 * whole chain -- map lookup, byte copy/relocation via copyOrganizationInternal()'s own file-copy
 * block, and copyDataSpace()'s earlier blanket portal/{orgId} rename -- runs for real end to end.
 *
 * Precedents: OrgLifecycleDataSpaceIntegrationTest (real @Autowired DataSpace + BaseTestConfiguration
 * pattern, same package so this file reuses OrgLifecyclePortalBrandingTest.StubProvider directly);
 * PermissionMatrixActionsS8Test (real `new PortalThemesManager(Cluster.getInstance(),
 * DataSpace.getDataSpace())` construction -- the no-arg constructor passes a null Cluster, which
 * NPEs inside loadThemes()'s save() call when it acquires the distributed save lock, so the 2-arg
 * constructor with a real Cluster bean is required). Unlike PermissionMatrixActionsS8Test's plain
 * `new` construction inside a test method, this class registers the real manager as a Spring @Bean
 * (same "portalThemesManager" bean name BaseTestConfiguration/OrgLifecyclePortalBrandingTest use),
 * so PortalThemesManager.getManager() -- which resolves via
 * ConfigurationContext.getContext().getSpringBean(PortalThemesManager.class) -- returns this same
 * instance inside copyOrganizationInternal(), and Spring's bean post-processing invokes its
 * @PostConstruct loadThemes() automatically (no manual call needed, unlike the plain-`new` case).
 *
 * Also registers the same RepletRegistryManager bean OrgLifecyclePortalBrandingTest does -- the
 * rename path's cleanup calls RepletRegistryManager.getInstance().clearOrgCache(fromOrgId), which
 * BaseTestConfiguration doesn't provide.
 */

import inetsoft.sree.RepletRegistryManager;
import inetsoft.sree.internal.DataCycleManager;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.portal.CustomThemesManager;
import inetsoft.sree.portal.PortalThemesManager;
import inetsoft.sree.web.dashboard.DashboardRegistryManager;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.util.DataSpace;
import inetsoft.web.admin.security.IdentityService;
import inetsoft.web.admin.security.user.IdentityThemeService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  OrgLifecyclePortalBrandingDataSpaceIntegrationTest.RealPortalThemesManagerConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class OrgLifecyclePortalBrandingDataSpaceIntegrationTest {

   @Autowired
   private DataSpace dataSpace;

   @Autowired
   private PortalThemesManager portalThemesManager;

   // ── TC-05: copy -- bytes present at destination, source untouched ──

   @Test
   void copy_logoFile_bytesPresentAtDestination() throws Exception {
      String fromOrgId = "ds_logo_copy_from";
      String toOrgId = "ds_logo_copy_to";

      dataSpace.withOutputStream("portal/" + fromOrgId, "logo.png",
                                  out -> out.write(bytes("logo-bytes")));
      portalThemesManager.addLogoEntry(fromOrgId, "portal/" + fromOrgId + "/logo.png");

      invokeCopyOrganization(fromOrgId, toOrgId, false);

      assertTrue(dataSpace.exists("portal/" + toOrgId, "logo.png"),
                 "destination org must have received the logo file");
      assertEquals("logo-bytes", readAll("portal/" + toOrgId, "logo.png"));
      assertEquals("portal/" + toOrgId + "/logo.png",
                   portalThemesManager.getLogoEntries().get(toOrgId));

      assertTrue(dataSpace.exists("portal/" + fromOrgId, "logo.png"),
                 "source file must be left in place (copy, not rename)");
   }

   // ── TC-06: rename -- bytes relocated, source gone (relies on copyDataSpace()'s earlier blanket
   // rename of the whole portal/{orgId} subtree -- Risk 2 from the analysis) ──

   @Test
   void rename_logoFile_bytesRelocatedToDestination_sourceGone() throws Exception {
      String fromOrgId = "ds_logo_rename_from";
      String toOrgId = "ds_logo_rename_to";

      dataSpace.withOutputStream("portal/" + fromOrgId, "logo.png",
                                  out -> out.write(bytes("logo-bytes")));
      portalThemesManager.addLogoEntry(fromOrgId, "portal/" + fromOrgId + "/logo.png");

      invokeCopyOrganization(fromOrgId, toOrgId, true);

      assertTrue(dataSpace.exists("portal/" + toOrgId, "logo.png"),
                 "destination org must have received the logo file");
      assertEquals("logo-bytes", readAll("portal/" + toOrgId, "logo.png"));
      assertEquals("portal/" + toOrgId + "/logo.png",
                   portalThemesManager.getLogoEntries().get(toOrgId));

      assertFalse(dataSpace.exists("portal/" + fromOrgId, "logo.png"),
                  "source file must be relocated (gone), not left behind, after a rename");
      assertNull(portalThemesManager.getLogoEntries().get(fromOrgId),
                 "source org's map entry must be removed after rename");
   }

   // ── Bonus favicon variants (plan's "optional but recommended", same mechanism, low
   // incremental cost -- closing the symmetric gap the plan flagged) ──

   @Test
   void copy_faviconFile_bytesPresentAtDestination() throws Exception {
      String fromOrgId = "ds_favicon_copy_from";
      String toOrgId = "ds_favicon_copy_to";

      dataSpace.withOutputStream("portal/" + fromOrgId, "favicon.ico",
                                  out -> out.write(bytes("favicon-bytes")));
      portalThemesManager.addFaviconEntry(fromOrgId, "portal/" + fromOrgId + "/favicon.ico");

      invokeCopyOrganization(fromOrgId, toOrgId, false);

      assertTrue(dataSpace.exists("portal/" + toOrgId, "favicon.ico"),
                 "destination org must have received the favicon file");
      assertEquals("favicon-bytes", readAll("portal/" + toOrgId, "favicon.ico"));
      assertEquals("portal/" + toOrgId + "/favicon.ico",
                   portalThemesManager.getFaviconEntries().get(toOrgId));

      assertTrue(dataSpace.exists("portal/" + fromOrgId, "favicon.ico"),
                 "source file must be left in place (copy, not rename)");
   }

   @Test
   void rename_faviconFile_bytesRelocatedToDestination_sourceGone() throws Exception {
      String fromOrgId = "ds_favicon_rename_from";
      String toOrgId = "ds_favicon_rename_to";

      dataSpace.withOutputStream("portal/" + fromOrgId, "favicon.ico",
                                  out -> out.write(bytes("favicon-bytes")));
      portalThemesManager.addFaviconEntry(fromOrgId, "portal/" + fromOrgId + "/favicon.ico");

      invokeCopyOrganization(fromOrgId, toOrgId, true);

      assertTrue(dataSpace.exists("portal/" + toOrgId, "favicon.ico"),
                 "destination org must have received the favicon file");
      assertEquals("favicon-bytes", readAll("portal/" + toOrgId, "favicon.ico"));
      assertEquals("portal/" + toOrgId + "/favicon.ico",
                   portalThemesManager.getFaviconEntries().get(toOrgId));

      assertFalse(dataSpace.exists("portal/" + fromOrgId, "favicon.ico"),
                  "source file must be relocated (gone), not left behind, after a rename");
      assertNull(portalThemesManager.getFaviconEntries().get(fromOrgId),
                 "source org's map entry must be removed after rename");
   }

   // ── fixture helpers ──

   private static byte[] bytes(String s) {
      return s.getBytes(StandardCharsets.UTF_8);
   }

   private String readAll(String dir, String file) throws Exception {
      try(InputStream in = dataSpace.getInputStream(dir, file)) {
         assertNotNull(in, "expected a readable file at " + dir + "/" + file);
         return new String(in.readAllBytes(), StandardCharsets.UTF_8);
      }
   }

   // Same mockStatic(CustomThemesManager.class) + copyOrganization(...) shape as
   // OrgLifecyclePortalBrandingTest.invokeCopyOrganization(); duplicated rather than shared because
   // that method is `private` on its own class and this integration test intentionally keeps its
   // collaborator mocks/real-bean wiring separate from the pure-mock class.
   private void invokeCopyOrganization(String fromOrgId, String toOrgId, boolean replace) {
      OrgLifecyclePortalBrandingTest.StubProvider provider =
         new OrgLifecyclePortalBrandingTest.StubProvider();

      CustomThemesManager noopThemesManager = mock(CustomThemesManager.class);
      when(noopThemesManager.getCustomThemes()).thenReturn(Collections.emptySet());

      FSOrganization fromOrganization = new FSOrganization(fromOrgId);
      fromOrganization.setName(fromOrgId);

      try(MockedStatic<CustomThemesManager> ctm = mockStatic(CustomThemesManager.class)) {
         ctm.when(CustomThemesManager::getManager).thenReturn(noopThemesManager);

         provider.copyOrganization(fromOrganization, toOrgId,
            mock(IdentityService.class), mock(IdentityThemeService.class),
            mock(DashboardRegistryManager.class), mock(DataCycleManager.class),
            mock(Principal.class), replace, null);
      }
   }

   @Configuration
   public static class RealPortalThemesManagerConfig {
      // Real, non-mocked PortalThemesManager -- the 2-arg constructor is required, not the no-arg
      // one: the no-arg constructor passes a null Cluster, which NPEs inside loadThemes()'s save()
      // when it acquires the distributed save lock. BaseTestConfiguration provides a real Cluster
      // bean (MockCluster) for exactly this purpose (see PermissionMatrixActionsS8Test precedent).
      @Bean
      public PortalThemesManager portalThemesManager(Cluster cluster, DataSpace dataSpace) {
         return new PortalThemesManager(cluster, dataSpace);
      }

      // rename's cleanup path calls RepletRegistryManager.getInstance().clearOrgCache(); not
      // provided by BaseTestConfiguration, so registered here as a real instance (same as
      // OrgLifecyclePortalBrandingTest.PortalThemesManagerConfig).
      @Bean
      public RepletRegistryManager repletRegistryManager(DataSpace dataSpace) {
         return new RepletRegistryManager(dataSpace);
      }
   }
}
