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
package inetsoft.web;

import inetsoft.util.CoreTool;
import inetsoft.util.UserMessage;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/*
 * Bug #77110 regression coverage.
 *
 * User messages live in a thread-local, and servlet threads are pooled. ThreadLocalCleanupFilter
 * must clear them around each top-level (REQUEST) dispatch so a message left by one request is
 * never seen by the next request on the same thread, but must not clear them on a nested
 * FORWARD/INCLUDE dispatch, which would drop messages the enclosing request already added.
 */
@Tag("core")
class ThreadLocalCleanupFilterTest {
   @BeforeEach
   void setUp() {
      CoreTool.clearUserMessage();
   }

   @AfterEach
   void tearDown() {
      CoreTool.clearUserMessage();
   }

   @Test
   void requestDispatchClearsMessagesBeforeAndAfterChain() throws Exception {
      ThreadLocalCleanupFilter filter = new ThreadLocalCleanupFilter();
      CoreTool.addUserMessage("LEFT-BY-EARLIER-REQUEST");
      AtomicReference<UserMessage> seenInChain = new AtomicReference<>();

      filter.doFilter(request(DispatcherType.REQUEST), mock(HttpServletResponse.class),
                      (req, res) -> {
                         seenInChain.set(CoreTool.getUserMessage());
                         CoreTool.addUserMessage("ADDED-DURING-REQUEST");
                      });

      assertNull(seenInChain.get(), "stale message must be cleared before the chain runs");
      assertNull(CoreTool.getUserMessage(), "messages must be cleared after the request");
   }

   @Test
   void nestedForwardKeepsOuterRequestMessages() throws Exception {
      ThreadLocalCleanupFilter filter = new ThreadLocalCleanupFilter();
      AtomicReference<UserMessage> seenAfterForward = new AtomicReference<>();
      FilterChain forwarded = (req, res) -> CoreTool.addUserMessage("ADDED-BY-FORWARD");

      filter.doFilter(request(DispatcherType.REQUEST), mock(HttpServletResponse.class),
                      (req, res) -> {
                         CoreTool.addUserMessage("ADDED-BEFORE-FORWARD");
                         filter.doFilter(request(DispatcherType.FORWARD), res, forwarded);
                         seenAfterForward.set(CoreTool.getUserMessage());
                      });

      UserMessage message = seenAfterForward.get();
      assertNotNull(message, "nested forward must not clear the outer request's messages");
      assertTrue(message.getMessage().contains("ADDED-BEFORE-FORWARD"), message.getMessage());
      assertTrue(message.getMessage().contains("ADDED-BY-FORWARD"), message.getMessage());
   }

   @ParameterizedTest
   @EnumSource(value = DispatcherType.class, names = { "FORWARD", "INCLUDE", "ERROR", "ASYNC" })
   void nonRequestDispatchLeavesMessagesUntouched(DispatcherType type) throws Exception {
      // only the REQUEST dispatch owns the clear; ERROR runs after the REQUEST's finally has
      // already cleared, and FORWARD/INCLUDE/ASYNC must not drop messages of the enclosing request
      ThreadLocalCleanupFilter filter = new ThreadLocalCleanupFilter();
      CoreTool.addUserMessage("ADDED-BEFORE-DISPATCH");
      AtomicReference<UserMessage> seenInChain = new AtomicReference<>();

      filter.doFilter(request(type), mock(HttpServletResponse.class), (req, res) -> {
         seenInChain.set(CoreTool.getUserMessage());
         CoreTool.addUserMessage("ADDED-DURING-DISPATCH");
      });

      assertNotNull(seenInChain.get(), type + " dispatch must not clear before the chain");
      assertTrue(seenInChain.get().getMessage().contains("ADDED-BEFORE-DISPATCH"));
      UserMessage after = CoreTool.getUserMessage();
      assertNotNull(after, type + " dispatch must not clear after the chain");
      assertTrue(after.getMessage().contains("ADDED-DURING-DISPATCH"), after.getMessage());
   }

   private static HttpServletRequest request(DispatcherType type) {
      HttpServletRequest request = mock(HttpServletRequest.class);
      when(request.getDispatcherType()).thenReturn(type);
      return request;
   }
}
