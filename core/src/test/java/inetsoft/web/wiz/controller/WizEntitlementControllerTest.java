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

package inetsoft.web.wiz.controller;

import inetsoft.sree.security.*;
import inetsoft.uql.util.XSessionService;
import inetsoft.web.wiz.WizPermissions;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * The caller is a wiz principal, which the engine's delegating strategy approves for everything.
 * The engine here reproduces that: it approves any wiz-tagged principal and otherwise answers from
 * a fixed grant set (plus the default-allow end-user permissions), so the endpoint only reports
 * the real grants if it strips the wiz tag.
 */
@Tag("core")
class WizEntitlementControllerTest {
   private MockedStatic<XSessionService> sessionStatic;
   private SecurityEngine engine;
   private WizEntitlementController controller;
   private Set<String> grants;

   @BeforeEach
   void setUp() throws Exception {
      XSessionService sessionService = mock(XSessionService.class);
      AtomicLong counter = new AtomicLong();
      lenient().when(sessionService.createSessionID(anyString(), any()))
         .thenAnswer(inv -> inv.getArgument(0, String.class) + counter.incrementAndGet());
      sessionStatic = mockStatic(XSessionService.class);
      sessionStatic.when(XSessionService::getService).thenReturn(sessionService);

      grants = Set.of();
      engine = mock(SecurityEngine.class);
      lenient().when(engine.checkPermission(any(Principal.class), eq(ResourceType.WIZ_PERMISSION),
                                            anyString(), eq(ResourceAction.ACCESS)))
         .thenAnswer(inv -> {
            Principal p = inv.getArgument(0);
            String name = inv.getArgument(2);
            return SRPrincipal.isWizPrincipal(p) || grants.contains(name) ||
               WizPermissions.isEndUser(name);
         });
      controller = new WizEntitlementController(engine);

      MockHttpServletRequest request = new MockHttpServletRequest();
      request.addHeader("Authorization", "Bearer test-jwt");
      RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
   }

   @AfterEach
   void tearDown() throws Exception {
      sessionStatic.close();
      RequestContextHolder.resetRequestAttributes();
   }

   private SRPrincipal wizPrincipal(String name) {
      SRPrincipal p = new SRPrincipal(new IdentityID(name, "org0"), new IdentityID[0],
                                      new String[0], "org0", 1L);
      p.setProperty("wiz", "true");
      return p;
   }

   @Test
   void wizPrincipalWithoutGrantDoesNotReceiveAdminPermissions() throws Exception {
      List<String> permissions =
         controller.getMyEntitlements(wizPrincipal("alice")).permissions();

      assertFalse(permissions.contains("entitlement.write"),
                  "the wiz bypass must not leak admin permissions");
      assertEquals(WizPermissions.END_USER, permissions);
   }

   @Test
   void explicitGrantIsReported() throws Exception {
      grants = Set.of("config.read");

      List<String> permissions =
         controller.getMyEntitlements(wizPrincipal("alice")).permissions();

      assertTrue(permissions.contains("config.read"));
      assertFalse(permissions.contains("config.write"));
   }

   @Test
   void userHoldingEverythingReceivesAllPermissions() throws Exception {
      grants = Set.copyOf(WizPermissions.ALL);

      assertEquals(WizPermissions.ALL,
                   controller.getMyEntitlements(wizPrincipal("root")).permissions());
   }

   @Test
   void evaluatesOnPrincipalWithoutWizTagAndWithoutLoginCheck() throws Exception {
      SRPrincipal wiz = wizPrincipal("alice");
      controller.getMyEntitlements(wiz);

      ArgumentCaptor<Principal> captor = ArgumentCaptor.forClass(Principal.class);
      verify(engine, atLeastOnce()).checkPermission(captor.capture(),
         eq(ResourceType.WIZ_PERMISSION), anyString(), eq(ResourceAction.ACCESS));

      for(Principal checked : captor.getAllValues()) {
         assertFalse(SRPrincipal.isWizPrincipal(checked));
         assertTrue(((SRPrincipal) checked).isIgnoreLogin());
         assertEquals(wiz.getName(), checked.getName());
      }

      assertTrue(SRPrincipal.isWizPrincipal(wiz), "the caller's own principal is left untouched");
   }

   @Test
   void missingPrincipalIsUnauthorized() throws Exception {
      ResponseStatusException ex = assertThrows(ResponseStatusException.class,
         () -> controller.getMyEntitlements(null));
      assertEquals(401, ex.getStatusCode().value());
   }

   @Test
   void missingBearerTokenIsForbidden() throws Exception {
      RequestContextHolder.setRequestAttributes(
         new ServletRequestAttributes(new MockHttpServletRequest()));

      ResponseStatusException ex = assertThrows(ResponseStatusException.class,
         () -> controller.getMyEntitlements(wizPrincipal("alice")));
      assertEquals(403, ex.getStatusCode().value());
      verifyNoInteractions(engine);
   }
}
