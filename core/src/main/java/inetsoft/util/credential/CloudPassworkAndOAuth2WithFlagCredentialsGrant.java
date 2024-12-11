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

@JsonSerialize(using = CloudPassworkAndOAuth2WithFlagCredentialsGrant.Serializer.class)
@JsonDeserialize(using = CloudPassworkAndOAuth2WithFlagCredentialsGrant.Deserializer.class)
public class CloudPassworkAndOAuth2WithFlagCredentialsGrant extends CloudPassworkAndOAuth2CredentialsGrant
   implements PassworkAndOAuth2WithFlagCredentialsGrant
{
   public CloudPassworkAndOAuth2WithFlagCredentialsGrant() {
      super();
   }

   @Override
   public String getOauthFlags() {
      return oauthFlags;
   }

   @Override
   public void setOauthFlags(String oauthFlags) {
      this.oauthFlags = oauthFlags;
   }

   @Override
   public boolean isEmpty() {
      return super.isEmpty() && StringUtils.isEmpty(oauthFlags);
   }

   @Override
   public void reset() {
      super.reset();
      oauthFlags = null;
   }

   @Override
   public boolean equals(Object obj) {
      if(!super.equals(obj)) {
         return false;
      }

      if(!(obj instanceof CloudPassworkAndOAuth2WithFlagCredentialsGrant)) {
         return false;
      }

      return Tool.equals(((CloudPassworkAndOAuth2WithFlagCredentialsGrant) obj).oauthFlags, oauthFlags);
   }

   @Override
   public void refreshCredential(Credential credential) {
      super.refreshCredential(credential);
      CloudPassworkAndOAuth2WithFlagCredentialsGrant cloudCredential = (CloudPassworkAndOAuth2WithFlagCredentialsGrant) credential;
      setOauthFlags(cloudCredential.getOauthFlags());
   }

   public static class Serializer<T extends CloudPassworkAndOAuth2WithFlagCredentialsGrant>
      extends CloudPassworkAndOAuth2CredentialsGrant.Serializer<T>
   {
      public Serializer() {
         super((Class<T>) CloudPassworkAndOAuth2WithFlagCredentialsGrant.class);
      }

      public Serializer(Class<T> tClass) {
         super(tClass);
      }

      @Override
      protected void serializeContent(T credential, JsonGenerator generator) throws IOException {
         super.serializeContent(credential, generator);

         if(credential.getOauthFlags() != null) {
            generator.writeStringField("oauth_flags", credential.getOauthFlags());
         }
      }
   }

   public static class Deserializer<T extends CloudPassworkAndOAuth2WithFlagCredentialsGrant>
      extends CloudPassworkAndOAuth2CredentialsGrant.Deserializer<T>
   {
      public Deserializer() {
         super((Class<T>) CloudPassworkAndOAuth2WithFlagCredentialsGrant.class);
      }

      public Deserializer(Class<T> tClass) {
         super(tClass);
      }

      @Override
      protected void deserializeContent(JsonNode node, T credential) {
         super.deserializeContent(node, credential);

         if(node.get("oauth_flags") != null) {
            credential.setOauthFlags(node.get("oauth_flags").textValue());
         }
      }
   }

   private String oauthFlags;
}
