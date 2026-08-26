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
package inetsoft.sree.security;

/*
 * CompositeSecurityProvider scenario table
 *
 * [Wiz delegation: wired]   principal is wiz-authenticated, checkPermission() called DIRECTLY on
 *                           the provider (no SecurityEngine involved)                -> true
 *
 * This is the regression test for the finding that SecurityEngine.checkPermission is NOT the only
 * permission gate: DefaultAuthorizationFilter (the actual front door for EM access),
 * ClusterController, BasicAuthenticationFilter, and ~25 other admin/** call sites all call
 * SecurityProvider.checkPermission(...) directly on the instance obtained from
 * SecurityEngine.getSecurityProvider() -- which is always a CompositeSecurityProvider (see
 * SecurityEngine.doInit()). Wiring the wiz bypass into createCheckPermissionStrategy() here, rather
 * than only inside SecurityEngine.checkPermission(), is what makes it reach those call sites too.
 */

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.util.XSessionService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// CompositeSecurityProvider.create() reads SreeEnv (Spring-backed PropertiesEngine) to resolve an
// optional custom CheckPermissionStrategy override, so this needs the same Spring test context
// SecurityEnginePermissionTest/DefaultCheckPermissionStrategyTest use, not a bare unit test.
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome()
@Tag("core")
class CompositeSecurityProviderTest {
   private static final IdentityID USER_ID = new IdentityID("alice", "org1");

   // [Wiz delegation: wired] a wiz principal is allowed even when checkPermission() is called
   // directly on the provider -- the exact pattern DefaultAuthorizationFilter uses for the EM
   // access gate, bypassing SecurityEngine.checkPermission entirely
   @Test
   void checkPermission_wizPrincipalCalledDirectlyOnProvider_returnsTrue() {
      try(MockedStatic<XSessionService> sessionService = mockSessionService()) {
         AuthenticationProvider authentication = mock(AuthenticationProvider.class);
         AuthorizationProvider authorization = mock(AuthorizationProvider.class);
         CompositeSecurityProvider provider =
            CompositeSecurityProvider.create(authentication, authorization);
         SRPrincipal principal = new SRPrincipal(USER_ID, new IdentityID[0], new String[0], "org1", 1L);
         principal.setProperty("wiz", "true");

         boolean allowed = provider.checkPermission(principal, ResourceType.EM, "*", ResourceAction.ACCESS);

         assertTrue(allowed);
         verifyNoInteractions(authentication, authorization);
      }
   }

   private static MockedStatic<XSessionService> mockSessionService() {
      MockedStatic<XSessionService> mocked = mockStatic(XSessionService.class);
      mocked.when(XSessionService::getService).thenReturn(new XSessionService());
      return mocked;
   }
}
