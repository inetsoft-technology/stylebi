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

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import inetsoft.sree.ClientInfo;
import inetsoft.sree.security.*;
import inetsoft.util.PasswordEncryption;
import inetsoft.util.Tool;
import inetsoft.web.wiz.AppDomainUtils;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;

/**
 * Service for creating RS256-signed JWT tokens for cross-app SSO.
 * This service generates tokens that can be verified by external applications
 * using the public key exposed via the JWKS endpoint.
 */
@Component
public class SSOTokenService {
   @Autowired
   public SSOTokenService(SecurityProvider securityProvider) {
      this.securityProvider = securityProvider;
   }

   @PostConstruct
   public void init() throws IOException {
      ssoKeyPair = PasswordEncryption.newInstance().getSSOKeyPair();
   }

   /**
    * Creates an RS256-signed SSO JWT token for the given principal.
    *
    * @param principal the authenticated user principal
    * @param issuerUrl the StyleBI server URL to use as the JWT issuer
    * @return the serialized JWT token string
    */
   public String createSSOToken(Principal principal, String issuerUrl) {
      SRPrincipal srPrincipal = convertPrincipal(principal);

      try {
         long expirationSeconds = ZonedDateTime.now(ZoneOffset.UTC)
            .plusHours(SSO_TOKEN_EXPIRATION_HOURS)
            .toEpochSecond();
         Date expirationTime = new Date(expirationSeconds * 1000L);
         Date issueTime = new Date();

         String[] roles = Arrays.stream(srPrincipal.getRoles())
            .map(IdentityID::convertToKey)
            .toArray(String[]::new);

         ClientInfo clientInfo = srPrincipal.getUser();
         String clientAddress = clientInfo != null ? clientInfo.getIPAddress() : null;
         String clientSession = clientInfo != null ? clientInfo.getSession() : null;
         Locale clientLocale = clientInfo != null ? clientInfo.getLocale() : null;
         IdentityID loginUserID = clientInfo != null ? clientInfo.getLoginUserID() : null;

         JWTClaimsSet.Builder claimsBuilder = new JWTClaimsSet.Builder()
            .issuer(normalizeIssuerUrl(issuerUrl))
            .audience(SSO_AUDIENCE)
            .subject(srPrincipal.getName())
            .issueTime(issueTime)
            .expirationTime(expirationTime)
            .claim("roles", roles)
            .claim("groups", srPrincipal.getGroups())
            .claim("organizationId", srPrincipal.getOrgId())
            .claim("appDomain", AppDomainUtils.getAppDomain(srPrincipal))
            .claim("secureId", srPrincipal.getSecureID())
            .claim("clientAddress", clientAddress)
            .claim("clientSession", clientSession)
            .claim("clientLoginUser", loginUserID != null ? loginUserID.convertToKey() : null);

         // Add email if available
         String email = getUserEmail(srPrincipal);

         if(email != null && !email.isEmpty()) {
            claimsBuilder.claim("email", email);
         }

         // Add locale from ClientInfo (use toString() for Java locale format: zh_CN)
         if(clientLocale != null) {
            claimsBuilder.claim("locale", clientLocale.toString());
         }

         claimsBuilder.claim("version", Tool.getReportVersion());

         JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
            .keyID(getKeyId())
            .build();

         SignedJWT jwt = new SignedJWT(header, claimsBuilder.build());
         JWSSigner signer = new RSASSASigner(ssoKeyPair.getPrivate());
         jwt.sign(signer);

         return jwt.serialize();
      }
      catch(JOSEException e) {
         throw new RuntimeException("Failed to create SSO token", e);
      }
   }

   /**
    * Gets the JWKS (JSON Web Key Set) containing the public key for JWT verification.
    *
    * @return the JWKS as a Map that can be serialized to JSON
    */
   public Map<String, Object> getJWKS() {
      try {
         RSAPublicKey publicKey = (RSAPublicKey) ssoKeyPair.getPublic();

         RSAKey rsaKey = new RSAKey.Builder(publicKey)
            .keyID(getKeyId())
            .keyUse(KeyUse.SIGNATURE)
            .algorithm(JWSAlgorithm.RS256)
            .build();

         JWKSet jwkSet = new JWKSet(rsaKey);
         return jwkSet.toJSONObject();
      }
      catch(Exception e) {
         throw new RuntimeException("Failed to generate JWKS", e);
      }
   }

   /**
    * Gets a stable key ID for the RSA key pair.
    * Uses the SHA-256 thumbprint of the public key.
    */
   private String getKeyId() {
      try {
         RSAPublicKey publicKey = (RSAPublicKey) ssoKeyPair.getPublic();
         RSAKey rsaKey = new RSAKey.Builder(publicKey).build();
         return rsaKey.computeThumbprint().toString();
      }
      catch(JOSEException e) {
         // Fallback to a simple hash if thumbprint fails
         return Integer.toHexString(ssoKeyPair.getPublic().hashCode());
      }
   }

   private SRPrincipal convertPrincipal(Principal principal) {
      if(principal instanceof SRPrincipal) {
         return (SRPrincipal) principal;
      }

      IdentityID userID = IdentityID.getIdentityIDFromKey(principal.getName());
      return createPrincipal(userID);
   }

   private SRPrincipal createPrincipal(IdentityID username) {
      IdentityID[] roles = securityProvider.getRoles(username);
      User user = securityProvider.getUser(username);
      String[] groups = user == null ? new String[0] : user.getGroups();
      String orgID = user == null ? Organization.getDefaultOrganizationID() :
         securityProvider.getOrganization(user.getOrganizationID()).getId();

      if(roles == null) {
         roles = new IdentityID[0];
      }

      if(groups == null) {
         groups = new String[0];
      }

      return new SRPrincipal(username, roles, groups, orgID,
         inetsoft.util.Tool.getSecureRandom().nextLong());
   }

   private String getUserEmail(SRPrincipal principal) {
      try {
         IdentityID userID = principal.getIdentityID();
         User user = securityProvider.getUser(userID);
         String[] emails = user != null ? user.getEmails() : null;
         return emails != null && emails.length > 0 ? emails[0] : null;
      }
      catch(Exception e) {
         return null;
      }
   }

   /**
    * Normalizes the issuer URL by removing trailing slashes.
    */
   private String normalizeIssuerUrl(String url) {
      if(url == null || url.isEmpty()) {
         return url;
      }

      // Remove trailing slashes for consistency
      while(url.endsWith("/")) {
         url = url.substring(0, url.length() - 1);
      }

      return url;
   }

   private KeyPair ssoKeyPair;
   private final SecurityProvider securityProvider;

   private static final List<String> SSO_AUDIENCE = Arrays.asList("chat-app", "wiz-service");
   private static final long SSO_TOKEN_EXPIRATION_HOURS = 8L;
}
