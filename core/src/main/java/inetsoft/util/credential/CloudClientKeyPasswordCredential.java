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

@JsonSerialize(using = CloudClientKeyPasswordCredential.Serializer.class)
@JsonDeserialize(using = CloudClientKeyPasswordCredential.Deserializer.class)
public class CloudClientKeyPasswordCredential extends CloudPasswordCredential
   implements CloudCredential, ClientKeyPasswordCredential
{
   public CloudClientKeyPasswordCredential() {
      super();
   }

   public String getClientKey() {
      return clientKey;
   }

   public void setClientKey(String clientKey) {
      this.clientKey = clientKey;
   }

   @Override
   public boolean isEmpty() {
      return super.isEmpty() && StringUtils.isEmpty(clientKey);
   }

   @Override
   public void reset() {
      super.reset();
      clientKey = null;
   }

   @Override
   public boolean equals(Object obj) {
      if(!super.equals(obj)) {
         return false;
      }

      if(!(obj instanceof CloudClientKeyPasswordCredential)) {
         return false;
      }

      return Tool.equals(((CloudClientKeyPasswordCredential) obj).clientKey, clientKey);
   }

   @Override
   public void refreshCredential(Credential credential) {
      super.refreshCredential(credential);
      CloudClientKeyPasswordCredential credential0 = (CloudClientKeyPasswordCredential) credential;
      setClientKey(credential0.getClientKey());
   }

   @Override
   public Credential createLocal() {
      return new LocalClientKeyPasswordCredential();
   }

   @Override
   public void copyToLocal(Credential credential) {
      if(credential instanceof LocalClientKeyPasswordCredential localCredential) {
         localCredential.setClientKey(clientKey);
         super.copyToLocal(localCredential);
      }
   }

   public static class Serializer<T extends CloudClientKeyPasswordCredential>
      extends CloudPasswordCredential.Serializer<T>
   {
      public Serializer(Class<T> tClass) {
         super(tClass);
      }

      @Override
      protected void serializeContent(T credential, JsonGenerator generator) throws IOException {
         super.serializeContent(credential, generator);

         if(credential.getClientKey() != null) {
            generator.writeStringField("client_key", credential.getClientKey());
         }
      }
   }

   public static class Deserializer<T extends CloudClientKeyPasswordCredential>
      extends CloudPasswordCredential.Deserializer<T>
   {
      public Deserializer() {
         super((Class<T>) CloudClientKeyPasswordCredential.class);
      }

      public Deserializer(Class<T> tClass) {
         super(tClass);
      }

      @Override
      protected void deserializeContent(JsonNode node, T credential) {
         super.deserializeContent(node, credential);

         if(node.get("client_key") != null) {
            credential.setClientKey(node.get("client_key").textValue());
         }
      }
   }

   private String clientKey;
}
