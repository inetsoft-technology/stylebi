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

@JsonSerialize(using = CloudSignatureCredential.Serializer.class)
@JsonDeserialize(using = CloudSignatureCredential.Deserializer.class)
public class CloudSignatureCredential extends AbstractCloudCredential
   implements CloudCredential, SignatureCredential
{
   public CloudSignatureCredential() {
      super();
   }

   public String getAccountKey() {
      return accountKey;
   }

   public void setAccountKey(String accountKey) {
      this.accountKey = accountKey;
   }

   @Override
   public String getSignature() {
      return signature;
   }

   @Override
   public void setSignature(String signature) {
      this.signature = signature;
   }

   @Override
   public boolean isEmpty() {
      return super.isEmpty() && StringUtils.isEmpty(accountKey) && StringUtils.isEmpty(signature);
   }

   @Override
   public void reset() {
      super.reset();
      accountKey = null;
      signature = null;
   }

   @Override
   public boolean equals(Object obj) {
      if(!super.equals(obj)) {
         return false;
      }

      if(!(obj instanceof CloudSignatureCredential)) {
         return false;
      }

      return Tool.equals(((CloudSignatureCredential) obj).accountKey, accountKey) &&
         Tool.equals(((CloudSignatureCredential) obj).signature, signature);
   }

   @Override
   public void refreshCredential(Credential credential) {
      super.refreshCredential(credential);
      CloudSignatureCredential credential0 = (CloudSignatureCredential) credential;
      setAccountKey(credential0.getAccountKey());
      setSignature(credential0.getSignature());
   }

   public static class Serializer<T extends CloudSignatureCredential>
      extends AbstractCloudCredential.Serializer<T>
   {
      public Serializer(Class<T> tClass) {
         super(tClass);
      }

      @Override
      protected void serializeContent(T credential, JsonGenerator generator) throws IOException {
         super.serializeContent(credential, generator);

         if(credential.getAccountKey() != null) {
            generator.writeStringField("account_key", credential.getAccountKey());
         }

         if(credential.getSignature() != null) {
            generator.writeStringField("signature", credential.getSignature());
         }
      }
   }

   public static class Deserializer<T extends CloudSignatureCredential>
      extends AbstractCloudCredential.Deserializer<T>
   {
      public Deserializer() {
         super((Class<T>) CloudSignatureCredential.class);
      }

      public Deserializer(Class<T> tClass) {
         super(tClass);
      }

      @Override
      protected void deserializeContent(JsonNode node, T credential) {
         super.deserializeContent(node, credential);

         if(node.get("account_key") != null) {
            credential.setAccountKey(node.get("account_key").textValue());
         }

         if(node.get("signature") != null) {
            credential.setSignature(node.get("signature").textValue());
         }
      }
   }

   private String accountKey;
   private String signature;
}
