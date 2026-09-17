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
 * {@code TabularDataSourceProperties} contains the properties of a tabular data source definition.
 */
@Validated
@Schema(description = "The properties of a tabular data source definition.")
public class TabularDataSourceProperties extends DataSourceProperties {
   /**
    * Creates a new instance of {@code TabularDataSourceProperties}.
    */
   public TabularDataSourceProperties() {
      setType(Type.TABULAR);
   }

   /**
    * Creates a new instance of {@code TabularDataSourceProperties}.
    *
    * @param ds the data source from which to copy the properties.
    * @param id the unique identifier of the data source.
    */
   public TabularDataSourceProperties(XDataSource ds, String id) {
      setId(id);
      setName(ds.getName());
      setType(Type.TABULAR);
      setTabularType(ds.getType());
   }

   @Override
   @NotNull
   @Schema(
      description = "The unique identifier of the data source.",
      example = "535A0845FF4F3AA8A0EF4A40D28924CA")
   public String getId() {
      return super.getId();
   }

   @Override
   @NotNull
   @Schema(description = "The name of the data source.", example = "Users")
   public String getName() {
      return super.getName();
   }

   /**
    * Gets the tabular sub-type of this data source.
    *
    * @return the tabular type.
    */
   @NotNull
   @Schema(description = "The tabular sub-type of the data source.", example = "Rest")
   public String getTabularType() {
      return tabularType;
   }

   /**
    * Sets the tabular sub-type of this data source.
    *
    * @param tabularType the tabular type.
    */
   public void setTabularType(String tabularType) {
      this.tabularType = tabularType;
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(o == null || getClass() != o.getClass()) {
         return false;
      }

      if(!super.equals(o)) {
         return false;
      }

      TabularDataSourceProperties that = (TabularDataSourceProperties) o;
      return Objects.equals(tabularType, that.tabularType);
   }

   @Override
   public int hashCode() {
      return Objects.hash(super.hashCode(), tabularType);
   }

   @Override
   public String toString() {
      return "TabularDataSourceProperties{" +
         "id='" + getId() + '\'' +
         ", name ='" + getName() + '\'' +
         ", type ='" + getType() + '\'' +
         ", tabularType='" + tabularType + '\'' +
         '}';
   }

   private String tabularType;
}
