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
package inetsoft.uql.tabular.oauth;

import inetsoft.uql.tabular.*;

public interface OAuthDataSource {
   default HttpParameter[] getRequestParameters() {
      return new HttpParameter[0];
   }

   void updateTokens(Tokens tokens);

   default String getServiceName() {
      return null;
   }

   default void setServiceName(String serviceName) {
   }

   @Property(label="User")
   default String getUser() {
      return null;
   }

   default void setUser(String user) {
   }

   @Property(label="Password", password=true)
   default String getPassword() {
      return null;
   }

   default void setPassword(String password) {
   }

   @Property(label = "Client ID")
   default String getClientId() {
      return null;
   }

   default void setClientId(String clientId) {
   }

   @Property(label = "Client Secret", password = true)
   default String getClientSecret() {
      return null;
   }

   default void setClientSecret(String clientSecret) {
   }

   @Property(label = "Authorization URI")
   default String getAuthorizationUri() {
      return null;
   }

   default void setAuthorizationUri(String authorizationUri) {
   }

   @Property(label = "Token URI")
   default String getTokenUri() {
      return null;
   }

   default void setTokenUri(String tokenUri) {
   }

   @Property(label = "Scope")
   default String getScope() {
      return null;
   }

   default void setScope(String scope) {
   }

   @Property(label = "OAuth Flags")
   default String getOauthFlags() {
      return null;
   }

   default void setOauthFlags(String oauthFlags) {
   }

   @PropertyEditor(enabled = false)
   @Property(label = "Access Token", password = true)
   default String getAccessToken() {
      return null;
   }

   default void setAccessToken(String accessToken) {
   }

   @PropertyEditor(enabled = false)
   @Property(label = "Refresh Token", password = true)
   default String getRefreshToken() {
      return null;
   }

   default void setRefreshToken(String refreshToken) {
   }

   @PropertyEditor(enabled = false)
   @Property(label = "Token Expiration")
   default long getTokenExpiration() {
      return Long.MAX_VALUE;
   }

   default void setTokenExpiration(long tokenExpiration) {
   }
}
