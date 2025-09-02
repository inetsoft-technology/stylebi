/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

import inetsoft.sree.SreeEnv;

import org.junit.jupiter.api.*;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class FileAuthenticationProviderTest {
   @BeforeEach
   void createProvider() throws Exception {
      provider = new FileAuthenticationProvider();

      AuthenticationChain authcChain = new AuthenticationChain();
      authcChain.setProviders(List.of(provider));
      authcChain.saveConfiguration();

      FileAuthorizationProvider authz = new FileAuthorizationProvider();
      authz.setProviderName("Primary");
      AuthorizationChain authzChain = new AuthorizationChain();
      authzChain.setProviders(List.of(authz));
      authcChain.saveConfiguration();

      SreeEnv.setProperty("security.enabled", "true");
      SreeEnv.setProperty("security.users.multiTenant", "true");
      SreeEnv.save();

      SecurityEngine.getSecurity().init();
   }

   @AfterEach
   void destroyProvider() {
      if(provider != null) {
         provider.tearDown();
         provider = null;
      }

      SreeEnv.remove("security.enabled");
      SreeEnv.remove("security.users.multiTenant");
   }

   @Test
   void testAddGetUser() {
      IdentityID userIdentity = new IdentityID("testUser", "testOrg");
      FSUser expectedUser = new FSUser(userIdentity);
      expectedUser.setPassword("hashedPassword");
      expectedUser.setPasswordAlgorithm("bcrypt");
      provider.addUser(expectedUser);

      User actualUser = provider.getUser(userIdentity);

      assertNotNull(actualUser, "User should not be null");
      assertEquals(expectedUser, actualUser, "Returned user should match the expected user");
      assertTrue(Arrays.asList(provider.getUsers()).contains(userIdentity),
                 "Users should contain the expected userIdentity");
   }

   private FileAuthenticationProvider provider;
}