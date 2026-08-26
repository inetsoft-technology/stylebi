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
 * WizDelegatingCheckPermissionStrategy scenario table
 *
 * [Wiz: any type]        principal is wiz-authenticated (SRPrincipal, wiz="true")   -> true, delegate untouched
 * [Non-wiz: delegates]   principal is not wiz-authenticated                          -> delegate's answer, called once
 *
 * This is the single choke point that both SecurityEngine.checkPermission and every direct
 * SecurityProvider.checkPermission(...) caller (DefaultAuthorizationFilter, ClusterController,
 * BasicAuthenticationFilter, etc.) go through once wired into CompositeSecurityProvider -- see
 * CompositeSecurityProviderTest for proof of that end-to-end wiring.
 */

import inetsoft.uql.util.XSessionService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("core")
class WizDelegatingCheckPermissionStrategyTest {
   private static final IdentityID USER_ID = new IdentityID("alice", "org1");

   // [Wiz: any type] a wiz-authenticated principal is allowed unconditionally, for any resource
   // type, and the wrapped delegate is never consulted
   @ParameterizedTest
   @MethodSource("resourceTypes")
   void checkPermission_wizPrincipal_bypassesDelegate(ResourceType type) {
      try(MockedStatic<XSessionService> sessionService = mockSessionService()) {
         CheckPermissionStrategy delegate = mock(CheckPermissionStrategy.class);
         WizDelegatingCheckPermissionStrategy strategy =
            new WizDelegatingCheckPermissionStrategy(delegate);
         SRPrincipal principal = new SRPrincipal(USER_ID, new IdentityID[0], new String[0], "org1", 1L);
         principal.setProperty("wiz", "true");

         boolean allowed = strategy.checkPermission(principal, type, "*", ResourceAction.ACCESS);

         assertTrue(allowed);
         verifyNoInteractions(delegate);
      }
   }

   private static Stream<Arguments> resourceTypes() {
      return Stream.of(
         // object-ACL resource type
         Arguments.of(ResourceType.ASSET),
         // capability-switch resource type
         Arguments.of(ResourceType.VIEWSHEET),
         // the actual gate DefaultAuthorizationFilter checks before EM is ever reached
         Arguments.of(ResourceType.EM)
      );
   }

   // [Non-wiz: delegates] a non-wiz principal's decision comes entirely from the wrapped delegate
   @Test
   void checkPermission_nonWizPrincipal_returnsDelegateDecision() {
      try(MockedStatic<XSessionService> sessionService = mockSessionService()) {
         CheckPermissionStrategy delegate = mock(CheckPermissionStrategy.class);
         WizDelegatingCheckPermissionStrategy strategy =
            new WizDelegatingCheckPermissionStrategy(delegate);
         SRPrincipal principal = new SRPrincipal(USER_ID, new IdentityID[0], new String[0], "org1", 2L);

         when(delegate.checkPermission(principal, ResourceType.EM, "*", ResourceAction.ACCESS))
            .thenReturn(false);

         boolean allowed = strategy.checkPermission(principal, ResourceType.EM, "*", ResourceAction.ACCESS);

         assertFalse(allowed);
         verify(delegate).checkPermission(principal, ResourceType.EM, "*", ResourceAction.ACCESS);
      }
   }

   private static MockedStatic<XSessionService> mockSessionService() {
      MockedStatic<XSessionService> mocked = mockStatic(XSessionService.class);
      mocked.when(XSessionService::getService).thenReturn(new XSessionService());
      return mocked;
   }
}
