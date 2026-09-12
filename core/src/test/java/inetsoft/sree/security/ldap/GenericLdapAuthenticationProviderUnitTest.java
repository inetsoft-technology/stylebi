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
package inetsoft.sree.security.ldap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.util.Identity;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/*
 * Cases deferred - require real LDAP (ApacheDS) or cannot run under Spring classloader:
 *
 * [GenericLdapAuthenticationProvider] testContext() StartTLS session validity check
 *     -> Mockito extraInterfaces fails for package-private StartTlsContext under Spring classloader;
 *        behavior is exercised by GenericLdapAuthenticationProviderTest integration tests
 * [GenericLdapAuthenticationProvider] createContext() StartTLS negotiation
 *     -> needs real TLS server; covered by GenericLdapAuthenticationProviderTest
 * [GenericLdapAuthenticationProvider] getUserCommonName(String) - LDAP search path
 *     -> needs real LDAP context; covered indirectly via authenticate() integration tests
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class GenericLdapAuthenticationProviderUnitTest {

   private GenericLdapAuthenticationProvider provider;

   @BeforeEach
   void setUp() {
      provider = new GenericLdapAuthenticationProvider();
      provider.setProtocol("ldap");
      provider.setHost("localhost");
      provider.setPort(10389);
      provider.setRootDn("dc=example,dc=com");
      provider.setLdapAdministrator("uid=admin,ou=system");
      provider.setUserSearch("(objectClass=inetOrgPerson)");
      provider.setUserBase("ou=people");
      provider.setUserAttribute("uid");
      provider.setMailAttribute("mail");
      provider.setGroupSearch("(objectClass=organizationalUnit)");
      provider.setGroupBase("ou=people");
      provider.setGroupAttribute("ou");
      provider.setRoleSearch("(objectClass=groupOfUniqueNames)");
      provider.setRoleBase("ou=groups");
      provider.setRoleAttribute("cn");
      provider.setUserRolesSearch("(&(objectClass=groupOfUniqueNames)(uniqueMember={1}))");
      provider.setRoleRolesSearch("(&(objectClass=groupOfUniqueNames)(uniqueMember={1}))");
      provider.setGroupRolesSearch("(&(objectClass=groupOfUniqueNames)(uniqueMember={1}))");
   }

   @AfterEach
   void tearDown() {
      if(provider != null) {
         provider.tearDown();
         provider = null;
      }
   }

   // ---- writeConfiguration: startTls field is serialized correctly ----

   @Test
   void writeConfiguration_startTlsFlag_isSerializedCorrectly() throws Exception {
      ObjectMapper mapper = new ObjectMapper();

      provider.setStartTls(false);
      ObjectNode configFalse = (ObjectNode) provider.writeConfiguration(mapper);
      assertFalse(configFalse.get("startTls").asBoolean(true));

      provider.setStartTls(true);
      ObjectNode configTrue = (ObjectNode) provider.writeConfiguration(mapper);
      assertTrue(configTrue.get("startTls").asBoolean(false));

      provider.setStartTls(false); // restore
   }

   // ---- readConfiguration: startTls field is deserialized correctly ----

   @Test
   void readConfiguration_startTlsTrue_isRestoredCorrectly() throws Exception {
      ObjectMapper mapper = new ObjectMapper();
      // Build config from current provider (startTls=false), then override to true
      ObjectNode config = (ObjectNode) provider.writeConfiguration(mapper);
      config.put("startTls", true);

      GenericLdapAuthenticationProvider fresh = new GenericLdapAuthenticationProvider();
      fresh.readConfiguration(config);
      assertTrue(fresh.isStartTls());
      fresh.tearDown();
   }

   // ---- getUserCommonName: non-USER type returns name unchanged without LDAP call ----

   @Test
   void getUserCommonName_nonUserType_returnsNameUnchanged() {
      // via: getUserCommonName(String, int) -> short-circuits when type != Identity.USER
      assertEquals("testGroup", provider.getUserCommonName("testGroup", Identity.GROUP));
      assertEquals("testRole", provider.getUserCommonName("testRole", Identity.ROLE));
   }
}
