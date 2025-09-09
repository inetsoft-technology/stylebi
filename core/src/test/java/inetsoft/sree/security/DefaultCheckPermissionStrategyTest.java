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
package inetsoft.sree.security;

import inetsoft.sree.internal.SUtil;
import inetsoft.test.SreeHome;
import inetsoft.util.Tool;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@SreeHome()
class DefaultCheckPermissionStrategyTest {
   static DefaultCheckPermissionStrategy defaultCheckPermissionStrategy;
   static SRPrincipal org_admin, normalUser;

   @BeforeAll
   static void before() throws Exception {
      SecurityEngine.clear();
      SecurityEngine engine = SecurityEngine.getSecurity();
      engine.enableSecurity();
      SUtil.setMultiTenant(true);
      defaultCheckPermissionStrategy =
         new DefaultCheckPermissionStrategy(engine.getSecurityProvider());

      IdentityID[] orgAdministrator = {new IdentityID("Organization Administrator",null)};
      org_admin = new SRPrincipal(new IdentityID("org_admin", "org0"), orgAdministrator,
                                  new String[0], "org0", Tool.getSecureRandom().nextLong());

      IdentityID[] everyoneRoles = {new IdentityID("Everyone", "org0")};
      normalUser = new SRPrincipal(new IdentityID("normalUser", "org0"), everyoneRoles,
                                   new String[0], "org0", Tool.getSecureRandom().nextLong());
   }

   @AfterAll
   static void cleanup() throws Exception {
      SecurityEngine.getSecurity().disableSecurity();
      SecurityEngine.clear();
   }

   @Test
   void checkNormalUserNoPermissionOfEM() {
      try(MockedStatic<SUtil> mocked = Mockito.mockStatic(SUtil.class, Mockito.CALLS_REAL_METHODS)) {
         mocked.when(SUtil::isMultiTenant).thenReturn(true);

         assertFalse(
            defaultCheckPermissionStrategy.checkPermission(normalUser, ResourceType.EM, "*", ResourceAction.ACCESS),
                     "normal user no permission of em");
      }
   }

   @ParameterizedTest
   @MethodSource("provideEMTestCases")
   void checkOrgAdminEMPermissions(String[] pages, Boolean[] expectedResults, String message) {
      ArrayList<Boolean> actualResults = new ArrayList<>();

      try(MockedStatic<SUtil> mocked = Mockito.mockStatic(SUtil.class, Mockito.CALLS_REAL_METHODS)) {
         mocked.when(SUtil::isMultiTenant).thenReturn(true);

         for(String page : pages) {
            actualResults.add(
               defaultCheckPermissionStrategy.checkPermission(org_admin, ResourceType.EM_COMPONENT, page,
                                                              ResourceAction.ACCESS));
         }
      }
      assertArrayEquals(expectedResults, actualResults.toArray(new Boolean[0]), message);
   }

   private static Stream<Arguments> provideEMTestCases() {
      return Stream.of(
         Arguments.of(
            new String[]{"monitoring/cache", "monitoring/cluster", "monitoring/log",
                         "monitoring/viewsheets", "monitoring/queries", "monitoring/summary", "monitoring/users"},
            new Boolean[]{false, false, false, true, true, false, true},
            "The org administrator permission of monitor not right"
         ),
         Arguments.of(
            new String[]{"auditing", "notification", "settings", "monitoring",
                         "settings/general", "settings/content", "settings/logging", "settings/presentation",
                         "settings/properties", "settings/schedule", "settings/security"},
            new Boolean[]{true, false, true, true, false, true, false, true, false, true, true},
            "The org administrator permission of EM not right"
         ),
         Arguments.of(
            new String[]{"settings/security/provider", "settings/security/sso",
                         "settings/security/actions", "settings/security/users", "settings/security/googleSignIn"},
            new Boolean[]{false, false, true, true, false},
            "The org administrator permission of security not right"
         ),
         Arguments.of(
            new String[]{"settings/content/data-space", "settings/content/drivers-and-plugins",
                         "settings/content/repository", "settings/content/materialized-views"},
            new Boolean[]{false, false, true, true},
            "The org administrator permission of content not right"
         ),
         Arguments.of(
            new String[]{"settings/schedule/settings", "settings/schedule/cycles",
                         "settings/schedule/status", "settings/schedule/tasks"},
            new Boolean[]{false, true, false, true},
            "The org administrator permission of schedule not right"
         ),
         Arguments.of(
            new String[]{"settings/presentation/themes",
                         "settings/presentation/org-settings", "settings/presentation/settings"},
            new Boolean[]{true, false, true},
            "The org administrator permission of presentation not right"
         )
      );
   }
}