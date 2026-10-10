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
package inetsoft.web.viewsheet.controller;

import inetsoft.report.composition.WorksheetService;
import inetsoft.sree.internal.cluster.AffinityCallable;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.IdentityID;
import inetsoft.uql.XPrincipal;
import inetsoft.util.ConfigurationContext;
import inetsoft.util.GroupedThread;
import inetsoft.util.Tool;
import inetsoft.util.UserMessage;
import inetsoft.web.messaging.MessageScopeInterceptor;
import inetsoft.web.viewsheet.event.VSRefreshEvent;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ExecutorSubscribableChannel;
import org.springframework.messaging.support.MessageBuilder;

import java.security.Principal;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78254 -- documents a general {@code ServiceProxyContext} mechanism, NOT a claim that the
 * bug remains unfixed.
 *
 * <p>{@code VSRefreshController.refreshViewsheet()} (core/.../VSRefreshController.java:61) always
 * calls the generated <b>async</b> {@code @ClusterProxy} flavor
 * ({@code VSRefreshServiceProxy.refreshViewsheetAsync}), never the sync one, and never awaits the
 * returned {@code Future}. The generated async callable always builds its
 * {@code ServiceProxyContext} with {@code async=true} (confirmed in the actual generated source,
 * {@code core/target/generated-sources/annotations/.../VSRefreshServiceProxy.java}, produced by
 * the module's {@code @ClusterProxy} annotation processor from
 * {@code build-tools/cluster-proxy-annotations/.../Proxy.java.mustache}). Its {@code call()}'s
 * {@code finally} block does {@code postprocess(); if(async) { apply(); }}, and
 * {@code ServiceProxyContext.apply()}'s {@code if(!async)} guard -- added for Bug #77135 to stop
 * one user's messages leaking onto the next task on a shared pool thread -- means harvested
 * messages are re-added to {@code Tool} only for the sync flavor. This generic relay still
 * discards an async call's harvested messages today, by design (protecting #77135's fix), and the
 * test below asserts that discard as the current, intended, permanent behavior of this relay.
 *
 * <p><b>This is not how bug #78254's user-visible symptom was actually fixed.</b> Rather than
 * touch this shared relay (used by every async {@code @ClusterProxy} method in the codebase, with
 * real risk of reintroducing #77135's cross-task leak), the fix forwards the message from
 * <i>inside</i> {@code CoreLifecycleService.refreshViewsheet()} itself, before this relay ever
 * gets a chance to harvest and discard it -- see
 * {@code CoreLifecycleService.forwardReraisedUserMessage} and
 * {@code inetsoft.web.viewsheet.service.CoreLifecycleServiceUserMessageForwardingTest}, which
 * verifies that actual fix and passes. So: the test below passing (asserting {@code null}) does
 * not indicate the bug is still present -- it documents a different, by-design limitation of the
 * generic relay that #78254's fix deliberately bypasses rather than touches; the other test is
 * what demonstrates the actual fix.
 *
 * <p>This drives the real generated {@link VSRefreshServiceProxy} (built by the module's
 * annotation processor) with the real {@code inetsoft.web.ServiceProxyContext}, over a real
 * STOMP-style calling thread (the same {@link MessageScopeInterceptor} harness as
 * {@code inetsoft.web.messaging.UserMessageThreadBoundaryTest}), mocking only
 * {@link VSRefreshService} (the heavy inner service -- constructing a real
 * {@code CoreLifecycleService}/{@code ViewsheetSandbox} is out of scope for this unit test) and
 * {@link WorksheetService} (the cluster affinity dispatch). It contrasts the always-async
 * controller path (general mechanism, expected red here) against the sync proxy flavor (works,
 * green control) to show the loss is specific to the async flavor, not to proxy dispatch in
 * general.
 */
@Tag("core")
class VSRefreshServiceAsyncUserMessageTest {
   private ExecutorService inboundPool;
   private ExecutorService wsPool;

   @BeforeEach
   void setUp() {
      Tool.clearUserMessage();
      inboundPool = Executors.newSingleThreadExecutor(r -> new GroupedThread(r, "clientInboundChannel-1"));
      wsPool = Executors.newSingleThreadExecutor(r -> new GroupedThread(r, "WorksheetEngine"));
   }

   @AfterEach
   void tearDown() {
      inboundPool.shutdownNow();
      wsPool.shutdownNow();
      Tool.clearUserMessage();
   }

   // Documents the general ServiceProxyContext async-discard mechanism underlying bug #78254
   // (still true today, by design -- see the class javadoc for why #78254's actual fix doesn't
   // touch it). VSRefreshController.refreshViewsheet() calls refreshViewsheetAsync(...) and never
   // awaits the Future; this test awaits it only so the assertion below is deterministic --
   // production's failure to await is a second, compounding problem not modeled here.
   @Test
   void asyncProxyPathStillDiscardsMessagesByDesign() throws Exception {
      VSRefreshService localService = mock(VSRefreshService.class);
      doAnswer(inv -> {
         // Simulates CoreLifecycleService.refreshViewsheet()'s re-executed table query re-raising
         // a script/calc-field error via CoreTool.addUserWarning (AssetQuery.java:1082).
         Tool.addUserWarning("calc field error re-raised on refresh");
         return null;
      }).when(localService).refreshViewsheet(anyString(), any(), any(), any(), any());

      WorksheetService wsService = mock(WorksheetService.class);
      when(wsService.isLocal(anyString())).thenReturn(false);
      when(wsService.affinityCallAsync(anyString(), any())).thenAnswer(inv -> {
         AffinityCallable<?> job = inv.getArgument(1);
         return CompletableFuture.supplyAsync(() -> {
            try(MockedStatic<ConfigurationContext> ignored =
                   mockConfigurationContext(localService, wsService))
            {
               return callQuietly(job);
            }
         }, wsPool);
      });

      VSRefreshServiceProxy proxy = new VSRefreshServiceProxy(mock(Cluster.class), wsService, localService);
      AtomicReference<UserMessage> callerSaw = new AtomicReference<>();

      ExecutorSubscribableChannel ch = channel(m -> {
         try {
            // Exactly VSRefreshController.refreshViewsheet()'s own call
            // (VSRefreshController.java:61), except the test awaits the Future so the assertion
            // below is deterministic.
            proxy.refreshViewsheetAsync("vs1", mock(VSRefreshEvent.class), mock(Principal.class),
                                        null, "link").get(5, TimeUnit.SECONDS);
         }
         catch(Exception e) {
            throw new RuntimeException(e);
         }

         // VSRefreshController itself never reads this either -- which is exactly the bug:
         // nothing on the calling/STOMP thread ever learns the message existed.
         callerSaw.set(Tool.getUserMessage());
      });

      ch.send(stomp("alice"));
      drain();

      assertNull(callerSaw.get(),
         "Bug #78254: ServiceProxyContext.apply()'s !async guard discards a harvested message " +
         "for an always-async @ClusterProxy call, by design (protects #77135's fix) -- the " +
         "caller correctly sees null here. This documents that general relay mechanism only; it " +
         "is not a claim that bug #78254's user-visible symptom is unfixed -- that symptom is " +
         "fixed in CoreLifecycleService.refreshViewsheet() itself (forwardReraisedUserMessage), " +
         "which bypasses this relay entirely. See CoreLifecycleServiceUserMessageForwardingTest " +
         "for the test that verifies the actual fix.");
   }

   // Control: the sync proxy flavor (never used by VSRefreshController, but confirms the loss
   // above is specific to the async flavor, not to @ClusterProxy dispatch in general).
   @Test
   void syncProxyPathDeliversItsOwnReraisedUserMessageToTheCaller() throws Exception {
      VSRefreshService localService = mock(VSRefreshService.class);
      doAnswer(inv -> {
         Tool.addUserWarning("calc field error re-raised on refresh");
         return null;
      }).when(localService).refreshViewsheet(anyString(), any(), any(), any(), any());

      WorksheetService wsService = mock(WorksheetService.class);
      when(wsService.isLocal(anyString())).thenReturn(false);
      when(wsService.affinityCall(anyString(), any())).thenAnswer(inv -> {
         AffinityCallable<?> job = inv.getArgument(1);

         try(MockedStatic<ConfigurationContext> ignored =
                mockConfigurationContext(localService, wsService))
         {
            return callQuietly(job);
         }
      });

      VSRefreshServiceProxy proxy = new VSRefreshServiceProxy(mock(Cluster.class), wsService, localService);
      AtomicReference<UserMessage> callerSaw = new AtomicReference<>();

      ExecutorSubscribableChannel ch = channel(m -> {
         try {
            proxy.refreshViewsheet("vs1", mock(VSRefreshEvent.class), mock(Principal.class),
                                   null, "link");
         }
         catch(Exception e) {
            throw new RuntimeException(e);
         }

         callerSaw.set(Tool.getUserMessage());
      });

      ch.send(stomp("alice"));
      drain();

      assertNotNull(callerSaw.get(), "sync proxy path lost its own message too (unexpected)");
      assertEquals("calc field error re-raised on refresh", callerSaw.get().getMessage());
   }

   private static <T> T callQuietly(AffinityCallable<T> job) {
      try {
         return job.call();
      }
      catch(RuntimeException e) {
         throw e;
      }
      catch(Exception e) {
         throw new CompletionException(e);
      }
   }

   private static MockedStatic<ConfigurationContext> mockConfigurationContext(
      VSRefreshService localService, WorksheetService wsService)
   {
      ConfigurationContext context = mock(ConfigurationContext.class);
      when(context.lookupProxyTarget(VSRefreshService.class)).thenReturn(localService);
      when(context.getSpringBean(WorksheetService.class)).thenReturn(wsService);
      MockedStatic<ConfigurationContext> cc = mockStatic(ConfigurationContext.class);
      cc.when(ConfigurationContext::getContext).thenReturn(context);
      return cc;
   }

   private ExecutorSubscribableChannel channel(Consumer<Message<?>> handler) {
      // A handler exception propagates out of the channel's send task; swallow it here so the
      // single pooled thread survives and is reused, as the real inbound pool's threads are.
      Executor reusingExecutor = task -> inboundPool.execute(() -> {
         try {
            task.run();
         }
         catch(RuntimeException ignore) {
            // surfaced via the test body instead, if needed
         }
      });
      ExecutorSubscribableChannel ch = new ExecutorSubscribableChannel(reusingExecutor);
      ch.addInterceptor(new MessageScopeInterceptor());
      ch.subscribe(handler::accept);
      return ch;
   }

   private static Message<byte[]> stomp(String user) {
      StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
      accessor.setDestination("/events/vs/refresh");
      accessor.setSessionId("s-" + user);
      // ThreadContext.setContextPrincipal -> LogContext casts the principal to XPrincipal
      XPrincipal principal = mock(XPrincipal.class);
      when(principal.getName()).thenReturn(user + "~;~host-org");
      when(principal.getGroups()).thenReturn(new String[0]);
      when(principal.getRoles()).thenReturn(new IdentityID[0]);
      accessor.setUser(principal);
      accessor.setLeaveMutable(true);
      return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
   }

   private void drain() throws Exception {
      inboundPool.submit(() -> { }).get(5, TimeUnit.SECONDS);
   }
}
