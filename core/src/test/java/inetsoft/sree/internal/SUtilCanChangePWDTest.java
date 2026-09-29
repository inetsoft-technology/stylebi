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
package inetsoft.sree.internal;

import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.*;
import inetsoft.uql.XPrincipal;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77341: {@link SUtil#canChangePWD} must not offer change password to the anonymous
 * (guest) user, whose principal name is the identity key anonymous~;~orgID.
 */
@Tag("core")
class SUtilCanChangePWDTest {
   private MockedStatic<SecurityEngine> securityEngineStatic;
   private MockedStatic<SreeEnv> sreeEnvStatic;

   @BeforeEach
   void setUp() {
      SecurityEngine engine = mock(SecurityEngine.class);
      SecurityProvider provider = mock(SecurityProvider.class);
      // a plain (non-chain) authentication provider counts as the editable provider
      AuthenticationProvider authc = mock(AuthenticationProvider.class);
      when(engine.isSecurityEnabled()).thenReturn(true);
      when(engine.getSecurityProvider()).thenReturn(provider);
      when(provider.getAuthenticationProvider()).thenReturn(authc);

      securityEngineStatic = mockStatic(SecurityEngine.class);
      securityEngineStatic.when(SecurityEngine::getSecurity).thenReturn(engine);
      sreeEnvStatic = mockStatic(SreeEnv.class);
      sreeEnvStatic.when(() -> SreeEnv.getProperty("enable.changePassword")).thenReturn("true");
   }

   @AfterEach
   void tearDown() {
      sreeEnvStatic.close();
      securityEngineStatic.close();
   }

   @Test
   void anonymousGuestCannotChangePassword() throws Exception {
      assertFalse(SUtil.canChangePWD(internalPrincipal(XPrincipal.ANONYMOUS)));
   }

   @Test
   void internalNamedUserCanChangePassword() throws Exception {
      assertTrue(SUtil.canChangePWD(internalPrincipal("alice")));
   }

   @Test
   void userNamePrefixedWithAnonymousCanChangePassword() throws Exception {
      assertTrue(SUtil.canChangePWD(internalPrincipal("anonymousbob")));
   }

   private static XPrincipal internalPrincipal(String user) {
      XPrincipal principal = mock(XPrincipal.class);
      when(principal.getName()).thenReturn(new IdentityID(user, "host-org").convertToKey());
      when(principal.getProperty("__internal__")).thenReturn("true");
      return principal;
   }
}
