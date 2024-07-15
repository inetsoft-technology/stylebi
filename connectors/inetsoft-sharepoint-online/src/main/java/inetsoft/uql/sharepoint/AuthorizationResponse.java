/*
 * inetsoft-sharepoint-online - StyleBI is a business intelligence web application.
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
package inetsoft.uql.sharepoint;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@SuppressWarnings("unused")
@JsonIgnoreProperties(ignoreUnknown = true)
public class AuthorizationResponse {
   @JsonProperty("token_type")
   public String getTokenType() {
      return tokenType;
   }

   public void setTokenType(String tokenType) {
      this.tokenType = tokenType;
   }

   @JsonProperty("scope")
   public String getScope() {
      return scope;
   }

   public void setScope(String scope) {
      this.scope = scope;
   }

   @JsonProperty("expires_in")
   public int getExpiresIn() {
      return expiresIn;
   }

   public void setExpiresIn(int expiresIn) {
      this.expiresIn = expiresIn;
   }

   @JsonProperty("expires_on")
   public int getExpiresOn() {
      return expiresOn;
   }

   public void setExpiresOn(int expiresOn) {
      this.expiresOn = expiresOn;
   }

   @JsonProperty("access_token")
   public String getAccessToken() {
      return accessToken;
   }

   public void setAccessToken(String accessToken) {
      this.accessToken = accessToken;
   }

   @JsonProperty("id_token")
   public String getIdToken() {
      return idToken;
   }

   public void setIdToken(String idToken) {
      this.idToken = idToken;
   }

   @JsonProperty("refresh_token")
   public String getRefreshToken() {
      return refreshToken;
   }

   public void setRefreshToken(String refreshToken) {
      this.refreshToken = refreshToken;
   }

   @JsonProperty("resource")
   public String getResource() {
      return resource;
   }

   public void setResource(String resource) {
      this.resource = resource;
   }

   private String tokenType;
   private String scope;
   private int expiresIn;
   private int expiresOn;
   private String accessToken;
   private String idToken;
   private String refreshToken;
   private String resource;
}
