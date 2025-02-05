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
package inetsoft.uql.aerospike;

import inetsoft.uql.tabular.TabularService;

import java.util.Locale;
import java.util.ResourceBundle;

/**
 * Class that defines the Aerospike tabular service
 */
public class AerospikeService extends TabularService{
   @Override
   public String getDataSourceType() {
      return AerospikeDataSource.TYPE;
   }

   @Override
   public String getDataSourceClass() {
      return "inetsoft.uql.aerospike.AerospikeDataSource";
   }

   @Override
   public String getQueryClass() {
      return "inetsoft.uql.aerospike.AerospikeQuery";
   }

   @Override
   public String getRuntimeClass() {
      return "inetsoft.uql.aerospike.AerospikeRuntime";
   }

   @Override
   public String getDisplayLabel(Locale locale) {
      return ResourceBundle.getBundle("inetsoft.uql.aerospike.Bundle", locale)
         .getString("display.name");
   }
}
