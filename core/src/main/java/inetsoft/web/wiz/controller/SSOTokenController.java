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
import inetsoft.web.assistant.AIAssistantController;
import inetsoft.web.viewsheet.service.LinkUriArgumentResolver;
import inetsoft.web.wiz.security.SSOTokenService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.*;

import static inetsoft.web.wiz.controller.WizPortalController.WIZ_SERVICE_URL;

/**
 * Controller for SSO token endpoints.
 * Provides JWT token issuance via auto-submitting HTML form and public key distribution.
 */
@Controller
public class SSOTokenController {
   @Autowired
   public SSOTokenController(SSOTokenService ssoTokenService) {
      this.ssoTokenService = ssoTokenService;
   }

   /**
    * SSO authorization endpoint.
    * Generates an RS256-signed JWT and returns an auto-submitting HTML form
    * that POSTs the token to the chat-app's callback endpoint.
    *
    * If the user is not authenticated, they will be redirected to the login page
    * by the security filters, and after login will be redirected back here.
    *
    * @param callback the chat-app callback URL to POST the token to
    * @param request the HTTP request
    * @param response the HTTP response
    * @return HTML page with auto-submitting form
    */
   @GetMapping("/sso/authorize")
   public void authorize(
      @RequestParam("callback") String callback,
      @RequestParam(value = "redirect_url", required = false) String redirectUrl,
      HttpServletRequest request,
      HttpServletResponse response) throws IOException
   {
      Principal principal = request.getUserPrincipal();

      // If principal is null, the security filter should have redirected to login.
      // This is a fallback in case it didn't.
      if(principal == null) {
         String currentUrl = LinkUriArgumentResolver.transformUri(request);
         String queryString = request.getQueryString();

         if(queryString != null) {
            currentUrl = currentUrl + "?" + queryString;
         }

         String loginUrl = LinkUriArgumentResolver.getLinkUri(request) + "login.html?requestedUrl=" +
            URLEncoder.encode(currentUrl, StandardCharsets.UTF_8);
         response.sendRedirect(loginUrl);
         return;
      }

      // Validate the callback URL against the allowlist
      if(!isCallbackAllowed(callback, request)) {
         response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
         response.setContentType(MediaType.TEXT_PLAIN_VALUE);
         response.getWriter().write("Invalid callback URL");
         return;
      }

      // Get the StyleBI server URL to use as the JWT issuer
      String styleBIUrl = LinkUriArgumentResolver.getLinkUri(request);

      // Generate the SSO token with the StyleBI URL as issuer
      String token = ssoTokenService.createSSOToken(principal, styleBIUrl);

      // Embed the CSRF token only for proxy-mode callbacks that POST back to StyleBI's own
      // /api/assistant/proxy/** path — the CSRF filter requires it for those POSTs.
      // Direct-mode callbacks target the external assistant server, which ignores the token;
      // forwarding the session CSRF credential to a third-party is unnecessary and undesirable.
      String normalizedStyleBIUrl = styleBIUrl.endsWith("/")
         ? styleBIUrl.substring(0, styleBIUrl.length() - 1) : styleBIUrl;
      boolean proxyCallback = callback.toLowerCase().startsWith(
         (normalizedStyleBIUrl + AIAssistantController.PROXY_PATH_PREFIX).toLowerCase());

      String csrfToken = null;

      if(proxyCallback) {
         Cookie[] cookies = request.getCookies();

         if(cookies != null) {
            for(Cookie cookie : cookies) {
               if("XSRF-TOKEN".equals(cookie.getName())) {
                  csrfToken = cookie.getValue();
                  break;
               }
            }
         }
      }

      String html = buildAutoSubmitForm(callback, token, redirectUrl, csrfToken);

      response.setStatus(HttpServletResponse.SC_OK);
      response.setContentType(MediaType.TEXT_HTML_VALUE);
      response.setHeader(HttpHeaders.CACHE_CONTROL, CacheControl.noStore().getHeaderValue());
      response.getWriter().write(html);
   }

   /**
    * JWKS endpoint.
    * Returns the public key for JWT verification in JWKS format.
    * This endpoint is public (no authentication required).
    *
    * @return JWKS JSON document
    */
   @GetMapping(value = "/sso/jwks", produces = MediaType.APPLICATION_JSON_VALUE)
   @ResponseBody
   public Map<String, Object> jwks() {
      return ssoTokenService.getJWKS();
   }

