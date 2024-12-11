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
import inetsoft.util.config.crd.CRDProperty;
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

   @CRDProperty(description = "The hostname:port pairs for the database hosts")
   private String[] hosts;
   @CRDProperty(description = "The name of the database")
   private String database;
   @CRDProperty(description = "The name of the collection")
   private String collection;
   @CRDProperty(description = "The username", secret = true)
   private String username;
   @CRDProperty(description = "The password", secret = true)
   private String password;
   @CRDProperty(description = "The name of the authorization database")
   private String authDatabase;
   @CRDProperty(description = "A flag that indicates if SSL should be used")
   private boolean sslEnabled = false;
   @CRDProperty(description = "The name of the required replica set")
   private String replicaSet;
}
