/*
 * inetsoft-rest - StyleBI is a business intelligence web application.
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
package inetsoft.uql.rest.xml;

import inetsoft.uql.rest.AbstractRestQuery;
import inetsoft.uql.rest.pagination.PaginationParameter;
import inetsoft.uql.rest.pagination.PaginationType;
import inetsoft.uql.rest.xml.schema.XMLSchema;
import inetsoft.uql.tabular.*;
import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.io.PrintWriter;

@View(vertical=true, value={
   @View1("suffix"),
   @View1("schema"),
   @View1(value="schemaUrl", visibleMethod="isSchemaUsed"),
   @View1("xpath"),
   @View1(type = ViewType.LABEL, text = "Example: /bookstore/book/title", col = 1),
   @View1(type = ViewType.PANEL, colspan = 2, elements = {
         @View2(value = "expanded", paddingRight = 30),
         @View2(value = "expandTop", visibleMethod = "isExpanded", align = ViewAlign.FILL)
      }),
   @View1(value = "expandedPath", visibleMethod = "isExpandedPathEnabled"),
   @View1(value = "timeout"),
   @View1("requestType"),
   @View1(value = "contentType", visibleMethod = "isPostRequest"),
   @View1(value = "requestBody", visibleMethod = "isPostRequest"),

   @View1("paginationType"),
   @View1(value = "pageCountXpathValue", visibleMethod="isPageCountPagination"),
   @View1(value = "pageNumberURLVariableValue", visibleMethod="isPageCountPagination"),
   @View1(type = ViewType.LABEL, text = "Example: PGNUM would replace home/page/{{PGNUM}} with the correct page number.",
          col = 1, visibleMethod = "isPageCountPagination"),
   @View1(value = "zeroBasedPageIndex", visibleMethod="isPageCountPagination"),
})
public class RestXMLQuery extends AbstractRestQuery {
   public RestXMLQuery() {
      this(RestXMLDataSource.TYPE);
   }

   public RestXMLQuery(String type) {
      super(type);
   }

   @Property(label="URL Suffix", checkEnvVariables = true)
   @Override
   public String getSuffix() {
      return super.getSuffix();
   }

   @Property(label="XML Schema")
   @PropertyEditor(tags={"NONE", "DTD", "XSD"},
                   labels={"None", "DTD", "XSD"})
   public XMLSchema getSchema() {
      return schema;
   }

   public void setSchema(XMLSchema schema) {
      this.schema = schema;
   }

   @Property(label="XML Schema URL")
   @PropertyEditor(dependsOn="schema")
   public String getSchemaUrl() {
      return schemaUrl;
   }

   public void setSchemaUrl(String schemaUrl) {
      this.schemaUrl = schemaUrl;
   }

   @Property(label="XPath expression")
   public String getXpath() {
      return xpath;
   }

   public void setXpath(String xpath) {
      this.xpath = xpath;
   }

   @Property(label="Pagination")
   @PropertyEditor(tags={"NONE", "PAGE_COUNT"},
                   labels={"None", "Page Count"})
   public void setPaginationType(PaginationType type) {
      paginationSpec.setType(type);
   }

   @Property(label="Page Count XPath")
   @PropertyEditor(dependsOn = "paginationType")
   public String getPageCountXpathValue() {
      return getPageCountXpath().getValue();
   }

   public void setPageCountXpathValue(String value) {
      getPageCountXpath().setValue(value);
   }

   @Property(label="Page Number URL Variable")
   @PropertyEditor(dependsOn = "paginationType")
   public String getPageNumberURLVariableValue() {
      return getPageNumberUrlVariable().getValue();
   }

   public void setPageNumberURLVariableValue(String value) {
      getPageNumberUrlVariable().setValue(value);
   }

   @Property(label="Zero-based Page Index")
   @PropertyEditor(dependsOn = "paginationType")
   public boolean isZeroBasedPageIndex() {
      return paginationSpec.isZeroBasedPageIndex();
   }

   public void setZeroBasedPageIndex(boolean zeroBasedPageNumber) {
      paginationSpec.setZeroBasedPageIndex(zeroBasedPageNumber);
   }

   public boolean isSchemaUsed() {
      return schema != XMLSchema.NONE;
   }

   public boolean isPageCountPagination() {
      return paginationSpec.getType() == PaginationType.PAGE_COUNT;
   }

   private PaginationParameter getPageCountXpath() {
      return paginationSpec.getPageCountXpath();
   }

   private PaginationParameter getPageNumberUrlVariable() {
      return paginationSpec.getPageNumberUrlVariable();
   }

   @Override
   protected void writeAttributes(PrintWriter writer) {
      super.writeAttributes(writer);

      if(schema != null) {
         writer.print(" schema=\"" + schema + "\"");
      }

      if(schemaUrl != null) {
         writer.print(" schemaUrl=\"" + schemaUrl + "\"");
      }

      if(xpath != null) {
         writer.print(" xpath=\"" + xpath + "\"");
      }
   }

   @Override
   protected void parseAttributes(Element tag) throws Exception {
      super.parseAttributes(tag);

      schema = XMLSchema.valueOf(Tool.getAttribute(tag, "schema"));
      schemaUrl = Tool.getAttribute(tag, "schemaUrl");
      xpath = Tool.getAttribute(tag, "xpath");
   }

   private XMLSchema schema = XMLSchema.NONE;
   private String schemaUrl;
   private String xpath;
}
