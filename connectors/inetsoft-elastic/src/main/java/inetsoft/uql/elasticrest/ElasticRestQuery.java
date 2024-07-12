/*
 * inetsoft-elastic - StyleBI is a business intelligence web application.
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
   }

   private String suffix;
   private String filter;
   private String jsonpath;
   private boolean expanded = false;
   private String expandedPath;
}

