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

@JsonSerialize(using = CloudClientTokenCredential.Serializer.class)
@JsonDeserialize(using = CloudClientTokenCredential.Deserializer.class)
public class CloudClientTokenCredential extends AbstractCloudCredential
   implements CloudCredential, ClientTokenCredential
{
   @Override
   public String getClientId() {
      return clientId;
   }

   @Override
   public void setClientId(String clientId) {
      this.clientId = clientId;
   }

   @Override
   public String getAccessToken() {
      return accessToken;
   }

   @Override
   public void setAccessToken(String accessToken) {
      this.accessToken = accessToken;
   }

   @Override
   public boolean isEmpty() {
      return super.isEmpty() && StringUtils.isEmpty(accessToken) && StringUtils.isEmpty(clientId);
   }

   @Override
   public void reset() {
      super.reset();
      accessToken = null;
      clientId = null;
   }

   @Override
   public boolean equals(Object obj) {
      if(!super.equals(obj)) {
         return false;
      }

      if(!(obj instanceof CloudClientTokenCredential)) {
         return false;
      }

      return Tool.equals(((CloudClientTokenCredential) obj).accessToken, accessToken) &&
         Tool.equals(((CloudClientTokenCredential) obj).clientId, clientId);
   }

   @Override
   public void refreshCredential(Credential credential) {
      super.refreshCredential(credential);
      CloudClientTokenCredential credential0 = (CloudClientTokenCredential) credential;
      setAccessToken(credential0.getAccessToken());
      setClientId(credential0.getClientId());
   }

   @Override
   public Credential createLocal() {
      return new LocalClientTokenCredential();
   }

   @Override
   public void copyToLocal(Credential credential) {
      if(credential instanceof LocalClientTokenCredential localCredential) {
         localCredential.setAccessToken(getAccessToken());
         localCredential.setClientId(getClientId());
      }
   }

   public static class Serializer<T extends CloudClientTokenCredential> extends
      AbstractCloudCredential.Serializer<T>
   {
      public Serializer() {
         super((Class<T>) CloudClientTokenCredential.class);
      }

      public Serializer(Class<T> tclass) {
         super(tclass);
      }

      @Override
      protected void serializeContent(CloudClientTokenCredential credential,
                                      JsonGenerator generator)
         throws IOException
      {
         if(credential.getAccessToken() != null) {
            generator.writeStringField("access_token", credential.getAccessToken());
         }

         if(credential.getClientId() != null) {
            generator.writeStringField("account_id", credential.getClientId());
         }
      }
   }

   public static class Deserializer<T extends CloudClientTokenCredential> extends
      AbstractCloudCredential.Deserializer<T>
   {
      public Deserializer() {
         super((Class<T>)  CloudClientTokenCredential.class);
      }

      public Deserializer(Class<T> tclass) {
         super(tclass);
      }

      @Override
      protected void deserializeContent(JsonNode node, CloudClientTokenCredential credential) {
         if(node.get("access_token") != null) {
            credential.setAccessToken(node.get("access_token").textValue());
         }

         if(node.get("account_id") != null) {
            credential.setClientId(node.get("account_id").textValue());
         }
      }
   }

   private String accessToken = "";
   private String clientId = "";
}
