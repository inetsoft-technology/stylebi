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
package inetsoft.web.admin.notifications;

import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.web.notifications.NotificationMessage;
import inetsoft.web.notifications.NotificationService;
import inetsoft.web.security.RequiredPermission;
import inetsoft.web.security.Secured;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * The admin endpoint that broadcasts a notification to every connected user. The controller is
 * a plain object over a mocked {@link NotificationService}; the mapping and permission contract
 * is checked through reflection on the annotations.
 */
@Tag("core")
class NotificationControllerTest {
   @BeforeEach
   void setUp() {
      service = mock(NotificationService.class);
      controller = new NotificationController(service);
   }

   @Test
   void sendMessageBroadcastsTheMessageTextOnce() throws Exception {
      controller.sendMessage(NotificationMessage.builder().message("hello").build());

      verify(service, times(1)).sendNotification("hello");
      verifyNoMoreInteractions(service);
   }

   @Test
   void sendMessagePropagatesTheServiceException() throws Exception {
      Exception failure = new Exception("x");
      doThrow(failure).when(service).sendNotification(anyString());

      Exception thrown = assertThrows(
         Exception.class,
         () -> controller.sendMessage(NotificationMessage.builder().message("m").build()));

      assertSame(failure, thrown);
   }

   @ParameterizedTest(name = "text variant {index}")
   @ValueSource(strings = { " padded ", "héllo 世界 😀" })
   void sendMessageForwardsTheTextVerbatim(String text) throws Exception {
      controller.sendMessage(NotificationMessage.builder().message(text).build());

      verify(service, times(1)).sendNotification(text);
   }

   @Test
   void sendMessageRequiresAccessToTheNotificationEmComponent() throws Exception {
      Secured secured = sendMessageMethod().getAnnotation(Secured.class);

      assertNotNull(secured);
      assertEquals(1, secured.value().length);
      RequiredPermission permission = secured.value()[0];
      assertEquals(ResourceType.EM_COMPONENT, permission.resourceType());
      assertEquals("notification", permission.resource());
      assertArrayEquals(new ResourceAction[] { ResourceAction.ACCESS }, permission.actions());
      assertEquals("AND", secured.operator());
   }

   @Test
   void sendMessageIsMappedToPostOnTheEmNotifyPath() throws Exception {
      PostMapping mapping = sendMessageMethod().getAnnotation(PostMapping.class);

      assertNotNull(mapping);
      assertArrayEquals(new String[] { "/api/em/notify" }, mapping.value());
   }

   @Test
   void sendMessageBindsTheRequestBodyToANotificationMessage() throws Exception {
      Method method = sendMessageMethod();

      assertEquals(1, method.getParameterCount());
      assertEquals(NotificationMessage.class, method.getParameterTypes()[0]);
      assertNotNull(method.getParameters()[0].getAnnotation(RequestBody.class));
   }

   private static Method sendMessageMethod() throws NoSuchMethodException {
      return NotificationController.class.getMethod("sendMessage", NotificationMessage.class);
   }

   private NotificationService service;
   private NotificationController controller;
}