   /**
    * Validates the callback URL against the allowlist.
    * Accepts callbacks under {@code {chat.app.server.url}} (direct) or under
    * {@code {styleBIUrl}/api/assistant/proxy} (proxy mode).
    */
   private boolean isCallbackAllowed(String callback, HttpServletRequest request) {
      if(callback == null || callback.isEmpty()) {
         return false;
      }

      // Always allow callbacks back to StyleBI's own origin (same scheme+host+port+context-path).
      String styleBIBase = LinkUriArgumentResolver.getLinkUri(request);

      if(!styleBIBase.endsWith("/")) {
         styleBIBase = styleBIBase + "/";
      }

      if(callback.toLowerCase().startsWith(styleBIBase.toLowerCase())) {
         return true;
      }

      List<String> allowedCallbacks = getAllowedCallbacks();

      // Check if callback starts with the allowed prefix
      return allowedCallbacks.stream()
         .anyMatch(allowed -> callback.toLowerCase().startsWith(allowed.toLowerCase()));
   }

   /**
    * Gets the allowed callback URL prefix from the assistant.base.url configuration.
    */
   private List<String> getAllowedCallbacks() {
      List<String> list = new ArrayList<>();
      String assistantCallBack = getAllowedCallback(
         SreeEnv.getProperty(AIAssistantController.CHAT_APP_SERVER_URL));

      if(assistantCallBack != null) {
         list.add(assistantCallBack.toLowerCase());
      }

      String wizCallBack = getAllowedCallback(SreeEnv.getProperty(WIZ_SERVICE_URL));

      if(wizCallBack != null) {
         list.add(wizCallBack.toLowerCase());
      }

      return list;
   }

   private String getAllowedCallback(String baseUrl) {
      if(baseUrl == null || baseUrl.trim().isEmpty()) {
         return null;
      }

      baseUrl = baseUrl.trim();

      if(baseUrl.endsWith("/")) {
         baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
      }

      return baseUrl + SSO_CALLBACK_PATH;
   }


   /**
    * Builds an HTML page with an auto-submitting form that POSTs the JWT to the callback URL.
    */
   private String buildAutoSubmitForm(String callback, String token, String redirectUrl,
                                      String csrfToken)
   {
      String escapedCallback = HtmlUtils.htmlEscape(callback);
      String escapedToken = HtmlUtils.htmlEscape(token);
      String redirectUrlInput = "";

      if(redirectUrl != null && !redirectUrl.isEmpty()) {
         String escapedRedirectUrl = HtmlUtils.htmlEscape(redirectUrl);
         redirectUrlInput = "<input type=\"hidden\" name=\"redirect_url\" value=\"" + escapedRedirectUrl + "\" />";
      }

      String csrfInput = "";

      if(csrfToken != null && !csrfToken.isEmpty()) {
         csrfInput = "<input type=\"hidden\" name=\"_csrf\" value=\"" + HtmlUtils.htmlEscape(csrfToken) + "\" />";
      }

      return """
         <!DOCTYPE html>
         <html>
         <head>
            <meta charset="UTF-8">
            <title>Authorize StyleBI Access</title>
            <style>
               * { box-sizing: border-box; margin: 0; padding: 0; }
               body {
                  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                  background: #f5f5f5;
                  display: flex;
                  align-items: center;
                  justify-content: center;
                  min-height: 100vh;
               }
               .card {
                  background: #fff;
                  border-radius: 12px;
                  box-shadow: 0 4px 24px rgba(0,0,0,0.10);
                  padding: 48px 40px;
                  max-width: 420px;
                  width: 100%%;
                  text-align: center;
               }
               .logo { font-size: 40px; margin-bottom: 16px; }
               h1 { font-size: 22px; font-weight: 600; color: #1a1a1a; margin-bottom: 8px; }
               p { font-size: 14px; color: #666; margin-bottom: 32px; line-height: 1.5; }
               .btn {
                  display: inline-block;
                  background: #ed711c;
                  color: #fff;
                  border: none;
                  border-radius: 8px;
                  padding: 14px 40px;
                  font-size: 16px;
                  font-weight: 600;
                  cursor: pointer;
                  width: 100%%;
                  transition: background 0.2s;
               }
               .btn:hover { background: #d25f11; }
               .hint { margin-top: 20px; font-size: 12px; color: #999; }
            </style>
         </head>
         <body>
            <div class="card">
               <div class="logo"><svg xmlns="http://www.w3.org/2000/svg" width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="#ed711c" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg></div>
               <h1>Authorize StyleBI Access</h1>
               <p>An AI agent is requesting access to your StyleBI session.<br>
                  Click <strong>Authorize</strong> to connect, or close this tab to cancel.</p>
               <form method="POST" action="%s">
                  <input type="hidden" name="token" value="%s" />
                  %s
                  %s
                  <button type="submit" class="btn">Authorize</button>
               </form>
               <p class="hint">You can copy this page's URL into another browser or incognito window before clicking.</p>
            </div>
         </body>
         </html>
         """.formatted(escapedCallback, escapedToken, redirectUrlInput, csrfInput);
   }

   private final SSOTokenService ssoTokenService;
   private static final String SSO_CALLBACK_PATH = "/api/wiz/auth/callback";
}
