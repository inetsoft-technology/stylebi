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
package inetsoft.web.admin.pageheader;

import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.util.Tool;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome()
@Disabled
@Tag("core")
class EmPageHeaderControllerTest {
   @Autowired SecurityEngine engine;
   FileAuthenticationProvider provider;

   @BeforeEach
   void before() throws Exception {
      engine.enableSecurity();
      SUtil.setMultiTenant(true);

      AuthenticationChain chain =
         (AuthenticationChain) engine.getSecurityProvider().getAuthenticationProvider();
      provider = (FileAuthenticationProvider) chain.getProviders().getFirst();

      FSUser org_admin = new FSUser(new IdentityID("org_admin", "org0"));
      org_admin.setRoles(new IdentityID[] {new IdentityID("Organization Administrator", null)});
      provider.addUser(org_admin);

      FSOrganization org0 = new FSOrganization("org0", "org0", null, null);
      org0.setMembers(new String[] {"org_admin"});
      provider.addOrganization(org0);

      FSUser sys_admin = new FSUser(new IdentityID("sys_admin", "host-org"));
      sys_admin.setRoles(new IdentityID[] {new IdentityID("Administrator", null)});
      provider.addUser(sys_admin);
      provider.getOrganization("host-org").setMembers(new String[]{"sys_admin"});
   }

   @Test
   void checkDiffUserForPageHeader() throws Exception {
      EmPageHeaderController emPageHeaderController = new EmPageHeaderController(engine, null, null, null, null);

      SRPrincipal org_admin = new SRPrincipal(new IdentityID("org_admin", "org0"), new IdentityID[] {new IdentityID("Organization Administrator", null)},
                                              new String[0], "org0",
                                              Tool.getSecureRandom().nextLong());
      EmPageHeaderModel orgEmPageModel =
         emPageHeaderController.getPageHeaderModel("Primary", false, org_admin);
      assertNull(orgEmPageModel.currOrgID());

      IdentityID[] administrator = {new IdentityID("Administrator", null)};
      SRPrincipal sys_admin = new SRPrincipal(new IdentityID("sys_admin", Organization.getDefaultOrganizationID()), administrator, new String[0],
                                              Organization.getDefaultOrganizationID(),
                                              Tool.getSecureRandom().nextLong());
      EmPageHeaderModel emPageHeaderModel =
         emPageHeaderController.getPageHeaderModel("Primary", false, sys_admin);
      String[] expOrgs = {"host-org", "template-org", "SELF", "org0"};

      assertArrayEquals(expOrgs, emPageHeaderModel.orgIDs().toArray(new String[0]));
      assertEquals("host-org", emPageHeaderModel.currOrgID());
   }
}