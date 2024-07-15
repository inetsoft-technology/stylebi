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
package inetsoft.web.portal.model.database.cube.xmla;

import java.util.List;

public class DomainModel {
   public String getDatasource() {
      return datasource;
   }

   public void setDatasource(String datasource) {
      this.datasource = datasource;
   }

   public List<CubeModel> getCubes() {
      return cubes;
   }

   public void setCubes(List<CubeModel> cubes) {
      this.cubes = cubes;
   }

   private String datasource;
   private List<CubeModel> cubes;
}
