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
package inetsoft.web.composer.model.condition;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import inetsoft.web.binding.drm.DataRefModel;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SubqueryValueModel {
   public String getQuery() {
      return query;
   }

   public void setQuery(String query) {
      this.query = query;
   }

   public DataRefModel getAttribute() {
      return attribute;
   }

   public void setAttribute(DataRefModel attribute) {
      this.attribute = attribute;
   }

   public DataRefModel getSubAttribute() {
      return subAttribute;
   }

   public void setSubAttribute(DataRefModel subAttribute) {
      this.subAttribute = subAttribute;
   }

   public DataRefModel getMainAttribute() {
      return mainAttribute;
   }

   public void setMainAttribute(DataRefModel mainAttribute) {
      this.mainAttribute = mainAttribute;
   }

   private String query;
   private DataRefModel attribute;
   private DataRefModel subAttribute;
   private DataRefModel mainAttribute;
}
