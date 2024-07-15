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

import inetsoft.uql.viewsheet.graph.GeographicOption;

public class GeographicOptionInfo {
   /**
    * Constructor.
    */
   public GeographicOptionInfo() {
   }

   /**
    * Constructor.
    */
   public GeographicOptionInfo(GeographicOption option) {
      setLayerValue(option.getLayerValue());
      setMapping(new FeatureMappingInfo(option.getMapping()));
   }

   /**
    * Get the layer value.
    * @return the layer option value.
    */
   public String getLayerValue() {
      return layerValue;
   }

   /**
    * Set the layer option value.
    * @param layerValue the layer option value.
    */
   public void setLayerValue(String layerValue) {
      this.layerValue = layerValue;
   }

   /**
    * Get the mapping.
    */
   public FeatureMappingInfo getMapping() {
      return mapping;
   }

   /**
    * Set the mapping.
    */
   public void setMapping(FeatureMappingInfo mapping) {
      this.mapping = mapping;
   }

   /**
    * Convert this to geographic option info
    * @return the convert geographic option info.
    */
   public GeographicOption toGeographicOptionInfo(){
      GeographicOption option = new GeographicOption();
      option.getDynamicValue().setDValue(getLayerValue());

      if(getMapping() != null) {
         option.setMapping(getMapping().toFeatureMapping());
      }

      return option;
   }

   private String layerValue;
   private FeatureMappingInfo mapping;
}
