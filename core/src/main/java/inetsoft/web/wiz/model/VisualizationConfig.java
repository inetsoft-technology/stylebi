/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.web.wiz.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class VisualizationConfig {
   public String getTitle() {
      return title;
   }

   public void setTitle(String title) {
      this.title = title;
   }

   public String getDescription() {
      return description;
   }

   public void setDescription(String description) {
      this.description = description;
   }

   public DataSource getData() {
      return data;
   }

   public void setData(DataSource data) {
      this.data = data;
   }

   public BindingInfo getBindingInfo() {
      return bindingInfo;
   }

   public void setBindingInfo(BindingInfo bindingInfo) {
      this.bindingInfo = bindingInfo;
   }

   public List<Layer> getLayers() {
      return layers;
   }

   public void setLayers(List<Layer> layers) {
      this.layers = layers;
   }

   private String title;
   private String description;
   private DataSource data;
   private BindingInfo bindingInfo;
   private List<Layer> layers;

   public static class DataSource {
      public String getSource() {
         return source;
      }

      public void setSource(String source) {
         this.source = source;
      }

      private String source;
   }
}
