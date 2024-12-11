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

@JsonSerialize(using = CloudSecurityTokenCredential.Serializer.class)
@JsonDeserialize(using = CloudSecurityTokenCredential.Deserializer.class)
public class CloudSecurityTokenCredential extends CloudPasswordCredential
   implements CloudCredential, SecurityTokenCredential
{
   public CloudSecurityTokenCredential() {
      super();
   }

   @Override
   public String getSecurityToken() {
      return securityToken;
   }

   @Override
   public void setSecurityToken(String securityToken) {
      this.securityToken = securityToken;
   }

   @Override
   public boolean isEmpty() {
      return super.isEmpty() && StringUtils.isEmpty(securityToken) ;
   }

   @Override
   public void reset() {
      super.reset();
      securityToken = null;
   }

   @Override
   public boolean equals(Object obj) {
      if(!super.equals(obj) || !(obj instanceof CloudSecurityTokenCredential)) {
         return false;
      }

      return Tool.equals(((CloudSecurityTokenCredential) obj).securityToken, securityToken);
   }

   @Override
   public void refreshCredential(Credential credential) {
      super.refreshCredential(credential);
      CloudSecurityTokenCredential cloudCredential = (CloudSecurityTokenCredential) credential;
      setSecurityToken(cloudCredential.getSecurityToken());
   }

   public static class Serializer<T extends CloudSecurityTokenCredential>
      extends CloudPasswordCredential.Serializer<T>
   {
      public Serializer(Class<T> tClass) {
         super(tClass);
      }

      @Override
      protected void serializeContent(T credential, JsonGenerator generator) throws IOException {
         super.serializeContent(credential, generator);

         if(credential.getSecurityToken() != null) {
            generator.writeStringField("security_token", credential.getSecurityToken());
         }
      }
   }

   public static class Deserializer<T extends CloudSecurityTokenCredential>
      extends CloudPasswordCredential.Deserializer<T>
   {
      public Deserializer() {
         super((Class<T>) CloudSecurityTokenCredential.class);
      }

      public Deserializer(Class<T> tClass) {
         super(tClass);
      }

      @Override
      protected void deserializeContent(JsonNode node, T credential) {
         super.deserializeContent(node, credential);

         if(node.get("security_token") != null) {
            credential.setSecurityToken(node.get("security_token").textValue());
         }
      }
   }

   private String securityToken;
}
