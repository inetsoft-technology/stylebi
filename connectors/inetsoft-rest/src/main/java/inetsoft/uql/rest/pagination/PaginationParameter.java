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
package inetsoft.uql.rest.pagination;

import inetsoft.util.Tool;
import inetsoft.util.XMLSerializable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;
import java.io.Serializable;
import java.util.*;

/**
 * Data input class which describes a parameter used by a pagination strategy.
 */
public class PaginationParameter implements Serializable, XMLSerializable, Cloneable {
   public PaginationParameter() {
      // for deserialization
   }

   @SuppressWarnings("unused")
   public PaginationParameter(String value) {
      this.value = value;
   }

   public PaginationParameter(String value, PaginationParamType type) {
      this.value = value;
      this.type = type;
   }

   @SuppressWarnings("unused")
   public static PaginationParameter forReadJson() {
      final PaginationParameter param = new PaginationParameter();
      param.setTypes(EnumSet.of(PaginationParamType.JSON_PATH, PaginationParamType.HEADER));
      param.setType(PaginationParamType.JSON_PATH);

      return param;
   }

   @SuppressWarnings("unused")
   public static PaginationParameter forReadXml() {
      final PaginationParameter param = new PaginationParameter();
      param.setTypes(EnumSet.of(PaginationParamType.XPATH, PaginationParamType.HEADER));
      param.setType(PaginationParamType.XPATH);

      return param;
   }

   @SuppressWarnings("unused")
   public static PaginationParameter forWrite() {
      final PaginationParameter param = new PaginationParameter();
      param.setTypes(EnumSet.of( PaginationParamType.QUERY, PaginationParamType.HEADER));
      param.setType(PaginationParamType.QUERY);

      return param;
   }

   @SuppressWarnings("unused")
   public static PaginationParameter forXpath() {
      final PaginationParameter param = new PaginationParameter();
      param.setTypes(EnumSet.of(PaginationParamType.XPATH));
      param.setType(PaginationParamType.XPATH);

      return param;
   }

   @SuppressWarnings("unused")
   public static PaginationParameter forUrlVariable() {
      final PaginationParameter param = new PaginationParameter();
      param.setTypes(EnumSet.of(PaginationParamType.URL_VARIABLE));
      param.setType(PaginationParamType.URL_VARIABLE);

      return param;
   }

   public static PaginationParameter forLink() {
      final PaginationParameter param = new PaginationParameter();
      param.setTypes(EnumSet.of(PaginationParamType.JSON_PATH,
                                PaginationParamType.HEADER,
                                PaginationParamType.LINK_HEADER));
      param.setType(PaginationParamType.HEADER);

      return param;
   }

   public String getValue() {
      return value;
   }

   public void setValue(String value) {
      this.value = value;
   }

   public PaginationParamType getType() {
      return type;
   }

   public void setType(PaginationParamType type) {
      this.type = type;
   }

   public Set<PaginationParamType> getTypes() {
      return types;
   }

   public void setTypes(Set<PaginationParamType> types) {
      this.types = types;
   }

   public String getLinkRelation() {
      return linkRelation;
   }

   public void setLinkRelation(String linkRelation) {
      this.linkRelation = linkRelation;
   }

   @Override
   public Object clone() {
      try {
         PaginationParameter copy = (PaginationParameter) super.clone();

         if(types != null) {
            copy.types = EnumSet.copyOf(types);
         }

         return copy;
      }
      catch(Exception e) {
         LOG.error("Failed to create copy of PaginationParameter", e);
         return null;
      }
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(o == null || getClass() != o.getClass()) {
         return false;
      }

      final PaginationParameter that = (PaginationParameter) o;
      return Objects.equals(value, that.value) &&
         type == that.type &&
         Objects.equals(types, that.types) &&
         Objects.equals(linkRelation, that.linkRelation);
   }

   @Override
   public int hashCode() {
      return Objects.hash(value, type, types, linkRelation);
   }

   @Override
   public String toString() {
      return "PaginationParameter{" +
         "value='" + value + '\'' +
         ", type=" + type +
         ", types=" + types +
         ", linkRelation='" + linkRelation + '\'' +
         '}';
   }

   @Override
   public void writeXML(PrintWriter writer) {
      if(value != null && !value.isEmpty()) {
         writer.format("<value><![CDATA[%s]]></value>%n", value);
      }

      writer.format("<type><![CDATA[%s]]></type>%n", type.name());

      writer.format("<types>%n");
      types.forEach(t -> writer.format("<type><![CDATA[%s]]></type>%n", t.name()));
      writer.format("</types>%n");

      if(linkRelation != null && !linkRelation.isEmpty()) {
         writer.format("<linkRelation><![CDATA[%s]]></linkRelation>%n", linkRelation);
      }
   }

   @Override
   public void parseXML(Element tag) {
      final Element valueEl = Tool.getChildNodeByTagName(tag, "value");

      if(valueEl != null) {
         value = Tool.getValue(valueEl);
      }

      type = PaginationParamType.valueOf(Tool.getValue(Tool.getChildNodeByTagName(tag, "type")));

      final Element typesEl = Tool.getChildNodeByTagName(tag, "types");
      final NodeList typeList = Tool.getChildNodesByTagName(typesEl, "type");
      types = EnumSet.noneOf(PaginationParamType.class);

      for(int i = 0; i < typeList.getLength(); i++) {
         types.add(PaginationParamType.valueOf(Tool.getValue(typeList.item(i))));
      }

      final Element linkRelationEl = Tool.getChildNodeByTagName(tag, "linkRelation");

      if(linkRelationEl != null) {
         linkRelation = Tool.getValue(linkRelationEl);
      }
   }

   private String value = "";
   private PaginationParamType type = PaginationParamType.HEADER;
   private Set<PaginationParamType> types;
   private String linkRelation = ""; // Used for link header param
   private static final Logger LOG = LoggerFactory.getLogger(PaginationParameter.class);
}
