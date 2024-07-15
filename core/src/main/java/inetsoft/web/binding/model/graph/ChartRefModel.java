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

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import inetsoft.uql.viewsheet.graph.ChartInfo;
import inetsoft.uql.viewsheet.graph.ChartRef;
import inetsoft.web.binding.model.BindingRefModel;

@JsonTypeInfo(
   use = JsonTypeInfo.Id.NAME,
   include = JsonTypeInfo.As.PROPERTY,
   property = "classType")
@JsonSubTypes({
   @JsonSubTypes.Type(value = ChartAggregateRefModel.class, name = "aggregate"),
   @JsonSubTypes.Type(value = ChartDimensionRefModel.class, name = "dimension"),
   @JsonSubTypes.Type(value = AllChartAggregateRefModel.class, name = "allaggregate"),
   @JsonSubTypes.Type(value = ChartGeoRefModel.class, name = "geo")
})
public interface ChartRefModel extends BindingRefModel {
   /**
    * Check this ref is treat as dimension or measure.
    */
   public boolean isMeasure();

   /**
    * Set this ref is treat as dimension or measure.
    */
   public void setMeasure(boolean measure);

   /**
    * Set the original descriptor.
    */
   public void setOriginal(OriginalDescriptor original);

   /**
    * Get the original descriptor.
    */
   public OriginalDescriptor getOriginal();


   /**
    * Set the specified info supports inverted chart.
    */
   public void setRefConvertEnabled(boolean refConvertEnabled);

   /**
    * Check if the specified data info supports inverted chart.
    */
   public boolean isRefConvertEnabled();

   /**
   * Create a chartRef depend on chart info.
   */
   public ChartRef createChartRef(ChartInfo cinfo);
}