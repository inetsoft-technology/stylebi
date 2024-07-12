/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.sree.security;

import inetsoft.sree.SreeEnv;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.*;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SreeHome()
class SecurityEngineTest {
   @BeforeAll
   static void before() {
      SreeEnv.setProperty("security.script.everyone", "true");
   }

   @BeforeEach
   void setup() throws Exception {
      SecurityEngine.clear();
      engine = SecurityEngine.getSecurity();
      engine.enableSecurity();
      final AuthenticationChain chain =
         (AuthenticationChain) engine.getSecurityProvider().getAuthenticationProvider();

      final FileAuthenticationProvider provider =
         (FileAuthenticationProvider) chain.getProviders().get(0);
      provider.addUser(new FSUser(new IdentityID("Alice", OrganizationManager.getCurrentOrgName())));
      provider.addUser(new FSUser(new IdentityID("Bob", OrganizationManager.getCurrentOrgName())));
   }

   @AfterAll
   static void cleanup() throws Exception {
      SecurityEngine.getSecurity().disableSecurity();
      SecurityEngine.clear();
      SreeEnv.remove("security.script.everyone");
   }

   @Test
   void readScriptDefault() throws SecurityException {
      final SRPrincipal principal = new SRPrincipal(new IdentityID("Alice", OrganizationManager.getCurrentOrgName()));
      final boolean allowed = engine.checkPermission(principal, ResourceType.SCRIPT,
                                                     "createBulletGraph", ResourceAction.READ);

      assertTrue(allowed);
   }

   @Test
   void readScriptWhenLibraryHasPermission() throws SecurityException {
      final SRPrincipal principal = new SRPrincipal(new IdentityID("Alice", OrganizationManager.getCurrentOrgName()));

      final Permission rootPerm = new Permission();
      String orgId = OrganizationManager.getInstance().getCurrentOrgID();
      rootPerm.setUserGrantsForOrg(ResourceAction.WRITE, Collections.singleton("Bob"), orgId);
      engine.getSecurityProvider().setPermission(ResourceType.LIBRARY, "*", rootPerm);
      engine.getSecurityProvider().removePermission(ResourceType.SCRIPT_LIBRARY, "*");

      boolean allowed = engine.checkPermission(principal, ResourceType.SCRIPT,
                                               "createBulletGraph", ResourceAction.READ);
      assertFalse(allowed);

      final Permission scriptPerm = new Permission();
      engine.getSecurityProvider().setPermission(ResourceType.SCRIPT_LIBRARY, "*", scriptPerm);
      allowed = engine.checkPermission(principal, ResourceType.SCRIPT,
                                       "createBulletGraph", ResourceAction.READ);
      assertTrue(allowed);
   }

   private SecurityEngine engine;
}
