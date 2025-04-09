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
package inetsoft.web.portal.service;

import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.test.SreeHome;
import inetsoft.web.admin.security.AuthenticationProviderService;

import org.junit.jupiter.api.*;
import org.mockito.Mock;
import org.mockito.Mockito;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@Disabled("Test is flaky on the build server")
@SreeHome
class UserSignupServiceTest {
   static UserSignupService userSignupService;
   static  SecurityEngine securityEngine;
   @Mock
   static SimpMessagingTemplate messagingTemplate;

   @BeforeAll
   static void before() throws Exception {
      securityEngine = SecurityEngine.getSecurity();
      securityEngine.enableSecurity();
      SUtil.setMultiTenant(true);

      ObjectMapper objectMapper = Mockito.mock(ObjectMapper.class);
      AuthenticationProviderService authenticationProviderService =
         new AuthenticationProviderService(securityEngine, objectMapper, messagingTemplate);

      userSignupService = new UserSignupService(authenticationProviderService);
   }

   @AfterAll
   static void cleanup() throws Exception {
      AuthenticationChain chain =
         (AuthenticationChain) securityEngine.getSecurityProvider().getAuthenticationProvider();
      FileAuthenticationProvider fileAuthenticationProvider = (FileAuthenticationProvider) chain.getProviders().get(0);
      fileAuthenticationProvider.removeUser(new IdentityID("1@inetsoft.com",Organization.getDefaultOrganizationID()));

      SecurityEngine.clear();
      securityEngine.disableSecurity();
   }

   @Test
   void checkAutoRegisterUser() {
      if(!userSignupService.userExist(new IdentityID("1@inetsoft.com",Organization.getDefaultOrganizationID()))) {
         userSignupService.autoRegisterUser("googleId", "1@inetsoft.com");
      }

      ArrayList<String> results = new ArrayList<>();
      User user = securityEngine.getSecurityProvider().getUser(new IdentityID("1@inetsoft.com",Organization.getDefaultOrganizationID()));
      results.add(user.getName());
      results.add(user.getGoogleSSOId());
      results.add(user.getOrganizationID());

      String[] exp = {"1@inetsoft.com", "googleId", "host-org"};
      assertArrayEquals(exp, results.toArray(new String[0]), "auto register user failed");
   }
}