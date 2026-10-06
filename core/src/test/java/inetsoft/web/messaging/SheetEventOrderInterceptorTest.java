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

import org.junit.jupiter.api.*;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77887: the events one STOMP session sends for one runtime sheet must be handled in the
 * order they were received, although the client inbound channel runs on a thread pool.
 *
 * <p>Axis covered: <b>same sheet vs. other sheet / other session</b> &times; <b>ordered event vs.
 * cancel event vs. frame without a sheet</b> &times; <b>normal vs. throwing handler</b>. The channel
 * is a real {@link ExecutorSubscribableChannel} on a multi-thread pool (like
 * {@code WebSocketConfig.eventTaskExecutor()}) with three subscribers, as the real inbound channel
 * fans each message out to the annotation, broker and user destination handlers, and with
 * {@link ImmutableMessageChannelInterceptor} last, as Spring adds it.
 */
@Tag("core")
class SheetEventOrderInterceptorTest {
   @BeforeEach
   void setUp() {
      pool = Executors.newFixedThreadPool(32);
      interceptor = new SheetEventOrderInterceptor();
   }

   @AfterEach
   void tearDown() {
      pool.shutdownNow();
   }

   // A burst of selection deltas: an earlier event takes longer before it is applied (pre-lock
   // jitter), so without ordering a later event is applied first.
   @Test
   void eventsOfOneSheetAreAppliedInReceiveOrder() throws Exception {
      List<Integer> applied = new CopyOnWriteArrayList<>();
      AtomicInteger running = new AtomicInteger();
      AtomicInteger peak = new AtomicInteger();
      int count = 16;
      CountDownLatch done = new CountDownLatch(count);

      ExecutorSubscribableChannel channel = channel(m -> {
         peak.accumulateAndGet(running.incrementAndGet(), Math::max);

         try {
            int seq = seq(m);
            Thread.sleep((count - seq) * 3L);
            applied.add(seq);
         }
         catch(InterruptedException e) {
            Thread.currentThread().interrupt();
         }
         finally {
            running.decrementAndGet();
            done.countDown();
         }
      });

      for(int i = 0; i < count; i++) {
         channel.send(event("s1", "vs1", "/events/selectionList/update/List1", i));
      }

      assertTrue(done.await(10, TimeUnit.SECONDS), "not all events were handled");
      assertEquals(sequence(count), applied, "events were applied out of receive order");
      assertEquals(1, peak.get(), "events of one sheet ran concurrently");
      awaitNoPendingSheet();
   }

   // Other sheets (and the same runtime id in another session) are not held behind a busy sheet.
   @Test
   void otherSheetsAndSessionsAreNotHeld() throws Exception {
      CountDownLatch release = new CountDownLatch(1);
      Set<String> handled = ConcurrentHashMap.newKeySet();
      ExecutorSubscribableChannel channel = channel(m -> {
         handled.add(id(m));

         if("busy".equals(id(m))) {
            await(release);
         }
      });

      channel.send(event("s1", "vs1", "/events/vs/refresh", 0, "busy"));
      channel.send(event("s1", "vs2", "/events/vs/refresh", 1, "otherSheet"));
      channel.send(event("s2", "vs1", "/events/vs/refresh", 2, "otherSession"));
      channel.send(event("s1", "vs1", "/events/vs/refresh", 3, "sameSheet"));

      awaitHandled(handled, "otherSheet");
      awaitHandled(handled, "otherSession");
      Thread.sleep(100);
      assertFalse(handled.contains("sameSheet"), "the next event of the busy sheet ran early");

      release.countDown();
      awaitHandled(handled, "sameSheet");
      awaitNoPendingSheet();
   }

   // A cancel event must reach the sheet while the event it cancels is still running, and frames
   // without a sheet (no runtime id, subscriptions) are not held either.
   @Test
   void cancelEventsAndFramesWithoutSheetAreNotHeld() throws Exception {
      CountDownLatch release = new CountDownLatch(1);
      Set<String> handled = ConcurrentHashMap.newKeySet();
      ExecutorSubscribableChannel channel = channel(m -> {
         handled.add(id(m));

         if("busy".equals(id(m))) {
            await(release);
         }
      });

      channel.send(event("s1", "vs1", "/events/selectionList/update/List1", 1, "busy"));
      channel.send(event("s1", "vs1", "/events/composer/viewsheet/cancelViewsheet", 2, "cancelVs"));
      channel.send(event("s1", "vs1", "/events/vschart/cancel-query", 3, "cancelChart"));
      channel.send(event("s1", "vs1", "/events/vs/wizard/use-meta", 5, "useMeta"));
      channel.send(event("s1", null, "/events/composer/touch-asset", 4, "noSheet"));
      channel.send(subscribe("s1", "vs1", "subscribe"));

      awaitHandled(handled, "cancelVs");
      awaitHandled(handled, "cancelChart");
      awaitHandled(handled, "useMeta");
      awaitHandled(handled, "noSheet");
      awaitHandled(handled, "subscribe");

      release.countDown();
      awaitNoPendingSheet();
   }

