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
package inetsoft.web.wiz.pairing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.sree.security.IdentityID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;

import java.lang.reflect.Method;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The deny-by-default viewer gate (A3 A4 A5 A6 A12 C1 C2 C6). The controller is never invoked on a
 * refusal: {@code preHandle} returning false is what stops Spring from calling it.
 */
@Tag("core")
class ViewerModeInterceptorTest {
   private static final String BASE = "/api/wiz/v1/agent/";
   private static final String VS = BASE + "viewsheet/{sessionToken}";
   private static final String ALICE = new IdentityID("alice", "host-org").convertToKey();
   private static final Principal ALICE_P = TestPrincipals.user("alice", "host-org");
   private static final ObjectMapper JSON = new ObjectMapper();

   private SheetSessionService sessions;
   private SheetRuntimeAccess runtimeAccess;
   private ViewerModeInterceptor interceptor;

   @BeforeEach
   void setUp() {
      sessions = new SheetSessionService();
      runtimeAccess = mock(SheetRuntimeAccess.class);
      interceptor = new ViewerModeInterceptor(sessions, runtimeAccess);
   }

   /** Denied for a viewer, one or more per class: design write, interact, design read, openers. */
   static Stream<Arguments> deniedRoutes() {
      return Stream.of(
         Arguments.of("POST", VS + "/edit"),
         Arguments.of("POST", VS + "/properties"),
         Arguments.of("POST", VS + "/vs-properties"),
         Arguments.of("POST", VS + "/selection"),
         Arguments.of("POST", VS + "/selection/clear"),
         Arguments.of("POST", VS + "/input/value"),
         Arguments.of("POST", VS + "/submit/click"),
         Arguments.of("POST", VS + "/bookmarks/goto"),
         Arguments.of("POST", VS + "/parameters"),
         Arguments.of("POST", VS + "/assembly/max-mode"),
         Arguments.of("POST", VS + "/form-table/set-cell"),
         Arguments.of("POST", VS + "/save"),
         Arguments.of("POST", VS + "/undo"),
         Arguments.of("POST", VS + "/open-base-worksheet"),
         Arguments.of("GET", VS + "/hyperlink"),
         Arguments.of("GET", VS + "/condition"),
         Arguments.of("GET", BASE + "binding/{sessionToken}/binding"),
         Arguments.of("POST", BASE + "binding/{sessionToken}/chart/shelf"),
         Arguments.of("GET", BASE + "script/{sessionToken}/script"),
         Arguments.of("POST", BASE + "worksheet/{sessionToken}/script"),
         Arguments.of("POST", BASE + "sheet/{sessionToken}/something-new"));
   }

   static Stream<Arguments> allowedRoutes() {
      return ViewerModePolicy.ALLOWED.stream()
         .map(r -> Arguments.of(r.method(), r.pattern()));
   }

   @ParameterizedTest
   @MethodSource("deniedRoutes")
   void aViewerSessionIsRefusedEveryRouteNotOnTheAllowList(String method, String pattern) throws Exception {
      String token = open(JoinSession.ConnectionMode.VIEWER).sessionToken();
      MockHttpServletResponse response = new MockHttpServletResponse();

      assertFalse(call(method, pattern, token, ALICE_P, response),
                  "the controller must not run for " + method + " " + pattern);

      assertEquals(403, response.getStatus());
      JsonNode body = JSON.readTree(response.getContentAsString());
      assertEquals("VIEWER_MODE", body.get("errorCode").asText());
      assertEquals(ViewerModePolicy.REFUSAL, body.get("error").asText());
      assertTrue(response.getContentType().startsWith("application/json"));
   }

   @ParameterizedTest
   @MethodSource("allowedRoutes")
   void aViewerSessionMayCallEveryAllowListedRoute(String method, String pattern) throws Exception {
      String token = open(JoinSession.ConnectionMode.VIEWER).sessionToken();
      MockHttpServletResponse response = new MockHttpServletResponse();

      assertTrue(call(method, pattern, token, ALICE_P, response));
      assertEquals(200, response.getStatus());
   }

