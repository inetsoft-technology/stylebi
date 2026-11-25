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
package inetsoft.web.wiz.controller;

import inetsoft.sree.SreeEnv;
import inetsoft.web.portal.WizPortalEntry;
import inetsoft.web.assistant.AIAssistantController;
import inetsoft.web.wiz.security.SSOTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link SSOTokenController}'s own logic — callback allowlist matching, the
 * unauthenticated-principal login redirect, and HTML escaping in the auto-submit form. JWT
 * claim encoding is covered separately by {@code SSOTokenServiceTest}.
 */
@Tag("core")
class SSOTokenControllerTest {

   private SSOTokenController controller;
   private MockedStatic<SreeEnv> sreeEnvMock;

   @BeforeEach
   void setUp() {
      controller = new SSOTokenController(mock(SSOTokenService.class));

      sreeEnvMock = mockStatic(SreeEnv.class);
      sreeEnvMock.when(() -> SreeEnv.getProperty(AIAssistantController.CHAT_APP_SERVER_URL))
         .thenReturn("https://chat.example.com");
      sreeEnvMock.when(() -> SreeEnv.getProperty(WizPortalEntry.WIZ_SERVICE_URL))
         .thenReturn("https://wiz.example.com");
   }

   @AfterEach
   void tearDown() {
      sreeEnvMock.close();
   }

   private HttpServletRequest mockRequest() {
      HttpServletRequest request = mock(HttpServletRequest.class);
      when(request.getScheme()).thenReturn("https");
      when(request.getServerName()).thenReturn("stylebi.example.com");
      when(request.getServerPort()).thenReturn(443);
      when(request.getHeader("X-Inetsoft-Remote-Uri")).thenReturn(null);
      when(request.getContextPath()).thenReturn("");
      return request;
   }

   private boolean isCallbackAllowed(String callback, HttpServletRequest request) {
      return ReflectionTestUtils.invokeMethod(controller, "isCallbackAllowed", callback, request);
   }

   @Test
   void isCallbackAllowed_sameOrigin_allowed() {
      assertTrue(isCallbackAllowed(
         "https://stylebi.example.com/api/assistant/proxy/foo", mockRequest()));
   }

   @Test
   void isCallbackAllowed_chatAppPrefix_allowed() {
      assertTrue(isCallbackAllowed(
         "https://chat.example.com/api/wiz/auth/callback", mockRequest()));
   }

   @Test
   void isCallbackAllowed_wizServicePrefix_withQueryString_allowed() {
      assertTrue(isCallbackAllowed(
         "https://wiz.example.com/api/wiz/auth/callback?nonce=abc", mockRequest()));
   }

   @Test
   void isCallbackAllowed_pathBoundaryGap_rejected() {
      // Regression test for the boundary gap flagged in review: a longer, unrelated path must
      // not match just because it shares the callback path as a literal string prefix.
      assertFalse(isCallbackAllowed(
         "https://chat.example.com/api/wiz/auth/callback-evil", mockRequest()));
   }

   @Test
   void isCallbackAllowed_unrelatedOrigin_rejected() {
      assertFalse(isCallbackAllowed("https://attacker.example.com/steal", mockRequest()));
   }

   @Test
   void isCallbackAllowed_nullOrEmpty_rejected() {
      assertFalse(isCallbackAllowed(null, mockRequest()));
      assertFalse(isCallbackAllowed("", mockRequest()));
   }

   @Test
   void authorize_nullPrincipal_redirectsToLogin() throws Exception {
      HttpServletRequest request = mockRequest();
      when(request.getUserPrincipal()).thenReturn(null);
      when(request.getServletPath()).thenReturn("/sso/authorize");
      when(request.getPathInfo()).thenReturn(null);
      when(request.getQueryString())
         .thenReturn("callback=https://chat.example.com/api/wiz/auth/callback");

      HttpServletResponse response = mock(HttpServletResponse.class);

      controller.authorize(
         "https://chat.example.com/api/wiz/auth/callback", null, request, response);

      ArgumentCaptor<String> redirectCaptor = ArgumentCaptor.forClass(String.class);
      verify(response).sendRedirect(redirectCaptor.capture());
      assertTrue(redirectCaptor.getValue()
         .startsWith("https://stylebi.example.com/login.html?requestedUrl="),
         "Unauthenticated requests must be redirected to login with the original URL preserved");
   }

