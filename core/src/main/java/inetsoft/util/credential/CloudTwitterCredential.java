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


@JsonSerialize(using = CloudTwitterCredential.Serializer.class)
@JsonDeserialize(using = CloudTwitterCredential.Deserializer.class)
public class CloudTwitterCredential extends AbstractCloudCredential
   implements CloudCredential, TwitterCredential
{
   public CloudTwitterCredential() {
      super();
   }

   public String getOauthToken() {
      return oauthToken;
   }

   public void setOauthToken(String oauthToken) {
      this.oauthToken = oauthToken;
   }

   public String getTokenSecret() {
      return tokenSecret;
   }

   public void setTokenSecret(String tokenSecret) {
      this.tokenSecret = tokenSecret;
   }

   public String getConsumerKey() {
      return consumerKey;
   }

   public void setConsumerKey(String consumerKey) {
      this.consumerKey = consumerKey;
   }

   public String getConsumerSecret() {
      return consumerSecret;
   }

   public void setConsumerSecret(String consumerSecret) {
      this.consumerSecret = consumerSecret;
   }

   @Override
   public boolean isEmpty() {
      return super.isEmpty() && StringUtils.isEmpty(oauthToken) && StringUtils.isEmpty(tokenSecret)
         && StringUtils.isEmpty(consumerKey) && StringUtils.isEmpty(consumerSecret);
   }

   @Override
   public void reset() {
      super.reset();
      oauthToken = null;
      tokenSecret = null;
      consumerKey = null;
      consumerSecret = null;
   }

   @Override
   public boolean equals(Object obj) {
      if(!super.equals(obj)) {
         return false;
      }

      if(!(obj instanceof CloudTwitterCredential)) {
         return false;
      }

      return Tool.equals(((CloudTwitterCredential) obj).oauthToken, oauthToken) &&
         Tool.equals(((CloudTwitterCredential) obj).tokenSecret, tokenSecret) &&
         Tool.equals(((CloudTwitterCredential) obj).consumerKey, consumerKey) &&
         Tool.equals(((CloudTwitterCredential) obj).consumerSecret, consumerSecret);
   }

   @Override
   public void refreshCredential(Credential credential) {
      CloudTwitterCredential cloudCredential = (CloudTwitterCredential) credential;
      setOauthToken(cloudCredential.getOauthToken());
      setTokenSecret(cloudCredential.getTokenSecret());
      setConsumerKey(cloudCredential.getConsumerKey());
      setConsumerSecret(cloudCredential.getConsumerSecret());
   }

   @Override
   public Credential createLocal() {
      return new LocalTwitterCredential();
   }

   @Override
   public void copyToLocal(Credential credential) {
      if(credential instanceof LocalTwitterCredential localCredential) {
         localCredential.setOauthToken(oauthToken);
         localCredential.setTokenSecret(tokenSecret);
         localCredential.setConsumerKey(consumerKey);
         localCredential.setConsumerSecret(consumerSecret);
      }
   }

   public static class Serializer extends AbstractCloudCredential.Serializer<CloudTwitterCredential>
   {
      public Serializer() {
         super(CloudTwitterCredential.class);
      }

      @Override
      public void serialize(CloudTwitterCredential credential, JsonGenerator generator,
                            SerializerProvider provider)
         throws IOException
      {
         generator.writeStartObject();
         generator.writeStringField("oauth_token", credential.getOauthToken());
         generator.writeStringField("token_secret", credential.getTokenSecret());
         generator.writeStringField("consumer_key", credential.getConsumerKey());
         generator.writeStringField("consumer_secret", credential.getConsumerSecret());
         generator.writeEndObject();
      }
   }

   public static class Deserializer extends AbstractCloudCredential.Deserializer<CloudTwitterCredential> {
      public Deserializer() {
         super(CloudTwitterCredential.class);
      }

      @Override
      protected void deserializeContent(JsonNode node, CloudTwitterCredential credential) {
         super.deserializeContent(node, credential);

         if(node.get("oauth_token") != null) {
            credential.setOauthToken(node.get("oauth_token").textValue());
         }

         if(node.get("token_secret") != null) {
            credential.setTokenSecret(node.get("token_secret").textValue());
         }

         if(node.get("consumer_key") != null) {
            credential.setConsumerKey(node.get("consumer_key").textValue());
         }

         if(node.get("consumer_secret") != null) {
            credential.setConsumerSecret(node.get("consumer_secret").textValue());
         }
      }
   }
   
   private String oauthToken;
   // twitter allows token secret to be empty string when signing requests
   private String tokenSecret = "";
   private String consumerKey;
   private String consumerSecret;
}