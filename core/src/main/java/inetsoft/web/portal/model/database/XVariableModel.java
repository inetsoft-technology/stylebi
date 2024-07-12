/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.portal.model.database;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import inetsoft.uql.schema.XVariable;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME,
   include = JsonTypeInfo.As.PROPERTY,
   property = "classType")
@JsonSubTypes({
   @JsonSubTypes.Type(value = UserVariableModel.class, name = "UserVariable"),
   @JsonSubTypes.Type(value = QueryVariableModel.class, name = "QueryVariable")
})
public class XVariableModel {
   public XVariableModel(XVariable variable) {
      super();

      setName(variable.getName());
      setSource(variable.getSource());
   }

   public String getName() {
      return name;
   }

   public void setName(String name) {
      this.name = name;
   }

   public String getSource() {
      return source;
   }

   public void setSource(String source) {
      this.source = source;
   }

   private String name;
   private String source;
}
