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
package inetsoft.uql.elasticrest;


import inetsoft.uql.tabular.*;
import inetsoft.util.*;

import java.io.PrintWriter;

import org.w3c.dom.*;

@View(vertical = true, value = {
   @View1("suffix"),
   @View1("jsonPath"),
   @View1("filter"),
   @View1("expanded"),
   @View1(value = "expandedPath", visibleMethod = "isExpanded"),
   @View1(type = ViewType.LABEL, text = "Example: $.store.book", col = 1)
})
public class ElasticRestQuery extends TabularQuery {
   public ElasticRestQuery() {
      super(ElasticRestDataSource.TYPE);
   }

   @Property(label = "URL Suffix")
   @PropertyEditor(autoSize = true)
   public String getSuffix() {
      return suffix;
   }

   public void setSuffix(String suffix) {
      this.suffix = suffix;
   }

   @Property(label = "Filter")
   @PropertyEditor(rows = 10, columns = 40)
   public String getFilter() {
      return filter;
   }

   public void setFilter(String filter) {
      this.filter = filter;
   }

   @Property(label = "Json Path")
   @PropertyEditor(columns = 40)
   public String getJsonPath() {
      return jsonpath;
   }

   public void setJsonPath(String jsonpath) {
      this.jsonpath = jsonpath;
   }

   @Property(label = "Expand Arrays")
   public boolean isExpanded() {
      return expanded;
   }

   public void setExpanded(boolean flag) {
      this.expanded = flag;
   }

   @Property(label = "Expanded Array Path")
   @PropertyEditor(dependsOn = "expanded")
   public String getExpandedPath() {
      return expandedPath;
   }

   public void setExpandedPath(String expandedPath) {
      this.expandedPath = expandedPath;
   }

   /**
    * The index whose {@code _mapping} types this query's columns, or null to infer every column's
    * type from the returned values. Deliberately not in the {@code @View}: it is set from the
    * tabular catalog's own {@code params} ({@code ElasticCatalog.describeDataset}), so a table built
    * from a catalog description has the column types that description reported. A query written in
    * the query dialog never has it, and keeps value inference unchanged.
    *
    * <p>{@code @Property} is what makes it settable through {@code params} at all — the query
    * contract only accepts annotated bean properties.
    */
   @Property(label = "Mapping Index")
   public String getMappingIndex() {
      return mappingIndex;
   }

   public void setMappingIndex(String mappingIndex) {
      this.mappingIndex = mappingIndex;
   }

   @Override
   public void writeAttributes(PrintWriter writer) {
      super.writeAttributes(writer);

      writer.print(" expanded=\"" + expanded + "\"");
   }

   @Override
   public void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(suffix != null) {
         writer.println("<suffix><![CDATA[" + suffix + "]]></suffix>");
      }

      if(jsonpath != null) {
         writer.println("<jsonpath><![CDATA[" + jsonpath + "]]></jsonpath>");
      }

      if(filter != null) {
         writer.println("<filter><![CDATA[" + filter + "]]></filter>");
      }

      if(expandedPath != null && !expandedPath.isEmpty()) {
         writer.format("<expandedPath><![CDATA[%s]]></expandedPath>%n", expandedPath);
      }

      if(mappingIndex != null && !mappingIndex.isEmpty()) {
         writer.format("<mappingIndex><![CDATA[%s]]></mappingIndex>%n", mappingIndex);
      }
   }

   @Override
   protected void parseAttributes(Element tag) throws Exception {
      super.parseAttributes(tag);
      expanded = "true".equals(Tool.getAttribute(tag, "expanded"));
   }

   @Override
   public void parseContents(Element root) throws Exception {
      super.parseContents(root);
      suffix = Tool.getChildValueByTagName(root, "suffix");
      jsonpath = Tool.getChildValueByTagName(root, "jsonpath");
      filter = Tool.getChildValueByTagName(root, "filter");
      expandedPath = Tool.getChildValueByTagName(root, "expandedPath");
      mappingIndex = Tool.getChildValueByTagName(root, "mappingIndex");
   }

   private String suffix;
   private String mappingIndex;
   private String filter;
   private String jsonpath;
   private boolean expanded = false;
   private String expandedPath;
}

