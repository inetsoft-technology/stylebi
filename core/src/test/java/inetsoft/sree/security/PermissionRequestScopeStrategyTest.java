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
import inetsoft.test.*;
import inetsoft.util.Tool;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The real permission strategy over the real security engine gives the same answers, and leaves
 * the principal's roles the same, whether or not a PermissionRequestScope is open and however many
 * times it runs inside one.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome()
@Tag("core")
class PermissionRequestScopeStrategyTest {
   private static DefaultCheckPermissionStrategy strategy;
   private static final List<SRPrincipal> principals = new ArrayList<>();

   @BeforeAll
   static void before(@Autowired SecurityEngine securityEngine) throws Exception {
      strategy = new DefaultCheckPermissionStrategy(securityEngine.getSecurityProvider());
      securityEngine.enableSecurity();
      SUtil.setMultiTenant(true);

      principals.add(new SRPrincipal(new IdentityID("sysAdmin", "org0"),
                                     new IdentityID[] { new IdentityID("Administrator", null) },
                                     new String[0], "org0", Tool.getSecureRandom().nextLong()));
      principals.add(new SRPrincipal(
         new IdentityID("org_admin", "org0"),
         new IdentityID[] { new IdentityID("Organization Administrator", null) },
         new String[0], "org0", Tool.getSecureRandom().nextLong()));
      principals.add(new SRPrincipal(new IdentityID("normalUser", "org0"),
                                     new IdentityID[] { new IdentityID("Everyone", "org0") },
                                     new String[0], "org0", Tool.getSecureRandom().nextLong()));
   }

   @Test
   void answersAreIdenticalInsideAndOutsideAScope() throws Exception {
      String[] paths = { "/", "folder", "folder/report", "My Reports/x", "other" };
      ResourceAction[] actions = { ResourceAction.READ, ResourceAction.WRITE,
                                   ResourceAction.DELETE, ResourceAction.ADMIN };

      for(SRPrincipal principal : principals) {
         Map<String, Boolean> outside = new LinkedHashMap<>();

         for(String path : paths) {
            for(ResourceAction action : actions) {
               outside.put(path + "|" + action,
                           strategy.checkPermission(principal, ResourceType.REPORT, path, action));
            }
         }

         outside.put("composer", strategy.checkPermission(
            principal, ResourceType.VIEWSHEET, "*", ResourceAction.ACCESS));
         outside.put("mv", strategy.checkPermission(
            principal, ResourceType.MATERIALIZATION, "*", ResourceAction.ACCESS));

         for(int round = 0; round < 3; round++) {
            Map<String, Boolean> inside = new LinkedHashMap<>();

            try(PermissionRequestScope.Handle scope = PermissionRequestScope.open()) {
               for(int i = 0; i < 5; i++) {
                  inside.clear();

                  for(String path : paths) {
                     for(ResourceAction action : actions) {
                        inside.put(path + "|" + action, strategy.checkPermission(
                           principal, ResourceType.REPORT, path, action));
                     }
                  }

                  inside.put("composer", strategy.checkPermission(
                     principal, ResourceType.VIEWSHEET, "*", ResourceAction.ACCESS));
                  inside.put("mv", strategy.checkPermission(
                     principal, ResourceType.MATERIALIZATION, "*", ResourceAction.ACCESS));
               }
            }

            assertEquals(outside, inside, principal.getName());
         }
      }
   }
}
