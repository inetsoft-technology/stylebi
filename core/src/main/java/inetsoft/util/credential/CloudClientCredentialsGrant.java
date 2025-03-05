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
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import inetsoft.util.Tool;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;

@JsonSerialize(using = CloudClientCredentialsGrant.Serializer.class)
@JsonDeserialize(using = CloudClientCredentialsGrant.Deserializer.class)
public class CloudClientCredentialsGrant extends AbstractCloudCredential
   implements CloudCredential, ClientCredentialsGrant
{
   public CloudClientCredentialsGrant() {
      super();
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

   @Override
   public boolean isEmpty() {
      return super.isEmpty() &&
         StringUtils.isEmpty(clientId) && StringUtils.isEmpty(clientSecret) &&
         StringUtils.isEmpty(accessToken) && StringUtils.isEmpty(refreshToken);
   }

   @Override
   public void reset() {
      super.reset();
      clientId = null;
      clientSecret = null;
      accessToken = null;
      refreshToken = null;
   }

   @Override
   public boolean equals(Object obj) {
      if(!super.equals(obj) || !(obj instanceof CloudClientCredentialsGrant)) {
         return false;
      }

      return Tool.equals(((CloudClientCredentialsGrant) obj).clientId, clientId) &&
         Tool.equals(((CloudClientCredentialsGrant) obj).clientSecret, clientSecret) &&
         Tool.equals(((CloudClientCredentialsGrant) obj).accessToken, accessToken) &&
         Tool.equals(((CloudClientCredentialsGrant) obj).refreshToken, refreshToken);
   }

   @Override
   public void refreshCredential(Credential credential) {
      super.refreshCredential(credential);
      CloudClientCredentialsGrant cloudCredential = (CloudClientCredentialsGrant) credential;
      setClientId(cloudCredential.getClientId());
      setRefreshToken(cloudCredential.getRefreshToken());
      setAccessToken(cloudCredential.getAccessToken());
      setClientSecret(cloudCredential.getClientSecret());
   }

   @Override
   public Credential createLocal() {
      return new LocalClientCredentialsGrant();
   }

   @Override
   public void copyToLocal(Credential credential) {
      if(credential instanceof LocalClientCredentialsGrant localCredentials) {
         localCredentials.setClientId(clientId);
         localCredentials.setRefreshToken(refreshToken);
         localCredentials.setAccessToken(accessToken);
         localCredentials.setClientSecret(clientSecret);
      }
   }

   public static class Serializer<T extends CloudClientCredentialsGrant>
      extends AbstractCloudCredential.Serializer<T>
   {
      public Serializer() {
         super((Class<T>) CloudClientCredentialsGrant.class);
      }

      public Serializer(Class<T> tClass) {
         super(tClass);
      }

      @Override
      protected void serializeContent(T credential, JsonGenerator generator) throws IOException {
         super.serializeContent(credential, generator);

         generator.writeStringField("client_id", credential.getClientId());
         generator.writeStringField("access_token", credential.getAccessToken());
         generator.writeStringField("client_secret", credential.getClientSecret());
         generator.writeStringField("refresh_token", credential.getRefreshToken());
      }
   }

   public static class Deserializer<T extends CloudClientCredentialsGrant>
      extends AbstractCloudCredential.Deserializer<T>
   {
      public Deserializer() {
         super((Class<T>) CloudClientCredentialsGrant.class);
      }

      public Deserializer(Class<T> tClass) {
         super(tClass);
      }

      @Override
      protected void deserializeContent(JsonNode node, T credential) {
         super.deserializeContent(node, credential);

         if(node.get("client_id") != null) {
            credential.setClientId(node.get("client_id").textValue());
         }

         if(node.get("access_token") != null) {
            credential.setAccessToken(node.get("access_token").textValue());
         }

         if(node.get("client_secret") != null) {
            credential.setClientSecret(node.get("client_secret").textValue());
         }

         if(node.get("refresh_token") != null) {
            credential.setRefreshToken(node.get("refresh_token").textValue());
         }
      }
   }

   private String clientId;
   private String clientSecret;
   private String accessToken;
   private String refreshToken;
}