   private boolean isFirstPartyWizCallback(String callback) {
      return ReflectionTestUtils.invokeMethod(controller, "isFirstPartyWizCallback", callback);
   }

   @Test
   void isFirstPartyWizCallback_exactWizCallback_true() {
      assertTrue(isFirstPartyWizCallback("https://wiz.example.com/api/wiz/auth/callback"));
      assertTrue(isFirstPartyWizCallback("HTTPS://WIZ.EXAMPLE.COM/api/wiz/auth/callback"));
   }

   @Test
   void isFirstPartyWizCallback_nonceQuery_false() {
      // The OAuth flow binds a nonce here and hands the token to a third party's client, so it
      // must keep the explicit consent page.
      assertFalse(isFirstPartyWizCallback(
         "https://wiz.example.com/api/wiz/auth/callback?nonce=abc"));
   }

   @Test
   void isFirstPartyWizCallback_otherAllowedCallbacks_false() {
      assertFalse(isFirstPartyWizCallback("https://chat.example.com/api/wiz/auth/callback"));
      assertFalse(isFirstPartyWizCallback("https://stylebi.example.com/api/assistant/proxy/x"));
      assertFalse(isFirstPartyWizCallback(null));
   }

   @Test
   void isFirstPartyWizCallback_noWizServiceConfigured_false() {
      sreeEnvMock.when(() -> SreeEnv.getProperty(WizPortalEntry.WIZ_SERVICE_URL))
         .thenReturn(null);
      assertFalse(isFirstPartyWizCallback("https://wiz.example.com/api/wiz/auth/callback"));
   }

   @Test
   void buildAutoSubmitForm_silent_submitsWithoutConsentPage() {
      String html = ReflectionTestUtils.invokeMethod(controller, "buildAutoSubmitForm",
         "https://wiz.example.com/api/wiz/auth/callback", "tok", "/chat", null, true);

      assertTrue(html.contains("<script>document.getElementById(\"sso-form\").submit();</script>"));
      assertTrue(html.contains("name=\"redirect_url\" value=\"/chat\""));
      assertFalse(html.contains("An AI agent is requesting access"));
   }

   @Test
   void buildAutoSubmitForm_consent_hasNoAutoSubmitScript() {
      String html = ReflectionTestUtils.invokeMethod(controller, "buildAutoSubmitForm",
         "https://chat.example.com/api/wiz/auth/callback?nonce=abc", "tok", null, null, false);

      assertFalse(html.contains("<script>"));
      assertTrue(html.contains("An AI agent is requesting access"));
   }

   @Test
   void buildAutoSubmitForm_silent_escapesHtmlInAllFields() {
      String html = ReflectionTestUtils.invokeMethod(controller, "buildAutoSubmitForm",
         "https://wiz.example.com/cb\"><b>x</b>", "token\"><b>y</b>", "/r\"><b>z</b>",
         "csrf\"><b>w</b>", true);

      assertFalse(html.contains("<b>"), "Interpolated values must be HTML-escaped");
      assertTrue(html.contains("&lt;b&gt;"));
   }

   @Test
   void buildAutoSubmitForm_escapesHtmlInAllFields() {
      String html = ReflectionTestUtils.invokeMethod(controller, "buildAutoSubmitForm",
         "https://chat.example.com/cb\"><script>alert(1)</script>",
         "token\"><script>alert(2)</script>",
         "https://example.com/redirect\"><script>alert(3)</script>",
         "csrf\"><script>alert(4)</script>");

      assertNotNull(html);
      assertFalse(html.contains("<script>"), "Raw <script> must never appear unescaped");
      assertTrue(html.contains("&lt;script&gt;"), "Interpolated values must be HTML-escaped");
   }
}
