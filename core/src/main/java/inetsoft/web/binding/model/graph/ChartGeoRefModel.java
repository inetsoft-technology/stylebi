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
package inetsoft.web.binding.model.graph;

import inetsoft.uql.viewsheet.graph.*;

public class ChartGeoRefModel extends ChartDimensionRefModel {
   /**
    * Constructor
    */
   public ChartGeoRefModel() {
   }

   /**
    * Constructor
    */
   public ChartGeoRefModel(GeoRef ref, ChartInfo cinfo) {
      super(ref, cinfo);

      setOption(new GeographicOptionInfo(ref.getGeographicOption()));
   }
   /**
    * Get geographic option.
    * @return the geographic option.
    */
   public GeographicOptionInfo getOption() {
      return option;
   }

   /**
    * Set the geographic option.
    * @param option the geographic option.
    */
   public void setOption(GeographicOptionInfo option) {
      this.option = option;
   }

   /**
    * Create a chartRef depend on chart info.
    */
   @Override
   public ChartRef createChartRef(ChartInfo cinfo) {
      return new VSChartGeoRef();
   }

   private GeographicOptionInfo option;
}
