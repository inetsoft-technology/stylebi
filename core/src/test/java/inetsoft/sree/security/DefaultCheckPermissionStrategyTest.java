/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.sree.security;

import inetsoft.sree.internal.SUtil;
import inetsoft.test.SreeHome;
import inetsoft.util.Tool;

import org.junit.jupiter.api.*;
import java.util.ArrayList;
import java.util.Arrays;

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
      org_admin = new SRPrincipal(new IdentityID("org_admin", "org0"), orgAdministrator, new String[0], "org0",
                                  Tool.getSecureRandom().nextLong());

      IdentityID[] everyoneRoles = {new IdentityID("Everyone", "org0")};
      normalUser = new SRPrincipal(new IdentityID("normalUser", "org0"), everyoneRoles, new String[0], "org0",
                                   Tool.getSecureRandom().nextLong());
   }

   @AfterAll
   static void cleanup() throws Exception {
      SecurityEngine.getSecurity().disableSecurity();
      SecurityEngine.clear();
   }

   @Test
   void checkNormalUserNoPermissionOfEM() {
      Boolean r1 = defaultCheckPermissionStrategy.checkPermission(normalUser, ResourceType.EM,
                                                                  "*", ResourceAction.ACCESS);
      assertFalse(r1, "normal user no permission of em");
   }

   @Test
   void checkOrgAdminPermssionOnMonitor() {
      String[] monitorPages = {"monitoring/cache", "monitoring/cluster", "monitoring/log",
                               "monitoring/viewsheets", "monitoring/queries","monitoring/summary",
                               "monitoring/users"};
      ArrayList<Boolean> monitorResults = new ArrayList<>();

      for(String str:monitorPages) {
         monitorResults.add(
            defaultCheckPermissionStrategy.checkPermission(org_admin, ResourceType.EM_COMPONENT, str,
                                                           ResourceAction.ACCESS));
      }

      Boolean[] monitorExps = {false, false, false, true, true, false, true};

      assertArrayEquals(monitorResults.toArray(new Boolean[0]), monitorExps,
                        "The org administrator permission of monitor not right: \nExpected: " +
                           Arrays.toString(monitorExps) + ", \nActual: " + monitorResults);
   }

   @Test
   void checkOrgAdminPermissionOfEM() {
      String[] components = {"auditing", "notification", "settings", "monitoring",
                             "settings/general", "settings/content", "settings/logging",
                             "settings/presentation", "settings/properties", "settings/schedule",
                             "settings/security"};

      ArrayList<Boolean> results = new ArrayList<>();

      for(String str:components) {
         results.add(defaultCheckPermissionStrategy.checkPermission(org_admin, ResourceType.EM_COMPONENT,
                                                                    str, ResourceAction.ACCESS));
      }
      Boolean[]  exps = {true, false, true, true, false, true, false, true, false, true, true};
      assertArrayEquals(results.toArray(new Boolean[0]), exps,
                        "The org administrator permission not right: \nExpected: " +
                           Arrays.toString(exps) + ", \nActual: " + results);;
   }

   @Test
   void checkOrgAdminPermissionOnSecurityContent() {
      String[] securityPages = {"settings/security/provider", "settings/security/sso",
                                "settings/security/actions", "settings/security/users"};
      String[] contentPages = {"settings/content/data-space", "settings/content/drivers-and-plugins",
                               "settings/content/repository", "settings/content/materialized-views"};

      ArrayList<Boolean> securityResults = new ArrayList<>();
      ArrayList<Boolean> contentResults = new ArrayList<>();

      for(String str:securityPages) {
         securityResults.add(
            defaultCheckPermissionStrategy.checkPermission(org_admin, ResourceType.EM_COMPONENT, str,
                                                           ResourceAction.ACCESS));
      }

      Boolean[] securityExps = {false, false, true, true};

      assertArrayEquals(securityResults.toArray(new Boolean[0]), securityExps,
                        "The org administrator permission of security not right: \nExpected: " +
                           Arrays.toString(securityExps) + ", \nActual: " + securityResults);

      for(String str:contentPages) {
         contentResults.add(
            defaultCheckPermissionStrategy.checkPermission(org_admin, ResourceType.EM_COMPONENT, str,
                                                           ResourceAction.ACCESS));
      }
      Boolean[] contentExps = {false, false, true, true};
      assertArrayEquals(contentResults.toArray(new Boolean[0]), contentExps,
                        "The org administrator permission of security content not right: \nExpected: " +
                           Arrays.toString(contentExps) + ", \nActual: " + contentResults);
   }

   @Test
   void checkOrgAdminPermissionSchedulePages() {
      String[] schedulePages = {"settings/schedule/settings", "settings/schedule/cycles",
                                "settings/schedule/status", "settings/schedule/tasks",
                                "settings/presentation/themes", "settings/presentation/org-settings",
                                "settings/presentation/settings"};

      ArrayList<Boolean> scheduleResults = new ArrayList<>();

      for(String str:schedulePages) {
         scheduleResults.add(
            defaultCheckPermissionStrategy.checkPermission(org_admin, ResourceType.EM_COMPONENT, str,
                                                           ResourceAction.ACCESS));
      }

      Boolean[] scheduleExps = {false, true, false, true, true, false, true};

      assertArrayEquals(scheduleResults.toArray(new Boolean[0]), scheduleExps,
                        "The org administrator permission of schedule,presentation not right: \nExpected: " +
                           Arrays.toString(scheduleExps) + ", \nActual: " + scheduleResults);
   }
}