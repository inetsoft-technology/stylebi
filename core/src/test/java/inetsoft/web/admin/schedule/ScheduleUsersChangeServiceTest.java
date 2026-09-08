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
package inetsoft.web.admin.schedule;

/*
 * Test plan 2026-09-03, scenario 20: SSOTypeChangedMessage is NOT dead code -- this class is a
 * real consumer, found by a repo-wide grep the original investigation missed (it only searched a
 * narrower set of directories). ScheduleUsersChangeService.messageReceived() reacts to it by
 * pushing a debounced STOMP notification to every currently-subscribed schedule-view client, but
 * ONLY when SSO was toggled fully on or fully off (old/new type transitions through NONE) -- a
 * protocol switch that stays "on" (e.g. SAML -> OpenID) does not notify.
 *
 * This also surfaces a new, independent finding, filed as Bug #76438: SSOSettingsService.
 * updateSSOSettings() constructs the message as `new SSOTypeChangedMessage(activeFilterType,
 * ssoType)` -- passing (OLD, NEW) -- but the constructor signature is
 * `SSOTypeChangedMessage(SSOType newType, SSOType oldType)`, i.e. (NEW, OLD). The two are swapped
 * at the only call site. This is currently harmless for the one consumer here, because its guard
 * condition (`newType != oldType && (oldType == NONE || newType == NONE)`) is symmetric under
 * swapping the two arguments -- but getNewType()/getOldType() return the wrong values relative to
 * their names, which would bite any future consumer that cares about direction. Left unfixed
 * pending a decision -- this test file does not touch SSOSettingsService.
 *
 * IMPORTANT for whoever fixes #76438: none of the four tests below need to change either way.
 * They never call SSOSettingsService -- ssoTypeChanged() below constructs SSOTypeChangedMessage
 * directly, deliberately reproducing whatever {oldType, newType} pair the current (buggy) call
 * site would have produced. Because messageReceived()'s guard is symmetric in old/new (see above),
 * the resulting notify/don't-notify outcome is identical whether the message's two fields are
 * populated in the "buggy" order or the corrected one. Fixing #76438 only matters to a consumer
 * that reads getNewType()/getOldType() asymmetrically -- this one does not.
 *
 * The debouncer is a real (not mocked) DefaultDebouncer with a fixed 1-second interval, hardcoded
 * as a private field with no way to inject a test double -- these tests wait past that real
 * interval rather than mocking it out.
 */

import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.internal.cluster.MessageEvent;
import inetsoft.web.admin.security.SSOType;
import inetsoft.web.admin.security.SSOTypeChangedMessage;
import inetsoft.web.service.BaseSubscribeChangeHandler;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.security.Principal;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Tag("core")
class ScheduleUsersChangeServiceTest {
   @Mock
   private SimpMessagingTemplate messageTemplate;
   @Mock
   private Cluster cluster;
   @Mock
   private Principal principal;

   private ScheduleUsersChangeService service;

   // the debouncer's own interval -- verify() with Mockito's timeout() polls up to this long
   // instead of a flat Thread.sleep(), so a passing test does not have to wait the full interval.
   private static final long DEBOUNCE_MS = 1000;
   private static final long VERIFY_TIMEOUT_MS = DEBOUNCE_MS + 1500;

   @BeforeEach
   void setUp() {
      service = new ScheduleUsersChangeService(messageTemplate, cluster);
      service.addSubscriber(new BaseSubscribeChangeHandler.BaseSubscriber(
         "session-1", "sub-1", "/lookup/schedule", "/topic/schedule", principal));
   }

   @AfterEach
   void tearDown() {
      service.destroyInstance();
   }

   private static MessageEvent ssoTypeChanged(SSOType oldType, SSOType newType) {
      // matches the real (buggy) call site in SSOSettingsService: constructor args are
      // (newType, oldType) but the caller passes (old, new) -- see class javadoc. Passed through
      // as-is here, since this test targets the CONSUMER's behavior given whatever message it
      // actually receives in production, not a hypothetical corrected one.
      return new MessageEvent(
         "test", "other-node", false, new SSOTypeChangedMessage(oldType, newType));
   }

   @Test
   void ssoTurnedOn_noneToSaml_notifiesSubscribers() {
      service.messageReceived(ssoTypeChanged(SSOType.NONE, SSOType.SAML));

      verify(messageTemplate, timeout(VERIFY_TIMEOUT_MS))
         .convertAndSendToUser(eq("session-1"), eq("/lookup/schedule"), any(), anyMap());
   }

   @Test
   void ssoTurnedOff_samlToNone_notifiesSubscribers() {
      service.messageReceived(ssoTypeChanged(SSOType.SAML, SSOType.NONE));

      verify(messageTemplate, timeout(VERIFY_TIMEOUT_MS))
         .convertAndSendToUser(eq("session-1"), eq("/lookup/schedule"), any(), anyMap());
   }

   @Test
   void protocolSwitchWhileStayingOn_samlToOpenId_doesNotNotify() throws Exception {
      // neither side of the transition is NONE -- this must NOT trigger a subscriber push,
      // unlike the two "turned on"/"turned off" cases above.
      service.messageReceived(ssoTypeChanged(SSOType.SAML, SSOType.OPENID));

      // wait past the real debounce interval to distinguish "never notified" from "not notified
      // yet" -- there is no pending-task hook to poll instead.
      Thread.sleep(DEBOUNCE_MS + 200);
      verify(messageTemplate, never()).convertAndSendToUser(any(), any(), any(), anyMap());
   }

   @Test
   void noActualChange_noneToNone_doesNotNotify() throws Exception {
      service.messageReceived(ssoTypeChanged(SSOType.NONE, SSOType.NONE));

      Thread.sleep(DEBOUNCE_MS + 200);
      verify(messageTemplate, never()).convertAndSendToUser(any(), any(), any(), anyMap());
   }
}
