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
 * {@code AzureBlobConfig} contains the configuration for the Azure blob container.
 */
@InetsoftConfigBean
public class AzureBlobConfig implements Serializable  {
   /**
    * The connection string for the web service.
    */
   @JsonSerialize(using = PasswordSerializer.class)
   @JsonDeserialize(using = PasswordDeserializer.class)
   public String getConnectionString() {
      return connectionString;
   }

   public void setConnectionString(String connectionString) {
      Objects.requireNonNull(connectionString, "The Azure blob connection string is required");
      this.connectionString = connectionString;
   }

   /**
    * The endpoint for the web service if using the default azure credential.
    */
   public String getEndpoint() {
      return endpoint;
   }

   public void setEndpoint(String endpoint) {
      this.endpoint = endpoint;
   }

   /**
    * The name of the container.
    */
   public String getContainer() {
      return container;
   }

   public void setContainer(String container) {
      Objects.requireNonNull(container, "The Azure blob container is required");
      this.container = container;
   }

   @CRDProperty(description = "The connection string for the web service", secret = true)
   private String connectionString;
   @CRDProperty(description = "The endpoint for the web service using the default Azure credential")
   private String endpoint;
   @CRDProperty(description = "The name of the container")
   private String container;
}
