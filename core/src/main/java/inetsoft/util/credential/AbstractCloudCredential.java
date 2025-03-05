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
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import inetsoft.util.PasswordEncryption;
import inetsoft.util.Tool;
import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.Element;

import java.io.IOException;
import java.io.PrintWriter;

public abstract class AbstractCloudCredential extends AbstractCredential implements CloudCredential {
   @Override
   public boolean isEmpty() {
      return StringUtils.isEmpty(id);
   }

   @Override
   public String getId() {
      return id;
   }

   @Override
   public void setId(String id) {
      this.id = id;
   }

   @Override
   public void setDBType(String dbType) {
      this.dbType = dbType;
   }

   @Override
   public String getDBType() {
      return dbType;
   }

   @Override
   public boolean equals(Object obj) {
      if(!(obj instanceof AbstractCloudCredential)) {
         return false;
      }

      return Tool.equals(((AbstractCloudCredential) obj).id, id);
   }

   @Override
   public void refreshCredential(Credential credential) {
      if(credential instanceof CloudCredential) {
         setId(credential.getId());
      }
   }

   /**
    * Convert the current cloud credential to local credential.
    */
   private Credential convertToLocal() {
      Credential local = createLocal();

      if(local != null) {
         copyToLocal(local);
      }

      return local;
   }

   public void writeXML(PrintWriter writer) {
      if(isEmpty()) {
         return;
      }

      if(PasswordEncryption.isEncryptForceLocal()) {
         Credential newCredential = Tool.decryptPasswordToCredential(
            getId(), getClass(), getDBType());

         if(newCredential != null) {
            newCredential.setId(getId());
            refreshCredential(newCredential);
         }

         Credential localCredential = convertToLocal();

         if(localCredential != null) {
            localCredential.writeXML(writer);
            return;
         }
      }

      StringBuilder builder = new StringBuilder();
      builder.append("<PasswordCredential cloud=\"true\"");
      builder.append(" class=\"");
      builder.append(this.getClass().getName());
      builder.append("\" id=\"");
      builder.append(getId());
      builder.append("\" dbType=\"");
      builder.append(getDBType() != null && !getDBType().isEmpty() ? getDBType() : "");
      builder.append("\">");
      builder.append("</PasswordCredential>");
      writer.write(builder.toString());
   }

   public void parseXML(Element elem) throws Exception {
      if(elem == null) {
         return;
      }

      setId(elem.getAttribute("id"));
      setDBType(elem.getAttribute("dbType"));
      fetchCredential();
   }

   public static class Serializer<T extends AbstractCloudCredential> extends StdSerializer<T> {
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
      }
   }

   public static class Deserializer<T extends AbstractCloudCredential> extends StdDeserializer<T> {
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
      }
   }

   private String id;
   private String dbType;;
}
