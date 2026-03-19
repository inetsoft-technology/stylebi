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
import inetsoft.sree.portal.CustomThemesManager;
import inetsoft.sree.portal.PortalThemesManager;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.test.*;
import inetsoft.web.admin.security.AuthenticationProviderService;
import inetsoft.web.portal.model.SignupResponseModel;
import inetsoft.web.portal.service.UserSignupService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class SignupControllerTest {
   @Autowired
   SecurityEngine securityEngine;
   SignupController signupController;

   @Mock
   SimpMessagingTemplate messagingTemplate;

   @BeforeEach
   void before() throws Exception {
      securityEngine.enableSecurity();
      SUtil.setMultiTenant(true);

      ObjectMapper objectMapper = Mockito.mock(ObjectMapper.class);
      AuthenticationProviderService authenticationProviderService =
         new AuthenticationProviderService(securityEngine, objectMapper, messagingTemplate, null, null);
      UserSignupService userSignupService = new UserSignupService(authenticationProviderService);
      CustomThemesManager customThemesManager = Mockito.mock(CustomThemesManager.class);
      PortalThemesManager portalThemesManager = Mockito.mock(PortalThemesManager.class);
      signupController = new SignupController(userSignupService, customThemesManager, portalThemesManager);
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