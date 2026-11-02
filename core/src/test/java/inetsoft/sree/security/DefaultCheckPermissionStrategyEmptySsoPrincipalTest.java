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

import inetsoft.sree.internal.SUtil;
import inetsoft.test.*;
import inetsoft.uql.XPrincipal;
import inetsoft.util.Tool;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #79036, a principal that is authenticated but has no roles, no groups and no local user
 * (an SSO user whose provider sends no roles) has no identity. It must get the "My Dashboards
 * allowed by default" rule that any principal with one role or group gets, and nothing else.
 * The anonymous principal stays denied.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome()
@Tag("core")
class DefaultCheckPermissionStrategyEmptySsoPrincipalTest {
   private static DefaultCheckPermissionStrategy strategy;
   private static SRPrincipal emptySso;
   private static SRPrincipal oneRoleSso;
   private static SRPrincipal anonymous;
   private static SRPrincipal internal;

   @BeforeAll
   static void before(@Autowired SecurityEngine securityEngine) throws Exception {
      securityEngine.enableSecurity();
      SUtil.setMultiTenant(true);
      strategy = new DefaultCheckPermissionStrategy(securityEngine.getSecurityProvider());

      emptySso = principal("ssoUser", new IdentityID[0]);
      // a role the provider does not know, as in the diagnosis
      oneRoleSso = principal("ssoUser2", new IdentityID[]{ new IdentityID("Reviewer", "org0") });
      anonymous = principal(XPrincipal.ANONYMOUS, new IdentityID[0]);
      internal = principal("internalUser", new IdentityID[0]);
      internal.setProperty("__internal__", "true");
   }

   private static SRPrincipal principal(String name, IdentityID[] roles) {
      return new SRPrincipal(new IdentityID(name, "org0"), roles, new String[0], "org0",
                             Tool.getSecureRandom().nextLong());
   }

   @Test
   void emptySsoPrincipalHasNoIdentity() {
      assertNull(emptySso.createUser());
      assertNull(anonymous.createUser());
      assertNotNull(oneRoleSso.createUser());
   }

   @Test
   void emptySsoPrincipalReadsMyDashboards() {
      assertTrue(strategy.checkPermission(
         emptySso, ResourceType.MY_DASHBOARDS, "*", ResourceAction.READ));
      assertTrue(strategy.checkPermission(
         emptySso, ResourceType.REPORT, Tool.MY_DASHBOARD, ResourceAction.READ));
   }

   @Test
   void emptySsoPrincipalMatchesOneRolePrincipal() {
      assertEquals(
         strategy.checkPermission(oneRoleSso, ResourceType.MY_DASHBOARDS, "*", ResourceAction.READ),
         strategy.checkPermission(emptySso, ResourceType.MY_DASHBOARDS, "*", ResourceAction.READ));
   }

   @Test
   void anonymousStillDenied() {
      assertFalse(strategy.checkPermission(
         anonymous, ResourceType.MY_DASHBOARDS, "*", ResourceAction.READ));
      assertFalse(strategy.checkPermission(
         anonymous, ResourceType.REPORT, Tool.MY_DASHBOARD, ResourceAction.READ));
   }

   @Test
   void internalPrincipalStillDenied() {
      assertFalse(strategy.checkPermission(
         internal, ResourceType.MY_DASHBOARDS, "*", ResourceAction.READ));
   }

   @Test
   void emptySsoPrincipalOnlyReadsMyDashboards() {
      assertFalse(strategy.checkPermission(
         emptySso, ResourceType.MY_DASHBOARDS, "*", ResourceAction.WRITE));
      assertFalse(strategy.checkPermission(
         emptySso, ResourceType.MY_DASHBOARDS, "*", ResourceAction.ADMIN));
   }

   @Test
   void reportsFolderStillDenied() {
      assertFalse(strategy.checkPermission(
         emptySso, ResourceType.REPORT, "/", ResourceAction.READ));
      assertFalse(strategy.checkPermission(
         emptySso, ResourceType.REPORT, "Reports", ResourceAction.READ));
   }

   @Test
   void assetsAndEmComponentsStillDenied() {
      assertFalse(strategy.checkPermission(
         emptySso, ResourceType.ASSET, "/", ResourceAction.READ));
      assertFalse(strategy.checkPermission(
         emptySso, ResourceType.EM_COMPONENT, "settings", ResourceAction.READ));
      assertFalse(strategy.checkPermission(
         emptySso, ResourceType.EM, "*", ResourceAction.ACCESS));
   }

   /** Defaults granted to a principal with an identity stay denied without one. */
   @ParameterizedTest
   @EnumSource(value = ResourceType.class,
               names = { "VIEWSHEET_TOOLBAR_ACTION", "REPORT_EXPORT", "SHARE", "PORTAL_TAB",
                         "SCHEDULE_OPTION", "AI_ASSISTANT" })
   void otherDefaultGrantsStillDenied(ResourceType type) {
      assertFalse(strategy.checkPermission(emptySso, type, "Dashboard", ResourceAction.READ));
   }
}
