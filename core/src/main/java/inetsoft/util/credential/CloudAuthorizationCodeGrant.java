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

@JsonSerialize(using = CloudAuthorizationCodeGrant.Serializer.class)
@JsonDeserialize(using = CloudAuthorizationCodeGrant.Deserializer.class)
public class CloudAuthorizationCodeGrant extends CloudClientCredentialsGrant
   implements AuthorizationCodeGrant
{
   public CloudAuthorizationCodeGrant() {
      super();
   }

   @Override
   public Credential createLocal() {
      return new LocalAuthorizationCodeGrant();
   }

   @Override
   public void copyToLocal(Credential credential) {
      if(credential instanceof LocalAuthorizationCodeGrant localCredential) {
         localCredential.setAccountDomain(accountDomain);
         localCredential.setAuthorizationCode(authorizationCode);
         super.copyToLocal(credential);
      }
   }

   @Override
   public String getAuthorizationCode() {
      return authorizationCode;
   }

   @Override
   public void setAuthorizationCode(String authorizationCode) {
      this.authorizationCode = authorizationCode;
   }

   @Override
   public String getAccountDomain() {
      return this.accountDomain;
   }

   @Override
   public void setAccountDomain(String accountDomain) {
      this.accountDomain = accountDomain;
   }

   @Override
   public boolean isEmpty() {
      return super.isEmpty() &&
         StringUtils.isEmpty(authorizationCode) && StringUtils.isEmpty(accountDomain);
   }

   @Override
   public void reset() {
      super.reset();
      authorizationCode = null;
      accountDomain = null;
   }

   @Override
   public boolean equals(Object obj) {
      if(!super.equals(obj) || !(obj instanceof CloudAuthorizationCodeGrant)) {
         return false;
      }

      return Tool.equals(((CloudAuthorizationCodeGrant) obj).authorizationCode, authorizationCode)
         && Tool.equals(((CloudAuthorizationCodeGrant) obj).accountDomain, accountDomain);
   }

   @Override
   public void refreshCredential(Credential credential) {
      super.refreshCredential(credential);
      CloudAuthorizationCodeGrant cloudCredential = (CloudAuthorizationCodeGrant) credential;
      setAuthorizationCode(cloudCredential.getAuthorizationCode());
      setAccountDomain(cloudCredential.getAccountDomain());
   }

   public static class Serializer<T extends CloudAuthorizationCodeGrant>
      extends CloudClientCredentialsGrant.Serializer<T>
   {
      public Serializer() {
         super((Class<T>) CloudAuthorizationCodeGrant.class);
      }

      public Serializer(Class<T> tClass) {
         super(tClass);
      }

      @Override
      protected void serializeContent(T credential, JsonGenerator generator) throws IOException {
         super.serializeContent(credential, generator);

         if(credential.getAuthorizationCode() != null) {
            generator.writeStringField("authorization_code", credential.getAuthorizationCode());
         }

         if(credential.getAccountDomain() != null) {
            generator.writeStringField("account_domain", credential.getAccountDomain());
         }
      }
   }

   public static class Deserializer<T extends CloudAuthorizationCodeGrant>
      extends CloudClientCredentialsGrant.Deserializer<T>
   {
      public Deserializer() {
         super((Class<T>) CloudAuthorizationCodeGrant.class);
      }

      public Deserializer(Class<T> tClass) {
         super(tClass);
      }

      @Override
      protected void deserializeContent(JsonNode node, T credential) {
         super.deserializeContent(node, credential);

         if(node.get("authorization_code") != null) {
            credential.setAuthorizationCode(node.get("authorization_code").textValue());
         }

         if(node.get("account_domain") != null) {
            credential.setAccountDomain(node.get("account_domain").textValue());
         }
      }
   }

   private String authorizationCode;
   private String accountDomain;
}
