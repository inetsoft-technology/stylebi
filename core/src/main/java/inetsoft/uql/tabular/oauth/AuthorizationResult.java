/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.uql.tabular.oauth;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;

import java.util.*;

public class AuthorizationResult {
   public String getAccessToken() {
      return accessToken;
   }

   public void setAccessToken(String accessToken) {
      this.accessToken = accessToken;
   }

   public String getRefreshToken() {
      return refreshToken;
   }

   public void setRefreshToken(String refreshToken) {
      this.refreshToken = refreshToken;
   }

   public String getIssued() {
      return issued;
   }

   public void setIssued(String issued) {
      this.issued = issued;
   }

   public String getExpiration() {
      return expiration;
   }

   public void setExpiration(String expiration) {
      this.expiration = expiration;
   }

   public String getScope() {
      return scope;
   }

   public void setScope(String scope) {
      this.scope = scope;
   }

   public String getErrorType() {
      return errorType;
   }

   public void setErrorType(String errorType) {
      this.errorType = errorType;
   }

   public String getErrorMessage() {
      return errorMessage;
   }

   public void setErrorMessage(String errorMessage) {
      this.errorMessage = errorMessage;
   }

   public String getErrorUri() {
      return errorUri;
   }

   public void setErrorUri(String errorUri) {
      this.errorUri = errorUri;
   }

   @JsonAnyGetter
   public Map<String, Object> getProperties() {
      return properties;
   }

   @JsonAnySetter
   public void setProperty(String name, Object value) {
      properties.put(name, value);
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(!(o instanceof AuthorizationResult)) {
         return false;
      }

      AuthorizationResult that = (AuthorizationResult) o;
      return Objects.equals(accessToken, that.accessToken) &&
         Objects.equals(refreshToken, that.refreshToken) &&
         Objects.equals(issued, that.issued) &&
         Objects.equals(expiration, that.expiration) &&
         Objects.equals(scope, that.scope) &&
         Objects.equals(errorType, that.errorType) &&
         Objects.equals(errorMessage, that.errorMessage) &&
         Objects.equals(errorUri, that.errorUri) &&
         Objects.equals(properties, that.properties);
   }

   @Override
   public int hashCode() {
      return Objects.hash(
         accessToken, refreshToken, issued, expiration, scope, errorType, errorMessage, errorUri,
         properties);
   }

   @Override
   public String toString() {
      return "AuthorizationResult{" +
         "accessToken='" + accessToken + '\'' +
         ", refreshToken='" + refreshToken + '\'' +
         ", issued='" + issued + '\'' +
         ", expiration='" + expiration + '\'' +
         ", scope='" + scope + '\'' +
         ", errorType='" + errorType + '\'' +
         ", errorMessage='" + errorMessage + '\'' +
         ", errorUri='" + errorUri + '\'' +
         ", properties=" + properties +
         '}';
   }

   private String accessToken;
   private String refreshToken;
   private String issued;
   private String expiration;
   private String scope;
   private String errorType;
   private String errorMessage;
   private String errorUri;
   private Map<String, Object> properties = new HashMap<>();
}
