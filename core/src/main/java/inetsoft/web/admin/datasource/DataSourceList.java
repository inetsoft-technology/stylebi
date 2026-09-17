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

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code DataSourceList} contains a list of all data sources.
 */
@Validated
@Schema(description = "A list of all data sources.")
public class DataSourceList {
   /**
    * Gets the list of data sources.
    *
    * @return the data sources.
    */
   @NotNull
   @Schema(description = "The data sources.")
   public List<DataSourceDescription> getDataSources() {
      if(dataSources == null) {
         dataSources = new ArrayList<>();
      }

      return dataSources;
   }

   /**
    * Sets the list of data sources.
    *
    * @param dataSources the data sources.
    */
   public void setDataSources(List<DataSourceDescription> dataSources) {
      this.dataSources = dataSources;
   }

   private List<DataSourceDescription> dataSources;
}
