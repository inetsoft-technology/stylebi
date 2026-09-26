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
import inetsoft.sree.SreeEnv;
import inetsoft.sree.web.SessionLicenseServiceProvider;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.XSessionService;
import inetsoft.util.IndexedStorage;
import inetsoft.util.audit.Audit;
import inetsoft.web.admin.monitoring.MonitorLevelService;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77081: the interactive log in overload of {@link AuthenticationService#authenticate}
 * (used by the Basic authentication filter and by the public API log in,
 * {@code /api/public/login}) must authenticate with the organization id the security provider
 * stores, not the case the client sent, so the session principal carries the canonical id.
 */
@Tag("core")
class AuthenticationServiceStoredOrgTest {
   private MockedStatic<SreeEnv> sreeEnv;
   private MockedStatic<Audit> audit;
   private MockedStatic<MonitorLevelService> monitorLevel;
   private SecurityEngine securityEngine;
   private SecurityProvider provider;
   private AuthenticationService service;

   @BeforeEach
   void setUp() {
      sreeEnv = mockStatic(SreeEnv.class, withSettings().strictness(Strictness.LENIENT));
      audit = mockStatic(Audit.class, withSettings().strictness(Strictness.LENIENT));
      audit.when(Audit::getInstance).thenReturn(mock(Audit.class));
      monitorLevel = mockStatic(MonitorLevelService.class,
                                withSettings().strictness(Strictness.LENIENT));
      monitorLevel.when(MonitorLevelService::getMonitorLevel).thenReturn(0);

      securityEngine = mock(SecurityEngine.class, withSettings().lenient());
      provider = mock(SecurityProvider.class, withSettings().lenient());
      when(securityEngine.getSecurityProvider()).thenReturn(provider);
      XSessionService sessionService = mock(XSessionService.class, withSettings().lenient());
      when(sessionService.createSessionID(any(), any())).thenReturn("session");

      service = new AuthenticationService(
         securityEngine, mock(MVManager.class), mock(DataSourceRegistry.class), sessionService,
         mock(LocaleService.class), mock(SessionLicenseServiceProvider.class),
         mock(ApplicationEventPublisher.class), mock(IndexedStorage.class));
   }

   @AfterEach
   void tearDown() {
      monitorLevel.close();
      audit.close();
      sreeEnv.close();
   }

   // The provider matched "orga" ignoring case and returned its stored user "alice~;~OrgA":
   // authentication (and so the principal) must use "OrgA".
   @Test
   void mixedCaseOrg_providerReturnsStoredUser_authenticatesWithStoredOrgId() throws Exception {
      when(provider.getUser(any())).thenReturn(new User(new IdentityID("alice", "OrgA")));
      assertAuthenticatedAs(new IdentityID("alice", "orga"), "OrgA");
   }

   @Test
   void exactOrg_isUnchanged() throws Exception {
      when(provider.getUser(any())).thenReturn(new User(new IdentityID("alice", "OrgA")));
      assertAuthenticatedAs(new IdentityID("alice", "OrgA"), "OrgA");
   }

   // Unknown user: authentication decides, the requested id is passed through.
   @Test
   void unknownUser_isUnchanged() throws Exception {
      when(provider.getUser(any())).thenReturn(null);
      assertAuthenticatedAs(new IdentityID("alice", "orga"), "orga");
   }

   // A provider that returns a user in a different organization must never redirect the log in.
   @Test
   void providerReturnsDifferentOrg_isUnchanged() throws Exception {
      when(provider.getUser(any())).thenReturn(new User(new IdentityID("alice", "OrgB")));
      assertAuthenticatedAs(new IdentityID("alice", "orga"), "orga");
   }

   // Anonymous log ins are not canonicalized (the provider's user is not consulted for them).
   @Test
   void anonymous_isUnchanged() throws Exception {
      when(provider.getUser(any()))
         .thenReturn(new User(new IdentityID(ClientInfo.ANONYMOUS, "OrgA")));
      assertAuthenticatedAs(new IdentityID(ClientInfo.ANONYMOUS, "orga"), "orga");
   }

   private void assertAuthenticatedAs(IdentityID requested, String expectedOrgID)
      throws Exception
   {
      // no stub -> null principal (failed log in); only the identity passed down matters here
      service.authenticate(requested, null, "secret", "host", "127.0.0.1", "server", null,
                           Locale.US, false, true, "http-session", "/api/public/login");

      ArgumentCaptor<ClientInfo> info = ArgumentCaptor.forClass(ClientInfo.class);
      ArgumentCaptor<Object> ticket = ArgumentCaptor.forClass(Object.class);
      verify(securityEngine).authenticate(info.capture(), ticket.capture(), eq(provider));
      assertEquals(requested.name, info.getValue().getUserIdentity().name);
      assertEquals(expectedOrgID, info.getValue().getUserIdentity().orgID);
      assertEquals(expectedOrgID, ((DefaultTicket) ticket.getValue()).getName().orgID);
   }
}
