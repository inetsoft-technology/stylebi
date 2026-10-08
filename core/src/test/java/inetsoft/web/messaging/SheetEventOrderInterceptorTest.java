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

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.web.ServiceProxyContext;
import org.junit.jupiter.api.*;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.*;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77887: the events one STOMP session sends for one runtime sheet must be handled in the
 * order they were received, although the client inbound channel runs on a thread pool.
 *
 * <p>Axis covered: <b>same sheet vs. other sheet / other session</b> &times; <b>ordered event vs.
 * cancel / flyover event vs. close vs. periodic touch-asset (keep-alive vs. auto save / refresh /
 * malformed) vs. frame without a sheet</b> &times;
 * <b>normal vs. throwing handler vs. disconnected / reconnected session</b>. The channel
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
      interceptor = new SheetEventOrderInterceptor(new ObjectMapper());
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

   // A newer flyover must reach the sheet while an older one runs, so it can cancel the older
   // one's query (Bug #77155).
   @Test
   void flyoverEventsAreNotHeld() throws Exception {
      CountDownLatch release = new CountDownLatch(1);
      Set<String> handled = ConcurrentHashMap.newKeySet();
      ExecutorSubscribableChannel channel = channel(m -> {
         handled.add(id(m));

         if("busy".equals(id(m))) {
            await(release);
         }
      });

      channel.send(event("s1", "vs1", "/events/vschart/flyover", 0, "busy"));
      channel.send(event("s1", "vs1", "/events/vschart/flyover", 1, "chartFlyover"));
      channel.send(event("s1", "vs1", "/events/table/flyover", 2, "tableFlyover"));

      awaitHandled(handled, "chartFlyover", WHILE_BUSY);
      awaitHandled(handled, "tableFlyover", WHILE_BUSY);

      release.countDown();
      awaitNoPendingSheet();
   }

   // The viewer sends a touch-asset refresh every interval without waiting for the last one, and
   // the composer an auto save. A refresh that takes longer than the interval must not build a
   // backlog of refreshes.
   @Test
   void periodicTouchAssetStaysBounded() throws Exception {
      CountDownLatch release = new CountDownLatch(1);
      List<String> handled = new CopyOnWriteArrayList<>();
      ExecutorSubscribableChannel channel = channel(m -> {
         handled.add(id(m));

         if("busy".equals(id(m))) {
            await(release);
         }
      });

      channel.send(event("s1", "vs1", "/events/vs/refresh", 0, "busy"));
      channel.send(event("s1", "vs1", "/events/selectionList/update/List1", 1, "selection"));

      for(int i = 0; i < 50; i++) {
         channel.send(event("s1", "vs1", "/events/composer/touch-asset", 2, "update",
                            "{\"update\":true}"));
         channel.send(event("s1", "vs1", "/events/composer/touch-asset", 3, "autoSave",
                            "{\"changed\":true}"));
      }

      // the running event, the selection and one touch-asset of each kind
      assertEquals(4, interceptor.getQueuedEventCount(), "the touch-asset events were queued");

      release.countDown();
      awaitHandled(handled, "autoSave");
      awaitNoPendingSheet();
      assertEquals(List.of("busy", "selection", "update", "autoSave"), handled);
   }

   // A touch-asset that only keeps the sheet alive must refresh its heartbeat while a long event
   // runs, or the sheet is recycled as expired before the event ends.
   @Test
   void keepAliveTouchAssetIsNotHeld() throws Exception {
      CountDownLatch release = new CountDownLatch(1);
      Set<String> handled = ConcurrentHashMap.newKeySet();
      ExecutorSubscribableChannel channel = channel(m -> {
         handled.add(id(m));

         if("busy".equals(id(m))) {
            await(release);
         }
      });

      channel.send(event("s1", "vs1", "/events/selectionList/update/List1", 0, "busy"));
      channel.send(event("s1", "vs1", "/events/composer/touch-asset", 1, "viewer",
                         "{\"design\":false,\"changed\":false,\"update\":false," +
                         "\"wallboard\":true,\"width\":0,\"height\":0}"));
      channel.send(event("s1", "vs1", "/events/composer/touch-asset", 2, "composer",
                         "{\"design\":true,\"changed\":false,\"update\":false}"));
      channel.send(event("s1", "vs1", "/events/composer/touch-asset", 3, "noFlags", "{}"));

      awaitHandled(handled, "viewer", WHILE_BUSY);
      awaitHandled(handled, "composer", WHILE_BUSY);
      awaitHandled(handled, "noFlags", WHILE_BUSY);
      assertEquals(1, interceptor.getQueuedEventCount(), "a keep-alive touch-asset was queued");

      release.countDown();
      awaitNoPendingSheet();
   }

   // An auto save must not be applied before an earlier edit is.
   @Test
   void autoSaveTouchAssetIsHeld() throws Exception {
      assertTouchAssetIsHeld("{\"design\":true,\"changed\":true,\"update\":false}");
   }

   // A refresh must not be applied before an earlier selection is.
   @Test
   void refreshTouchAssetIsHeld() throws Exception {
      assertTouchAssetIsHeld("{\"design\":false,\"changed\":false,\"update\":true}");
   }

   // A touch-asset that cannot be parsed may be an auto save or a refresh, so it is held.
   @Test
   void malformedTouchAssetIsHeld() throws Exception {
      assertTouchAssetIsHeld("{\"changed\":fal");
   }

   private void assertTouchAssetIsHeld(String payload) throws Exception {
      CountDownLatch release = new CountDownLatch(1);
      List<String> handled = new CopyOnWriteArrayList<>();
      ExecutorSubscribableChannel channel = channel(m -> {
         handled.add(id(m));

         if("busy".equals(id(m))) {
            await(release);
         }
      });

      channel.send(event("s1", "vs1", "/events/selectionList/update/List1", 0, "busy"));
      channel.send(event("s1", "vs1", "/events/composer/touch-asset", 1, "touch", payload));

      Thread.sleep(200);
      assertFalse(handled.contains("touch"), "the touch-asset ran before the running event ended");
      assertEquals(2, interceptor.getQueuedEventCount(), "the touch-asset was not held");

      release.countDown();
      awaitHandled(handled, "touch");
      awaitNoPendingSheet();
      assertEquals(List.of("busy", "touch"), handled);
   }

   // Closing a busy sheet must cancel its running query, so the close is not held behind it.
   @Test
   void closeOfABusySheetIsNotHeld() throws Exception {
      CountDownLatch release = new CountDownLatch(1);
      Set<String> handled = ConcurrentHashMap.newKeySet();
      ExecutorSubscribableChannel channel = channel(m -> {
         handled.add(id(m));

         if(id(m).startsWith("busy")) {
            await(release);
         }
      });

      channel.send(event("s1", "vs1", "/events/vs/refresh", 0, "busy"));
      channel.send(event("s1", "vs1", "/events/composer/viewsheet/close", 1, "closeVs"));
      channel.send(event("s1", "ws1", "/events/vs/refresh", 2, "busyWs"));
      channel.send(event("s1", "ws1", "/events/ws/close", 3, "closeWs"));

      awaitHandled(handled, "closeVs", WHILE_BUSY);
      awaitHandled(handled, "closeWs", WHILE_BUSY);

      release.countDown();
      awaitNoPendingSheet();
   }

   // A close must not overtake an event that is held, e.g. a save sent before the close.
   @Test
   void closeIsNotAppliedBeforeHeldEvents() throws Exception {
      CountDownLatch release = new CountDownLatch(1);
      List<String> handled = new CopyOnWriteArrayList<>();
      ExecutorSubscribableChannel channel = channel(m -> {
         handled.add(id(m));

         if("busy".equals(id(m))) {
            await(release);
         }
      });

      channel.send(event("s1", "vs1", "/events/vs/refresh", 0, "busy"));
      channel.send(event("s1", "vs1", "/events/composer/viewsheet/save", 1, "save"));
      channel.send(event("s1", "vs1", "/events/composer/viewsheet/close", 2, "close"));

      // busy runs on a pool thread, wait for it before checking that nothing else ran
      awaitHandled(handled, "busy");
      Thread.sleep(100);
      assertEquals(List.of("busy"), handled);

      release.countDown();
      awaitHandled(handled, "close");
      assertEquals(List.of("busy", "save", "close"), handled);
      awaitNoPendingSheet();
   }

   // The client reconnects after its STOMP session drops and keeps using its sheets, so the held
   // events of the session (e.g. an edit and a save) still run, in order.
   @Test
   void disconnectKeepsTheHeldEventsOfTheSession() throws Exception {
      CountDownLatch release = new CountDownLatch(1);
      List<String> handled = new CopyOnWriteArrayList<>();
      ExecutorSubscribableChannel channel = channel(m -> {
         handled.add(id(m));

         if("busy".equals(id(m))) {
            await(release);
         }
      });

      channel.send(event("s1", "vs1", "/events/vs/refresh", 0, "busy"));
      channel.send(event("s1", "vs1", "/events/composer/viewsheet/edit", 1, "edit"));
      channel.send(event("s1", "vs1", "/events/composer/viewsheet/save", 2, "save"));
      awaitHandled(handled, "busy");

      channel.send(disconnect("s1"));
      assertEquals(3, interceptor.getQueuedEventCount(), "the held events were dropped");

      release.countDown();
      awaitHandled(handled, "save");
      awaitNoPendingSheet();
      assertEquals(List.of("busy", "edit", "save"), events(handled));
      assertEquals(0, interceptor.getQueuedEventCount());
   }

   // The events a reconnected client sends (a new STOMP session of the same HTTP session) are
   // applied after the ones it sent before, other HTTP sessions are not held.
   @Test
   void reconnectedSessionIsOrderedAfterTheOldOne() throws Exception {
      CountDownLatch release = new CountDownLatch(1);
      List<String> handled = new CopyOnWriteArrayList<>();
      ExecutorSubscribableChannel channel = channel(m -> {
         handled.add(id(m));

         if("busy".equals(id(m))) {
            await(release);
         }
      });

      channel.send(event("s1", "h1", "vs1", "/events/vs/refresh", 0, "busy"));
      channel.send(event("s1", "h1", "vs1", "/events/selectionList/update/List1", 1, "held"));
      channel.send(disconnect("s1"));
      channel.send(event("s2", "h1", "vs1", "/events/selectionList/update/List1", 2, "after"));
      // other HTTP sessions are not ordered after busy, so wait for busy to run before sending
      // one, otherwise the two handlers can run in either order on the pool (Bug #78020)
      awaitHandled(handled, "busy");
      channel.send(event("s3", "h2", "vs1", "/events/vs/refresh", 3, "otherHttpSession"));

      awaitHandled(handled, "otherHttpSession", WHILE_BUSY);
      assertFalse(handled.contains("after"), "the reconnected session overtook the held event");

      release.countDown();
      awaitHandled(handled, "after");
      awaitNoPendingSheet();
      assertEquals(List.of("busy", "otherHttpSession", "held", "after"), events(handled));
   }

   // A touch-asset (e.g. an auto save, changed:true) must not be dropped in favour of a held copy
   // that runs before a later event, that would write the auto save file without the later edit.
   @Test
   void touchAssetIsNotMovedBeforeALaterEvent() throws Exception {
      CountDownLatch release = new CountDownLatch(1);
      List<String> handled = new CopyOnWriteArrayList<>();
      ExecutorSubscribableChannel channel = channel(m -> {
         handled.add(id(m));

         if("busy".equals(id(m))) {
            await(release);
         }
      });

      channel.send(event("s1", "vs1", "/events/vs/refresh", 0, "busy"));
      channel.send(event("s1", "vs1", "/events/composer/touch-asset", 1, "save1",
                         "{\"changed\":true}"));
      channel.send(event("s1", "vs1", "/events/composer/viewsheet/edit", 2, "edit"));
      channel.send(event("s1", "vs1", "/events/composer/touch-asset", 3, "save2",
                         "{\"changed\":true}"));

      release.countDown();
      awaitHandled(handled, "save2");
      awaitNoPendingSheet();
      assertEquals(List.of("busy", "save1", "edit", "save2"), handled);
   }

   // The subscribers are counted when a held event is sent, not when it is held.
   @Test
   void subscribersAreCountedWhenTheEventIsSent() throws Exception {
      CountDownLatch release = new CountDownLatch(1);
      Set<String> handled = ConcurrentHashMap.newKeySet();
      MessageHandler broker = m -> {};
      ExecutorSubscribableChannel channel = new ExecutorSubscribableChannel(pool);
      channel.setInterceptors(List.of(interceptor, new ImmutableMessageChannelInterceptor()));
      channel.subscribe(m -> {
         handled.add(id(m));

         if("busy".equals(id(m))) {
            await(release);
         }
      });
      channel.subscribe(broker);
      channel.subscribe(m -> {});

      channel.send(event("s1", "vs1", "/events/vs/refresh", 0, "busy"));
      channel.send(event("s1", "vs1", "/events/vs/refresh", 1, "held"));
      channel.send(event("s1", "vs1", "/events/vs/refresh", 2, "next"));
      awaitHandled(handled, "busy");
      channel.unsubscribe(broker);

      release.countDown();
      awaitHandled(handled, "next");
      awaitNoPendingSheet();
   }

   // The ticket must not reach the message headers that ServiceProxyContext copies into
   // cluster calls, it holds the whole message and its session attributes.
   @Test
   void serviceProxyContextCarriesNoTicket() throws Exception {
      ExecutorSubscribableChannel channel = channel(m -> {});
      Message<?> message = interceptor.preSend(
         event("s1", "vs1", "/events/vs/refresh", 0), channel);
      assertNotNull(message);
      MessageContextHolder.setMessageAttributes(new MessageAttributes(message));

      try {
         ServiceProxyContext context = new ServiceProxyContext(false);
         Field field = ServiceProxyContext.class.getDeclaredField("messageHeaders");
         field.setAccessible(true);
         @SuppressWarnings("unchecked")
         Map<String, Object> headers = (Map<String, Object>) field.get(context);

         assertEquals(1, interceptor.getQueuedEventCount());
         assertTrue(headers.keySet().stream().anyMatch(k -> k.contains("SheetEventOrder")),
                    "the event was not ticketed");

         for(Object value : headers.values()) {
            assertFalse(value.getClass().getName().startsWith(
                           SheetEventOrderInterceptor.class.getName()),
                        "a ticket is copied into the context: " + value);
            assertFalse(value instanceof Message, "a message is copied into the context");
         }
      }
      finally {
         MessageContextHolder.setMessageAttributes(null);
      }
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
      return event(session, runtimeId, destination, seq, id, "");
   }

   private static Message<byte[]> event(String session, String runtimeId, String destination,
                                        int seq, String id, String payload)
   {
      StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
      accessor.setDestination(destination);
      return message(accessor, session, runtimeId, seq, id, payload);
   }

   // an event of a STOMP session opened in an HTTP session
   private static Message<byte[]> event(String session, String httpSession, String runtimeId,
                                        String destination, int seq, String id)
   {
      StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
      accessor.setDestination(destination);
      accessor.setSessionAttributes(new HashMap<>(
         Map.of(HttpSessionHandshakeInterceptor.HTTP_SESSION_ID_ATTR_NAME, httpSession)));
      return message(accessor, session, runtimeId, seq, id, "");
   }

   private static Message<byte[]> disconnect(String session) {
      StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.DISCONNECT);
      return message(accessor, session, null, -1, "disconnect");
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
      return message(accessor, session, runtimeId, seq, id, "");
   }

   private static Message<byte[]> message(StompHeaderAccessor accessor, String session,
                                          String runtimeId, int seq, String id, String payload)
   {
      accessor.setSessionId(session);

      if(runtimeId != null) {
         accessor.setNativeHeader("sheetRuntimeId", runtimeId);
      }

      accessor.setNativeHeader("seq", Integer.toString(seq));
      accessor.setNativeHeader("id", id);
      // as StompSubProtocolHandler leaves it when ImmutableMessageChannelInterceptor is present
      accessor.setLeaveMutable(true);
      return MessageBuilder.createMessage(payload.getBytes(StandardCharsets.UTF_8),
                                          accessor.getMessageHeaders());
   }

   private static int seq(Message<?> message) {
      return Integer.parseInt(StompHeaderAccessor.wrap(message).getFirstNativeHeader("seq"));
   }

   private static String id(Message<?> message) {
      return StompHeaderAccessor.wrap(message).getFirstNativeHeader("id");
   }

   // the handled events without the disconnect frame
   private static List<String> events(List<String> handled) {
      return handled.stream().filter(id -> !"disconnect".equals(id)).toList();
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
      awaitHandled(handled, id, 10000);
   }

   // wait for an event that must run while the busy event is blocked (well before await() of
   // the blocked handler times out and releases the sheet)
   private static void awaitHandled(Collection<String> handled, String id, long timeout)
      throws Exception
   {
      long end = System.currentTimeMillis() + timeout;

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
   private static final long WHILE_BUSY = 3000;
}
