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
package inetsoft.web.wiz.security;

import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import inetsoft.sree.ClientInfo;
import inetsoft.sree.RepletRepository;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.*;
import inetsoft.util.*;
import inetsoft.web.security.AbstractSecurityFilter;
import inetsoft.web.security.AuthenticatedRequest;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import java.io.IOException;
import java.security.KeyPair;
import java.security.interfaces.RSAPublicKey;
import java.text.ParseException;
import java.util.*;

/**
 * Filter that authenticates requests from WIZ Service using JWT tokens.
 * <p>
 * This filter intercepts requests to /api/wiz/** endpoints
 * and validates the JWT token provided in the Authorization header. The token must
 * be signed by StyleBI Server using the SSO RSA key pair.
 * <p>
 * Authentication flow:
 * 1. WIZ Service receives a request from StyleBI Server with a JWT token
 * 2. WIZ Service makes a callback to StyleBI Server with the same token
 * 3. This filter validates the token signature, expiration, and audience
 * 4. If valid, extracts user identity and sets up the security context
 */
@Component
public class WizServiceAuthenticationFilter extends AbstractSecurityFilter {
   @PostConstruct
   public void initializeKeyPair() {
      try {
         ssoKeyPair = PasswordEncryption.newInstance().getSSOKeyPair();
      }
      catch(IOException e) {
         LOG.error("Failed to load SSO key pair for WIZ service authentication", e);
      }
   }

   @Override
   public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException
   {
      HttpServletRequest httpRequest = (HttpServletRequest) request;
      HttpServletResponse httpResponse = (HttpServletResponse) response;

      // Only process requests to WIZ endpoints
      if(!isWizApiRequest(httpRequest)) {
         chain.doFilter(request, response);
         return;
      }

      // Check if WIZ service authentication is enabled
      if(!isWizAuthEnabled()) {
         chain.doFilter(request, response);
         return;
      }

      // Extract and validate the JWT token
      String authHeader = httpRequest.getHeader(AUTHORIZATION_HEADER);

      if(authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
         // No token provided, let other filters handle authentication
         chain.doFilter(request, response);
         return;
      }

      String token = authHeader.substring(BEARER_PREFIX.length()).trim();

      try {
         SRPrincipal principal = validateTokenAndCreatePrincipal(token, httpRequest);

         if(principal != null) {
            // Store principal in session for compatibility with SUtil.getPrincipal()
            httpRequest.getSession(true).setAttribute(
               RepletRepository.PRINCIPAL_COOKIE, principal);

            // Set up the authenticated request
            HttpServletRequest authenticatedRequest = new AuthenticatedRequest(httpRequest, principal);
            ThreadContext.setContextPrincipal(principal);

            if(principal.getLocale() != null) {
               ThreadContext.setLocale(principal.getLocale());
            }

            try {
               chain.doFilter(authenticatedRequest, response);
            }
            finally {
               ThreadContext.setContextPrincipal(null);
               ThreadContext.setLocale(null);
            }
         }
         else {
            sendUnauthorized(httpResponse, "Invalid token");
         }
      }
      catch(WizAuthenticationException e) {
         LOG.warn("WIZ service authentication failed: {}", e.getMessage());
         sendUnauthorized(httpResponse, e.getMessage());
      }
      catch(Exception e) {
         LOG.error("Error during WIZ service authentication", e);
         sendUnauthorized(httpResponse, "Authentication error");
      }
   }

   /**
    * Checks if the request is for a WIZ API endpoint.
    */
   private boolean isWizApiRequest(HttpServletRequest request) {
      String path = request.getServletPath();

      if(request.getPathInfo() != null) {
         path += request.getPathInfo();
      }

      return pathMatcher.match(WIZ_API_PATTERN, path);
   }

   /**
    * Checks if WIZ service authentication is enabled.
    */
   private boolean isWizAuthEnabled() {
      return "true".equalsIgnoreCase(SreeEnv.getProperty(WIZ_AUTH_ENABLED_PROPERTY, "true"));
   }

