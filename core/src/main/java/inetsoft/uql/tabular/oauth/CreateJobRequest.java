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

import java.util.*;

public class CreateJobRequest {
   public CreateJobRequest() {
   }

   public CreateJobRequest(String serviceName, String clientId, String clientSecret,
                           List<String> scope, String authorizationUri, String tokenUri,
                           Set<String> flags, Map<String, String> additionalParameters)
   {
      this.serviceName = serviceName;
      this.clientId = clientId;
      this.clientSecret = clientSecret;
      this.scope = scope;
      this.authorizationUri = authorizationUri;
      this.tokenUri = tokenUri;
      this.flags = flags;
      this.additionalParameters = additionalParameters;
   }

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

   public List<String> getScope() {
      return scope;
   }

   public void setScope(List<String> scope) {
      this.scope = scope;
   }

   public String getAuthorizationUri() {
      return authorizationUri;
   }

   public void setAuthorizationUri(String authorizationUri) {
      this.authorizationUri = authorizationUri;
   }

   public String getTokenUri() {
      return tokenUri;
   }

   public void setTokenUri(String tokenUri) {
      this.tokenUri = tokenUri;
   }

   public Set<String> getFlags() {
      return flags;
   }

   public void setFlags(Set<String> flags) {
      this.flags = flags;
   }

   public Map<String, String> getAdditionalParameters() {
      return additionalParameters;
   }

   public void setAdditionalParameters(Map<String, String> additionalParameters) {
      this.additionalParameters = additionalParameters;
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(o == null || getClass() != o.getClass()) {
         return false;
      }

      CreateJobRequest that = (CreateJobRequest) o;
      return Objects.equals(serviceName, that.serviceName) &&
         Objects.equals(clientId, that.clientId) &&
         Objects.equals(clientSecret, that.clientSecret) &&
         Objects.equals(scope, that.scope) &&
         Objects.equals(authorizationUri, that.authorizationUri) &&
         Objects.equals(tokenUri, that.tokenUri) &&
         Objects.equals(flags, that.flags) &&
         Objects.equals(additionalParameters, that.additionalParameters);
   }

   @Override
   public int hashCode() {
      return Objects.hash(
         serviceName, clientId, clientSecret, scope, authorizationUri, tokenUri, flags,
         additionalParameters);
   }

   @Override
   public String toString() {
      return "CreateJobRequest{" +
         "serviceName='" + serviceName + '\'' +
         ", clientId='" + clientId + '\'' +
         ", clientSecret='" + clientSecret + '\'' +
         ", scope=" + scope +
         ", authorizationUri='" + authorizationUri + '\'' +
         ", tokenUri='" + tokenUri + '\'' +
         ", flags=" + flags +
         ", additionalParameters=" + additionalParameters +
         '}';
   }

   private String serviceName;
   private String clientId;
   private String clientSecret;
   private List<String> scope;
   private String authorizationUri;
   private String tokenUri;
   private Set<String> flags = new HashSet<>();
   private Map<String, String> additionalParameters = new HashMap<>();
}
