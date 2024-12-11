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

@JsonSerialize(using = CloudSiteTokenCredential.Serializer.class)
@JsonDeserialize(using = CloudSiteTokenCredential.Deserializer.class)
public class CloudSiteTokenCredential extends CloudApiKeyCredential
   implements CloudCredential, SiteTokenCredential
{
   public CloudSiteTokenCredential() {
      super();
   }

   public String getSiteToken() {
      return siteToken;
   }

   public void setSiteToken(String siteToken) {
      this.siteToken = siteToken;
   }

   @Override
   public boolean isEmpty() {
      return super.isEmpty() && StringUtils.isEmpty(siteToken);
   }

   @Override
   public void reset() {
      super.reset();
      siteToken = null;
   }

   @Override
   public boolean equals(Object obj) {
      if(!super.equals(obj)) {
         return false;
      }

      if(!(obj instanceof CloudSiteTokenCredential)) {
         return false;
      }

      return Tool.equals(((CloudSiteTokenCredential) obj).siteToken, siteToken);
   }

   private String siteToken;

   @Override
   public void refreshCredential(Credential credential) {
      super.refreshCredential(credential);
      CloudSiteTokenCredential cloudCredential = (CloudSiteTokenCredential) credential;
      setSiteToken(cloudCredential.getSiteToken());
   }

   public static class Serializer<T extends CloudSiteTokenCredential>
      extends CloudApiKeyCredential.Serializer<T>
   {
      public Serializer() {
         super((Class<T>) CloudSiteTokenCredential.class);
      }

      @Override
      protected void serializeContent(T credential,
                                      JsonGenerator generator)
         throws IOException
      {
         super.serializeContent(credential, generator);

         if(credential.getSiteToken() != null) {
            generator.writeStringField("site_token", credential.getSiteToken());
         }
      }
   }

   public static class Deserializer<T extends CloudSiteTokenCredential>
      extends CloudApiKeyCredential.Deserializer<T>
   {
      public Deserializer() {
         super((Class<T>) CloudSiteTokenCredential.class);
      }

      @Override
      protected void deserializeContent(JsonNode node, T credential) {
         super.deserializeContent(node, credential);

         if(node.get("site_token") != null) {
            credential.setSiteToken(node.get("site_token").textValue());
         }
      }
   }
}
