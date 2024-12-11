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
import org.thymeleaf.util.StringUtils;

import java.io.IOException;

@JsonSerialize(using = CloudAdobeAnalyticsCredential.Serializer.class)
@JsonDeserialize(using = CloudAdobeAnalyticsCredential.Deserializer.class)
public class CloudAdobeAnalyticsCredential extends AbstractCloudCredential
   implements AdobeAnalyticsCredential
{
   public CloudAdobeAnalyticsCredential() {
      super();
   }

   @Override
   public String getApiKey() {
      return apiKey;
   }

   @Override
   public void setApiKey(String apiKey) {
      this.apiKey = apiKey;
   }

   @Override
   public String getGlobalCompanyId() {
      return globalCompanyId;
   }

   @Override
   public void setGlobalCompanyId(String globalCompanyId) {
      this.globalCompanyId = globalCompanyId;
   }

   @Override
   public void refreshCredential(Credential credential) {
      super.refreshCredential(credential);
      CloudAdobeAnalyticsCredential tokensCredential = (CloudAdobeAnalyticsCredential) credential;
      setApiKey(tokensCredential.getApiKey());
      setGlobalCompanyId(tokensCredential.getGlobalCompanyId());
   }

   @Override
   public boolean isEmpty() {
      return super.isEmpty() && StringUtils.isEmpty(apiKey) && StringUtils.isEmpty(globalCompanyId);
   }

   @Override
   public boolean equals(Object obj) {
      if(!(obj instanceof CloudAdobeAnalyticsCredential)) {
         return false;
      }

      return Tool.equals(((CloudAdobeAnalyticsCredential) obj).apiKey, apiKey) &&
         Tool.equals(((CloudAdobeAnalyticsCredential) obj).globalCompanyId, globalCompanyId);
   }

   public static class Serializer<T extends CloudAdobeAnalyticsCredential> extends AbstractCloudCredential.Serializer<T> {
      public Serializer() {
         super((Class<T>) CloudAdobeAnalyticsCredential.class);
      }

      @Override
      public void serializeContent(T credential, JsonGenerator generator)
         throws IOException
      {
         super.serializeContent(credential, generator);

         if(credential.getApiKey() != null) {
            generator.writeStringField("api_key", credential.getApiKey());
         }

         if(credential.getGlobalCompanyId() != null) {
            generator.writeStringField("global_company_id", credential.getGlobalCompanyId());
         }
      }
   }

   public static class Deserializer<T extends CloudAdobeAnalyticsCredential> extends AbstractCloudCredential.Deserializer<T> {
      public Deserializer() {
         super((Class<T>) CloudAdobeAnalyticsCredential.class);
      }

      @Override
      protected void deserializeContent(JsonNode node, T credential) {
         super.deserializeContent(node, credential);

         if(node.get("api_key") != null) {
            credential.setApiKey(node.get("api_key").textValue());
         }

         if(node.get("global_company_id") != null) {
            credential.setGlobalCompanyId(node.get("global_company_id").textValue());
         }
      }
   }

   private String apiKey;
   private String globalCompanyId;
}
