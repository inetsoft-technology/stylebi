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
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import inetsoft.util.Tool;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

@JsonSerialize(using = CloudPasswordCredential.Serializer.class)
@JsonDeserialize(using = CloudPasswordCredential.Deserializer.class)
public class CloudPasswordCredential extends AbstractCloudCredential
   implements PasswordCredential, CloudCredential
{
   public CloudPasswordCredential() {
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
   public void refreshCredential(Credential credential) {
      super.refreshCredential(credential);

      if(credential instanceof CloudPasswordCredential) {
         setUser(((CloudPasswordCredential) credential).getUser());
         setPassword(((CloudPasswordCredential) credential).getPassword());
      }
   }

   @Override
   public void reset() {
      super.reset();
      user = "";
      password = "";
   }

   @Override
   public boolean equals(Object obj) {
      if(!super.equals(obj) || !(obj instanceof CloudPasswordCredential)) {
         return false;
      }

      return Tool.equals(((CloudPasswordCredential) obj).user, user) &&
         Tool.equals(((CloudPasswordCredential) obj).password, password);
   }

   @Override
   public boolean isEmpty() {
      return StringUtils.isEmpty(getId()) &&
         StringUtils.isEmpty(getUser()) && StringUtils.isEmpty(getPassword());
   }

   @Override
   public Credential createLocal() {
      return new LocalPasswordCredential();
   }

   @Override
   public void copyToLocal(Credential credential) {
      if(credential instanceof LocalPasswordCredential localCredential) {
         localCredential.setUser(getUser());
         localCredential.setPassword(getPassword());
      }
   }

   public static class Serializer<T extends CloudPasswordCredential> extends StdSerializer<T> {
      public Serializer() {
         super((Class<T>) CloudPasswordCredential.class);
      }

      public Serializer(Class<T> tClass) {
         super(tClass);
      }

      @Override
      public void serialize(T credential, JsonGenerator generator, SerializerProvider provider)
         throws IOException
      {
         generator.writeStartObject();
         serializeContent(credential, generator);
         generator.writeEndObject();
      }

      protected void serializeContent(T credential, JsonGenerator generator) throws IOException {
         generator.writeStringField("user", credential.getUser());
         generator.writeStringField("password", credential.getPassword());
      }
   }

   public static class Deserializer<T extends CloudPasswordCredential> extends StdDeserializer<T> {
      public Deserializer() {
         super(CloudPasswordCredential.class);
      }

      public Deserializer(Class<T> tClass) {
         super(tClass);
      }

      @Override
      public T deserialize(JsonParser parser, DeserializationContext context) throws IOException {
         JsonNode node = parser.getCodec().readTree(parser);
         T credential = null;

         try {
            credential = (T) handledType().getDeclaredConstructor().newInstance();
         }
         catch(Exception e) {
            throw new IOException("Failed to create instance of " + handledType().getName(), e);
         }

         deserializeContent(node, credential);

         return credential;
      }

      protected void deserializeContent(JsonNode node, T credential) {
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
   private static final Logger LOG = LoggerFactory.getLogger(CloudPasswordCredential.class);
}
