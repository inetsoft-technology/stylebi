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

@JsonSerialize(using = CloudTokenCredential.Serializer.class)
@JsonDeserialize(using = CloudTokenCredential.Deserializer.class)
public class CloudTokenCredential extends AbstractCloudCredential
   implements CloudCredential, TokenCredential
{
   public CloudTokenCredential() {
      super();
   }

   public String getToken() {
      return token;
   }

   public void setToken(String token) {
      this.token = token;
   }

   @Override
   public boolean isEmpty() {
      return super.isEmpty() && StringUtils.isEmpty(token);
   }

   @Override
   public void reset() {
      super.reset();
      token = null;
   }

   @Override
   public boolean equals(Object obj) {
      if(!super.equals(obj)) {
         return false;
      }

      if(!(obj instanceof CloudTokenCredential)) {
         return false;
      }

      return Tool.equals(((CloudTokenCredential) obj).token, token);
   }

   @Override
   public void refreshCredential(Credential credential) {
      super.refreshCredential(credential);
      CloudTokenCredential credential0 = (CloudTokenCredential) credential;
      setToken(credential0.getToken());
   }

   @Override
   public Credential createLocal() {
      return new LocalTokenCredential();
   }

   @Override
   public void copyToLocal(Credential credential) {
      if(credential instanceof LocalTokenCredential localCredential) {
         localCredential.setToken(token);
      }
   }

   public static class Serializer<T extends CloudTokenCredential>
      extends AbstractCloudCredential.Serializer<T>
   {
      public Serializer(Class<T> tClass) {
         super(tClass);
      }

      @Override
      protected void serializeContent(T credential, JsonGenerator generator) throws IOException {
         super.serializeContent(credential, generator);

         if(credential.getToken() != null) {
            generator.writeStringField("token", credential.getToken());
         }
      }
   }

   public static class Deserializer<T extends CloudTokenCredential>
      extends AbstractCloudCredential.Deserializer<T>
   {
      public Deserializer() {
         super((Class<T>) CloudTokenCredential.class);
      }

      public Deserializer(Class<T> tClass) {
         super(tClass);
      }

      @Override
      protected void deserializeContent(JsonNode node, T credential) {
         super.deserializeContent(node, credential);

         if(node.get("token") != null) {
            credential.setToken(node.get("token").textValue());
         }
      }
   }

   private String token;
}