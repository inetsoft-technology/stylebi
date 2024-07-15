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
package inetsoft.uql.rest;

import com.fasterxml.jackson.annotation.JsonIgnore;
import inetsoft.uql.rest.pagination.PaginationSpec;
import inetsoft.uql.rest.pagination.PaginationType;
import inetsoft.uql.tabular.*;
import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.io.PrintWriter;

@View(vertical=true, value={
   @View1("suffix"),
   @View1("expanded"),
   @View1(value = "expandTop", visibleMethod = "isExpanded"),
   @View1(value = "expandedPath", visibleMethod = "isExpandedPathEnabled"),
   @View1("requestType"),
   @View1(value = "contentType", visibleMethod = "isPostRequest"),
   @View1(value = "requestBody", visibleMethod = "isPostRequest")
})
public abstract class AbstractRestQuery extends TabularQuery {
   public AbstractRestQuery(String type) {
      super(type);
   }

   public String getURL() {
      return ((AbstractRestDataSource<?>) getDataSource()).getURL();
   }

   @Property(label="URL Suffix")
   public String getSuffix() {
      return suffix;
   }

   public void setSuffix(String suffix) {
      this.suffix = suffix;
   }

   @Property(label="Expand Arrays")
   public boolean isExpanded() {
      return expanded;
   }

   public void setExpanded(boolean flag) {
      this.expanded = flag;
   }

   @Property(label="Top Level Only")
   @PropertyEditor(dependsOn = "expanded")
   public boolean isExpandTop() {
      return expandTop;
   }

   public void setExpandTop(boolean expandTop) {
      this.expandTop = expandTop;
   }

   @Property(label = "Expanded Array Path")
   @PropertyEditor(dependsOn = {"expanded", "expandTop"})
   public String getExpandedPath() {
      return expandedPath;
   }

   public void setExpandedPath(String expandedPath) {
      this.expandedPath = expandedPath;
   }

   public boolean isExpandedPathEnabled() {
      return isExpanded() && !isExpandTop();
   }

   public PaginationType getPaginationType() {
      return paginationSpec.getType();
   }

   @Property(label="Request Type")
   @PropertyEditor(tags={"GET", "POST"}, labels={"GET", "POST"})
   public String getRequestType() {
      return requestType;
   }

   @JsonIgnore
   public void setRequestType(String requestType) {
      this.requestType = requestType;
   }

   @Property(label="Content Type")
   @PropertyEditor(tags={"application/json", "application/xml", "application/x-www-form-urlencoded", "text/plain", "text/xml"},
                   labels={"application/json", "application/xml", "application/x-www-form-urlencoded", "text/plain", "text/xml"},
                   enabledMethod = "isPostRequest")
   public String getContentType() {
      return contentType;
   }

   public void setContentType(String contentType) {
      this.contentType = contentType;
   }

   @Property(label="Request Body")
   @PropertyEditor(columns = 40,
                   rows = 16, enabledMethod = "isPostRequest", dependsOn = {"requestType"})
   public String getRequestBody() {
      return requestBody;
   }

   public boolean isPaged() {
      return paginationSpec.getType() != PaginationType.NONE;
   }

   public PaginationSpec getPaginationSpec() {
      return paginationSpec;
   }

   public void setRequestBody(String requestBody) {
      this.requestBody = requestBody;
   }

   public boolean isPostRequest() {
      return "POST".equals(getRequestType());
   }

   @Property(label="Timeout (sec)")
   @Override
   public int getTimeout() {
      return super.getTimeout();
   }

   @Override
   public void setTimeout(int seconds) {
      super.setTimeout(seconds);
   }

   public String getRowsPath() {
      return rowsPath;
   }

   public void setRowsPath(String rowsPath) {
      this.rowsPath = rowsPath;
   }

   public String getHeadersPath() {
      return headersPath;
   }

   public void setHeadersPath(String headersPath) {
      this.headersPath = headersPath;
   }

   @Override
   protected void writeAttributes(PrintWriter writer) {
      super.writeAttributes(writer);

      writer.print(" expanded=\"" + expanded + "\"");
      writer.print(" expandTop=\"" + expandTop + "\"");
   }

   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(suffix != null) {
         writer.println("<suffix><![CDATA[" + suffix + "]]></suffix>");
      }

      if(requestType != null) {
         writer.println("<requestType><![CDATA[" + requestType + "]]></requestType>");
      }

      if(contentType != null) {
         writer.println("<contentType><![CDATA[" + contentType + "]]></contentType>");
      }

      if(requestBody != null) {
         writer.println("<requestBody><![CDATA[" + requestBody + "]]></requestBody>");
      }

      if(expandedPath != null && !expandedPath.isEmpty()) {
         writer.format("<expandedPath><![CDATA[%s]]></expandedPath>%n", expandedPath);
      }

      if(headersPath != null && !headersPath.isEmpty()) {
         writer.format("<headersPath><![CDATA[%s]]></headersPath>%n", headersPath);
      }

      if(rowsPath != null && !rowsPath.isEmpty()) {
         writer.format("<rowsPath><![CDATA[%s]]></rowsPath>%n", rowsPath);
      }

      if(paginationSpec != null) {
         writer.println("<paginationSpec>");
         paginationSpec.writeXML(writer);
         writer.println("</paginationSpec>");
      }
   }

   @Override
   protected void parseAttributes(Element tag) throws Exception {
      super.parseAttributes(tag);

      expanded = "true".equals(Tool.getAttribute(tag, "expanded"));
      expandTop = "true".equals(Tool.getAttribute(tag, "expandTop"));
   }

   @Override
   public void parseContents(Element root) throws Exception {
      super.parseContents(root);
      suffix = Tool.getChildValueByTagName(root, "suffix");
      requestBody = Tool.getChildValueByTagName(root, "requestBody");
      contentType = Tool.getChildValueByTagName(root, "contentType");
      requestType = Tool.getChildValueByTagName(root, "requestType");
      expandedPath = Tool.getChildValueByTagName(root, "expandedPath");
      headersPath = Tool.getChildValueByTagName(root, "headersPath");
      rowsPath = Tool.getChildValueByTagName(root, "rowsPath");
      Tool.getChildObjectByTagName(root, "paginationSpec", () -> paginationSpec);
   }

   @Override
   public AbstractRestQuery clone() {
      AbstractRestQuery copy = (AbstractRestQuery) super.clone();

      if(paginationSpec != null) {
         copy.paginationSpec = (PaginationSpec) paginationSpec.clone();
      }

      return copy;
   }

   private String suffix;
   private boolean expanded = true;
   private String expandedPath;
   private boolean expandTop = true;
   protected PaginationSpec paginationSpec = new PaginationSpec();
   private String requestType = "GET";
   private String contentType = "application/json";
   private String requestBody;
   private String rowsPath;
   private String headersPath;
}
