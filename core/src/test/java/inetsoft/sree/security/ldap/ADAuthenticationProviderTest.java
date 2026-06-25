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
package inetsoft.sree.security.ldap;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AD-specific configuration defaults in ADAuthenticationProvider.
 * Tests that do not require a real LDAP/AD server connection.
 */
@Tag("core")
class ADAuthenticationProviderTest {

   private ADAuthenticationProvider provider;

   @BeforeEach
   void setUp() {
      provider = new ADAuthenticationProvider();
   }

   @AfterEach
   void tearDown() {
      if(provider != null) {
         provider.tearDown();
      }
   }

   // AD uses sAMAccountName as the user identifier attribute
   @Test
   void getUserAttribute_returnsADSpecificAttribute() {
      assertEquals("sAMAccountName", provider.getUserAttribute(),
         "AD provider must use sAMAccountName as the user ID attribute");
   }

   // AD role filter targets objectclass=group
   @Test
   void getRoleSearch_targetsADGroupObjectClass() {
      String search = provider.getRoleSearch();
      assertNotNull(search);
      assertTrue(search.contains("objectclass=group"),
         "AD role search must filter on objectclass=group");
   }

   // AD role attribute is 'cn' (Common Name)
   @Test
   void getRoleAttribute_returnsCn() {
      assertEquals("cn", provider.getRoleAttribute(),
         "AD provider must use 'cn' as the role attribute");
   }

   // Default user search targets objectclass=user
   @Test
   void getUserSearch_targetsADUserObjectClass() {
      String search = provider.getUserSearch();
      assertNotNull(search);
      assertTrue(search.contains("objectclass=user"),
         "AD user search must filter on objectclass=user");
   }

   // Default LDAP admin is the standard AD administrator path
   @Test
   void getLdapAdministrator_returnsADDefaultAdminPath() {
      String admin = provider.getLdapAdministrator();
      assertNotNull(admin);
      // AD default is cn=Administrator,cn=Users
      assertTrue(admin.contains("Administrator"),
         "Default AD admin must reference the Administrator account");
   }

   // isVirtual() inherited from parent — AD provider is a real directory
   @Test
   void isVirtual_returnsFalse() {
      assertFalse(provider.isVirtual(),
         "ADAuthenticationProvider is a real directory, not virtual");
   }
}
