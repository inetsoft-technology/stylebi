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

@JsonSerialize(using = CloudAuthorizationTokenCredential.Serializer.class)
@JsonDeserialize(using = CloudAuthorizationTokenCredential.Deserializer.class)
public class CloudAuthorizationTokenCredential extends AbstractCloudCredential
   implements CloudCredential, AuthorizationTokenCredential
{
   public CloudAuthorizationTokenCredential() {
      super();
   }

   public String getApplicationId() {
      return applicationId;
   }

   public void setApplicationId(String applicationId) {
      this.applicationId = applicationId;
   }

   public String getAuthorizationToken() {
      return authorizationToken;
   }

   public void setAuthorizationToken(String authorizationToken) {
      this.authorizationToken = authorizationToken;
   }

   @Override
   public boolean isEmpty() {
      return super.isEmpty() && StringUtils.isEmpty(applicationId) &&
         StringUtils.isEmpty(authorizationToken);
   }

   @Override
   public void reset() {
      super.reset();
      applicationId = null;
      authorizationToken = null;
   }

   @Override
   public boolean equals(Object obj) {
      if(!super.equals(obj)) {
         return false;
      }

      if(!(obj instanceof CloudAuthorizationTokenCredential)) {
         return false;
      }

      return Tool.equals(((CloudAuthorizationTokenCredential) obj).applicationId, applicationId) &&
         Tool.equals(((CloudAuthorizationTokenCredential) obj).authorizationToken, authorizationToken);
   }

   @Override
   public void refreshCredential(Credential credential) {
      super.refreshCredential(credential);
      CloudAuthorizationTokenCredential credential0 = (CloudAuthorizationTokenCredential) credential;
      setApplicationId(credential0.getApplicationId());
      setAuthorizationToken(credential0.getAuthorizationToken());
   }

   @Override
   public Credential createLocal() {
      return new LocalAuthorizationTokenCredential();
   }

   @Override
   public void copyToLocal(Credential credential) {
      if(credential instanceof LocalAuthorizationTokenCredential localCredential) {
         localCredential.setApplicationId(applicationId);
         localCredential.setAuthorizationToken(authorizationToken);
      }
   }

   public static class Serializer<T extends CloudAuthorizationTokenCredential>
      extends AbstractCloudCredential.Serializer<T>
   {
      public Serializer(Class<T> tClass) {
         super(tClass);
      }

      @Override
      protected void serializeContent(T credential, JsonGenerator generator) throws IOException {
         super.serializeContent(credential, generator);

         if(credential.getApplicationId() != null) {
            generator.writeStringField("application_id", credential.getApplicationId());
         }

         if(credential.getAuthorizationToken() != null) {
            generator.writeStringField("authorization_token", credential.getAuthorizationToken());
         }
      }
   }

   public static class Deserializer<T extends CloudAuthorizationTokenCredential>
      extends AbstractCloudCredential.Deserializer<T>
   {
      public Deserializer() {
         super((Class<T>) CloudAuthorizationTokenCredential.class);
      }

      public Deserializer(Class<T> tClass) {
         super(tClass);
      }

      @Override
      protected void deserializeContent(JsonNode node, T credential) {
         super.deserializeContent(node, credential);

         if(node.get("application_id") != null) {
            credential.setApplicationId(node.get("application_id").textValue());
         }

         if(node.get("authorization_token") != null) {
            credential.setAuthorizationToken(node.get("authorization_token").textValue());
         }
      }
   }

   private String applicationId;
   private String authorizationToken;
}