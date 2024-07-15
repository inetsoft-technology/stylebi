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
package inetsoft.util.config;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import inetsoft.util.config.json.PasswordDeserializer;
import inetsoft.util.config.json.PasswordSerializer;

import java.io.Serializable;

@InetsoftConfigBean
public class AwsSecretsConfig implements Serializable, Cloneable{
   public String getRegion() {
      return region;
   }

   public void setRegion(String region) {
      this.region = region;
   }

   public String getAccessKeyId() {
      return accessKeyId;
   }

   public void setAccessKeyId(String accessKeyId) {
      this.accessKeyId = accessKeyId;
   }

   @JsonSerialize(using = PasswordSerializer.class)
   @JsonDeserialize(using = PasswordDeserializer.class)
   public String getSecretAccessKey() {
      return secretAccessKey;
   }

   public void setSecretAccessKey(String secretAccessKey) {
      this.secretAccessKey = secretAccessKey;
   }

   public String getKmsKeyId() {
      return kmsKeyId;
   }

   public void setKmsKeyId(String kmsKeyId) {
      this.kmsKeyId = kmsKeyId;
   }

   public String getEndpoint() {
      return endpoint;
   }

   public void setEndpoint(String endpoint) {
      this.endpoint = endpoint;
   }

   @Override
   public Object clone() {
      AwsSecretsConfig config = new AwsSecretsConfig();
      config.setRegion(region);
      config.setAccessKeyId(accessKeyId);
      config.setSecretAccessKey(secretAccessKey);
      config.setKmsKeyId(kmsKeyId);
      config.setEndpoint(endpoint);

      return config;
   }

   private String region;
   private String accessKeyId;
   private String secretAccessKey;
   private String kmsKeyId;
   private String endpoint;
}
