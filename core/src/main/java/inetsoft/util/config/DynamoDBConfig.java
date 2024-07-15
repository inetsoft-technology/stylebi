/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.util.config;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import inetsoft.util.config.json.PasswordDeserializer;
import inetsoft.util.config.json.PasswordSerializer;

import java.io.Serializable;
import java.util.Objects;

/**
 * {@code DynamoDBConfig} contains the configuration for DynamoDB.
 */
@InetsoftConfigBean
public class DynamoDBConfig implements Serializable {
   /**
    * The name of the default AWS region.
    */
   public String getRegion() {
      return region;
   }

   public void setRegion(String region) {
      this.region = region;
   }

   /**
    * Sets the access key ID for the IAM account.
    */
   public String getAccessKeyId() {
      return accessKeyId;
   }

   public void setAccessKeyId(String accessKeyId) {
      this.accessKeyId = accessKeyId;
   }

   /**
    * The secure access key for the IAM account.
    */
   @JsonSerialize(using = PasswordSerializer.class)
   @JsonDeserialize(using = PasswordDeserializer.class)
   public String getSecretAccessKey() {
      return secretAccessKey;
   }

   public void setSecretAccessKey(String secretAccessKey) {
      this.secretAccessKey = secretAccessKey;
   }

   /**
    * The optional endpoint URL for the DynamoDB service.
    */
   public String getEndpoint() {
      return endpoint;
   }

   public void setEndpoint(String endpoint) {
      this.endpoint = endpoint;
   }

   /**
    * The name of the DynamoDB table.
    */
   public String getTable() {
      return table;
   }

   public void setTable(String table) {
      Objects.requireNonNull(table, "The DynamoDB table is required");
      this.table = table;
   }

   /**
    * A flag that is {@code true} if on-demand capacity or {@code false} if provisioned capacity is
    * used for the table created by the provider. If the table already exists, this value is
    * ignored.
    */
   public boolean isOnDemandCapacity() {
      return onDemandCapacity;
   }

   public void setOnDemandCapacity(boolean onDemandCapacity) {
      this.onDemandCapacity = onDemandCapacity;
   }

   /**
    * The provisioned read throughput configured for the table if it is created by the provider. If
    * {@link #isOnDemandCapacity()} is {@code true}, this value is ignored.
    */
   public long getProvisionedReadThroughput() {
      return provisionedReadThroughput;
   }

   public void setProvisionedReadThroughput(long provisionedReadThroughput) {
      this.provisionedReadThroughput = provisionedReadThroughput;
   }

   /**
    * The provisioned write throughput configured for the table if it is created by the provider. If
    * {@link #isOnDemandCapacity()} is {@code true}, this value is ignored.
    */
   public long getProvisionedWriteThroughput() {
      return provisionedWriteThroughput;
   }

   public void setProvisionedWriteThroughput(long provisionedWriteThroughput) {
      this.provisionedWriteThroughput = provisionedWriteThroughput;
   }

   private String region;
   private String accessKeyId;
   private String secretAccessKey;
   private String endpoint;
   private String table;
   private boolean onDemandCapacity = true;
   private long provisionedReadThroughput = 0L;
   private long provisionedWriteThroughput = 0L;
}
