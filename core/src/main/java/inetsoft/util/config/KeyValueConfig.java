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

import java.io.Serializable;
import java.util.Objects;

/**
 * {@code KeyValueConfig} contains the configuration for the key-value storage.
 */
@InetsoftConfigBean
public class KeyValueConfig implements Serializable {
   /**
    * The type of key-value storage being used.
    */
   public String getType() {
      return type;
   }

   public void setType(String type) {
      Objects.requireNonNull(type, "The key value type is required");
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
    * The database configuration.
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
    * The filesystem configuration.
    */
   public FilesystemConfig getFilesystem() {
      return filesystem;
   }

   public void setFilesystem(FilesystemConfig filesystem) {
      this.filesystem = filesystem;
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
    * The MapDB configuration.
    */
   public MapDBConfig getMapdb() {
      return mapdb;
   }

   public void setMapdb(MapDBConfig mapdb) {
      this.mapdb = mapdb;
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
   private FilesystemConfig filesystem;
   private FirestoreConfig firestore;
   private MapDBConfig mapdb;
   private MongoConfig mongo;
}
