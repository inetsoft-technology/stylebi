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

@JsonSerialize(using = CloudApiSecretCredential.Serializer.class)
@JsonDeserialize(using = CloudApiSecretCredential.Deserializer.class)
public class CloudApiSecretCredential extends AbstractCloudCredential
   implements CloudCredential, ApiSecretCredential
{
   public CloudApiSecretCredential() {
      super();
   }

   public String getApiSecret() {
      return apiSecret;
   }

   public void setApiSecret(String apiSecret) {
      this.apiSecret = apiSecret;
   }

   @Override
   public Credential createLocal() {
      return new LocalApiSecretCredential();
   }

   @Override
   public void copyToLocal(Credential credential) {
      if(credential instanceof LocalApiSecretCredential localCredential) {
         localCredential.setApiSecret(apiSecret);
      }
   }

   @Override
   public boolean isEmpty() {
      return super.isEmpty() && StringUtils.isEmpty(apiSecret);
   }

   @Override
   public void reset() {
      super.reset();
      apiSecret = null;
   }

   @Override
   public boolean equals(Object obj) {
      if(!super.equals(obj)) {
         return false;
      }

      if(!(obj instanceof CloudApiSecretCredential)) {
         return false;
      }

      return Tool.equals(((CloudApiSecretCredential) obj).apiSecret, apiSecret);
   }

   @Override
   public void refreshCredential(Credential credential) {
      super.refreshCredential(credential);
      CloudApiSecretCredential cloudCredential = (CloudApiSecretCredential) credential;
      setApiSecret(cloudCredential.getApiSecret());
   }

   public static class Serializer<T extends CloudApiSecretCredential>
      extends AbstractCloudCredential.Serializer<T>
   {
      public Serializer(Class<T> tClass) {
         super(tClass);
      }

      @Override
      protected void serializeContent(T credential, JsonGenerator generator) throws IOException {
         super.serializeContent(credential, generator);

         if(credential.getApiSecret() != null) {
            generator.writeStringField("api_secret", credential.getApiSecret());
         }
      }
   }

   public static class Deserializer<T extends CloudApiSecretCredential>
      extends AbstractCloudCredential.Deserializer<T>
   {
      public Deserializer() {
         super((Class<T>) CloudApiSecretCredential.class);
      }

      public Deserializer(Class<T> tClass) {
         super(tClass);
      }

      @Override
      protected void deserializeContent(JsonNode node, T credential) {
         super.deserializeContent(node, credential);

         if(node.get("api_secret") != null) {
            credential.setApiSecret(node.get("api_secret").textValue());
         }
      }
   }

   private String apiSecret;
}
