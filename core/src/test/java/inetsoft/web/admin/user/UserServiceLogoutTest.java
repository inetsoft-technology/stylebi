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
package inetsoft.web.admin.user;

import inetsoft.sree.RepletRepository;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.*;
import inetsoft.web.admin.monitoring.MonitoringDataService;
import inetsoft.web.admin.monitoring.StatusMetricsType;
import inetsoft.web.admin.viewsheet.ViewsheetService;
import inetsoft.web.cluster.ServerClusterClient;
import inetsoft.web.session.IgniteSessionRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.security.Principal;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77052: POST /api/em/monitor/user/logout must not invalidate a session the caller
 * cannot see in the session list (another organization's session, or one whose user the
 * caller cannot ADMIN).
 */
@Tag("core")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserServiceLogoutTest {
   @Mock private ViewsheetService viewsheetService;
   @Mock private ServerClusterClient client;
   @Mock private SecurityEngine securityEngine;
   @Mock private SecurityProvider securityProvider;
   @Mock private MonitoringDataService monitoringDataService;
   @Mock private IgniteSessionRepository sessionRepository;
   @Mock private Cluster cluster;
   @Mock private OrganizationManager organizationManager;
   @Mock private Principal caller;

   private MockedStatic<OrganizationManager> orgManagerStatic;
   private UserStatusController controller;

   private static final String CALLER_ORG = "org-a";
   private static final IdentityID OWN_USER = new IdentityID("alice", CALLER_ORG);
   private static final IdentityID FOREIGN_USER = new IdentityID("admin", "host-org");
   private static final String OWN_SESSION = "USER1700000000000_1_alice~;~org-a";
   private static final String FOREIGN_SESSION = "USER1700000000000_2_admin~;~host-org";
   private static final String OWN_KEY = "ignite-own";
   private static final String FOREIGN_KEY = "ignite-foreign";

   @BeforeEach
   void setUp() {
      orgManagerStatic = mockStatic(OrganizationManager.class);
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(organizationManager);
      when(organizationManager.getCurrentOrgID(caller)).thenReturn(CALLER_ORG);
      when(caller.getName()).thenReturn("alice~;~org-a");

      UserService userService = spy(new UserService(viewsheetService, client, securityEngine,
         securityProvider, monitoringDataService, sessionRepository, cluster));
      // monitor level is read from SreeEnv, which needs a Spring context
      doReturn(false).when(userService).isLevelQualified(anyString());
      controller = new UserStatusController(userService, monitoringDataService, securityEngine);
      when(securityEngine.getSecurityProvider()).thenReturn(securityProvider);
      when(securityProvider.checkPermission(eq(caller), eq(ResourceType.SECURITY_USER),
         anyString(), eq(ResourceAction.ADMIN))).thenReturn(true);

      SRPrincipal own = sessionPrincipal(OWN_SESSION, OWN_KEY);
      SRPrincipal foreign = sessionPrincipal(FOREIGN_SESSION, FOREIGN_KEY);
      when(sessionRepository.getActiveSessions()).thenReturn(List.of(own, foreign));

      // the monitoring metrics contain sessions from every organization
      when(client.getMetrics(eq(StatusMetricsType.USER_METRICS), any()))
         .thenReturn(UserMetrics.builder()
            .addSessions(sessionModel(OWN_SESSION, OWN_USER),
                         sessionModel(FOREIGN_SESSION, FOREIGN_USER))
            .build());
   }

   @AfterEach
   void tearDown() {
      orgManagerStatic.close();
   }

   // [G1] a session in another organization is not invalidated
   @Test
   void logout_sessionInOtherOrg_isNotInvalidated() {
      controller.logout(new String[] { FOREIGN_SESSION }, "/", caller);

      verify(sessionRepository, never()).invalidateSession(anyString());
   }

   // [G2] a session whose user the caller cannot ADMIN is not invalidated
   @Test
   void logout_sessionWithoutAdminPermission_isNotInvalidated() {
      when(securityProvider.checkPermission(eq(caller), eq(ResourceType.SECURITY_USER),
         eq(OWN_USER.convertToKey()), eq(ResourceAction.ADMIN))).thenReturn(false);

      controller.logout(new String[] { OWN_SESSION }, "/", caller);

      verify(sessionRepository, never()).invalidateSession(anyString());
   }

   // [G3] a session visible to the caller is invalidated
   @Test
   void logout_visibleSession_isInvalidated() {
      controller.logout(new String[] { OWN_SESSION }, "/", caller);

      verify(sessionRepository).invalidateSession(OWN_KEY);
   }

   // [G4] a mixed request only invalidates the permitted sessions
   @Test
   void logout_mixedRequest_onlyInvalidatesVisibleSessions() {
      controller.logout(new String[] { FOREIGN_SESSION, OWN_SESSION }, "/", caller);

      verify(sessionRepository).invalidateSession(OWN_KEY);
      verify(sessionRepository, never()).invalidateSession(FOREIGN_KEY);
   }

   private SRPrincipal sessionPrincipal(String sessionId, String key) {
      SRPrincipal principal = mock(SRPrincipal.class);
      String name = sessionId.substring(sessionId.lastIndexOf('_') + 1);
      when(principal.getSessionID()).thenReturn(sessionId);
      when(principal.getName()).thenReturn(name);

      IgniteSessionRepository.IgniteSession session =
         mock(IgniteSessionRepository.IgniteSession.class);
      when(session.getAttribute(RepletRepository.PRINCIPAL_COOKIE)).thenReturn(principal);
      when(sessionRepository.findByIndexNameAndIndexValue(anyString(), eq(name)))
         .thenReturn(Map.of(key, session));
      return principal;
   }

   private static SessionModel sessionModel(String sessionId, IdentityID user) {
      return SessionModel.builder()
         .address("127.0.0.1")
         .id(sessionId)
         .user(user)
         .dateCreated(0L)
         .dateAccessed(0L)
         .activeViewsheets(0)
         .organization(user.getOrgID())
         .build();
   }
}
