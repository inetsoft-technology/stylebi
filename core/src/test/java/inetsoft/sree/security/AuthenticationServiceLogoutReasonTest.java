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
import inetsoft.sree.web.SessionLicenseServiceProvider;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.XSessionService;
import inetsoft.util.IndexedStorage;
import inetsoft.util.audit.Audit;
import inetsoft.util.audit.SessionRecord;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Tag;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import inetsoft.test.*;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #81146: the session-limit eviction writes the audited LOGOFF row with its own reason, and
 * publishes a logout event that invalidates the session and carries the fixed reason code the
 * browser is told. The plain logout forms carry no reason.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class AuthenticationServiceLogoutReasonTest {
   private ApplicationEventPublisher publisher;
   private AuthenticationService service;
   private Audit audit;
   private MockedStatic<Audit> auditStatic;
   private SRPrincipal principal;

   @BeforeEach
   void setUp() {
      publisher = mock(ApplicationEventPublisher.class);
      service = new AuthenticationService(mock(SecurityEngine.class), mock(MVManager.class),
         mock(DataSourceRegistry.class), mock(XSessionService.class), mock(LocaleService.class),
         mock(SessionLicenseServiceProvider.class), publisher, mock(IndexedStorage.class));
      audit = mock(Audit.class);
      auditStatic = mockStatic(Audit.class);
      auditStatic.when(Audit::getInstance).thenReturn(audit);
      IdentityID id = new IdentityID("alice", "host-org");
      principal = new SRPrincipal(new ClientInfo(id, "10.0.0.1", "sid"), new IdentityID[0],
                                  new String[0], "host-org", 1L);
   }

   @AfterEach
   void tearDown() {
      auditStatic.close();
   }

   @Test
   void limitEvictionIsAuditedAndPublishedWithTheLimitReason() {
      service.logout(principal, "10.0.0.1", SessionRecord.LOGOFF_SESSION_LIMIT, true,
                     SessionLoggedOutEvent.REASON_LIMIT);

      ArgumentCaptor<SessionRecord> record = ArgumentCaptor.forClass(SessionRecord.class);
      verify(audit).auditSession(record.capture(), eq(principal));
      assertEquals(SessionRecord.OP_TYPE_LOGOFF, record.getValue().getOpType());
      assertEquals("Session Limit Exceeded", record.getValue().getLogoffReason());

      SessionLoggedOutEvent event = published();
      assertTrue(event.isInvalidateSession());
      assertEquals("limit", event.getEndReason());
   }

   @Test
   void auditedLogoutWithoutAReasonCodeKeepsTheGenericEnd() {
      service.logout(principal, "10.0.0.1", "", true);
      assertNull(published().getEndReason());
   }

   @Test
   void plainLogoutCarriesNoReason() {
      service.logout(principal);
      SessionLoggedOutEvent event = published();
      assertFalse(event.isInvalidateSession());
      assertNull(event.getEndReason());
   }

   private SessionLoggedOutEvent published() {
      ArgumentCaptor<org.springframework.context.ApplicationEvent> captor =
         ArgumentCaptor.forClass(org.springframework.context.ApplicationEvent.class);
      verify(publisher).publishEvent(captor.capture());
      return (SessionLoggedOutEvent) captor.getValue();
   }
}
