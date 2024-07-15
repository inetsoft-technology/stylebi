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

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;

import java.util.*;

public class RefreshTokenRequest {
   public String getServiceName() {
      return serviceName;
   }

   public void setServiceName(String serviceName) {
      this.serviceName = serviceName;
   }

   public String getClientId() {
      return clientId;
   }

   public void setClientId(String clientId) {
      this.clientId = clientId;
   }

   public String getClientSecret() {
      return clientSecret;
   }

   public void setClientSecret(String clientSecret) {
      this.clientSecret = clientSecret;
   }

   public String getTokenUri() {
      return tokenUri;
   }

   public void setTokenUri(String tokenUri) {
      this.tokenUri = tokenUri;
   }

   public String getRefreshToken() {
      return refreshToken;
   }

   public void setRefreshToken(String refreshToken) {
      this.refreshToken = refreshToken;
   }

   public Set<String> getFlags() {
      return flags;
   }

   public void setFlags(Set<String> flags) {
      this.flags = flags;
   }

   public boolean isUseBasicAuth() {
      return useBasicAuth;
   }

   public void setUseBasicAuth(boolean useBasicAuth) {
      this.useBasicAuth = useBasicAuth;
   }

   @JsonAnyGetter
   public Map<String, Object> getProperties() {
      return properties;
   }

   @JsonAnySetter
   public void setProperty(String name, Object value) {
      properties.put(name, value);
   }

   private String serviceName;
   private String clientId;
   private String clientSecret;
   private String tokenUri;
   private String refreshToken;
   private Set<String> flags = new HashSet<>();
   private boolean useBasicAuth = false;
   private Map<String, Object> properties = new HashMap<>();
}
