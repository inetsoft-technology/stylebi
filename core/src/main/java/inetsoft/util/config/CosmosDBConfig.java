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
import java.util.Objects;

/**
 * {@code CosmosDBConfig} contains the configuration for the Azure CosmosDB.
 */
@InetsoftConfigBean
public class CosmosDBConfig implements Serializable {
   /**
    * The account host.
    */
   public String getAccountHost() {
      return accountHost;
   }

   public void setAccountHost(String accountHost) {
      Objects.requireNonNull(accountHost, "The Cosmos DB account host is required");
      this.accountHost = accountHost;
   }

   /**
    * The account key.
    */
   @JsonSerialize(using = PasswordSerializer.class)
   @JsonDeserialize(using = PasswordDeserializer.class)
   public String getAccountKey() {
      return accountKey;
   }

   public void setAccountKey(String accountKey) {
      this.accountKey = accountKey;
   }

   /**
    * The name of the default region.
    */
   public String getRegion() {
      return region;
   }

   public void setRegion(String region) {
      this.region = region;
   }

   /**
    * The name of the database.
    */
   public String getDatabase() {
      return database;
   }

   public void setDatabase(String database) {
      Objects.requireNonNull(database, "The Cosmos DB database is required");
      this.database = database;
   }

   /**
    * The name of the container.
    */
   public String getContainer() {
      return container;
   }

   public void setContainer(String container) {
      Objects.requireNonNull(container, "The Cosmos DB container is required");
      this.container = container;
   }

   /**
    * The throughput configured for the container if it is created by the storage provider.
    */
   public int getThroughput() {
      return throughput;
   }

   public void setThroughput(int throughput) {
      this.throughput = throughput;
   }

   /**
    * A flag indicating if the CosmosDB service is being served by an emulator.
    */
   public boolean isEmulated() {
      return emulated;
   }

   public void setEmulated(boolean emulated) {
      this.emulated = emulated;
   }

   private String accountHost;
   private String accountKey;
   private String region;
   private String database;
   private String container;
   private int throughput = 0;
   private boolean emulated = false;
}
