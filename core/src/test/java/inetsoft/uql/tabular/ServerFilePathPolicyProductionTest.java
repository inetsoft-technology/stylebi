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
package inetsoft.uql.tabular;

import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.uql.XPrincipal;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Bug #64331: the production policy (ServerFilePathPolicy.create) with the real
 * OrganizationManager.isSiteAdmin and the real property engine. An organization admin is not a
 * site admin and is restricted, and the per-organization roots are found for a mixed-case
 * organization id, with the global roots as the fallback.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ServerFilePathPolicyProductionTest {
   @BeforeEach
   void setUp() {
      provider = mock(SecurityProvider.class);
      when(provider.isSystemAdministratorRole(any())).thenAnswer(
         inv -> SITE_ADMIN_ROLE.equals(inv.getArgument(0)));
      when(provider.isOrgAdministratorRole(any())).thenAnswer(
         inv -> ORG_ADMIN_ROLE.equals(inv.getArgument(0)));
      engine = mock(SecurityEngine.class);
      when(engine.isSecurityEnabled()).thenReturn(true);
      when(engine.getSecurityProvider()).thenReturn(provider);
      securityStatic = mockStatic(SecurityEngine.class, CALLS_REAL_METHODS);
      securityStatic.when(SecurityEngine::getSecurity).thenReturn(engine);

      SreeEnv.setProperty(ServerFilePathPolicy.ALLOWED_ROOTS_PROPERTY, GLOBAL_ROOT);
      SreeEnv.setProperty("inetsoft.org.OrgB." + ServerFilePathPolicy.ALLOWED_ROOTS_PROPERTY,
                          ORG_B_ROOT);
   }

   @AfterEach
   void tearDown() {
      SreeEnv.setProperty(ServerFilePathPolicy.ALLOWED_ROOTS_PROPERTY, null);
      SreeEnv.setProperty("inetsoft.org.OrgB." + ServerFilePathPolicy.ALLOWED_ROOTS_PROPERTY,
                          null);
      securityStatic.close();
   }

   @Test
   void orgAdminIsRestrictedAndSiteAdminIsNot() {
      ServerFilePathPolicy policy = ServerFilePathPolicy.create(engine);

      assertFalse(policy.isUnrestricted(principal("orgadmin", "orgA", ORG_ADMIN_ROLE)));
      assertFalse(policy.isUnrestricted(principal("user", "orgA", USER_ROLE)));
      assertFalse(policy.isUnrestricted(null));
      assertTrue(policy.isUnrestricted(principal("admin", "host-org", SITE_ADMIN_ROLE)));
   }

   @Test
   void securityDisabledIsUnrestricted() {
      when(engine.isSecurityEnabled()).thenReturn(false);
      ServerFilePathPolicy policy = ServerFilePathPolicy.create(engine);

      assertTrue(policy.isUnrestricted(principal("orgadmin", "orgA", ORG_ADMIN_ROLE)));
      assertTrue(policy.isUnrestricted(null));
   }

   @Test
   void organizationRootsOverrideTheGlobalRoots() {
      ServerFilePathPolicy policy = ServerFilePathPolicy.create(engine);

      // the override was written as OrgB, the principal's org id is orgb
      assertEquals(List.of(Paths.get(ORG_B_ROOT)),
                   policy.getAllowedRoots(principal("orgadmin", "orgb", ORG_ADMIN_ROLE)));
      assertEquals(List.of(Paths.get(ORG_B_ROOT)),
                   policy.getAllowedRoots(principal("orgadmin", "OrgB", ORG_ADMIN_ROLE)));
      assertEquals(List.of(Paths.get(GLOBAL_ROOT)),
                   policy.getAllowedRoots(principal("orgadmin", "orgA", ORG_ADMIN_ROLE)));
      assertEquals(List.of(Paths.get(GLOBAL_ROOT)), policy.getAllowedRoots(null));
   }

   private static XPrincipal principal(String user, String org, IdentityID role) {
      return new XPrincipal(new IdentityID(user, org), new IdentityID[] { role }, new String[0],
                            org);
   }

   private static final String GLOBAL_ROOT =
      Path.of(System.getProperty("java.io.tmpdir"), "global-root").toAbsolutePath().toString();
   private static final String ORG_B_ROOT =
      Path.of(System.getProperty("java.io.tmpdir"), "org-b-root").toAbsolutePath().toString();
   private static final IdentityID SITE_ADMIN_ROLE = new IdentityID("Administrator", null);
   private static final IdentityID ORG_ADMIN_ROLE =
      new IdentityID("Organization Administrator", null);
   private static final IdentityID USER_ROLE = new IdentityID("Everyone", null);
   private SecurityProvider provider;
   private SecurityEngine engine;
   private MockedStatic<SecurityEngine> securityStatic;
}
