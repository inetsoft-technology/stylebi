/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.uql.tabular.oauth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PasswordGrantResponse {
   @JsonProperty("access_token")
   public String getAccessToken() {
      return accessToken;
   }

   public void setAccessToken(String accessToken) {
      this.accessToken = accessToken;
   }

   @JsonProperty("refresh_token")
   public String getRefreshToken() {
      return refreshToken;
   }

   public void setRefreshToken(String refreshToken) {
      this.refreshToken = refreshToken;
   }

   @JsonProperty("token_type")
   public String getTokenType() {
      return tokenType;
   }

   public void setTokenType(String tokenType) {
      this.tokenType = tokenType;
   }

   /**
    * @return the number of seconds the access token expires in.
    */
   @JsonProperty("expires_in")
   public long getExpiresIn() {
      return expiresIn;
   }

   public void setExpiresIn(int expiresIn) {
      this.expiresIn = expiresIn;
   }

   public String getScope() {
      return scope;
   }

   public void setScope(String scope) {
      this.scope = scope;
   }

   private String accessToken;
   private String refreshToken;
   private String tokenType;
   private long expiresIn;
   private String scope;
}
