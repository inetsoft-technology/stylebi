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

@JsonSerialize(using = CloudAccessTokenCredential.Serializer.class)
@JsonDeserialize(using = CloudAccessTokenCredential.Deserializer.class)
public class CloudAccessTokenCredential extends AbstractCloudCredential
   implements CloudCredential, AccessTokenCredential
{
   public CloudAccessTokenCredential() {
      super();
   }

   public String getAccessToken() {
      return accessToken;
   }

   public void setAccessToken(String accessToken) {
      setAccessToken(accessToken, true);
   }

   public void setAccessToken(String accessToken, boolean reset) {
      this.accessToken = accessToken;
   }

   @Override
   public Credential createLocal() {
      return new LocalAccessTokenCredential();
   }

   @Override
   public void copyToLocal(Credential credential) {
      if(credential instanceof LocalAccessTokenCredential localCredential) {
         localCredential.setAccessToken(accessToken);
      }
   }

   @Override
   public boolean isEmpty() {
      return super.isEmpty() && StringUtils.isEmpty(accessToken);
   }

   @Override
   public void reset() {
      super.reset();
      accessToken = null;
   }

   @Override
   public boolean equals(Object obj) {
      if(!super.equals(obj)) {
         return false;
      }

      if(!(obj instanceof CloudAccessTokenCredential)) {
         return false;
      }

      return Tool.equals(((CloudAccessTokenCredential) obj).accessToken, accessToken);
   }

   @Override
   public void refreshCredential(Credential credential) {
      super.refreshCredential(credential);
      CloudAccessTokenCredential credential0 = (CloudAccessTokenCredential) credential;
      setAccessToken(credential0.getAccessToken(), false);
   }

   public static class Serializer<T extends CloudAccessTokenCredential> extends
      AbstractCloudCredential.Serializer<T>
   {
      public Serializer() {
         super((Class<T>) CloudAccessTokenCredential.class);
      }

      public Serializer(Class<T> tclass) {
         super(tclass);
      }

      @Override
      protected void serializeContent(CloudAccessTokenCredential credential,
                                      JsonGenerator generator)
         throws IOException
      {
         if(credential.getAccessToken() != null) {
            generator.writeStringField("access_token", credential.getAccessToken());
         }
      }
   }

   public static class Deserializer<T extends CloudAccessTokenCredential> extends
      AbstractCloudCredential.Deserializer<T>
   {
      public Deserializer() {
         super((Class<T>)  CloudAccessTokenCredential.class);
      }

      public Deserializer(Class<T> tclass) {
         super(tclass);
      }

      @Override
      protected void deserializeContent(JsonNode node, CloudAccessTokenCredential credential) {
         if(node.get("access_token") != null) {
            credential.setAccessToken(node.get("access_token").textValue());
         }
      }
   }

   private String accessToken = "";
}