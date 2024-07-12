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

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import inetsoft.util.config.json.PasswordDeserializer;
import inetsoft.util.config.json.PasswordSerializer;

import java.io.Serializable;
import java.util.Objects;

/**
 * {@code MongoConfig} contains the connection properties for MongoDB.
 */
@InetsoftConfigBean
public class MongoConfig implements Serializable {
   /**
    * The hostname:port for the database hosts.
    */
   public String[] getHosts() {
      return hosts;
   }

   public void setHosts(String[] hosts) {
      this.hosts = hosts;
   }

   /**
    * The name of the database.
    */
   public String getDatabase() {
      return database;
   }

   public void setDatabase(String database) {
      Objects.requireNonNull(database, "The Mongo database is required");
      this.database = database;
   }

   /**
    * The name of the collection.
    */
   public String getCollection() {
      return collection;
   }

   public void setCollection(String collection) {
      Objects.requireNonNull(collection, "The Mongo collection is required");
      this.collection = collection;
   }

   /**
    * The username.
    */
   public String getUsername() {
      return username;
   }

   public void setUsername(String username) {
      this.username = username;
   }

   /**
    * The password.
    */
   @JsonSerialize(using = PasswordSerializer.class)
   @JsonDeserialize(using = PasswordDeserializer.class)
   public String getPassword() {
      return password;
   }

   public void setPassword(String password) {
      this.password = password;
   }

   /**
    * The name of the authorization database.
    */
   public String getAuthDatabase() {
      return authDatabase;
   }

   public void setAuthDatabase(String authDatabase) {
      this.authDatabase = authDatabase;
   }

   /**
    * A flag that indicates if SSL should be used.
    */
   public boolean isSslEnabled() {
      return sslEnabled;
   }

   public void setSslEnabled(boolean sslEnabled) {
      this.sslEnabled = sslEnabled;
   }

   /**
    * The name of the required replica set.
    */
   public String getReplicaSet() {
      return replicaSet;
   }

   public void setReplicaSet(String replicaSet) {
      this.replicaSet = replicaSet;
   }

   private String[] hosts;
   private String database;
   private String collection;
   private String username;
   private String password;
   private String authDatabase;
   private boolean sslEnabled = false;
   private String replicaSet;
}
