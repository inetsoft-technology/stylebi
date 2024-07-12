/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.storage;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.Organization;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.uql.XPrincipal;
import inetsoft.util.ThreadContext;
import org.apache.commons.io.FileUtils;

import java.io.*;
import java.nio.file.*;
import java.security.Principal;
import java.util.Iterator;
import java.util.stream.Stream;
import java.util.zip.*;

public abstract class AbstractStorageTransfer implements StorageTransfer {

   @Override
   public final void exportContents(OutputStream output) throws IOException {
      Path work = Files.createTempDirectory("storage-export");

      try {
         Path blobDir = work.resolve("blobs");
         Files.createDirectories(blobDir);

         ObjectMapper objectMapper = KeyValueEngine.createObjectMapper();

         try(OutputStream kvOutput = Files.newOutputStream(work.resolve("key-value-index.json"));
             OutputStream blobOutput = Files.newOutputStream((work.resolve("blob-index.json"))))
         {
            JsonGenerator kvGenerator = objectMapper.getFactory().createGenerator(kvOutput);
            JsonGenerator blobGenerator = objectMapper.getFactory().createGenerator(blobOutput);

            export(kvGenerator, blobGenerator, blobDir);

            kvGenerator.flush();
            blobGenerator.flush();
         }

         ZipOutputStream zip = new ZipOutputStream(output);

         zip.putNextEntry(new ZipEntry("key-value-index.json"));
         Files.copy(work.resolve("key-value-index.json"), zip);

         zip.putNextEntry(new ZipEntry("blob-index.json"));
         Files.copy(work.resolve("blob-index.json"), zip);

         zip.putNextEntry(new ZipEntry("blobs/"));

         File[] dirs = blobDir.toFile().listFiles();

         if(dirs != null) {
            for(File dir : dirs) {
               zip.putNextEntry(new ZipEntry("blobs/" + dir.getName() + "/"));

               File[] files = dir.listFiles();

               if(files != null) {
                  for(File file : files) {
                     zip.putNextEntry(new ZipEntry("blobs/" + dir.getName() + "/" + file.getName()));
                     Files.copy(file.toPath(), zip);
                  }
               }
            }
         }

         zip.finish();
      }
      finally {
         FileUtils.deleteDirectory(work.toFile());
      }
   }

   private void export(JsonGenerator kvGenerator, JsonGenerator blobGenerator, Path blobDir)
      throws IOException
   {
      kvGenerator.writeStartObject();
      blobGenerator.writeStartObject();

      try {
         getKeyValueStoreIds().forEach(id -> exportStore(id, kvGenerator, blobGenerator, blobDir));
      }
      catch(RuntimeException e) {
         if(e.getCause() instanceof IOException) {
            throw (IOException) e.getCause();
         }

         throw e;
      }

      kvGenerator.writeEndObject();
      blobGenerator.writeEndObject();
   }

   @Override
   public final void importContents(Path file) throws IOException {
      Principal oPrincipal = ThreadContext.getPrincipal();

      if(oPrincipal == null) {
         IdentityID tempPID = new IdentityID(XPrincipal.SYSTEM, OrganizationManager.getCurrentOrgName());
         XPrincipal tempPrincipal = new XPrincipal(tempPID, new IdentityID[0], new String[0],
                                                   Organization.getDefaultOrganizationID());
         ThreadContext.setPrincipal(tempPrincipal);
      }

      try(ZipFile zip = new ZipFile(file.toFile(), ZipFile.OPEN_READ)) {
         importKeyValues(zip);
         importBlobs(zip);
      }

      if(oPrincipal == null) {
         ThreadContext.setPrincipal(oPrincipal);
      }
   }

   protected abstract Stream<String> getKeyValueStoreIds();

   protected abstract void exportStore(String id, JsonGenerator kvGenerator,
                                       JsonGenerator blobGenerator, Path blobDir);
   protected abstract void putKeyValue(String id, String key, Object value);

   protected abstract void saveBlob(String id, String digest, Path file) throws IOException;

   private void importKeyValues(ZipFile zip) throws IOException {
      ZipEntry entry = zip.getEntry("key-value-index.json");
      ObjectMapper mapper = KeyValueEngine.createObjectMapper();
      ObjectNode root;

      try(InputStream input = zip.getInputStream(entry)) {
         root = (ObjectNode) mapper.readTree(input);
      }

      for(Iterator<String> i = root.fieldNames(); i.hasNext();) {
         String id = i.next();
         ObjectNode entries = (ObjectNode) root.get(id);

         for(Iterator<String> j = entries.fieldNames(); j.hasNext();) {
            String key = j.next();
            Wrapper wrapper = mapper.convertValue(entries.get(key), Wrapper.class);
            putKeyValue(id, key, wrapper.getValue());
         }
      }
   }

   private void importBlobs(ZipFile zip) throws IOException {
      ZipEntry entry = zip.getEntry("blob-index.json");
      ObjectMapper mapper = KeyValueEngine.createObjectMapper();
      ObjectNode root;

      try(InputStream input = zip.getInputStream(entry)) {
         root = (ObjectNode) mapper.readTree(input);
      }

      for(Iterator<String> i = root.fieldNames(); i.hasNext();) {
         String id = i.next();
         ObjectNode entries = (ObjectNode) root.get(id);

         for(Iterator<String> j = entries.fieldNames(); j.hasNext(); ) {
            String key = j.next();
            Blob<?> blob = mapper.convertValue(entries.get(key), Blob.class);

            String digest = blob.getDigest();

            if(digest != null) {
               entry = zip.getEntry("blobs/" + digest.substring(0, 2) + "/" + digest.substring(2));
               Path temp = Files.createTempFile("import-storage", ".dat");

               try(InputStream input = zip.getInputStream(entry)) {
                  Files.copy(input, temp, StandardCopyOption.REPLACE_EXISTING);
               }

               saveBlob(id, digest, temp);
               Files.delete(temp);
            }

            putKeyValue(id, key, blob);
         }
      }
   }

   @JsonSerialize(using = Serializer.class)
   @JsonDeserialize(using = Deserializer.class)
   static final class Wrapper {
      public Wrapper() {
      }

      public Wrapper(Object value) {
         this.value = value;
         this.type = value.getClass().getName();
      }

      public String getType() {
         return type;
      }

      public void setType(String type) {
         this.type = type;
      }

      public Object getValue() {
         return value;
      }

      public void setValue(Object value) {
         this.value = value;
      }

      private String type;
      private Object value;
   }

   static final class Serializer extends StdSerializer<Wrapper> {
      public Serializer() {
         super(Wrapper.class);
      }

      @Override
      public void serialize(Wrapper value, JsonGenerator gen, SerializerProvider provider)
         throws IOException
      {
         gen.writeStartObject();
         gen.writeStringField("type", value.getType());
         gen.writeObjectField("value", value.getValue());
         gen.writeEndObject();
      }
   }

   static final class Deserializer extends StdDeserializer<Wrapper> {
      public Deserializer() {
         super(Wrapper.class);
      }

      @Override
      public Wrapper deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
         ObjectNode root = p.getCodec().readTree(p);
         String type = root.get("type").asText();
         Class<?> valueClass;

         try {
            valueClass =  Class.forName(type);
         }
         catch(Exception e) {
            throw new JsonMappingException(p, "Failed to create class " + type, e);
         }

         JsonNode node = root.get("value");
         Object value = ((ObjectMapper) p.getCodec()).convertValue(node, valueClass);

         Wrapper wrapper = new Wrapper();
         wrapper.setType(type);
         wrapper.setValue(value);
         return wrapper;
      }
   }
}
