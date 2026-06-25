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
package inetsoft.sree.security.provider;

import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.util.DataSpace;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mindrot.BCrypt;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class VirtualAuthenticationProviderTest
   extends AuthenticationProviderContractTest<VirtualAuthenticationProvider>
{
   static final String ADMIN_PASSWORD = "Admin@123!";
   static final IdentityID ADMIN_ID =
      new IdentityID("admin", Organization.getDefaultOrganizationID());

   @BeforeAll
   static void writeVirtualConfig() throws Exception {
      String defaultOrgId = Organization.getDefaultOrganizationID();
      String bcryptHash = BCrypt.hashpw(ADMIN_PASSWORD, BCrypt.gensalt());
      String adminRoleKey = new IdentityID("Administrator", defaultOrgId).convertToKey();

      // Build virtual_security.xml so VirtualAuthenticationProvider can load
      // without requiring the INETSOFT_ADMIN_PASSWORD environment variable.
      String xml =
         "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n" +
         "<virtualSecurityProvider>\n" +
         "<FSUser class=\"inetsoft.sree.security.FSUser\">" +
         "<name><![CDATA[admin]]></name>" +
         "<active><![CDATA[true]]></active>" +
         "<organization><![CDATA[" + defaultOrgId + "]]></organization>" +
         "<password algorithm=\"bcrypt\"><![CDATA[" + bcryptHash + "]]></password>" +
         "<emails></emails>" +
         "<roles><role><![CDATA[" + adminRoleKey + "]]></role></roles>" +
         "<groups></groups>" +
         "</FSUser>\n" +
         "</virtualSecurityProvider>\n";

      DataSpace space = DataSpace.getDataSpace();

      try(DataSpace.Transaction tx = space.beginTransaction();
          OutputStream out = tx.newStream(null, "virtual_security.xml"))
      {
         out.write(xml.getBytes(StandardCharsets.UTF_8));
         tx.commit();
      }
   }

   @Override
   protected VirtualAuthenticationProvider createProvider() {
      return new VirtualAuthenticationProvider();
   }

   @Override
   protected IdentityID validUserId()  { return ADMIN_ID; }
   @Override
   protected String    validPassword()  { return ADMIN_PASSWORD; }
   @Override
   protected String    validOrgId()     { return Organization.getDefaultOrganizationID(); }

   @Test
   void isVirtual_returnsTrue() {
      assertTrue(provider.isVirtual(), "VirtualAuthenticationProvider must report isVirtual()=true");
   }

   @Test
   void getRoles_containsAdministratorAndEveryone() {
      IdentityID[] roles = provider.getRoles();
      assertNotNull(roles);
      boolean hasAdmin = false, hasEveryone = false;

      for(IdentityID r : roles) {
         if("Administrator".equals(r.name)) hasAdmin    = true;
         if("Everyone".equals(r.name))      hasEveryone = true;
      }

      assertTrue(hasAdmin,    "Must expose Administrator role");
      assertTrue(hasEveryone, "Must expose Everyone role");
   }

   @Test
   void getOrganizationIDs_returnsDefaultOrg() {
      String[] orgIds = provider.getOrganizationIDs();
      assertNotNull(orgIds);
      assertEquals(1, orgIds.length);
      assertEquals(Organization.getDefaultOrganizationID(), orgIds[0]);
   }

   @Test
   void getUsers_containsAdminAndAnonymous() {
      IdentityID[] users = provider.getUsers();
      assertNotNull(users);
      boolean hasAdmin = false, hasAnonymous = false;

      for(IdentityID u : users) {
         if("admin".equals(u.name))     hasAdmin     = true;
         if("anonymous".equals(u.name)) hasAnonymous = true;
      }

      assertTrue(hasAdmin,     "getUsers() must include admin");
      assertTrue(hasAnonymous, "getUsers() must include anonymous");
   }
}
