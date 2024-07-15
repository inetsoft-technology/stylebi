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
package inetsoft.web.admin.security;

import inetsoft.sree.SreeEnv;
import inetsoft.util.PasswordEncryption;
import jakarta.annotation.PostConstruct;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class OpenIDConfig implements OpenIDSSOConfig {
   @PostConstruct
   public void convertLegacyAuth0Properties() {
      if("Auth0".equals(SreeEnv.getProperty("sso.protocol.type"))) {
         SreeEnv.setProperty("sso.protocol.type", "OpenID");
         String domain = SreeEnv.getProperty("auth0.domain");

         setClientId(SreeEnv.getProperty("auth0.client.id"));
         setClientSecret(SreeEnv.getProperty("auth0.client.secret"));
         setScopes(SreeEnv.getProperty("auth0.openid.scope", "openid email profile"));
         setNameClaim(SreeEnv.getProperty("auth0.jwt.claim", "name"));
         SreeEnv.setProperty("openid.callback.url", "/sso/login");

         if(domain != null) {
            setAuthorizationEndpoint(String.format("https://%s/authorize", domain));
            setTokenEndpoint(String.format("https://%s/oauth/token", domain));
            setJwksUri(SreeEnv.getProperty(
               "auth0.jwk.url", String.format("https://%s/.well-known/jwks.json", domain)));
         }

         if(SreeEnv.getProperty("portal.logout.url") == null &&
            SreeEnv.getProperty("auth0.logout.url") != null)
         {
            // use standard mechanism for single sign-out
            SreeEnv.setProperty("portal.logout.url", SreeEnv.getProperty("auth0.logout.url"));
         }

         try {
            SreeEnv.save();
         }
         catch(Exception e) {
            LoggerFactory.getLogger(getClass()).error("Failed to save SSO filter properties", e);
         }
      }
   }

   public String getClientId() {
      return SreeEnv.getProperty("openid.client.id");
   }

   public void setClientId(String clientId) {
      SreeEnv.setProperty("openid.client.id", clientId);
   }

   public String getClientSecret() {
      return PasswordEncryption.newInstance()
         .decryptPassword(SreeEnv.getProperty("openid.client.secret"));
   }

   public void setClientSecret(String clientSecret) {
      SreeEnv.setProperty(
         "openid.client.secret",
         PasswordEncryption.newInstance().encryptPassword(clientSecret));
   }

   public String getScopes() {
      return SreeEnv.getProperty("openid.scopes", "openid email");
   }

   public void setScopes(String scopes) {
      SreeEnv.setProperty("openid.scopes", scopes);
   }

   public String getIssuer() {
      return SreeEnv.getProperty("openid.issuer");
   }

   public void setIssuer(String issuer) {
      SreeEnv.setProperty("openid.issuer", issuer);
   }

   public String getAudience() {
      return SreeEnv.getProperty("openid.audience", getClientId());
   }

   public void setAudience(String audience) {
      SreeEnv.setProperty("openid.audience", audience);
   }

   public String getTokenEndpoint() {
      return SreeEnv.getProperty("openid.token.endpoint");
   }

   public void setTokenEndpoint(String tokenEndpoint) {
      SreeEnv.setProperty("openid.token.endpoint", tokenEndpoint);
   }

   public String getAuthorizationEndpoint() {
      return SreeEnv.getProperty("openid.authorization.endpoint");
   }

   public void setAuthorizationEndpoint(String authorizationEndpoint) {
      SreeEnv.setProperty("openid.authorization.endpoint", authorizationEndpoint);
   }

   public String getJwksUri() {
      return SreeEnv.getProperty("openid.jwks.uri");
   }

   public void setJwksUri(String jwksUri) {
      SreeEnv.setProperty("openid.jwks.uri", jwksUri);
   }

   public String getJwkCertificate() {
      return SreeEnv.getProperty("openid.jwk.cert");
   }

   public void setJwkCertificate(String jwkCertificate) {
      SreeEnv.setProperty("openid.jwk.cert", jwkCertificate);
   }

   public String getOpenidLoginPage() {
      return SreeEnv.getProperty("openid.callback.url", "/openid/login");
   }

   public void setNameClaim(String nameClaim) {
      SreeEnv.setProperty("openid.name.claim", nameClaim);
   }

   public String getNameClaim() {
      return SreeEnv.getProperty("openid.name.claim", "email");
   }

   public void setRoleClaim(String roleClaim) {
      SreeEnv.setProperty("openid.role.claim", roleClaim);
   }

   public String getRoleClaim() {
      return SreeEnv.getProperty("openid.role.claim");
   }

   public void setGroupClaim(String groupClaim) {
      SreeEnv.setProperty("openid.group.claim", groupClaim);
   }

   public String getGroupClaim() {
      return SreeEnv.getProperty("openid.group.claim");
   }

   public void setOrgIDClaim(String orgIDClaim) {
      SreeEnv.setProperty("openid.orgID.claim", orgIDClaim);
   }

   public String getOrgIDClaim() {
      return SreeEnv.getProperty("openid.orgID.claim");
   }

   public String getOpenIDPropertyProvider() {
      return SreeEnv.getProperty("openid.property.provider");
   }

   public void setOpenIDPropertyProvider(String provider) {
      SreeEnv.setProperty("openid.property.provider", provider);
   }

   public String getOpenIDPostprocessor() {
      return SreeEnv.getProperty("openid.postprocessor");
   }

   public void setOpenIDPostprocessor(String postprocessor) {
      SreeEnv.setProperty("openid.postprocessor", postprocessor);
   }
}
