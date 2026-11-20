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

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The deny-by-default gate for viewer-mode sessions (Redmine #80547).
 *
 * <p>Every {@code /api/wiz/v1/agent/**} route that carries a {@code {sessionToken}} in its path
 * passes through here after Spring has matched its handler. If the token names a live session of
 * the calling agent and that session is a viewer session ({@link ViewerModePolicy#isViewer}), only
 * the routes on {@link ViewerModePolicy#ALLOWED} get through; anything else is answered with
 * HTTP 403 {@code {"error": ..., "errorCode": "VIEWER_MODE"}} and the controller is never
 * invoked. So an endpoint someone adds later is refused for viewer sessions until it is listed.
 *
 * <p>Passes through, leaving the controller's own error authoritative: no principal, a token
 * that is unknown, expired or owned by someone else, and routes with no token in the path
 * ({@code join}, {@code pairing/peek}, {@code script/signature}; the three openers whose token
 * is in the body are guarded in {@code SheetOpenService}). It adds no feature-flag logic and
 * never opens or refreshes a session ({@link SheetSessionService#peek} does not extend the TTL).
 *
 * <p>Not a servlet filter, so it runs after handler mapping and can use the matched pattern and
 * path variables instead of re-parsing URLs; and it never sees STOMP {@code @MessageMapping}
 * traffic, so the browser's own mint / rebind / viewer-detach stay ungated.
 */
@Component
public class ViewerModeInterceptor implements HandlerInterceptor {
   private static final Logger LOG = LoggerFactory.getLogger(ViewerModeInterceptor.class);
   private static final ObjectMapper MAPPER = new ObjectMapper();

   @Autowired
   public ViewerModeInterceptor(SheetSessionService sessions, SheetRuntimeAccess runtimeAccess) {
      this.sessions = sessions;
      this.runtimeAccess = runtimeAccess;
   }

   @Override
   public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                            Object handler) throws Exception
   {
      Object vars = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
      Object tokenValue = vars instanceof Map<?, ?> m ? m.get(ViewerModePolicy.TOKEN_VARIABLE)
                                                      : null;
      String token = tokenValue instanceof String s ? s : null;

      if(token == null) {
         return true;
      }

      Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);

      String refusal = ViewerModePolicy.REFUSAL;

      if(pattern != null && ViewerModePolicy.isAllowed(request.getMethod(), pattern.toString())) {
         // On the allow-list: a viewer may call it and a design session always may -- except for
         // a restricted flag (raw=true returns the unfiltered dialog model), which only a design
         // session may use.
         if(!usesRestrictedFlag(request, pattern.toString())) {
            return true;
         }

         refusal = ViewerModePolicy.RAW_REFUSAL;
      }

      Principal user = request.getUserPrincipal();

      if(user == null) {
         return true;
      }

      JoinSession session = sessions.peek(token, PairingUtil.identityKey(user));

      if(session == null || !ViewerModePolicy.isViewer(session, runtimeAccess)) {
         return true;
      }

      LOG.info("Viewer-mode session refused (method={}, route={}, runtimeId={}, agent={})",
               request.getMethod(), pattern, session.runtimeId(), PairingUtil.label(user));
      Map<String, String> body = new LinkedHashMap<>();
      body.put("error", refusal);
      body.put("errorCode", ViewerModePolicy.ERROR_CODE);
      response.setStatus(HttpStatus.FORBIDDEN.value());
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      response.setCharacterEncoding(StandardCharsets.UTF_8.name());
      response.getWriter().write(MAPPER.writeValueAsString(body));
      response.getWriter().flush();
      return false;
   }

   /** Whether the request sets, to a value that binds to true, a flag this route restricts. */
   private static boolean usesRestrictedFlag(HttpServletRequest request, String pattern) {
      java.util.Set<String> flags = ViewerModePolicy.RESTRICTED_FLAGS.get(
         new ViewerModePolicy.Route(
            "HEAD".equalsIgnoreCase(request.getMethod()) ? "GET"
               : request.getMethod().toUpperCase(java.util.Locale.ROOT), pattern));

      if(flags == null) {
         return false;
      }

      for(String flag : flags) {
         if(ViewerModePolicy.bindsToTrue(request.getParameterValues(flag))) {
            return true;
         }
      }

      return false;
   }

   private final SheetSessionService sessions;
   private final SheetRuntimeAccess runtimeAccess;
}
