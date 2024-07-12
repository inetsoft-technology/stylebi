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
package inetsoft.web.portal.model.database.cube;

import com.fasterxml.jackson.annotation.*;
import inetsoft.web.portal.model.database.cube.xmla.CubeDimensionModel;
import inetsoft.web.portal.model.database.cube.xmla.HierDimensionModel;

import java.util.ArrayList;
import java.util.List;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME,
   include = JsonTypeInfo.As.PROPERTY,
   property = "classType")
@JsonSubTypes({
   @JsonSubTypes.Type(value = CubeDimensionModel.class, name = "DimensionModel"),
   @JsonSubTypes.Type(value = HierDimensionModel.class, name = "HierDimensionModel"),
})
@JsonIgnoreProperties(ignoreUnknown = true)
public class XDimensionModel {

   /**
    * Get the name of this dimension.
    *
    * @return the name of this dimension.
    */
   public String getName() {
      return name;
   }

   /**
    * set the name of this dimension.
    */
   public void setName(String name) {
      this.name = name;
   }

   /**
    * Get the cube type of this dimension.
    */
   public int getType() {
      return type;
   }

   /**
    * Set the cube type of this dimension.
    */
   public void setType(int type) {
      this.type = type;
   }

   public List<XCubeMemberModel> getMembers() {
      return this.members;
   }

   public void setMembers(List<XCubeMemberModel> members) {
      this.members = members;
   }

   public void addMember(XCubeMemberModel member) {
      if(member == null) {
         members = new ArrayList<>();
      }

      members.add(member);
   }

   private String name;
   private int type;
   private List<XCubeMemberModel> members = new ArrayList<>();
}
