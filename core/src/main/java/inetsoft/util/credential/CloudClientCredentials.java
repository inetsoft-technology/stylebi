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

@JsonSerialize(using = CloudClientCredentials.Serializer.class)
@JsonDeserialize(using = CloudClientCredentials.Deserializer.class)
public class CloudClientCredentials extends AbstractCloudCredential
   implements CloudCredential, ClientCredentials
{
   public CloudClientCredentials() {
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

   @Override
   public boolean isEmpty() {
      return super.isEmpty() &&
         StringUtils.isEmpty(clientId) && StringUtils.isEmpty(clientSecret);
   }

   @Override
   public void reset() {
      super.reset();
      clientId = null;
      clientSecret = null;
   }

   @Override
   public boolean equals(Object obj) {
      if(!super.equals(obj) || !(obj instanceof CloudClientCredentials)) {
         return false;
      }

      return Tool.equals(((CloudClientCredentials) obj).clientId, clientId) &&
         Tool.equals(((CloudClientCredentials) obj).clientSecret, clientSecret);
   }

   @Override
   public void refreshCredential(Credential credential) {
      super.refreshCredential(credential);
      CloudClientCredentials cloudCredential = (CloudClientCredentials) credential;
      setClientId(cloudCredential.getClientId());
      setClientSecret(cloudCredential.getClientSecret());
   }

   @Override
   public Credential createLocal() {
      return new LocalClientCredentials();
   }

   @Override
   public void copyToLocal(Credential credential) {
      if(credential instanceof LocalClientCredentials localCredentials) {
         localCredentials.setClientId(clientId);
         localCredentials.setClientSecret(clientSecret);
      }
   }

   public static class Serializer<T extends CloudClientCredentials>
      extends AbstractCloudCredential.Serializer<T>
   {
      public Serializer() {
         super((Class<T>) CloudClientCredentials.class);
      }

      public Serializer(Class<T> tClass) {
         super(tClass);
      }

      @Override
      protected void serializeContent(T credential, JsonGenerator generator) throws IOException {
         super.serializeContent(credential, generator);

         if(credential.getClientId() != null) {
            generator.writeStringField("client_id", credential.getClientId());
         }

         if(credential.getClientSecret() != null) {
            generator.writeStringField("client_secret", credential.getClientSecret());
         }
      }
   }

   public static class Deserializer<T extends CloudClientCredentials>
      extends AbstractCloudCredential.Deserializer<T>
   {
      public Deserializer() {
         super((Class<T>) CloudClientCredentials.class);
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

         if(node.get("client_secret") != null) {
            credential.setClientSecret(node.get("client_secret").textValue());
         }
      }
   }

   private String clientId;
   private String clientSecret;
}