   /**
    * Validates the JWT token and creates an SRPrincipal from the claims.
    *
    * @param token   the JWT token string
    * @param request the HTTP request
    * @return the authenticated principal, or null if validation fails
    * @throws WizAuthenticationException if token validation fails
    */
   private SRPrincipal validateTokenAndCreatePrincipal(String token, HttpServletRequest request)
      throws WizAuthenticationException
   {
      if(ssoKeyPair == null) {
         throw new WizAuthenticationException("SSO key pair not initialized");
      }

      SignedJWT jwt;

      try {
         jwt = SignedJWT.parse(token);
      }
      catch(ParseException e) {
         throw new WizAuthenticationException("Invalid JWT format");
      }

      // Verify signature
      try {
         RSAPublicKey publicKey = (RSAPublicKey) ssoKeyPair.getPublic();
         JWSVerifier verifier = new RSASSAVerifier(publicKey);

         if(!jwt.verify(verifier)) {
            throw new WizAuthenticationException("Invalid token signature");
         }
      }
      catch(Exception e) {
         throw new WizAuthenticationException("Signature verification failed: " + e.getMessage());
      }

      // Validate claims
      JWTClaimsSet claims;

      try {
         claims = jwt.getJWTClaimsSet();
      }
      catch(ParseException e) {
         throw new WizAuthenticationException("Failed to parse token claims");
      }

      // Check expiration
      Date expirationTime = claims.getExpirationTime();

      if(expirationTime == null || expirationTime.before(new Date())) {
         throw new WizAuthenticationException("Token has expired");
      }

      // Check audience
      List<String> audience = claims.getAudience();

      if(audience == null || !containsValidAudience(audience)) {
         throw new WizAuthenticationException("Invalid token audience");
      }

      // Extract user identity
      String subject = claims.getSubject();

      if(subject == null || subject.isEmpty()) {
         throw new WizAuthenticationException("Token missing subject");
      }

      // Get organization ID
      String orgId;

      try {
         orgId = claims.getStringClaim("organizationId");
      }
      catch(ParseException e) {
         orgId = Organization.getDefaultOrganizationID();
      }

      if(orgId == null) {
         orgId = Organization.getDefaultOrganizationID();
      }

      // Get roles
      IdentityID[] roles = extractRoles(claims);

      // Get groups
      String[] groups = extractGroups(claims);

      // Create the principal
      IdentityID userID = IdentityID.getIdentityIDFromKey(subject);

      if(userID == null) {
         userID = new IdentityID(subject, orgId);
      }

      // Extract secureId from token (preserves the original session identity)
      long secureId;

      try {
         Long tokenSecureId = claims.getLongClaim("secureId");
         secureId = tokenSecureId != null ? tokenSecureId : Tool.getSecureRandom().nextLong();
      }
      catch(ParseException e) {
         secureId = Tool.getSecureRandom().nextLong();
      }

      // Extract ClientInfo fields from token
      String clientAddress = null;
      String clientSession = null;
      Locale clientLocale = null;
      IdentityID clientLoginUser = null;

      try {
         clientAddress = claims.getStringClaim("clientAddress");
         clientSession = claims.getStringClaim("clientSession");

         String localeStr = claims.getStringClaim("locale");

         if(localeStr != null && !localeStr.isEmpty()) {
            localeStr = localeStr.replace('-', '_');
            clientLocale = Catalog.parseLocale(localeStr);
         }

         String loginUserKey = claims.getStringClaim("clientLoginUser");

         if(loginUserKey != null && !loginUserKey.isEmpty()) {
            clientLoginUser = IdentityID.getIdentityIDFromKey(loginUserKey);
         }
      }
      catch(ParseException ignored) {
         // Use null defaults
      }

      ClientInfo clientInfo = new ClientInfo(userID, clientAddress, clientSession, clientLocale);

      if(clientLoginUser != null) {
         clientInfo.setLoginUserName(clientLoginUser);
      }

      SRPrincipal principal = new SRPrincipal(
         clientInfo,
         roles,
         groups,
         orgId,
         secureId
      );

      principal.setProperty("wiz", "true");

      if(clientLocale != null) {
         principal.setProperty("locale", clientLocale.toString());
      }

      return principal;
   }

   /**
    * Checks if the audience list contains a valid WIZ service audience.
    */
   private boolean containsValidAudience(List<String> audience) {
      for(String aud : VALID_AUDIENCES) {
         if(audience.contains(aud)) {
            return true;
         }
      }

      return false;
   }

   /**
    * Extracts roles from the JWT claims.
    */
   @SuppressWarnings("unchecked")
   private IdentityID[] extractRoles(JWTClaimsSet claims) {
      try {
         Object rolesObj = claims.getClaim("roles");

         if(rolesObj instanceof List) {
            List<String> rolesList = (List<String>) rolesObj;
            return rolesList.stream()
               .map(IdentityID::getIdentityIDFromKey)
               .filter(Objects::nonNull)
               .toArray(IdentityID[]::new);
         }
      }
      catch(Exception e) {
         LOG.debug("Failed to extract roles from token", e);
      }

      return new IdentityID[0];
   }

   /**
    * Extracts groups from the JWT claims.
    */
   @SuppressWarnings("unchecked")
   private String[] extractGroups(JWTClaimsSet claims) {
      try {
         Object groupsObj = claims.getClaim("groups");

         if(groupsObj instanceof List) {
            List<String> groupsList = (List<String>) groupsObj;
            return groupsList.toArray(new String[0]);
         }
      }
      catch(Exception e) {
         LOG.debug("Failed to extract groups from token", e);
      }

      return new String[0];
   }

   /**
    * Sends an unauthorized response.
    */
   private void sendUnauthorized(HttpServletResponse response, String message) throws IOException {
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      response.setContentType("application/json");
      response.setCharacterEncoding("UTF-8");
      // Escape special characters to prevent JSON injection
      String escaped = message.replace("\\", "\\\\").replace("\"", "\\\"");
      response.getWriter().write("{\"error\":\"" + escaped + "\"}");
   }

   private KeyPair ssoKeyPair;
   private final AntPathMatcher pathMatcher = new AntPathMatcher();

   private static final String AUTHORIZATION_HEADER = "Authorization";
   private static final String BEARER_PREFIX = "Bearer ";
   private static final String WIZ_API_PATTERN = "/api/wiz/**";
   private static final String WIZ_AUTH_ENABLED_PROPERTY = "wiz.auth.enabled";

   // Valid audiences for WIZ service tokens
   private static final List<String> VALID_AUDIENCES = Arrays.asList(
      "wiz-service",
      "chat-app",
      "stylebi-server"
   );

   private static final Logger LOG = LoggerFactory.getLogger(WizServiceAuthenticationFilter.class);

   /**
    * Exception thrown when WIZ service authentication fails.
    */
   public static class WizAuthenticationException extends Exception {
      public WizAuthenticationException(String message) {
         super(message);
      }
   }
}
