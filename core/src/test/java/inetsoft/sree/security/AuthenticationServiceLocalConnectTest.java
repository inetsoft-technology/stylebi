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

import inetsoft.mv.MVManager;
import inetsoft.sree.ClientInfo;
import inetsoft.sree.web.*;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.XSessionService;
import inetsoft.util.IndexedStorage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

/*
 * Intent vs implementation suspects
 *
 * [Suspect] shell-dsl-command-test-plan.md Task 6 Step 2c (Epic 70095): the shell's local
 *           connection mode (ClientFactory.createLocalClient(), enterprise module) authenticates
 *           through AuthenticationService.authenticate(IdentityID, String, String, String,
 *           boolean) -- a 5-arg overload that only calls SecurityEngine.authenticate(...) and
 *           never AuthenticationService.addSession(...). Session-license enforcement
 *           (SessionLicenseManager.newSession(...)) is only ever invoked from
 *           AbstractSecurityFilter (a Servlet Filter), which local mode's in-process connect
 *           never passes through. This test pins that gap at its source: local connect succeeds
 *           even when the configured SessionLicenseManager would reject every session.
 *           NEEDS-VERIFICATION with product: is bypassing the concurrent-session license by
 *           design (local mode is a privileged, non-HTTP admin/scripting path) or an oversight?
 *           Not fixed here -- see docs/shell-dsl-apitest-traceability.md "Epic 70095" section.
 */
@Tag("core")
class AuthenticationServiceLocalConnectTest {
   // via: ClientFactory.createLocalClient() -> AuthenticationService.authenticate(IdentityID,
   // String, String, String, boolean) -> AuthenticationService.authenticate(ClientInfo, Object)
   // -> SecurityEngine.authenticate(ClientInfo, Object, SecurityProvider)
   @Test
   void authenticate_fiveArgOverload_neverConsultsSessionLicenseManagerEvenWhenAtLimit()
      throws Exception
   {
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      SecurityProvider securityProvider = mock(SecurityProvider.class);
      MVManager mvManager = mock(MVManager.class);
      DataSourceRegistry dataSourceRegistry = mock(DataSourceRegistry.class);
      XSessionService sessionService = mock(XSessionService.class);
      LocaleService localeService = mock(LocaleService.class);
      SessionLicenseServiceProvider sessionLicenseServiceProvider =
         mock(SessionLicenseServiceProvider.class);
      SessionLicenseManager sessionLicenseManager = mock(SessionLicenseManager.class);
      ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
      IndexedStorage indexedStorage = mock(IndexedStorage.class);

      // The session license is fully exhausted: any real attempt to acquire one always fails.
      when(sessionLicenseServiceProvider.getSessionLicenseManager())
         .thenReturn(sessionLicenseManager);
      doThrow(new SessionsExceededException("session limit reached", List.of()))
         .when(sessionLicenseManager).newSession(any());

      Principal authenticated = mock(Principal.class);
      when(authenticated.getName()).thenReturn("alice");
      when(securityEngine.getSecurityProvider()).thenReturn(securityProvider);
      when(securityEngine.authenticate(any(ClientInfo.class), any(), eq(securityProvider)))
         .thenReturn(authenticated);
      when(localeService.getLocale(any(), any())).thenReturn("en_US");

      AuthenticationService service = new AuthenticationService(securityEngine, mvManager,
         dataSourceRegistry, sessionService, localeService, sessionLicenseServiceProvider,
         eventPublisher, indexedStorage);

      IdentityID userId = new IdentityID("alice", "host-org");
      Principal principal =
         service.authenticate(userId, "password", null, "127.0.0.1", false);

      assertNotNull(principal, "local connect must succeed on valid credentials alone");
      verifyNoInteractions(sessionLicenseServiceProvider, sessionLicenseManager);
   }
}
