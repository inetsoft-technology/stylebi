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
import org.thymeleaf.util.StringUtils;

import java.io.IOException;

@JsonSerialize(using = CloudAuthTokensCredential.Serializer.class)
@JsonDeserialize(using = CloudAuthTokensCredential.Deserializer.class)
public class CloudAuthTokensCredential extends AbstractCloudCredential implements AuthTokensCredential {
   public CloudAuthTokensCredential() {
      super();
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
   public String getRefreshToken() {
      return refreshToken;
   }

   @Override
   public void setRefreshToken(String refreshToken) {
      this.refreshToken = refreshToken;
   }

   @Override
   public void refreshCredential(Credential credential) {
      super.refreshCredential(credential);
      CloudAuthTokensCredential tokensCredential = (CloudAuthTokensCredential) credential;
      setAccessToken(tokensCredential.getAccessToken());
      setRefreshToken(tokensCredential.getRefreshToken());
   }

   @Override
   public boolean isEmpty() {
      return super.isEmpty() && StringUtils.isEmpty(accessToken) &&
         StringUtils.isEmpty(refreshToken);
   }

   @Override
   public boolean equals(Object obj) {
      if(!(obj instanceof CloudAuthTokensCredential)) {
         return false;
      }

      return Tool.equals(((CloudAuthTokensCredential) obj).accessToken, accessToken) &&
         Tool.equals(((CloudAuthTokensCredential) obj).refreshToken, refreshToken);
   }

   public static class Serializer<T extends CloudAuthTokensCredential> extends AbstractCloudCredential.Serializer<T> {
      public Serializer() {
         super((Class<T>) CloudAuthTokensCredential.class);
      }

      @Override
      public void serializeContent(T credential, JsonGenerator generator)
         throws IOException
      {
         super.serializeContent(credential, generator);

         if(credential.getAccessToken() != null) {
            generator.writeStringField("access_token", credential.getAccessToken());
         }

         if(credential.getRefreshToken() != null) {
            generator.writeStringField("refresh_token", credential.getRefreshToken());
         }
      }
   }

   public static class Deserializer<T extends CloudAuthTokensCredential> extends AbstractCloudCredential.Deserializer<T> {
      public Deserializer() {
         super((Class<T>) CloudAuthTokensCredential.class);
      }

      @Override
      protected void deserializeContent(JsonNode node, T credential) {
         super.deserializeContent(node, credential);

         if(node.get("access_token") != null) {
            credential.setAccessToken(node.get("access_token").textValue());
         }

         if(node.get("refresh_token") != null) {
            credential.setRefreshToken(node.get("refresh_token").textValue());
         }
      }
   }

   private String accessToken;
   private String refreshToken;
}