   @ParameterizedTest
   @MethodSource("deniedRoutes")
   void aDesignSessionPassesEveryRoute(String method, String pattern) throws Exception {
      String token = open(JoinSession.ConnectionMode.PAIRED).sessionToken();
      when(runtimeAccess.getRuntimeSheetDirect(any(), any())).thenReturn(mock(RuntimeViewsheet.class));

      assertTrue(call(method, pattern, token, ALICE_P, new MockHttpServletResponse()));
   }

   /** Lead ruling: the plugin's liveness probe is allowed; every sibling under /script/{t}/ is not. */
   @Test
   void theScriptSessionProbeIsAllowedButItsSiblingsAreStillRefused() throws Exception {
      String token = open(JoinSession.ConnectionMode.VIEWER).sessionToken();
      String script = BASE + "script/{sessionToken}";

      assertTrue(call("GET", script + "/session", token, ALICE_P, new MockHttpServletResponse()));
      assertFalse(call("POST", script + "/session", token, ALICE_P, new MockHttpServletResponse()));

      for(String[] sibling : new String[][] {
         { "GET", "/script" }, { "POST", "/script" }, { "POST", "/enable" },
         { "POST", "/execute" }, { "POST", "/execute-live" }, { "POST", "/save" },
         { "GET", "/targets" }, { "GET", "/context" }, { "GET", "/image" },
         { "GET", "/pane-context" }, { "POST", "/detach" } })
      {
         MockHttpServletResponse response = new MockHttpServletResponse();
         assertFalse(call(sibling[0], script + sibling[1], token, ALICE_P, response),
                     sibling[0] + " " + sibling[1]);
         assertEquals(403, response.getStatus());
      }
   }

   @Test
   void theSamePathIsJudgedByMethod() throws Exception {
      String token = open(JoinSession.ConnectionMode.VIEWER).sessionToken();

      assertTrue(call("GET", VS + "/properties", token, ALICE_P, new MockHttpServletResponse()));
      assertTrue(call("HEAD", VS + "/properties", token, ALICE_P, new MockHttpServletResponse()));
      assertFalse(call("POST", VS + "/properties", token, ALICE_P, new MockHttpServletResponse()));
      assertFalse(call("PUT", VS + "/model", token, ALICE_P, new MockHttpServletResponse()));
   }

   /** C1: a stamp of PAIRED does not make a live viewer runtime a design session. */
   @Test
   void aSessionStampedPairedWhoseLiveRuntimeIsAViewerIsStillRefused() throws Exception {
      JoinSession s = open(JoinSession.ConnectionMode.PAIRED);
      RuntimeViewsheet viewer = mock(RuntimeViewsheet.class);
      when(viewer.isViewer()).thenReturn(true);
      when(runtimeAccess.getRuntimeSheetDirect(SheetType.VIEWSHEET, "vs-1")).thenReturn(viewer);
      MockHttpServletResponse response = new MockHttpServletResponse();

      assertFalse(call("POST", VS + "/edit", s.sessionToken(), ALICE_P, response));
      assertEquals(403, response.getStatus());
   }

   @Test
   void aPairedSessionOnAComposerRuntimePasses() throws Exception {
      JoinSession s = open(JoinSession.ConnectionMode.PAIRED);
      RuntimeViewsheet composer = mock(RuntimeViewsheet.class);
      when(composer.isViewer()).thenReturn(false);
      when(runtimeAccess.getRuntimeSheetDirect(SheetType.VIEWSHEET, "vs-1")).thenReturn(composer);

      assertTrue(call("POST", VS + "/edit", s.sessionToken(), ALICE_P,
                      new MockHttpServletResponse()));
   }

