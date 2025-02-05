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

@JsonSerialize(using = CloudPasswordAndOAuth2WithFlagCredentialsGrant.Serializer.class)
@JsonDeserialize(using = CloudPasswordAndOAuth2WithFlagCredentialsGrant.Deserializer.class)
public class CloudPasswordAndOAuth2WithFlagCredentialsGrant extends CloudPasswordAndOAuth2CredentialsGrant
   implements PasswordAndOAuth2WithFlagCredentialsGrant
{
   public CloudPasswordAndOAuth2WithFlagCredentialsGrant() {
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

      if(!(obj instanceof CloudPasswordAndOAuth2WithFlagCredentialsGrant)) {
         return false;
      }

      return Tool.equals(((CloudPasswordAndOAuth2WithFlagCredentialsGrant) obj).oauthFlags, oauthFlags);
   }

   @Override
   public void refreshCredential(Credential credential) {
      super.refreshCredential(credential);
      CloudPasswordAndOAuth2WithFlagCredentialsGrant cloudCredential = (CloudPasswordAndOAuth2WithFlagCredentialsGrant) credential;
      setOauthFlags(cloudCredential.getOauthFlags());
   }

   public static class Serializer<T extends CloudPasswordAndOAuth2WithFlagCredentialsGrant>
      extends CloudPasswordAndOAuth2CredentialsGrant.Serializer<T>
   {
      public Serializer() {
         super((Class<T>) CloudPasswordAndOAuth2WithFlagCredentialsGrant.class);
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

   public static class Deserializer<T extends CloudPasswordAndOAuth2WithFlagCredentialsGrant>
      extends CloudPasswordAndOAuth2CredentialsGrant.Deserializer<T>
   {
      public Deserializer() {
         super((Class<T>) CloudPasswordAndOAuth2WithFlagCredentialsGrant.class);
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
