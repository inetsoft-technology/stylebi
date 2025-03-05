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
@JsonSerialize(using = CloudAccountSecretCrendential.Serializer.class)
@JsonDeserialize(using = CloudAccountSecretCrendential.Deserializer.class)
public class CloudAccountSecretCrendential extends AbstractCloudCredential
   implements CloudCredential, AccountSecretCrendential
{
   public CloudAccountSecretCrendential() {
      super();
   }

   public String getAccountToken() {
      return accountToken;
   }

   public void setAccountToken(String accountToken) {
      this.accountToken = accountToken;
   }

   public String getSecretKey() {
      return secretKey;
   }

   public void setSecretKey(String secretKey) {
      this.secretKey = secretKey;
   }

   @Override
   public Credential createLocal() {
      return new LocalAccountSecretCrendential();

   }

   @Override
   public void copyToLocal(Credential credential) {
      if(credential instanceof LocalAccountSecretCrendential localCrendential) {
         localCrendential.setSecretKey(secretKey);
         localCrendential.setAccountToken(accountToken);
      }
   }

   @Override
   public boolean isEmpty() {
      return super.isEmpty() && StringUtils.isEmpty(accountToken) && StringUtils.isEmpty(secretKey);
   }

   @Override
   public void reset() {
      super.reset();
      accountToken = null;
      secretKey = null;
   }

   @Override
   public boolean equals(Object obj) {
      if(!super.equals(obj)) {
         return false;
      }

      if(!(obj instanceof CloudAccountSecretCrendential)) {
         return false;
      }

      return Tool.equals(((CloudAccountSecretCrendential) obj).accountToken, accountToken) &&
         Tool.equals(((CloudAccountSecretCrendential) obj).secretKey, secretKey);
   }

   @Override
   public void refreshCredential(Credential credential) {
      super.refreshCredential(credential);
      CloudAccountSecretCrendential credential0 = (CloudAccountSecretCrendential) credential;
      setAccountToken(credential0.getAccountToken());
      setSecretKey(credential0.getSecretKey());
   }

   public static class Serializer<T extends CloudAccountSecretCrendential>
      extends AbstractCloudCredential.Serializer<T>
   {
      public Serializer(Class<T> tClass) {
         super(tClass);
      }

      @Override
      protected void serializeContent(T credential, JsonGenerator generator) throws IOException {
         super.serializeContent(credential, generator);

         if(credential.getAccountToken() != null) {
            generator.writeStringField("account_token", credential.getAccountToken());
         }

         if(credential.getSecretKey() != null) {
            generator.writeStringField("secret_key", credential.getSecretKey());
         }
      }
   }

   public static class Deserializer<T extends CloudAccountSecretCrendential>
      extends AbstractCloudCredential.Deserializer<T>
   {
      public Deserializer() {
         super((Class<T>) CloudAccountSecretCrendential.class);
      }

      public Deserializer(Class<T> tClass) {
         super(tClass);
      }

      @Override
      protected void deserializeContent(JsonNode node, T credential) {
         super.deserializeContent(node, credential);

         if(node.get("account_token") != null) {
            credential.setAccountToken(node.get("account_token").textValue());
         }

         if(node.get("secret_key") != null) {
            credential.setSecretKey(node.get("secret_key").textValue());
         }
      }
   }

   private String accountToken;
   private String secretKey;
}
