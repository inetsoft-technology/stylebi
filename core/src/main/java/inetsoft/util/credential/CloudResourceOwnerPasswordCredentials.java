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

import java.io.IOException;

@JsonSerialize(using = CloudResourceOwnerPasswordCredentials.Serializer.class)
@JsonDeserialize(using = CloudResourceOwnerPasswordCredentials.Deserializer.class)
public class CloudResourceOwnerPasswordCredentials extends CloudPasswordCredential
   implements ResourceOwnerPasswordCredentials
{
   public CloudResourceOwnerPasswordCredentials() {
      super();
   }

   public String getClientId() {
      return clientId;
   }

   @Override
   public void setClientId(String clientId) {
      this.clientId = clientId;
   }

   @Override
   public String getClientSecret() {
      return clientSecret;
   }

   @Override
   public void setClientSecret(String clientSecret) {
      this.clientSecret = clientSecret;
   }

   @Override
   public String getTenantId() {
      return tenantId;
   }

   @Override
   public void setTenantId(String tenantId) {
      this.tenantId = tenantId;
   }

   /**
    * Gets the access token obtained at runtime. The tokens are not part of the secret, they are
    * neither read from nor written to the secrets manager, and they are not saved with the data
    * source, which stores only the id of a cloud credential.
    */
   @Override
   public String getAccessToken() {
      return accessToken;
   }

   @Override
   public void setAccessToken(String accessToken) {
      this.accessToken = accessToken;
   }

   @Override
   public String getRefreshToken() {
      return refreshToken;
   }

   @Override
   public void setRefreshToken(String refreshToken) {
      this.refreshToken = refreshToken;
   }

   @Override
   public void reset() {
      super.reset();
      clientId = "";
      clientSecret = "";
      tenantId = "";
      accessToken = null;
      refreshToken = null;
   }

   @Override
   public boolean equals(Object obj) {
      if(!super.equals(obj) || !(obj instanceof CloudResourceOwnerPasswordCredentials)) {
         return false;
      }

      return Tool.equals(((CloudResourceOwnerPasswordCredentials) obj).clientId, clientId) &&
         Tool.equals(((CloudResourceOwnerPasswordCredentials) obj).clientSecret, clientSecret) &&
         Tool.equals(((CloudResourceOwnerPasswordCredentials) obj).tenantId, tenantId) &&
         // Bug #77730, the tokens are compared so that saving them onto a stored data source is
         // detected as a change of its cloud credential, which can't be saved (Bug #77699)
         Tool.equals(((CloudResourceOwnerPasswordCredentials) obj).accessToken, accessToken) &&
         Tool.equals(((CloudResourceOwnerPasswordCredentials) obj).refreshToken, refreshToken);
   }

   @Override
   public void refreshCredential(Credential credential) {
      super.refreshCredential(credential);
      CloudResourceOwnerPasswordCredentials credential0 = (CloudResourceOwnerPasswordCredentials) credential;
      setClientId(credential0.getClientId());
      setClientSecret(credential0.getClientSecret());
      setTenantId(credential0.getTenantId());
   }

   @Override
   public Credential createLocal() {
      return new LocalResourceOwnerPasswordCredentials();
   }

   @Override
   public void copyToLocal(Credential credential) {
      if(credential instanceof LocalResourceOwnerPasswordCredentials localCredentials) {
         localCredentials.setClientId(clientId);
         localCredentials.setClientSecret(clientSecret);
         localCredentials.setTenantId(tenantId);
         super.copyToLocal(localCredentials);
      }
   }

   public static class Serializer<T extends CloudResourceOwnerPasswordCredentials>
      extends CloudPasswordCredential.Serializer<T>
   {
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

         if(credential.getTenantId() != null) {
            generator.writeStringField("tenant_id", credential.getTenantId());
         }
      }
   }

   public static class Deserializer<T extends CloudResourceOwnerPasswordCredentials>
      extends CloudPasswordCredential.Deserializer<T>
   {
      public Deserializer() {
         super((Class<T>) CloudResourceOwnerPasswordCredentials.class);
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

         if(node.get("tenant_id") != null) {
            credential.setTenantId(node.get("tenant_id").textValue());
         }
      }
   }

   private String clientId;
   private String clientSecret;
   private String tenantId;
   // runtime only, see getAccessToken()
   private String accessToken;
   private String refreshToken;
}
