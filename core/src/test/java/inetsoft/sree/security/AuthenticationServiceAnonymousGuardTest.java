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

import inetsoft.mv.MVManager;
import inetsoft.sree.ClientInfo;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.web.SessionLicenseServiceProvider;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.XSessionService;
import inetsoft.util.IndexedStorage;
import inetsoft.util.audit.Audit;
import inetsoft.web.admin.monitoring.MonitorLevelService;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.springframework.context.ApplicationEventPublisher;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77341: the guards in {@link AuthenticationService} that drop an anonymous principal
 * returned for a non-anonymous log in compared {@link Principal#getName()} (the identity key
 * anonymous~;~orgID) to the bare name "anonymous", so they never fired.
 */
@Tag("core")
class AuthenticationServiceAnonymousGuardTest {
   private static final String ORG = "host-org";

   private SecurityEngine securityEngine;
   private SecurityProvider securityProvider;
   private LocaleService localeService;
   private AuthenticationService service;
   private MockedStatic<SreeEnv> sreeEnvStatic;
   private MockedStatic<SUtil> sutilStatic;
   private MockedStatic<Audit> auditStatic;
   private MockedStatic<MonitorLevelService> monitorStatic;

   @BeforeEach
   void setUp() {
      securityEngine = mock(SecurityEngine.class);
      securityProvider = mock(SecurityProvider.class);
      localeService = mock(LocaleService.class);
      XSessionService sessionService = mock(XSessionService.class);
      when(securityEngine.getSecurityProvider()).thenReturn(securityProvider);
      when(sessionService.createSessionID(any(), any())).thenReturn("session");

      service = new AuthenticationService(securityEngine, mock(MVManager.class),
         mock(DataSourceRegistry.class), sessionService, localeService,
         mock(SessionLicenseServiceProvider.class), mock(ApplicationEventPublisher.class),
         mock(IndexedStorage.class));

      sreeEnvStatic = mockStatic(SreeEnv.class);
      sutilStatic = mockStatic(SUtil.class);
      sutilStatic.when(() -> SUtil.getUserID(any(), any()))
         .thenAnswer(i -> i.getArgument(1) != null ? i.getArgument(1) : i.getArgument(0));
      auditStatic = mockStatic(Audit.class);
      auditStatic.when(Audit::getInstance).thenReturn(mock(Audit.class));
      monitorStatic = mockStatic(MonitorLevelService.class);
      monitorStatic.when(MonitorLevelService::getMonitorLevel).thenReturn(0);
   }

   @AfterEach
   void tearDown() {
      monitorStatic.close();
      auditStatic.close();
      sutilStatic.close();
      sreeEnvStatic.close();
   }

   // G: login.loginAs=on, log in as alice with loginAs=anonymous. SecurityEngine returns the
   // anonymous principal without checking the credential; the guard must reject it.
   @Test
   void loginAsAnonymousForNamedUserIsRejected() throws Exception {
      securityProvider("file");
      sreeEnvStatic.when(() -> SreeEnv.getProperty("login.loginAs")).thenReturn("on");
      engineReturns(xprincipal(XPrincipal.ANONYMOUS));

      Principal result = login(new IdentityID("alice", ORG),
                               new IdentityID(XPrincipal.ANONYMOUS, ORG));

      assertNull(result, "an anonymous principal for a named log in must be rejected");
   }

   // G: the real guest log in (user id anonymous) must still succeed.
   @Test
   void anonymousGuestLoginIsKept() throws Exception {
      securityProvider("file");
      engineReturns(xprincipal(XPrincipal.ANONYMOUS));

      Principal result = login(new IdentityID(XPrincipal.ANONYMOUS, ORG), null);

      assertNotNull(result);
   }

   // G: a named user log in is unaffected.
   @Test
   void namedUserLoginIsKept() throws Exception {
      securityProvider("file");
      engineReturns(xprincipal("alice"));

      Principal result = login(new IdentityID("alice", ORG), null);

      assertNotNull(result);
   }

   // H: security on, shell local connect as anonymous is rejected.
   @Test
   void localConnectAsAnonymousIsRejectedWithSecurityOn() throws Exception {
      securityProvider("file");
      engineReturns(principal(XPrincipal.ANONYMOUS));

      assertNull(service.authenticate(
         new IdentityID(XPrincipal.ANONYMOUS, ORG), "any", null, "127.0.0.1", false));
   }

   // H: security off, security.provider is the empty default from defaults.properties; a
   // local connect as anonymous must keep working.
   @Test
   void localConnectAsAnonymousIsKeptWithSecurityOff() throws Exception {
      securityProvider("");
      engineReturns(principal(XPrincipal.ANONYMOUS));

      assertNotNull(service.authenticate(
         new IdentityID(XPrincipal.ANONYMOUS, ORG), "", null, "127.0.0.1", false));
   }

   // H: security on, a named user local connect is unaffected.
   @Test
   void localConnectAsNamedUserIsKept() throws Exception {
      securityProvider("file");
      engineReturns(principal("alice"));

      assertNotNull(service.authenticate(
         new IdentityID("alice", ORG), "password", null, "127.0.0.1", false));
   }

   private Principal login(IdentityID userId, IdentityID loginAs) throws Exception {
      return service.authenticate(userId, loginAs, "password", "localhost", "127.0.0.1",
                                  "server", null, null, false, true, "sid", "/");
   }

   private void securityProvider(String value) {
      sreeEnvStatic.when(() -> SreeEnv.getProperty("security.provider")).thenReturn(value);
   }

   private void engineReturns(Principal principal) {
      when(securityEngine.authenticate(any(ClientInfo.class), any(), eq(securityProvider)))
         .thenReturn(principal);
   }

   private static Principal principal(String user) {
      String key = new IdentityID(user, ORG).convertToKey();
      return () -> key;
   }

   private static XPrincipal xprincipal(String user) {
      XPrincipal principal = mock(XPrincipal.class);
      when(principal.getName()).thenReturn(new IdentityID(user, ORG).convertToKey());
      when(principal.getGroups()).thenReturn(new String[0]);
      when(principal.getRoles()).thenReturn(new IdentityID[0]);
      return principal;
   }
}
