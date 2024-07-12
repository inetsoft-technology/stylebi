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
package inetsoft.util.config.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.node.ObjectNode;
import inetsoft.util.PasswordEncryption;
import inetsoft.util.config.*;

import java.io.IOException;
import java.util.*;

/**
 * {@code ConfigDeserializer} handles deserializing {@link InetsoftConfig} objects from JSON.
 */
public class ConfigDeserializer extends StdDeserializer<InetsoftConfig> {
   public ConfigDeserializer() {
      super(InetsoftConfig.class);
   }

   @SuppressWarnings("unchecked")
   @Override
   public InetsoftConfig deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
      ObjectNode root = p.getCodec().readTree(p);
      JsonNode secretsNode = root.get("secrets");
      JsonNode fipsNode = secretsNode == null ? null : secretsNode.get("fipsComplianceMode");
      boolean fipsComplianceMode = fipsNode != null && fipsNode.asBoolean();
      PasswordEncryption encryption = PasswordEncryption.newInstance(fipsComplianceMode);
      PasswordDeserializer.encryption.set(encryption);
      Map<String, Object> additionalProperties =
         getObject(root, "additionalProperties", p , Map.class);

      if(additionalProperties == null) {
         additionalProperties = new HashMap<>();
      }

      List<String> driverDirectories = getStrings(root, "driverDirectories");

      if(!driverDirectories.isEmpty()) {
         additionalProperties.put(
            "legacyDriverDirectories", getStrings(root, "driverDirectories"));
      }

      InetsoftConfig config = new InetsoftConfig();
      config.setVersion(getString(root, "version"));
      config.setCluster(getObject(root, "cluster", p, ClusterConfig.class));
      config.setPluginDirectory(getString(root, "pluginDirectory"));
      config.setKeyValue(getObject(root, "keyValue", p, KeyValueConfig.class));
      config.setSecrets(getObject(root, "secrets", p, SecretsConfig.class));
      config.setBlob(getObject(root, "blob", p, BlobConfig.class));
      config.setExternalStorage(getObject(root, "externalStorage", p, ExternalStorageConfig.class));
      config.setCloudRunner(getObject(root, "cloudRunner", p, CloudRunnerConfig.class));
      config.setAudit(getObject(root, "audit", p, AuditConfig.class));
      config.setAdditionalProperties(additionalProperties);

      PasswordDeserializer.encryption.remove();
      return config;
   }

   @SuppressWarnings("unused")
   private boolean getBoolean(ObjectNode node, String field) {
      JsonNode value = node.get(field);
      return value != null && value.asBoolean(false);
   }

   private String getString(ObjectNode node, String field) {
      JsonNode value = node.get(field);
      return value == null ? null : value.asText(null);
   }

   @SuppressWarnings("SameParameterValue")
   private List<String> getStrings(ObjectNode node, String field) {
      List<String> list = new ArrayList<>();
      JsonNode value = node.get(field);

      if(value != null && value.isArray()) {

         for(JsonNode item : value) {
            list.add(item.asText(null));
         }

         return list;
      }

      return list;
   }

   private <T> T getObject(ObjectNode node, String field, JsonParser parser, Class<T> type) {
      JsonNode value = node.get(field);
      return value == null ? null : ((ObjectMapper) parser.getCodec()).convertValue(value, type);
   }
}
