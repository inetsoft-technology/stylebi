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

import inetsoft.util.config.crd.CRDProperty;

@InetsoftConfigBean
public class AuditConfig {
   /**
    * The type of storage used for audit records.
    */
   public String getType() {
      return type;
   }

   public void setType(String type) {
      this.type = type;
   }

   /**
    * The CosmosDB configuration.
    */
   public CosmosDBConfig getCosmosdb() {
      return cosmosdb;
   }

   public void setCosmosdb(CosmosDBConfig cosmosdb) {
      this.cosmosdb = cosmosdb;
   }

   /**
    * The relational database configuration
    */
   public DatabaseConfig getDatabase() {
      return database;
   }

   public void setDatabase(DatabaseConfig database) {
      this.database = database;
   }

   /**
    * The DynamoDB configuration.
    */
   public DynamoDBConfig getDynamodb() {
      return dynamodb;
   }

   public void setDynamodb(DynamoDBConfig dynamodb) {
      this.dynamodb = dynamodb;
   }

   /**
    * The Firestore configuration.
    */
   public FirestoreConfig getFirestore() {
      return firestore;
   }

   public void setFirestore(FirestoreConfig firestore) {
      this.firestore = firestore;
   }

   /**
    * The MongoDB configuration.
    */
   public MongoConfig getMongo() {
      return mongo;
   }

   public void setMongo(MongoConfig mongo) {
      this.mongo = mongo;
   }

   @CRDProperty(description = "The type of audit storage", allowedValues = { "mapdb", "cosmosdb", "database", "dynamodb", "firestore", "mongo" })
   private String type;
   @CRDProperty(description = "The CosmosDB audit storage settings")
   private CosmosDBConfig cosmosdb;
   @CRDProperty(description = "The relational database audit storage settings")
   private DatabaseConfig database;
   @CRDProperty(description = "The DynamoDB audit storage settings")
   private DynamoDBConfig dynamodb;
   @CRDProperty(description = "The Firestore audit storage settings")
   private FirestoreConfig firestore;
   @CRDProperty(description = "The MongoDB audit storage configuration")
   private MongoConfig mongo;
}
