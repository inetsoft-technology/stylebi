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
import inetsoft.util.UserMessage;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.command.ViewsheetCommand;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Covers two independent {@code CoreLifecycleService} user-message-forwarding fixes that each
 * added a test class under this same name; combined here rather than carried as two separate
 * files so a future reader finds all of this class's thread-boundary/message-forwarding coverage
 * in one place.
 *
 * <h2>Bug #77188</h2>
 * A viewer's own script/expression-failure {@link UserMessage} (e.g. {@code
 * CoreTool.addUserWarning} from {@code AssetQuery}'s {@code ExpressionFailedException} handling)
 * must still reach the browser when the viewsheet open is dispatched via {@code
 * CoreLifecycleService.HandleOpenSheetTask} to a <em>different</em> node than the one that
 * received the STOMP {@code /events/open} request ({@code viewsheetService.isLocal(id)} is
 * {@code false}).
 *
 * <p>Confirmed live-cluster root cause (see
 * {@code docs/teams/2026-10-10-bugs-77188-rework/bug-77188/04-live-repro.md}, n=28 real opens,
 * 100% correlation): {@code Tool}'s {@code UserMessage} thread-local does not cross the
 * {@code Ignite.affinityCall()} RPC boundary. The message is recorded on the remote node's own
 * thread inside {@code HandleOpenSheetTask.call()}; {@code EventAspect.sendUserMessage()} (the
 * {@code @Around} advice on every {@code @MessageMapping} handler) later reads
 * {@code Tool.getUserMessages(type)} on the <em>calling</em> node's thread and finds nothing, so
 * it sends zero {@code MessageCommand}s -- even though the table-structure
 * {@code LoadTableDataCommand} (built from {@code call()}'s actual, correctly-marshalled return
 * value) always arrives.
 *
 * <p>The fix: {@code HandleOpenSheetTask.harvestUserMessages()} captures
 * {@code Tool.getUserMessages(type)} for every {@link MessageCommand.Type} at the end of
 * {@code call()} (on whichever thread actually ran it) and attaches the result to the
 * {@code ProcessSheetResult} that crosses the RPC boundary back to the caller (that result type
 * was already {@code Serializable} and already carried across the same boundary for the table
 * data itself). {@code CoreLifecycleService.republishUserMessages()} then re-adds them via
 * {@code Tool.addUserMessage(UserMessage)} on the calling thread, inside
 * {@code handleOpenedSheet()}'s {@code !isLocal} branch -- before the enclosing
 * {@code @MessageMapping} handler method returns and {@code EventAspect}'s advice runs.
 *
 * <p>{@link #userMessageDoesNotCrossThreadsWithoutACarrier()},
 * {@link #remoteHandleOpenSheetTaskMessageIsForwardedToCallingThread()} and
 * {@link #republishUserMessagesIsNoOpForNullOrEmpty()} exercise the real harvest
 * ({@code CoreLifecycleService.HandleOpenSheetTask.harvestUserMessages()}) and republish
 * ({@code CoreLifecycleService.republishUserMessages(List)}) methods directly, simulating the
 * cross-node hop with two distinct threads (an {@link ExecutorService}-backed "remote node"
 * thread for the harvest, and this test's own thread -- standing in for the STOMP-receiving node
 * -- for the republish and the final {@code EventAspect}-style read), rather than driving the
 * full {@code doHandleOpenedSheet()}/viewsheet-sandbox machinery, which needs a live viewsheet and
 * is out of scope for a thread-boundary unit test. See {@code UserMessageThreadBoundaryTest} (bug
 * #77135) for the sibling tests covering the complementary "must NOT leak across threads"
 * contracts this fix must not regress.
 *
 * <h2>Bug #78254</h2>
 * {@code CoreLifecycleService.refreshViewsheet()} is reached via {@code VSRefreshController}'s
 * always-async {@code @ClusterProxy} call ({@code VSRefreshServiceProxy.refreshViewsheetAsync}).
 * A script/calc-field error re-raised during that method's table-query re-execution (via
 * {@code CoreTool.addUserWarning}, matching {@code AssetQuery.java:1082}) used to be lost: the
 * only harvest point was the generic {@code ServiceProxyContext.apply()}, whose
 * {@code if(!async)} guard (Bug #77135) discards it for this always-async entry point before any
 * caller thread could observe it.
 *
 * <p>The fix (see {@code CoreLifecycleService.forwardReraisedUserMessage}, called at the end of
 * {@code refreshViewsheet()} right after the table-query re-execution) reads
 * {@link Tool#getUserMessage()} and forwards it directly via the session-keyed,
 * thread-independent {@link CommandDispatcher#sendCommand}, bypassing the
 * {@code ServiceProxyContext}/{@code Tool} thread-local relay entirely -- mirroring the
 * pre-existing, working pattern in {@code CoreLifecycleService.execute()}.
 *
 * <p>{@link #forwardsAPendingReraisedUserMessageToTheDispatcher()} and
 * {@link #sendsNothingWhenNoMessageIsPending()} drive {@code forwardReraisedUserMessage} directly
 * (not the full ~500-line {@code refreshViewsheet()}, which has no prior unit-test coverage and
 * would need a large, fragile mock graph -- a real {@code Viewsheet}/{@code ViewsheetSandbox}/
 * {@code RuntimeViewsheet} -- disproportionate to this minimal, self-contained fix) using the real
 * {@code Tool} thread-local and a mocked {@link CommandDispatcher}, which is sufficient to verify
 * the fix's actual, user-visible effect: a pending user message reaches the dispatcher.
 *
 * <p>Neither bug's tests reach {@code ConfigurationContext}/any Spring bean (only {@code Tool}'s
 * thread-local and a mocked {@code CommandDispatcher}/{@code ExecutorService}), so no Spring test
 * context is required for this class.
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

   // --- Bug #77188: HandleOpenSheetTask.harvestUserMessages() / republishUserMessages() ---

   // Demonstrates the bug (pre-fix behavior) in isolation: a UserMessage recorded on one thread
   // is simply not visible to another thread via Tool's thread-local, with no carrier at all.
   // This is why EventAspect.sendUserMessage() on the calling thread saw nothing for the
   // isLocal=false case before this fix.
   @Test
   void userMessageDoesNotCrossThreadsWithoutACarrier() throws Exception {
      ExecutorService remoteNode = Executors.newSingleThreadExecutor();

      try {
         remoteNode.submit(() -> {
            Tool.addUserWarning("rg77188 boom: expression failed");
            return null;
         }).get(5, TimeUnit.SECONDS);

         // The calling thread's own thread-local was never touched.
         assertTrue(Tool.getUserMessages(MessageCommand.Type.WARNING).isEmpty(),
                    "a message recorded on another thread leaked without any carrier");
      }
      finally {
         remoteNode.shutdownNow();
      }
   }

   // The fix: HandleOpenSheetTask.harvestUserMessages(), run on the "remote node" thread,
   // packages up the message so it can ride back across the simulated RPC boundary, and
   // CoreLifecycleService.republishUserMessages(), run on the "calling" thread, makes it visible
   // there again -- before a final, EventAspect-style read on that same calling thread.
   @Test
   void remoteHandleOpenSheetTaskMessageIsForwardedToCallingThread() throws Exception {
      ExecutorService remoteNode = Executors.newSingleThreadExecutor();

      try {
         // Simulates HandleOpenSheetTask.call() running on a different node than the one that
         // received the STOMP open request: doHandleOpenedSheet()'s initial table load raises a
         // script-error UserMessage (bug #77188's ExpressionFailedException ->
         // CoreTool.addUserWarning path) on this thread, then the fix's own harvest runs, still
         // on this same remote thread, before the result crosses back over the RPC.
         List<UserMessage> harvested = remoteNode.submit(() -> {
            Tool.addUserWarning("rg77188 boom: expression failed");
            return CoreLifecycleService.HandleOpenSheetTask.harvestUserMessages();
         }).get(5, TimeUnit.SECONDS);

         Thread remoteThread = remoteNode.submit(Thread::currentThread).get(5, TimeUnit.SECONDS);
         assertNotSame(Thread.currentThread(), remoteThread,
                       "the harvest must be demonstrated across two distinct threads");

         assertEquals(1, harvested.size());
         assertEquals("rg77188 boom: expression failed", harvested.get(0).getMessage());

         // Nothing should be visible on the calling thread yet -- the carry is explicit, not
         // automatic.
         assertTrue(Tool.getUserMessages(MessageCommand.Type.WARNING).isEmpty(),
                    "the harvested message must not already be visible before republishing");

         // Back on the calling thread: handleOpenedSheet()'s own fix, after affinityCall()
         // returns, republishes the carried messages before the @MessageMapping handler itself
         // returns.
         CoreLifecycleService.republishUserMessages(harvested);

         // Simulates EventAspect.sendUserMessage()'s own harvest-and-send, which runs on this
         // same calling thread immediately after the handler method returns.
         List<String> sentToBrowser = Tool.getUserMessages(MessageCommand.Type.WARNING).stream()
            .map(UserMessage::getMessage)
            .toList();

         assertEquals(List.of("rg77188 boom: expression failed"), sentToBrowser,
                      "the remote node's script-error message never reached the calling " +
                         "thread's dispatch");
      }
      finally {
         remoteNode.shutdownNow();
      }
   }

   // republishUserMessages() must be a safe no-op when there is nothing to carry (the isLocal
   // path never populates ProcessSheetResult.userMessages, and a null/empty harvest on the
   // remote side must not, say, NPE or add a spurious empty message).
   @Test
   void republishUserMessagesIsNoOpForNullOrEmpty() {
      CoreLifecycleService.republishUserMessages(null);
      assertTrue(Tool.getUserMessages(MessageCommand.Type.WARNING).isEmpty());

      CoreLifecycleService.republishUserMessages(List.of());
      assertTrue(Tool.getUserMessages(MessageCommand.Type.WARNING).isEmpty());
   }

   // --- Bug #78254: forwardReraisedUserMessage() ---

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
