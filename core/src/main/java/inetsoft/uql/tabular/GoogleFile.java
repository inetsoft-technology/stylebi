/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.uql.tabular;

import inetsoft.util.Tool;
import inetsoft.util.XMLSerializable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.io.Serializable;
import java.util.Objects;

public class GoogleFile implements Serializable, XMLSerializable, Cloneable {
   public GoogleFile() {
   }

   public String getName() {
      return name;
   }

   public void setName(String name) {
      this.name = name;
   }

   public String getId() {
      return id;
   }

   public void setId(String id) {
      this.id = id;
   }

   @Override
   public void writeXML(PrintWriter writer) {
      writer.println("<googleFile>");

      if(name != null) {
         writer.format("<name><![CDATA[%s]]></name>", name);
      }

      if(id != null) {
         writer.format("<id><![CDATA[%s]]></id>", id);
      }

      writer.println("</googleFile>");
   }

   @Override
   public void parseXML(Element tag) throws Exception {
      Element element = Tool.getChildNodeByTagName(tag, "name");

      if(element != null) {
         name = Tool.getValue(element);
      }

      element = Tool.getChildNodeByTagName(tag, "id");

      if(element != null) {
         id = Tool.getValue(element);
      }
   }

   @Override
   public Object clone() {
      try {
         return super.clone();
      }
      catch(Exception ex) {
         LOG.error("Failed to clone object", ex);
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

      GoogleFile that = (GoogleFile) o;
      return Objects.equals(name, that.name) && Objects.equals(id, that.id);
   }

   @Override
   public int hashCode() {
      return Objects.hash(name, id);
   }

   private String name;
   private String id;
   private static final Logger LOG = LoggerFactory.getLogger(GoogleFile.class);
}
