/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.sree.SreeEnv;
import inetsoft.web.admin.ai.AdminAiCallerGuard;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.ResourceHttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises {@link WizDocSearchController#search} through real Spring MVC request binding,
 * the way {@code WizDocSearchControllerTest} cannot: those tests call {@code controller.search(...)}
 * directly and so never go through an {@code HttpMessageConverter} at all. That is exactly why
 * they did not catch Redmine #75966 — {@code @RequestBody String} with a
 * {@code Content-Type: application/json} body fails at binding under this application's real
 * converter set (see {@link inetsoft.web.WebConfig#configureMessageConverters}) before the
 * controller body ever runs.
 *
 * <p>The message converters registered on the {@link MockMvc} builder below deliberately mirror
 * {@code WebConfig}'s list and order (Jackson, Resource, a {@code text/plain}-only String
 * converter, then ByteArray) rather than {@code standaloneSetup}'s permissive defaults. A
 * {@code standaloneSetup} with default converters accepts {@code @RequestBody String} for a JSON
 * body just fine — its default {@code StringHttpMessageConverter} supports every media type — so
 * a test built that way would pass against both the broken and the fixed signature and would not
 * be a real regression guard. Registering the application's actual converters reproduces the
 * failure this defect report describes.</p>
 */
@Tag("core")
@ExtendWith(MockitoExtension.class)
class WizDocSearchControllerRequestBindingTest {
   @Mock private AssistantDocSearchGateway gateway;
   private MockedStatic<AdminAiCallerGuard> guard;
   private MockedStatic<SreeEnv> sreeEnv;
   private MockMvc mvc;

   @BeforeEach
   void setUp() {
      guard = mockStatic(AdminAiCallerGuard.class, withSettings().lenient());
      sreeEnv = mockStatic(SreeEnv.class, withSettings().lenient());
      sreeEnv.when(() -> SreeEnv.getProperty("chat.app.internal.url"))
         .thenReturn("https://assistant.example.com");
      sreeEnv.when(() -> SreeEnv.getProperty("chat.app.server.url")).thenReturn(null);

      MappingJackson2HttpMessageConverter jackson =
         new MappingJackson2HttpMessageConverter(new ObjectMapper());

      ResourceHttpMessageConverter resource = new ResourceHttpMessageConverter();
      resource.setSupportedMediaTypes(List.of(
         MediaType.IMAGE_GIF, MediaType.IMAGE_JPEG, MediaType.IMAGE_PNG, MediaType.APPLICATION_PDF,
         MediaType.parseMediaType("image/svg+xml")));

      StringHttpMessageConverter text = new StringHttpMessageConverter();
      text.setSupportedMediaTypes(List.of(
         MediaType.TEXT_PLAIN, MediaType.parseMediaType("application/openmetrics-text")));

      ByteArrayHttpMessageConverter byteArray = new ByteArrayHttpMessageConverter();
      byteArray.setSupportedMediaTypes(List.of(
         MediaType.APPLICATION_OCTET_STREAM, MediaType.TEXT_PLAIN,
         MediaType.parseMediaType("application/openmetrics-text")));

      mvc = MockMvcBuilders.standaloneSetup(new WizDocSearchController(gateway))
         .setMessageConverters(jackson, resource, text, byteArray)
         .build();
   }

   @AfterEach
   void tearDown() {
      guard.close();
      sreeEnv.close();
   }

   @Test
   void bindsAJsonPostBodyAndRelaysTheGatewaysStatus() throws Exception {
      when(gateway.post(any(), any(), any(), any()))
         .thenReturn(new AssistantDocSearchGateway.Response(200, "{\"matches\":[]}"));

      mvc.perform(post("/api/wiz/v1/docs/search")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"query\":\"q\"}"))
         .andExpect(status().isOk());

      // The request must reach the gateway with the body intact — proof that binding, not just
      // routing, succeeded.
      verify(gateway).post(any(), any(), eq("{\"query\":\"q\"}"), any());
   }

   /**
    * Guards the mirror-image defect from the request-binding one above: the gateway's response
    * body is already-serialized JSON text, and under the application's real converter set
    * (Jackson registered ahead of a {@code text/plain}-only {@code StringHttpMessageConverter})
    * {@code ResponseEntity<String>.contentType(APPLICATION_JSON)} gets written by Jackson, which
    * re-serializes the {@code String} as a JSON string literal — {@code "{\"matches\":[]}"}
    * instead of {@code {"matches":[]}}. A test built on {@code standaloneSetup}'s permissive
    * default converters would not catch this, for the same reason described in the class
    * javadoc.
    */
   @Test
   void relaysTheResponseBodyAsRealJsonRatherThanADoubleEncodedString() throws Exception {
      when(gateway.post(any(), any(), any(), any()))
         .thenReturn(new AssistantDocSearchGateway.Response(200, "{\"matches\":[]}"));

      MvcResult result = mvc.perform(post("/api/wiz/v1/docs/search")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"query\":\"q\"}"))
         .andExpect(status().isOk())
         .andReturn();

      String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);

      assertFalse(body.startsWith("\""),
         "response body must be a JSON object, not a JSON string literal: " + body);
      assertFalse(body.contains("\\\""),
         "response body must not contain escaped quotes (double-encoded JSON): " + body);
      assertEquals("{\"matches\":[]}", body);
   }
}
