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
package inetsoft.util.config;

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

   private String type;
   private CosmosDBConfig cosmosdb;
   private DatabaseConfig database;
   private DynamoDBConfig dynamodb;
   private FirestoreConfig firestore;
   private MongoConfig mongo;
}
