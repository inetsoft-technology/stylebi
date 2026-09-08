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

/*
 * Test plan 2026-09-03, scenario 10: SSOFilterPublisher.changeSSOFilterType() publishes the
 * local Spring event UNCONDITIONALLY, then separately tries to broadcast to the rest of the
 * cluster -- a cluster send failure is caught and only LOG.warn'd, never propagated, and there is
 * no retry/compensation. This exact link has a real historical bug (community commit 3bf25021e,
 * "correct handle ChangeSSOFilterMessage to ensure the sso type can be changed successfully"),
 * so this coverage gap is not theoretical.
 */

import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.internal.cluster.MessageEvent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Tag("core")
class SSOFilterPublisherTest {
   @Mock
   private Cluster cluster;
   @Mock
   private ApplicationEventPublisher publisher;

   private SSOFilterPublisher filterPublisher;

   @BeforeEach
   void setUp() {
      filterPublisher = new SSOFilterPublisher(cluster);
      filterPublisher.setApplicationEventPublisher(publisher);
   }

   @Test
   void changeSSOFilterType_clusterSendFails_localEventStillPublished_exceptionSwallowed()
      throws Exception
   {
      // Bug #72373 verified against commit 3bf25021e's own message ("fix Bug #72373, correct
      // handle ChangeSSOFilterMessage to ensure the sso type can be changed successfully.") --
      // not an assumed/guessed ticket number.
      doThrow(new RuntimeException("cluster unreachable")).when(cluster).sendMessage(any());

      assertDoesNotThrow(() -> filterPublisher.changeSSOFilterType(SSOType.OPENID),
         "a cluster broadcast failure must not propagate out of changeSSOFilterType() -- other " +
         "nodes silently miss the update (this exact link broke production once, community " +
         "commit 3bf25021e / Bug #72373), but the local node's own switch must still complete");

      ArgumentCaptor<ChangeSSOFilterEvent> captor = ArgumentCaptor.forClass(ChangeSSOFilterEvent.class);
      verify(publisher).publishEvent(captor.capture());
      assertEquals(SSOType.OPENID, captor.getValue().getFilterType(),
         "the local Spring event must still be published even though the cluster broadcast failed");
   }

   @Test
   void changeSSOFilterType_clusterSendSucceeds_broadcastsTheSameTypeThatWasPublishedLocally()
      throws Exception
   {
      filterPublisher.changeSSOFilterType(SSOType.SAML);

      ArgumentCaptor<ChangeSSOFilterEvent> localEvent =
         ArgumentCaptor.forClass(ChangeSSOFilterEvent.class);
      verify(publisher).publishEvent(localEvent.capture());
      assertEquals(SSOType.SAML, localEvent.getValue().getFilterType());

      ArgumentCaptor<ChangeSSOFilterMessage> clusterMessage =
         ArgumentCaptor.forClass(ChangeSSOFilterMessage.class);
      verify(cluster).sendMessage(clusterMessage.capture());
      assertEquals(SSOType.SAML, clusterMessage.getValue().getType());
   }

   @Test
   void messageReceived_remoteMessage_publishesLocalEventWithTheSameType() {
      MessageEvent event = new MessageEvent(
         this, "other-node", false, new ChangeSSOFilterMessage(SSOType.CUSTOM));

      filterPublisher.messageReceived(event);

      ArgumentCaptor<ChangeSSOFilterEvent> captor = ArgumentCaptor.forClass(ChangeSSOFilterEvent.class);
      verify(publisher).publishEvent(captor.capture());
      assertEquals(SSOType.CUSTOM, captor.getValue().getFilterType());
   }

   @Test
   void messageReceived_localMessage_isIgnored_doesNotRepublish() {
      // positive control: a message this same node just sent (event.isLocal() == true) must not
      // be echoed back into another local publishEvent() call.
      MessageEvent event = new MessageEvent(
         this, "this-node", true, new ChangeSSOFilterMessage(SSOType.CUSTOM));

      filterPublisher.messageReceived(event);

      verify(publisher, never()).publishEvent(any());
   }

   @Test
   void messageReceived_nonSsoMessage_isIgnored() {
      // positive control: messageReceived() is a shared cluster MessageListener callback --
      // messages of other types must be ignored, not misinterpreted.
      MessageEvent event = new MessageEvent(this, "other-node", false, "not an SSO message");

      filterPublisher.messageReceived(event);

      verify(publisher, never()).publishEvent(any());
   }
}
