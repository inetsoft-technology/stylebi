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

@JsonSerialize(using = CloudApiKeyCredential.Serializer.class)
@JsonDeserialize(using = CloudApiKeyCredential.Deserializer.class)
public class CloudApiKeyCredential extends AbstractCloudCredential
   implements CloudCredential, ApiKeyCredential
{
   public CloudApiKeyCredential() {
      super();
   }

   public String getApiKey() {
      return apiKey;
   }

   public void setApiKey(String apiKey) {
      setApiKey(apiKey, true);
   }

   public void setApiKey(String apiKey, boolean reset) {
      this.apiKey = apiKey;
   }

   @Override
   public boolean isEmpty() {
      return super.isEmpty() && StringUtils.isEmpty(apiKey);
   }

   @Override
   public void reset() {
      super.reset();
      apiKey = null;
   }

   @Override
   public boolean equals(Object obj) {
      if(!super.equals(obj)) {
         return false;
      }

      if(!(obj instanceof CloudApiKeyCredential)) {
         return false;
      }

      return Tool.equals(((CloudApiKeyCredential) obj).apiKey, apiKey);
   }

   @Override
   public void refreshCredential(Credential credential) {
      super.refreshCredential(credential);
      CloudApiKeyCredential credential0 = (CloudApiKeyCredential) credential;
      setApiKey(credential0.getApiKey(), false);
   }

   public static class Serializer<T extends CloudApiKeyCredential> extends
      AbstractCloudCredential.Serializer<T>
   {
      public Serializer() {
         super((Class<T>) CloudApiKeyCredential.class);
      }

      public Serializer(Class<T> tclass) {
         super(tclass);
      }

      @Override
      protected void serializeContent(CloudApiKeyCredential credential,
                                      JsonGenerator generator)
         throws IOException
      {
         generator.writeStringField("api_key", credential.getApiKey());
      }
   }

   public static class Deserializer<T extends CloudApiKeyCredential> extends
      AbstractCloudCredential.Deserializer<T>
   {
      public Deserializer() {
         super((Class<T>)  CloudApiKeyCredential.class);
      }

      public Deserializer(Class<T> tclass) {
         super(tclass);
      }

      @Override
      protected void deserializeContent(JsonNode node, CloudApiKeyCredential credential) {
         if(node.get("api_key") != null) {
            credential.setApiKey(node.get("api_key").textValue());
         }
      }
   }

   private String apiKey;
}
