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
package inetsoft.uql.rest.datasource.graphql;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.uql.rest.json.RestJsonQuery;
import inetsoft.uql.rest.pagination.PaginationSpec;
import inetsoft.uql.rest.pagination.PaginationType;
import inetsoft.uql.tabular.*;
import inetsoft.util.CoreTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.Map;

@View(vertical = true, value = {
   @View1(
      type = ViewType.PANEL,
      vertical = true,
      colspan = 2,
      elements = {
         @View2(type = ViewType.LABEL, text = "Query"),
         @View2(type = ViewType.EDITOR, value = "queryString"),
         @View2(type = ViewType.LABEL, text = "Variables"),
         @View2(type = ViewType.EDITOR, value = "variables"),
      }
   ),
   @View1("usePagination"),
   @View1(
      type = ViewType.PANEL,
      vertical = true,
      colspan = 2,
      elements = {
         @View2(
            type = ViewType.LABEL,
            text = "Enter the name of your pagination variable without the $", col = 1,
            visibleMethod = "isUsePagination"
         ),
         @View2(value = "paginationVariable", visibleMethod = "isUsePagination"),
         @View2(
            type = ViewType.LABEL,
            text = "JSON Path that counds the number of returned records for pagination", col = 1,
            visibleMethod = "isUsePagination"
         ),
         @View2(value = "paginationCountPath", visibleMethod = "isUsePagination")
      }
   ),
   @View1("requestType"),
   @View1("jsonPath"),
   @View1(type = ViewType.LABEL, text = "Example: $.store.book"),
   @View1(type = ViewType.PANEL, colspan = 2, elements = {
      @View2(value = "expanded", paddingRight = 30),
      @View2(value = "expandTop", visibleMethod = "isExpanded", align = ViewAlign.FILL)
   }),
   @View1(value = "expandedPath", visibleMethod = "isExpandedPathEnabled"),
   @View1(value = "timeout"),
})
public class GraphQLQuery extends RestJsonQuery {
   public GraphQLQuery() {
      super(GraphQLDataSource.TYPE);
   }

   public GraphQLQuery(String type) {
      super(type);
   }

   @Override
   public String getRequestBody() {
      final ObjectMapper objectMapper = new ObjectMapper();

      try {
         final JsonNode jsonNode = objectMapper.readTree(getVariables());
         final Map<String, String> parameters = new HashMap<>();
         parameters.put("query", queryString);
         final String variablesString =
            jsonNode.isTextual() ? jsonNode.textValue() : objectMapper.writeValueAsString(jsonNode);
         parameters.put(GraphQLDataSource.VARIABLE_KEY, variablesString);
         return objectMapper.writeValueAsString(parameters);
      }
      catch(IOException e) {
         LOG.error("Failed to parse GraphQL query", e);
         return null;
      }
   }

   @Override
   public PaginationType getPaginationType() {
      return usePagination ? PaginationType.GRAPHQL : PaginationType.NONE;
   }

   @Override
   public PaginationSpec getPaginationSpec() {
      if(usePagination) {
         return PaginationSpec.builder()
            .type(PaginationType.GRAPHQL)
            .pageNumberParamToWrite(null, getPaginationVariable())
            .recordCountPath(getPaginationCountPath())
            .build();
      }
      else {
         return PaginationSpec.builder().type(PaginationType.NONE).build();
      }
   }

   @Property(label="Request Type")
   @PropertyEditor(tags={"POST", "GET"}, labels={"POST", "GET"})
   public String getRequestType() {
      return super.getRequestType();
   }


   @Property(label = "Pagination Variable")
   @PropertyEditor(dependsOn = "usePagination")
   public String getPaginationVariable() {
      return paginationVariable;
   }

   public void setPaginationVariable(String paginationVariable) {
      this.paginationVariable = paginationVariable;
   }

   @Property(label = "Pagination JSON Path")
   @PropertyEditor(dependsOn = "usePagination")
   public String getPaginationCountPath() {
      return paginationCountPath;
   }

   public void setPaginationCountPath(String paginationCountPath) {
      this.paginationCountPath = paginationCountPath;
   }

   @Property(label="Query String", required=true)
   @PropertyEditor(rows=10, columns=65)
   public String getQueryString() {
      return queryString;
   }

   public void setQueryString(String queryString) {
      this.queryString = queryString;
   }

   @Property(label="Variables")
   @PropertyEditor(rows=3, columns=65)
   public String getVariables() {
      return variables == null || variables.isEmpty() ? "{}" : variables;
   }

   public void setVariables(String variables) {
      this.variables = variables;
   }

   @Property(label = "Use Pagination")
   public boolean isUsePagination() {
      return usePagination;
   }

   public void setUsePagination(boolean usePagination) {
      this.usePagination = usePagination;
   }


   @Override
   public void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(queryString != null && !queryString.isEmpty()) {
         writer.format("<queryString>%s</queryString>\n", queryString);
      }

      if(variables != null && !variables.isEmpty()) {
         writer.format("<variables>%s</variables>\n", variables);
      }

      writer.format("<usePagination>%s</usePagination>\n", usePagination);

      if(paginationVariable != null && !paginationVariable.isEmpty()) {
         writer.format("<paginationVariable>%s</paginationVariable>\n", paginationVariable);
      }

      if(paginationCountPath != null && !paginationCountPath.isEmpty()) {
         writer.format("<paginationCountPath>%s</paginationCountPath>\n", paginationCountPath);
      }
   }

   @Override
   public void parseContents(Element root) throws Exception {
      super.parseContents(root);
      queryString = CoreTool.getChildValueByTagName(root, "queryString");
      variables = CoreTool.getChildValueByTagName(root, "variables");
      paginationVariable = CoreTool.getChildValueByTagName(root, "paginationVariable");
      usePagination = "true".equals(CoreTool.getChildValueByTagName(root, "usePagination"));
      paginationCountPath = CoreTool.getChildValueByTagName(root, "paginationCountPath");
   }

   private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
   private String queryString;
   private String variables;
   private boolean usePagination;
   private String paginationVariable;
   private String paginationCountPath;
}
