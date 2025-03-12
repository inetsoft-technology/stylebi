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

@JsonSerialize(using = CloudPasswordAndApiTokenCredential.Serializer.class)
@JsonDeserialize(using = CloudPasswordAndApiTokenCredential.Deserializer.class)
public class CloudPasswordAndApiTokenCredential extends CloudPasswordCredential
   implements PasswordAndApiTokenCredential
{
   public CloudPasswordAndApiTokenCredential() {
      super();
   }

   @Override
   public String getApiToken() {
      return apiToken;
   }

   @Override
   public void setApiToken(String apiToken) {
      this.apiToken = apiToken;
   }

   @Override
   public void refreshCredential(Credential credential) {
      super.refreshCredential(credential);
      CloudPasswordAndApiTokenCredential credential1 = (CloudPasswordAndApiTokenCredential) credential;
      setApiToken(credential1.getApiToken());
   }

   @Override
   public void reset() {
      super.reset();
      apiToken = "";
   }

   @Override
   public boolean equals(Object obj) {
      if(!super.equals(obj) || !(obj instanceof CloudPasswordAndApiTokenCredential)) {
         return false;
      }

      return Tool.equals(((CloudPasswordAndApiTokenCredential) obj).apiToken, apiToken);
   }

   @Override
   public Credential createLocal() {
      return new LocalPasswordAndApiTokenCredential();
   }

   @Override
   public void copyToLocal(Credential credential) {
      if(credential instanceof LocalPasswordAndApiTokenCredential localCredential) {
         localCredential.setApiToken(apiToken);
         super.copyToLocal(localCredential);
      }
   }

   @Override
   public boolean isEmpty() {
      return super.isEmpty() && StringUtils.isEmpty(getApiToken());
   }

   public static class Serializer<T extends CloudPasswordAndApiTokenCredential>
      extends CloudPasswordCredential.Serializer<T>
   {
      public Serializer() {
         super((Class<T>) CloudPasswordAndApiTokenCredential.class);
      }

      public Serializer(Class<T> tClass) {
         super(tClass);
      }

      @Override
      protected void serializeContent(T credential, JsonGenerator generator) throws IOException {
         super.serializeContent(credential, generator);

         if(credential.getApiToken() != null) {
            generator.writeStringField("api_token", credential.getApiToken());
         }
      }
   }

   public static class Deserializer<T extends CloudPasswordAndApiTokenCredential>
      extends CloudPasswordCredential.Deserializer<T>
   {
      public Deserializer() {
         super((Class<T>) CloudPasswordAndApiTokenCredential.class);
      }

      public Deserializer(Class<T> tClass) {
         super(tClass);
      }

      @Override
      protected void deserializeContent(JsonNode node, T credential) {
         super.deserializeContent(node, credential);

         if(node.get("api_token") != null) {
            credential.setApiToken(node.get("api_token").textValue());
         }
      }
   }

   private String apiToken;
}
