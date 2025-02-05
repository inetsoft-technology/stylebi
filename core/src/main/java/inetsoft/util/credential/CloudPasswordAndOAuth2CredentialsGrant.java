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

@JsonSerialize(using = CloudPasswordAndOAuth2CredentialsGrant.Serializer.class)
@JsonDeserialize(using = CloudPasswordAndOAuth2CredentialsGrant.Deserializer.class)
public class CloudPasswordAndOAuth2CredentialsGrant extends CloudOAuth2CredentialsGrant
   implements PasswordAndOAuth2CredentialsGrant
{
   public CloudPasswordAndOAuth2CredentialsGrant() {
      super();
   }

   @Override
   public String getUser() {
      return user;
   }

   @Override
   public void setUser(String user) {
      this.user = user;
   }

   @Override
   public String getPassword() {
      return password;
   }

   @Override
   public void setPassword(String password) {
      this.password = password;
   }

   @Override
   public boolean isEmpty() {
      return super.isEmpty() && StringUtils.isEmpty(user) && StringUtils.isEmpty(password);
   }

   @Override
   public void reset() {
      super.reset();
      user = null;
      password = null;
   }

   @Override
   public boolean equals(Object obj) {
      if(!super.equals(obj)) {
         return false;
      }

      if(!(obj instanceof CloudPasswordAndOAuth2CredentialsGrant)) {
         return false;
      }

      return Tool.equals(((CloudPasswordAndOAuth2CredentialsGrant) obj).user, user) &&
         Tool.equals(((CloudPasswordAndOAuth2CredentialsGrant) obj).password, password);
   }

   @Override
   public void refreshCredential(Credential credential) {
      super.refreshCredential(credential);
      CloudPasswordAndOAuth2CredentialsGrant cloudCredential = (CloudPasswordAndOAuth2CredentialsGrant) credential;
      setUser(cloudCredential.getUser());
      setPassword(cloudCredential.getPassword());
   }

   public static class Serializer<T extends CloudPasswordAndOAuth2CredentialsGrant>
      extends CloudOAuth2CredentialsGrant.Serializer<T>
   {
      public Serializer() {
         super((Class<T>) CloudPasswordAndOAuth2CredentialsGrant.class);
      }

      public Serializer(Class<T> tClass) {
         super(tClass);
      }

      @Override
      protected void serializeContent(T credential, JsonGenerator generator) throws IOException {
         super.serializeContent(credential, generator);

         if(credential.getUser() != null) {
            generator.writeStringField("user", credential.getUser());
         }

         if(credential.getPassword() != null) {
            generator.writeStringField("password", credential.getPassword());
         }
      }
   }

   public static class Deserializer<T extends CloudPasswordAndOAuth2CredentialsGrant>
      extends CloudOAuth2CredentialsGrant.Deserializer<T>
   {
      public Deserializer() {
         super((Class<T>) CloudPasswordAndOAuth2CredentialsGrant.class);
      }

      public Deserializer(Class<T> tClass) {
         super(tClass);
      }

      @Override
      protected void deserializeContent(JsonNode node, T credential) {
         super.deserializeContent(node, credential);

         if(node.get("user") != null) {
            credential.setUser(node.get("user").textValue());
         }

         if(node.get("password") != null) {
            credential.setPassword(node.get("password").textValue());
         }
      }
   }

   private String user;
   private String password;
}
