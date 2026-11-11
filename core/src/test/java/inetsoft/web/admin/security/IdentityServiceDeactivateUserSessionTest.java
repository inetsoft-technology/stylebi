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
package inetsoft.web.admin.security;

import inetsoft.sree.security.*;
import inetsoft.sree.web.SessionLicenseServiceProvider;
import inetsoft.uql.util.Identity;
import inetsoft.web.admin.security.user.EditUserPaneModel;
import inetsoft.test.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #81089: saving a user that was active as inactive ends the user's open sessions, like a
 * delete does. A save that does not change the status from active to inactive keeps them.
 * <p>
 * The service is created without its constructor and only the collaborators setUserInfo touches
 * are injected, as IdentityServiceTest does. A Spring context is needed because the save reaches
 * Spring beans (SUtil, the logged in principals).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class IdentityServiceDeactivateUserSessionTest {
   private static final String ORG = "deactorg";
   private static final IdentityID ALICE = new IdentityID("alice", ORG);
   private static final IdentityID BOB = new IdentityID("bob", ORG);

   private IdentityService service;
   private EditableAuthenticationProvider eprovider;
   private AuthenticationService authenticationService;
   private SRPrincipal aliceSession;
   private SRPrincipal bobSession;

   @BeforeEach
   void setUp() {
      eprovider = mock(EditableAuthenticationProvider.class);
      authenticationService = mock(AuthenticationService.class);
      SecurityProvider securityProvider = mock(SecurityProvider.class);
      when(securityProvider.getAuthorizationProvider()).thenReturn(mock(AuthorizationChain.class));
      SecurityEngine engine = mock(SecurityEngine.class);
      aliceSession = session(ALICE);
      bobSession = session(BOB);
      when(engine.getActivePrincipalList()).thenReturn(List.of(aliceSession, bobSession));

      service = mock(IdentityService.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
      ReflectionTestUtils.setField(service, "securityEngine", engine);
      ReflectionTestUtils.setField(service, "securityProvider", securityProvider);
      ReflectionTestUtils.setField(service, "authenticationService", authenticationService);
      ReflectionTestUtils.setField(service, "sessionLicenseServiceProvider",
                                   mock(SessionLicenseServiceProvider.class));
      ReflectionTestUtils.setField(service, "LOG",
                                   org.slf4j.LoggerFactory.getLogger(IdentityService.class));
   }

   @Test
   void activeToInactiveEndsTheUsersSessionsAfterTheSave() throws Exception {
      saveWithStatus(true, false);

      InOrder order = inOrder(eprovider, authenticationService);
      order.verify(eprovider).setUser(eq(ALICE), any(User.class));
      order.verify(authenticationService).logout(aliceSession, true);
      verify(authenticationService, never()).logout(eq(bobSession), anyBoolean());
   }

   @Test
   void inactiveToInactiveKeepsSessions() throws Exception {
      saveWithStatus(false, false);
      verify(authenticationService, never()).logout(any(), anyBoolean());
   }

   @Test
   void activeToActiveKeepsSessions() throws Exception {
      saveWithStatus(true, true);
      verify(authenticationService, never()).logout(any(), anyBoolean());
   }

   @Test
   void inactiveToActiveKeepsSessions() throws Exception {
      saveWithStatus(false, true);
      verify(authenticationService, never()).logout(any(), anyBoolean());
   }

   @Test
   void omittedStatusKeepsSessions() throws Exception {
      // the model's status defaults to true, which is what an edit that omits it saves for an
      // active user
      FSUser old = existingUser(true);
      when(eprovider.getUser(ALICE)).thenReturn(old);
      EditUserPaneModel model = baseModel().build();
      invokeSetUserInfo(old, model);
      verify(authenticationService, never()).logout(any(), anyBoolean());
   }

   @Test
   void renameAndDeactivateLogsTheSessionOutOnce() throws Exception {
      FSUser old = existingUser(true);
      when(eprovider.getUser(ALICE)).thenReturn(old);
      EditUserPaneModel model = baseModel().name("alice2").status(false).build();

      try {
         invokeSetUserInfo(old, model);
      }
      catch(Exception ignore) {
         // the rename needs more collaborators than are mocked here; the old identity's
         // sessions are logged out before it, whatever happens after
      }

      verify(authenticationService, times(1)).logout(aliceSession, true);
   }

   @Test
   void logoutFailureDoesNotAbortTheSave() throws Exception {
      doThrow(new RuntimeException("boom")).when(authenticationService)
         .logout(any(), anyBoolean());

      saveWithStatus(true, false);

      verify(eprovider).setUser(eq(ALICE), any(User.class));
   }

   private static SRPrincipal session(IdentityID id) {
      SRPrincipal principal = mock(SRPrincipal.class);
      when(principal.getIdentityID()).thenReturn(id);
      return principal;
   }

   private void saveWithStatus(boolean oldActive, boolean newActive) throws Exception {
      FSUser old = existingUser(oldActive);
      when(eprovider.getUser(ALICE)).thenReturn(old);
      invokeSetUserInfo(old, baseModel().status(newActive).build());
   }

   private FSUser existingUser(boolean active) {
      FSUser user = new FSUser(ALICE);
      user.setActive(active);
      user.setOrganization(ORG);
      return user;
   }

   private EditUserPaneModel.Builder baseModel() {
      return EditUserPaneModel.builder()
         .name("alice")
         .oldName("alice")
         .organization(ORG)
         .status(true);
   }

   private void invokeSetUserInfo(FSUser old, EditUserPaneModel model) throws Exception {
      Method method = IdentityService.class.getDeclaredMethod(
         "setUserInfo", FSUser.class, EditUserPaneModel.class,
         EditableAuthenticationProvider.class, List.class);
      method.setAccessible(true);

      try {
         method.invoke(service, old, model, eprovider, List.<IdentityID>of());
      }
      catch(java.lang.reflect.InvocationTargetException e) {
         if(e.getCause() instanceof Exception ex) {
            throw ex;
         }

         throw e;
      }
   }
}
