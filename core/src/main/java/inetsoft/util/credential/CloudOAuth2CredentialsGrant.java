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

package inetsoft.util.credential;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import inetsoft.util.Tool;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;


@JsonSerialize(using = CloudOAuth2CredentialsGrant.Serializer.class)
@JsonDeserialize(using = CloudOAuth2CredentialsGrant.Deserializer.class)
public class CloudOAuth2CredentialsGrant extends CloudClientCredentialsGrant
   implements OAuth2CredentialsGrant
{
   public CloudOAuth2CredentialsGrant() {
      super();
   }

   @Override
   public String getAuthorizationUri() {
      return authorizationUri;
   }

   @Override
   public void setAuthorizationUri(String authorizationUri) {
      this.authorizationUri = authorizationUri;
   }

   @Override
   public String getTokenUri() {
      return tokenUri;
   }

   @Override
   public void setTokenUri(String tokenUri) {
      this.tokenUri = tokenUri;
   }

   @Override
   public String getScope() {
      return scope;
   }

   @Override
   public void setScope(String scope) {
      this.scope = scope;
   }

   @Override
   public boolean isEmpty() {
      return super.isEmpty() &&
         StringUtils.isEmpty(scope) && StringUtils.isEmpty(tokenUri) &&
         StringUtils.isEmpty(authorizationUri);
   }

   @Override
   public void reset() {
      super.reset();
      scope = null;
      tokenUri = null;
      authorizationUri = null;
   }

   @Override
   public boolean equals(Object obj) {
      if(!super.equals(obj) || !(obj instanceof CloudOAuth2CredentialsGrant)) {
         return false;
      }

      return Tool.equals(((CloudOAuth2CredentialsGrant) obj).scope, scope) &&
         Tool.equals(((CloudOAuth2CredentialsGrant) obj).tokenUri, tokenUri) &&
         Tool.equals(((CloudOAuth2CredentialsGrant) obj).authorizationUri, authorizationUri);
   }

   @Override
   public void refreshCredential(Credential credential) {
      super.refreshCredential(credential);
      CloudOAuth2CredentialsGrant cloudCredential = (CloudOAuth2CredentialsGrant) credential;
      setScope(cloudCredential.getScope());
      setTokenUri(cloudCredential.getTokenUri());
      setAuthorizationUri(cloudCredential.getAuthorizationUri());
   }

   @Override
   public Credential createLocal() {
      return new LocalOAuth2CredentialsGrant();
   }

   @Override
   public void copyToLocal(Credential credential) {
      if(credential instanceof LocalOAuth2CredentialsGrant localCredentials) {
         localCredentials.setScope(scope);
         localCredentials.setTokenUri(tokenUri);
         localCredentials.setAuthorizationUri(authorizationUri);
         super.copyToLocal(localCredentials);
      }
   }

   public static class Serializer<T extends CloudOAuth2CredentialsGrant>
      extends CloudClientCredentialsGrant.Serializer<T>
   {
      public Serializer() {
         super((Class<T>) CloudOAuth2CredentialsGrant.class);
      }

      public Serializer(Class<T> tClass) {
         super(tClass);
      }

      @Override
      protected void serializeContent(T credential, JsonGenerator generator) throws IOException {
         super.serializeContent(credential, generator);

         if(credential.getScope() != null) {
            generator.writeStringField("scope", credential.getScope());
         }

         if(credential.getTokenUri() != null) {
            generator.writeStringField("token_uri", credential.getTokenUri());
         }

         if(credential.getAuthorizationUri() != null) {
            generator.writeStringField("authorization_uri", credential.getAuthorizationUri());
         }
      }
   }

   public static class Deserializer<T extends CloudOAuth2CredentialsGrant>
      extends CloudClientCredentialsGrant.Deserializer<T>
   {
      public Deserializer() {
         super((Class<T>) CloudOAuth2CredentialsGrant.class);
      }

      public Deserializer(Class<T> tClass) {
         super(tClass);
      }

      @Override
      protected void deserializeContent(JsonNode node, T credential) {
         super.deserializeContent(node, credential);

         if(node.get("scope") != null) {
            credential.setScope(node.get("scope").textValue());
         }

         if(node.get("token_uri") != null) {
            credential.setTokenUri(node.get("token_uri").textValue());
         }

         if(node.get("authorization_uri") != null) {
            credential.setAuthorizationUri(node.get("authorization_uri").textValue());
         }
      }
   }

   private String scope;
   private String tokenUri;
   private String authorizationUri;
}
