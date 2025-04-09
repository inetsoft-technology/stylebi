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
package inetsoft.web.portal.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.test.SreeHome;
import inetsoft.web.admin.security.AuthenticationProviderService;
import inetsoft.web.portal.model.SignupResponseModel;
import inetsoft.web.portal.service.UserSignupService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@SreeHome
class SignupControllerTest {
   static SecurityEngine securityEngine;
   static SignupController signupController;

   @BeforeAll
   static void before() throws Exception {
      securityEngine = SecurityEngine.getSecurity();
      securityEngine.enableSecurity();
      SUtil.setMultiTenant(true);

      ObjectMapper objectMapper = Mockito.mock(ObjectMapper.class);
      AuthenticationProviderService authenticationProviderService =
         new AuthenticationProviderService(securityEngine, objectMapper);
      UserSignupService userSignupService = new UserSignupService(authenticationProviderService);
      signupController = new SignupController(userSignupService);
   }

   @AfterAll
   static void cleanup() throws Exception {
      SecurityEngine.clear();
      securityEngine.disableSecurity();
   }

   @Test
   @Disabled("Test fails without mail server configured")
   void checkSignupWithEmail() {
      HttpServletRequest httpServletRequest = Mockito.mock(HttpServletRequest.class);
      HttpSession httpSession = Mockito.mock(HttpSession.class);
      when(httpServletRequest.getSession(false)).thenReturn(httpSession);

      SreeEnv.setProperty("mail.smtp.host", "52.205.222.65");
      SignupResponseModel model = signupController.signupWithEmail("bonnieshi@inetsoft.com", httpServletRequest);
      assertTrue(model.isSuccess(), () -> "Signup with mail failed: " + model.getErrorMessage());
   }
}