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
package inetsoft.web.portal.model.database.cube;

import com.fasterxml.jackson.annotation.*;
import inetsoft.web.binding.drm.DataRefModel;
import inetsoft.web.portal.model.database.cube.xmla.CubeDimMemberModel;
import inetsoft.web.portal.model.database.cube.xmla.CubeMeasureModel;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME,
   include = JsonTypeInfo.As.PROPERTY,
   property = "classType")
@JsonSubTypes({
   @JsonSubTypes.Type(value = CubeDimMemberModel.class, name = "CubeDimMemberModel"),
   @JsonSubTypes.Type(value = CubeMeasureModel.class, name = "CubeMeasureModel")
})
@JsonIgnoreProperties(ignoreUnknown = true)
public class XCubeMemberModel {

   /**
    * Get the name of this cube member.
    *
    * @return the name of this cube member.
    */
   public String getName() {
      return name;
   }

   /**
    * Set the name of this cube member.
    */
   public void setName(String name) {
      this.name = name;
   }

   /**
    * Get the data reference of this cube member.
    *
    * @return an XDataRef object.
    */
   public DataRefModel getDataRef() {
      return dataRef;
   }

   /**
    * Set the data reference of this cube member.
    */
   public void setDataRef(DataRefModel dataRef) {
      this.dataRef = dataRef;
   }

   /**
    * Get the data type of this cube member. This will be one of the data type
    * constants defined in <code>inetsoft.uql.schema.XSchema</code>
    *
    * @return the data type of this cube member.
    */
   public String getType() {
      return type;
   }

   /**
    * Set the data type of this cube member. This will be one of the data type
    * constants defined in <code>inetsoft.uql.schema.XSchema</code>
    */
   public void setType(String type) {
      this.type = type;
   }

   /**
    * Get the XMetaInfo of this cube member.
    *
    * @return the XMetaInfo of this cube member.
    */
   public XMetaInfoModel getMetaInfo() {
      return metaInfo;
   }

   /**
    * Set the XMetaInfo of this cube member.
    */
   public void setMetaInfo(XMetaInfoModel metaInfo) {
      this.metaInfo = metaInfo;
   }

   /**
    * Get the type of original ref.
    */
   public String getOriginalType() {
      return originalType;
   }

   /**
    * Set the type of original ref.
    */
   public void setOriginalType(String originalType) {
      this.originalType = originalType;
   }

   /**
    * Get the date level of the date cube member.
    */
   public String getDateLevel() {
      return dateLevel;
   }

   /**
    * Set the date level of the date cube member.
    */
   public void setDateLevel(String dateLevel) {
      this.dateLevel = dateLevel;
   }

   private String name;
   private DataRefModel dataRef;
   private String type;
   private XMetaInfoModel metaInfo;
   private String originalType;
   private String dateLevel;
}
