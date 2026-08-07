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
package inetsoft.web.wiz.docs;

import inetsoft.sree.SreeEnv;
import inetsoft.web.admin.ai.AdminAiCallerGuard;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
@ExtendWith(MockitoExtension.class)
class WizDocSearchControllerTest {
   @Mock private AssistantDocSearchGateway gateway;
   private MockedStatic<SreeEnv> sreeEnv;
   private MockedStatic<AdminAiCallerGuard> guard;
   private WizDocSearchController controller;
   private MockHttpServletRequest request;
   private MockHttpServletResponse response;

   @BeforeEach
   void setUp() {
      controller = new WizDocSearchController(gateway);
      sreeEnv = mockStatic(SreeEnv.class, withSettings().lenient());
      guard = mockStatic(AdminAiCallerGuard.class, withSettings().lenient());
      request = new MockHttpServletRequest();
      request.addHeader("Authorization", "Bearer operator-token");
      response = new MockHttpServletResponse();
      configureAssistant("https://assistant.example.com");
   }

   @AfterEach
   void tearDown() {
      sreeEnv.close();
      guard.close();
   }

   private void configureAssistant(String internalUrl) {
      sreeEnv.when(() -> SreeEnv.getProperty("chat.app.internal.url")).thenReturn(internalUrl);
      sreeEnv.when(() -> SreeEnv.getProperty("chat.app.server.url")).thenReturn(null);
   }

   /**
    * The production controller now reads the body from the request stream instead of via
    * {@code @RequestBody}, so tests supply it the same way {@code MockHttpServletRequest}
    * would receive it off the wire.
    */
   private static void setBody(MockHttpServletRequest request, String body) {
      request.setContent(body.getBytes(StandardCharsets.UTF_8));
   }

   @Test
   void relaysASuccessfulResponseVerbatim() throws Exception {
      setBody(request, "{\"query\":\"q\"}");
      when(gateway.post(any(), any(), any(), any()))
         .thenReturn(new AssistantDocSearchGateway.Response(200, "{\"matches\":[]}"));

      controller.search(request, response);

      assertEquals(200, response.getStatus());
      assertEquals("{\"matches\":[]}", response.getContentAsString());
      assertEquals("application/json;charset=UTF-8", response.getContentType());
   }

   @Test
   void forwardsTheCallersBearerTokenAndBody() throws Exception {
      setBody(request, "{\"query\":\"q\"}");
      when(gateway.post(any(), any(), any(), any()))
         .thenReturn(new AssistantDocSearchGateway.Response(200, "{}"));

      controller.search(request, response);

      verify(gateway).post("https://assistant.example.com", "/api/doc-search",
                           "{\"query\":\"q\"}", "Bearer operator-token");
   }

   @Test
   void requiresABearerAuthenticatedRequest() throws Exception {
      setBody(request, "{\"query\":\"q\"}");
      when(gateway.post(any(), any(), any(), any()))
         .thenReturn(new AssistantDocSearchGateway.Response(200, "{}"));

      controller.search(request, response);

      guard.verify(AdminAiCallerGuard::requireBearerAuthenticatedRequest);
   }

   // The test above only proves the guard was called; it would still pass if the controller
   // ignored a thrown failure and pressed on. This proves the guard actually stops the request.
   @Test
   void aGuardFailureStopsTheRequestBeforeTheGatewayIsCalled() {
      setBody(request, "{\"query\":\"q\"}");
      guard.when(AdminAiCallerGuard::requireBearerAuthenticatedRequest)
         .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN));

      assertThrows(ResponseStatusException.class, () -> controller.search(request, response));

      verifyNoInteractions(gateway);
   }

   @Test
   void relaysAValidationErrorVerbatimSoTheAgentSeesTheNamedField() throws Exception {
      setBody(request, "{}");
      when(gateway.post(any(), any(), any(), any())).thenReturn(
         new AssistantDocSearchGateway.Response(400, "{\"error\":\"modules[0]: unknown module\"}"));

      controller.search(request, response);

      assertEquals(400, response.getStatus());
      assertTrue(response.getContentAsString().contains("modules[0]"));
   }

   @Test
   void relaysAnAssistantServerErrorVerbatim() throws Exception {
      setBody(request, "{}");
      when(gateway.post(any(), any(), any(), any()))
         .thenReturn(new AssistantDocSearchGateway.Response(500, "{\"error\":\"Document search failed\"}"));

      controller.search(request, response);

      assertEquals(500, response.getStatus());
      assertTrue(response.getContentAsString().contains("Document search failed"));
   }

   // A doc-search body is a short question; anything larger is a caller bug or an attack, and
   // there is no reason to relay it upstream to find that out.
   @Test
   void rejectsAnOversizedBodyBeforeCallingTheAssistant() throws Exception {
      String huge = "{\"query\":\"" + "x".repeat(70_000) + "\"}";
      setBody(request, huge);

      controller.search(request, response);

      assertEquals(413, response.getStatus());
      verifyNoInteractions(gateway);
   }

   @Test
   void returns503NamingBothPropertiesWhenNoAssistantIsConfigured() throws Exception {
      setBody(request, "{\"query\":\"q\"}");
      configureAssistant(null);

      controller.search(request, response);

      assertEquals(503, response.getStatus());
      assertTrue(response.getContentAsString().contains("chat.app.internal.url"));
      assertTrue(response.getContentAsString().contains("chat.app.server.url"));
      verifyNoInteractions(gateway);
   }

   @Test
   void fallsBackToTheBrowserFacingUrlInDirectMode() throws Exception {
      setBody(request, "{\"query\":\"q\"}");
      sreeEnv.when(() -> SreeEnv.getProperty("chat.app.internal.url")).thenReturn("");
      sreeEnv.when(() -> SreeEnv.getProperty("chat.app.server.url"))
         .thenReturn("https://direct.example.com");
      when(gateway.post(any(), any(), any(), any()))
         .thenReturn(new AssistantDocSearchGateway.Response(200, "{}"));

      controller.search(request, response);

      verify(gateway).post(eq("https://direct.example.com"), any(), any(), any());
   }

   // A bare 404 would read as "no such StyleBI route" and send the operator to the wrong layer.
   @Test
   void mapsAssistant404ToAnUpgradeMessage() throws Exception {
      setBody(request, "{\"query\":\"q\"}");
      when(gateway.post(any(), any(), any(), any()))
         .thenReturn(new AssistantDocSearchGateway.Response(404, "Cannot POST /api/doc-search"));

      controller.search(request, response);

      assertEquals(502, response.getStatus());
      assertTrue(response.getContentAsString().toLowerCase().contains("upgrade"));
      assertFalse(response.getContentAsString().contains("Cannot POST"));
   }

   @Test
   void returns502WhenTheAssistantDoesNotRespond() throws Exception {
      setBody(request, "{\"query\":\"q\"}");
      when(gateway.post(any(), any(), any(), any()))
         .thenThrow(new IOException("Connection refused"));

      controller.search(request, response);

      assertEquals(502, response.getStatus());
      assertTrue(response.getContentAsString().contains("did not respond"));
   }

   @Test
   void toleratesAMissingAuthorizationHeader() throws Exception {
      MockHttpServletRequest anonymous = new MockHttpServletRequest();
      setBody(anonymous, "{\"query\":\"q\"}");
      when(gateway.post(any(), any(), any(), any()))
         .thenReturn(new AssistantDocSearchGateway.Response(200, "{}"));

      controller.search(anonymous, response);

      verify(gateway).post(any(), any(), any(), isNull());
   }
}
