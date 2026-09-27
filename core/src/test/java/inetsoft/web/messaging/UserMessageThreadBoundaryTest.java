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
package inetsoft.web.messaging;

import inetsoft.sree.security.IdentityID;
import inetsoft.uql.XPrincipal;
import inetsoft.util.*;
import inetsoft.web.ServiceProxyContext;
import inetsoft.web.viewsheet.command.MessageCommand;
import org.junit.jupiter.api.*;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ExecutorSubscribableChannel;
import org.springframework.messaging.support.MessageBuilder;

import java.util.List;
import java.util.concurrent.*;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #77135: the thread-local user message list ({@code CoreTool.USER_MESSAGE_LOCAL}) must not
 * survive from one unit of work to the next on a pooled thread.
 *
 * <p>Axis covered: <b>STOMP entry paths</b> (any handler on the client inbound channel, not only
 * those inside {@code EventAspect}'s pointcut; normal and throwing handlers) &times;
 * <b>pooled threads</b> (one {@link GroupedThread} reused by two users) &times;
 * <b>sync / async proxy</b> ({@link ServiceProxyContext#apply()} on the caller thread vs. on the
 * async executor thread).
 *
 * <p>The STOMP cases use a real {@link ExecutorSubscribableChannel} with the real
 * {@link MessageScopeInterceptor} on a single-thread executor, so alice's and bob's messages are
 * handled on the same pooled thread. The async case replays the sequence the generated
 * {@code @ClusterProxy} callable runs ({@code preprocess -> service -> postprocess -> apply}) on a
 * WorksheetEngine-style pool, which has no end-of-task cleanup of its own.
 */
@Tag("core")
class UserMessageThreadBoundaryTest {
   private ExecutorService inboundPool;

   @BeforeEach
   void setUp() {
      Tool.clearUserMessage();
      inboundPool = Executors.newSingleThreadExecutor(
         r -> new GroupedThread(r, "clientInboundChannel-1"));
   }

   @AfterEach
   void tearDown() {
      inboundPool.shutdownNow();
      Tool.clearUserMessage();
   }

   // STOMP: a handler that leaves a message (as handlers outside EventAspect's pointcut do) must
   // not let the next user's handler read it through the CoreLifecycleService.execute sink.
   @Test
   void stompMessageLeftByAliceIsNotVisibleToBob() throws Exception {
      List<String> bobSaw = new CopyOnWriteArrayList<>();
      List<Thread> threads = new CopyOnWriteArrayList<>();
      ExecutorSubscribableChannel ch = channel(m -> {
         threads.add(Thread.currentThread());

         if("alice".equals(user(m))) {
            Tool.addUserWarning("alice-org1-secret");
         }
         else {
            bobSaw.add(text(Tool.getUserMessage()));
         }
      });

      ch.send(stomp("alice"));
      ch.send(stomp("bob"));
      drain();

      assertSame(threads.get(0), threads.get(1), "alice and bob must share one pooled thread");
      assertEquals(List.of("<none>"), bobSaw, "bob saw a message left by alice");
   }

   // STOMP: a handler that fails after adding a message must not leak it either
   // (afterMessageHandled is still invoked with the exception).
   @Test
   void stompMessageLeftByThrowingHandlerIsNotVisibleToBob() throws Exception {
      List<String> bobSaw = new CopyOnWriteArrayList<>();
      List<Thread> threads = new CopyOnWriteArrayList<>();
      ExecutorSubscribableChannel ch = channel(m -> {
         threads.add(Thread.currentThread());

         if("alice".equals(user(m))) {
            Tool.addUserWarning("alice-before-failure");
            throw new IllegalStateException("handler failed");
         }

         bobSaw.add(text(Tool.getUserMessage()));
      });

      ch.send(stomp("alice"));
      ch.send(stomp("bob"));
      drain();

      assertSame(threads.get(0), threads.get(1), "alice and bob must share one pooled thread");
      assertEquals(List.of("<none>"), bobSaw, "bob saw a message left by alice's failed handler");
   }

   // STOMP: the clears must not swallow the handler's own messages. Both an execute-style read
   // inside the handler and an EventAspect-style read in a finally still deliver to bob.
   @Test
   void stompHandlersOwnMessagesAreStillDelivered() throws Exception {
      List<String> delivered = new CopyOnWriteArrayList<>();
      ExecutorSubscribableChannel ch = channel(m -> {
         try {
            Tool.addUserWarning("bob-early");
            delivered.add(text(Tool.getUserMessage()));
            Tool.addUserWarning("bob-late");
         }
         finally {
            for(MessageCommand.Type type : MessageCommand.Type.values()) {
               Tool.getUserMessages(type).forEach(u -> delivered.add(u.getMessage()));
            }

            Tool.clearUserMessage();
         }
      });

      ch.send(stomp("bob"));
      drain();

      assertEquals(List.of("bob-early", "bob-late"), delivered);
   }

   // Sync proxy: a DIRECT (local) sync call returns the service's messages to the calling STOMP
   // thread and keeps the messages the caller had before the call.
   @Test
   void syncDirectProxyCallKeepsCallerMessages() throws Exception {
      List<String> seen = new CopyOnWriteArrayList<>();
      ExecutorSubscribableChannel ch = channel(m -> {
         Tool.addUserWarning("before-proxy");
         ServiceProxyContext ctx = new ServiceProxyContext(false);
         ctx.preprocess();

         try {
            Tool.addUserWarning("inside-service");
         }
         finally {
            ctx.postprocess();
         }

         ctx.apply();
         seen.add(text(Tool.getUserMessage()));
      });

      ch.send(stomp("bob"));
      drain();

      assertEquals(List.of("before-proxy\ninside-service"), seen);
   }

   // Async proxy: apply() on the async executor thread must not re-add alice's messages there,
   // where the next task (another user) would read them.
   @Test
   void asyncProxyApplyLeavesNoMessageOnPoolThread() throws Exception {
      ExecutorService wsPool =
         Executors.newSingleThreadExecutor(r -> new GroupedThread(r, "WorksheetEngine"));

      try {
         ServiceProxyContext aliceCtx = new ServiceProxyContext(true);

         CompletableFuture.supplyAsync(() -> {
            aliceCtx.preprocess();

            try {
               Tool.addUserWarning("alice-async-refresh-warning");
            }
            finally {
               aliceCtx.postprocess();
               aliceCtx.apply();
            }

            return null;
         }, wsPool).get(5, TimeUnit.SECONDS);

         String nextTaskSaw = CompletableFuture
            .supplyAsync(() -> text(Tool.getUserMessage()), wsPool)
            .get(5, TimeUnit.SECONDS);

         assertEquals("<none>", nextTaskSaw, "next task on the pool saw alice's async message");
      }
      finally {
         wsPool.shutdownNow();
      }
   }

   private ExecutorSubscribableChannel channel(Consumer<Message<?>> handler) {
      // A handler exception propagates out of the channel's send task; swallow it here so the
      // single pooled thread survives and is reused, as the real inbound pool's threads are.
      Executor reusingExecutor = task -> inboundPool.execute(() -> {
         try {
            task.run();
         }
         catch(RuntimeException ignore) {
            // expected for the throwing-handler case
         }
      });
      ExecutorSubscribableChannel ch = new ExecutorSubscribableChannel(reusingExecutor);
      ch.addInterceptor(new MessageScopeInterceptor());
      ch.subscribe(handler::accept);
      return ch;
   }

   private static Message<byte[]> stomp(String user) {
      StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
      accessor.setDestination("/events/test");
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

   private static String user(Message<?> message) {
      return StompHeaderAccessor.wrap(message).getUser().getName().split("~")[0];
   }

   private static String text(UserMessage message) {
      return message == null ? "<none>" : message.getMessage();
   }

   private void drain() throws Exception {
      inboundPool.submit(() -> { }).get(5, TimeUnit.SECONDS);
   }
}
