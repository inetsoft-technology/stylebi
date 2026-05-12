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
package inetsoft.web.admin.schedule;

/*
 * Test strategy
 *
 * SchedulerConfigurationController has real in-controller logic in one method:
 *   messageReceived — routes RestartSchedulerMessage to configService.setStatus("restart");
 *                     ignores all other message types
 *
 * The REST endpoints (getConfiguration, setConfiguration, getStatus, setStatus, checkMail)
 * are pure-delegation methods tested as such.
 *
 * @PostConstruct/@PreDestroy (cluster listener registration) are not invoked in pure
 * unit tests (no Spring context), so they are not tested here.
 *
 * Coverage scope:
 *   [messageReceived: restart message]   RestartSchedulerMessage → setStatus("restart") called
 *   [messageReceived: other message]     unknown message type → setStatus never called
 *   [getConfiguration: delegation]       delegates to configService.getConfiguration(principal)
 *   [getStatus: delegation]              delegates to configService.getStatus()
 */

import inetsoft.sree.internal.cluster.*;
import inetsoft.sree.schedule.RestartSchedulerMessage;
import inetsoft.web.admin.schedule.model.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("core")
@ExtendWith(MockitoExtension.class)
class SchedulerConfigurationControllerTest {

   @Mock private SchedulerConfigurationService configService;
   @Mock private Cluster cluster;
   @Mock private ScheduleConfigurationModel configModel;
   @Mock private ScheduleStatusModel statusModel;
   @Mock private Principal principal;

   private SchedulerConfigurationController controller;

   @BeforeEach
   void setUp() {
      controller = new SchedulerConfigurationController(configService, cluster);
   }

   // -------------------------------------------------------------------------
   // messageReceived()
   // -------------------------------------------------------------------------

   // [restart message] RestartSchedulerMessage → configService.setStatus("restart") called
   @Test
   void messageReceived_restartMessage_callsSetStatus() throws Exception {
      MessageEvent event = new MessageEvent("source", "sender", false, new RestartSchedulerMessage());

      controller.messageReceived(event);

      verify(configService).setStatus("restart");
   }

   // [other message] unknown message type → setStatus never called
   @Test
   void messageReceived_unknownMessage_doesNothing() throws Exception {
      MessageEvent event = new MessageEvent("source", "sender", false, "some-other-message");

      controller.messageReceived(event);

      verify(configService, never()).setStatus(anyString());
   }

   // -------------------------------------------------------------------------
   // getConfiguration() / getStatus()
   // -------------------------------------------------------------------------

   // [delegation] delegates to configService.getConfiguration(principal)
   @Test
   void getConfiguration_delegatesToService() throws Exception {
      when(configService.getConfiguration(principal)).thenReturn(configModel);

      ScheduleConfigurationModel result = controller.getConfiguration(principal);

      assertSame(configModel, result);
      verify(configService).getConfiguration(principal);
   }

   // [delegation] delegates to configService.getStatus()
   @Test
   void getStatus_delegatesToService() {
      when(configService.getStatus()).thenReturn(statusModel);

      ScheduleStatusModel result = controller.getStatus();

      assertSame(statusModel, result);
      verify(configService).getStatus();
   }
}