   @Test
   void aWorksheetSessionIsNeverAViewer() throws Exception {
      JoinSession s = sessions.open("ws-1", ALICE, SheetType.WORKSHEET, "sock-1", "alice", null);
      when(runtimeAccess.getRuntimeSheetDirect(any(), any())).thenReturn(mock(RuntimeWorksheet.class));

      assertTrue(call("POST", BASE + "worksheet/{sessionToken}/script", s.sessionToken(), ALICE_P,
                      new MockHttpServletResponse()));
   }

   @Test
   void unknownExpiredForeignAndAnonymousCallsPassThroughForTheControllerToAnswer()
      throws Exception
   {
      String token = open(JoinSession.ConnectionMode.VIEWER).sessionToken();
      Principal bob = TestPrincipals.user("bob", "host-org");

      assertTrue(call("POST", VS + "/edit", "NOSUCHTOKEN", ALICE_P, new MockHttpServletResponse()));
      assertTrue(call("POST", VS + "/edit", token, bob, new MockHttpServletResponse()));
      assertTrue(call("POST", VS + "/edit", token, null, new MockHttpServletResponse()));
   }

   @Test
   void aRouteWithNoTokenIsNotGated() throws Exception {
      MockHttpServletRequest request = new MockHttpServletRequest("POST", "/x");
      request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of());
      request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, BASE + "viewsheet/join");
      request.setUserPrincipal(ALICE_P);

      assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
   }

   @Test
   void aRefusalDoesNotExtendTheSessionLifetimeOrChangeIt() throws Exception {
      JoinSession s = open(JoinSession.ConnectionMode.VIEWER);

      call("POST", VS + "/edit", s.sessionToken(), ALICE_P, new MockHttpServletResponse());

      assertEquals(s.lastAccess(), sessions.peek(s.sessionToken(), ALICE).lastAccess());
      assertEquals(JoinSession.ConnectionMode.VIEWER,
                   sessions.peek(s.sessionToken(), ALICE).connectionMode());
   }

   @Test
   void theInterceptorIsRegisteredForTheAgentApiOnly() throws Exception {
      InterceptorRegistry registry = new InterceptorRegistry();
      new ViewerModeWebConfig(interceptor).addInterceptors(registry);
      Method m = InterceptorRegistry.class.getDeclaredMethod("getInterceptors");
      m.setAccessible(true);

      List<?> registered = (List<?>) m.invoke(registry);
      assertEquals(1, registered.size());
      org.springframework.web.servlet.handler.MappedInterceptor mapped =
         (org.springframework.web.servlet.handler.MappedInterceptor) registered.get(0);
      assertSame(interceptor, mapped.getInterceptor());

      assertTrue(mapped.matches(pathRequest("/api/wiz/v1/agent/viewsheet/t/properties")));
      assertTrue(mapped.matches(pathRequest("/api/wiz/v1/agent/sheet/t/keep-alive")));
      assertFalse(mapped.matches(pathRequest("/api/wiz/v1/other")));
      assertFalse(mapped.matches(pathRequest("/api/wiz/v1/agentx/viewsheet/t/properties")));
      assertFalse(mapped.matches(pathRequest("/api/wiz/pairing/mint")));
   }

   private static MockHttpServletRequest pathRequest(String path) {
      MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
      org.springframework.web.util.ServletRequestPathUtils.parseAndCache(request);
      return request;
   }

   private JoinSession open(JoinSession.ConnectionMode mode) {
      return sessions.open("vs-1", ALICE, SheetType.VIEWSHEET, "sock-1", "alice", null, mode);
   }

   private boolean call(String method, String pattern, String token, Principal user,
                        MockHttpServletResponse response) throws Exception
   {
      MockHttpServletRequest request = new MockHttpServletRequest(method, "/x");
      request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE,
                           Map.of("sessionToken", token));
      request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, pattern);
      request.setUserPrincipal(user);
      return interceptor.preHandle(request, response, new Object());
   }
}
