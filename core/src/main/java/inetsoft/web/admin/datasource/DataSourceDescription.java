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
package inetsoft.web.admin.datasource;

import inetsoft.uql.XDataSource;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;

import java.util.Objects;

/**
 * {@code DataSourceDescription} contains a description of a data source.
 */
@Validated
@Schema(description = "A description of a data source.")
public class DataSourceDescription {
   /**
    * Creates a new instance of {@code DataSourceDescription}.
    */
   @SuppressWarnings("unused")
   public DataSourceDescription() {
   }

   /**
    * Creates a new instance of {@code DataSourceDescription}.
    *
    * @param ds the data source from which to copy the description.
    * @param id the unique identifier of the data source.
    */
   public DataSourceDescription(XDataSource ds, String id) {
      setId(id);
      setName(ds == null ? null : ds.getFullName());
      setType(ds == null ? null : ds.getType());
   }

   /**
    * Gets the unique identifier of the data source.
    *
    * @return the data source identifier.
    */
   @NotNull
   @Schema(
      description = "The unique identifier of the data source.",
      example = "3B1FAA9AE1140E6580AE5C44CAD29631")
   public String getId() {
      return id;
   }

   /**
    * Sets the unique identifier of the data source.
    *
    * @param id the data source identifier.
    */
   public void setId(String id) {
      this.id = id;
   }

   /**
    * Gets the name of the data source.
    *
    * @return the name.
    */
   @NotNull
   @Schema(description = "The name of the data source.", example = "Examples/Orders")
   public String getName() {
      return name;
   }

   /**
    * Sets the name of the data source.
    *
    * @param name the name.
    */
   public void setName(String name) {
      this.name = name;
   }

   /**
    * Gets the type of the data source.
    *
    * @return the type.
    */
   @NotNull
   @Schema(
      description = "Type type of the data source.",
      allowableValues = { "jdbc", "text", "xml", "tabular" },
      example = "jdbc")
   public String getType() {
      return type;
   }

   /**
    * Sets the type of the data source.
    *
    * @param type the type.
    */
   public void setType(String type) {
      this.type = type;
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(o == null || getClass() != o.getClass()) {
         return false;
      }

      DataSourceDescription that = (DataSourceDescription) o;
      return Objects.equals(id, that.id) &&
         Objects.equals(name, that.name) &&
         Objects.equals(type, that.type);
   }

   @Override
   public int hashCode() {
      return Objects.hash(id, name, type);
   }

   @Override
   public String toString() {
      return "DataSourceDescription{" +
         "id='" + id + '\'' +
         ", name='" + name + '\'' +
         ", type='" + type + '\'' +
         '}';
   }

   private String id;
   private String name;
   private String type;
}
