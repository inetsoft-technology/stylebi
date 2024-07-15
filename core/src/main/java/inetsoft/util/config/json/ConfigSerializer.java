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
package inetsoft.util.config.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import inetsoft.util.PasswordEncryption;
import inetsoft.util.config.InetsoftConfig;
import inetsoft.util.config.SecretsConfig;
import org.apache.commons.lang.StringUtils;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * {@code ConfigSerializer} handles serializing {@link InetsoftConfig} objects as JSON.
 */
public class ConfigSerializer extends StdSerializer<InetsoftConfig> {
   public ConfigSerializer() {
      super(InetsoftConfig.class);
   }

   @Override
   public void serialize(InetsoftConfig value, JsonGenerator gen, SerializerProvider provider)
      throws IOException
   {
      SecretsConfig secretsConfig = value.getSecrets();
      PasswordEncryption encryption = PasswordEncryption.newInstance(secretsConfig);
      PasswordSerializer.encryption.set(encryption);

      Map<String, Object> additional = new HashMap<>(value.getAdditionalProperties());
      String encrypted = encryption.encryptMasterPassword("INETSOFT_MASTER_PASSWORD");
      additional.put("masterPasswordCheck", encrypted);
      additional.remove("legacyDriverDirectories");

      gen.writeStartObject();

      if(value.getVersion() != null) {
         gen.writeStringField("version", value.getVersion());
      }

      if(!StringUtils.isEmpty(value.getPluginDirectory())) {
         gen.writeStringField("pluginDirectory", value.getPluginDirectory());
      }

      gen.writeObjectField("keyValue", value.getKeyValue());
      gen.writeObjectField("blob", value.getBlob());
      gen.writeObjectField("externalStorage", value.getExternalStorage());

      if(value.getCluster() != null) {
         gen.writeObjectField("cluster", value.getCluster());
      }

      if(value.getSecrets() != null) {
         gen.writeObjectField("secrets", value.getSecrets());
      }

      gen.writeObjectField("cloudRunner", value.getCloudRunner());
      gen.writeObjectField("audit", value.getAudit());
      gen.writeObjectField("additionalProperties", additional);
      gen.writeEndObject();

      PasswordSerializer.encryption.remove();
   }
}