   // A failing event must not stall the events of the sheet after it.
   @Test
   void throwingHandlerReleasesTheNextEvent() throws Exception {
      List<String> handled = new CopyOnWriteArrayList<>();
      ExecutorSubscribableChannel channel = channel(m -> {
         handled.add(id(m));

         if("fails".equals(id(m))) {
            throw new IllegalStateException("handler failed");
         }
      });

      channel.send(event("s1", "vs1", "/events/vs/refresh", 0, "fails"));
      channel.send(event("s1", "vs1", "/events/vs/refresh", 1, "next"));

      awaitHandled(handled, "next");
      assertEquals("fails", handled.getFirst());
      awaitNoPendingSheet();
   }

   // An event that another interceptor rejects is never handled, so it must release the next one.
   @Test
   void rejectedEventReleasesTheNextEvent() throws Exception {
      List<String> handled = new CopyOnWriteArrayList<>();
      CountDownLatch release = new CountDownLatch(1);
      ExecutorSubscribableChannel channel = channel(m -> {
         handled.add(id(m));

         if("busy".equals(id(m))) {
            await(release);
         }
      });
      channel.addInterceptor(1, new ChannelInterceptor() {
         @Override
         public Message<?> preSend(Message<?> message, org.springframework.messaging.MessageChannel ch) {
            return "rejected".equals(id(message)) ? null : message;
         }
      });

      channel.send(event("s1", "vs1", "/events/vs/refresh", 0, "busy"));
      channel.send(event("s1", "vs1", "/events/vs/refresh", 1, "rejected"));
      channel.send(event("s1", "vs1", "/events/vs/refresh", 2, "next"));
      release.countDown();

      awaitHandled(handled, "next");
      assertFalse(handled.contains("rejected"));
      awaitNoPendingSheet();
   }

   private ExecutorSubscribableChannel channel(MessageHandler handler) {
      ExecutorSubscribableChannel channel = new ExecutorSubscribableChannel(pool);
      channel.setInterceptors(List.of(interceptor, new ImmutableMessageChannelInterceptor()));
      channel.subscribe(handler);
      // the broker and user destination handlers also receive every inbound message
      channel.subscribe(m -> {});
      channel.subscribe(m -> {});
      return channel;
   }

   private static Message<byte[]> event(String session, String runtimeId, String destination,
                                        int seq)
   {
      return event(session, runtimeId, destination, seq, "e" + seq);
   }

   private static Message<byte[]> event(String session, String runtimeId, String destination,
                                        int seq, String id)
   {
      StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
      accessor.setDestination(destination);
      return message(accessor, session, runtimeId, seq, id);
   }

   private static Message<byte[]> subscribe(String session, String runtimeId, String id) {
      StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
      accessor.setDestination("/user/commands");
      accessor.setSubscriptionId(id);
      return message(accessor, session, runtimeId, -1, id);
   }

   private static Message<byte[]> message(StompHeaderAccessor accessor, String session,
                                          String runtimeId, int seq, String id)
   {
      accessor.setSessionId(session);

      if(runtimeId != null) {
         accessor.setNativeHeader("sheetRuntimeId", runtimeId);
      }

      accessor.setNativeHeader("seq", Integer.toString(seq));
      accessor.setNativeHeader("id", id);
      // as StompSubProtocolHandler leaves it when ImmutableMessageChannelInterceptor is present
      accessor.setLeaveMutable(true);
      return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
   }

   private static int seq(Message<?> message) {
      return Integer.parseInt(StompHeaderAccessor.wrap(message).getFirstNativeHeader("seq"));
   }

   private static String id(Message<?> message) {
      return StompHeaderAccessor.wrap(message).getFirstNativeHeader("id");
   }

   private static List<Integer> sequence(int count) {
      List<Integer> list = new ArrayList<>();

      for(int i = 0; i < count; i++) {
         list.add(i);
      }

      return list;
   }

   private static void await(CountDownLatch latch) {
      try {
         latch.await(10, TimeUnit.SECONDS);
      }
      catch(InterruptedException e) {
         Thread.currentThread().interrupt();
      }
   }

   private static void awaitHandled(Collection<String> handled, String id) throws Exception {
      long end = System.currentTimeMillis() + 10000;

      while(!handled.contains(id) && System.currentTimeMillis() < end) {
         Thread.sleep(10);
      }

      assertTrue(handled.contains(id), id + " was not handled");
   }

   private void awaitNoPendingSheet() throws Exception {
      long end = System.currentTimeMillis() + 10000;

      while(interceptor.getPendingSheetCount() > 0 && System.currentTimeMillis() < end) {
         Thread.sleep(10);
      }

      assertEquals(0, interceptor.getPendingSheetCount(), "a sheet queue was left behind");
   }

   private ExecutorService pool;
   private SheetEventOrderInterceptor interceptor;
}
