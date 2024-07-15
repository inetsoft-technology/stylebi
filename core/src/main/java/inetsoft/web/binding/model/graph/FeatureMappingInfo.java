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
package inetsoft.web.binding.model.graph;

import inetsoft.uql.viewsheet.graph.FeatureMapping;

import java.util.*;

public class FeatureMappingInfo {
   /**
    * Constructure.
    */
   public FeatureMappingInfo() {
   }
   /**
    * Constructure.
    */
   public FeatureMappingInfo(FeatureMapping mapping) {
      setId(mapping.getID());
      setAlgorithm(mapping.getAlgorithm());
      setType(mapping.getType());
      setLayer(mapping.getLayer());
      setMappings(mapping.getMappings());
      setDupMapping(mapping.getDupMapping());
   }

   /**
    * Get algorithm name.
    */
   public String getAlgorithm() {
      return algorithm;
   }

   /**
    * Set algorithm name.
    */
   public void setAlgorithm(String algorithm) {
      this.algorithm = algorithm;
   }

   /**
    * Get map tyoe.
    */
   public String getType() {
      return type;
   }

   /**
    * Set map tyoe.
    */
   public void setType(String type) {
      this.type = type;
   }

   /**
    * Get map layer.
    */
   public int getLayer() {
      return layer;
   }

   /**
    * Set map layer.
    */
   public void setLayer(int layer) {
      this.layer = layer;
   }

   /**
    * Get mapping id.
    */
   public String getId() {
      return id;
   }

   /**
    * Set mapping id.
    */
   public void setId(String id) {
      this.id = id;
   }

   /**
    * Get manual mappings.
    */
   public Map<String, String> getMappings() {
      return mappings;
   }

   /**
    * Set manual mappings.
    */
   public void setMappings(Map<String, String> mappings) {
      this.mappings = new LinkedHashMap<>(mappings);
   }

   /**
    * Set duplicate mappings.
    */
   public void setDupMapping(Map<String, List<String>> dupMapping) {
      this.dupMapping = dupMapping;
   }

   /**
    * Get duplicate mappings.
    */
   public Map<String, List<String>> getDupMapping() {
      return dupMapping;
   }

   /**
    * Convert this to Feature mapping
    * @return the convert Feature mapping.
    */
   public FeatureMapping toFeatureMapping() {
      FeatureMapping mapping = new FeatureMapping();
      mapping.setAlgorithm(getAlgorithm());
      mapping.setType(getType());
      mapping.setLayer(getLayer());
      mapping.setID(getId());
      mapping.setMappings(getMappings());
      mapping.setDupMapping(getDupMapping());

      return mapping;
   }

   private String algorithm;
   private String type;
   private int layer;
   private String id;
   private LinkedHashMap<String, String> mappings;
   private Map<String, List<String>> dupMapping;
}
