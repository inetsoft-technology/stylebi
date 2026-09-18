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

import com.fasterxml.jackson.annotation.*;
import inetsoft.uql.XDataSource;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;

import java.util.Objects;

/**
 * {@code DataSourceProperties} contains the properties of a data source definition.
 */
@JsonTypeInfo(
   use = JsonTypeInfo.Id.NAME,
   include = JsonTypeInfo.As.EXISTING_PROPERTY,
   property = "type",
   visible = true
)
@JsonSubTypes({
   @JsonSubTypes.Type(value = JdbcDataSourceProperties.class, name = "jdbc"),
   @JsonSubTypes.Type(value = TabularDataSourceProperties.class, name = "tabular")
})
@Validated
@Schema(description = "The properties of a data source definition.")
public abstract class DataSourceProperties {
   /**
    * Creates a new instance of {@code DataSourceProperties}.
    */
   public DataSourceProperties() {
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
   @Schema(description = "The name of the data source.", example = "Orders")
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
    * Gets the type of the data source. This value must be one of the data source type constants
    * defined in {@link inetsoft.uql.XDataSource}.
    *
    * @return the type.
    */
   @NotNull
   @Schema(description = "The type of the data source.")
   public Type getType() {
      return type;
   }

   /**
    * Sets the type of the data source. This value must be one of the data source type constants
    * defined in {@link inetsoft.uql.XDataSource}.
    *
    * @param type the type.
    */
   public void setType(Type type) {
      this.type = type;
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(!(o instanceof DataSourceProperties)) {
         return false;
      }

      DataSourceProperties that = (DataSourceProperties) o;
      return Objects.equals(id, that.id) &&
         Objects.equals(name, that.name) &&
         type == that.type;
   }

   @Override
   public int hashCode() {
      return Objects.hash(id, name, type);
   }

   @Override
   public String toString() {
      return "DataSourceProperties{" +
         "id='" + id + '\'' +
         ", name='" + name + '\'' +
         ", type=" + type +
         '}';
   }

   private String id;
   private String name;
   private Type type;

   /**
    * Enumeration of the types of data sources.
    */
   public enum Type {
      /**
       * A JDBC data source.
       */
      JDBC(XDataSource.JDBC),

      /**
       * A tabular data source.
       */
      TABULAR("tabular");

      private final String value;

      Type(String value) {
         this.value = value;
      }

      @Override
      @JsonValue
      public String toString() {
         return value;
      }

      @SuppressWarnings("unused")
      @JsonCreator
      public static Type fromValue(String value) {
         for(Type type : values()) {
            if(type.value.equals(value)) {
               return type;
            }
         }

         return null;
      }
   }
}
