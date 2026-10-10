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
package inetsoft.web.viewsheet.service;

import inetsoft.util.Tool;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.command.ViewsheetCommand;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78254: {@code CoreLifecycleService.refreshViewsheet()} is reached via
 * {@code VSRefreshController}'s always-async {@code @ClusterProxy} call
 * ({@code VSRefreshServiceProxy.refreshViewsheetAsync}). A script/calc-field error re-raised
 * during that method's table-query re-execution (via {@code CoreTool.addUserWarning}, matching
 * {@code AssetQuery.java:1082}) used to be lost: the only harvest point was the generic
 * {@code ServiceProxyContext.apply()}, whose {@code if(!async)} guard (Bug #77135) discards it for
 * this always-async entry point before any caller thread could observe it.
 *
 * <p>The fix (see {@code CoreLifecycleService.forwardReraisedUserMessage}, called at the end of
 * {@code refreshViewsheet()} right after the table-query re-execution) reads
 * {@link Tool#getUserMessage()} and forwards it directly via the session-keyed, thread-independent
 * {@link CommandDispatcher#sendCommand}, bypassing the {@code ServiceProxyContext}/{@code Tool}
 * thread-local relay entirely -- mirroring the pre-existing, working pattern in
 * {@code CoreLifecycleService.execute()}.
 *
 * <p>This test drives {@code forwardReraisedUserMessage} directly (not the full ~500-line
 * {@code refreshViewsheet()}, which has no prior unit-test coverage and would need a large,
 * fragile mock graph -- a real {@code Viewsheet}/{@code ViewsheetSandbox}/{@code RuntimeViewsheet}
 * -- disproportionate to this minimal, self-contained fix) using the real {@code Tool} thread-local
 * and a mocked {@link CommandDispatcher}, which is sufficient to verify the fix's actual,
 * user-visible effect: a pending user message reaches the dispatcher.
 *
 * <p>Does not reach {@code ConfigurationContext}/any Spring bean (only {@code Tool}'s thread-local
 * and a mocked {@code CommandDispatcher}), so no Spring test context is required here.
 */
@Tag("core")
class CoreLifecycleServiceUserMessageForwardingTest {
   private CoreLifecycleService service;

   @BeforeEach
   void setUp() {
      Tool.clearUserMessage();
      // None of the 12 constructor dependencies are touched by forwardReraisedUserMessage --
      // it only reads Tool's thread-local and calls the dispatcher passed in explicitly.
      service = new CoreLifecycleService(
         null, null, null, null, null, null, null, null, null, null, null, null);
   }

   @AfterEach
   void tearDown() {
      Tool.clearUserMessage();
   }

   @Test
   void forwardsAPendingReraisedUserMessageToTheDispatcher() {
      Tool.addUserWarning("calc field error re-raised on refresh");
      CommandDispatcher dispatcher = mock(CommandDispatcher.class);

      service.forwardReraisedUserMessage(dispatcher);

      verify(dispatcher, times(1)).sendCommand(argThat((ViewsheetCommand cmd) ->
         cmd instanceof MessageCommand &&
         "calc field error re-raised on refresh".equals(((MessageCommand) cmd).getMessage())));
      // The message was consumed (not left for some later, unrelated harvest to re-discover).
      assertNull(Tool.getUserMessage());
   }

   @Test
   void sendsNothingWhenNoMessageIsPending() {
      CommandDispatcher dispatcher = mock(CommandDispatcher.class);

      service.forwardReraisedUserMessage(dispatcher);

      verify(dispatcher, never()).sendCommand(any());
   }
}
