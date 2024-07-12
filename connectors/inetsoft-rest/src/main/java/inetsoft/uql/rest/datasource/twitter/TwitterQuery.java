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
package inetsoft.uql.rest.datasource.twitter;

import inetsoft.uql.rest.json.*;
import inetsoft.uql.rest.pagination.*;
import inetsoft.uql.tabular.*;
import inetsoft.util.CoreTool;
import inetsoft.sree.SreeEnv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.lang.invoke.MethodHandles;
import java.util.*;

@View(vertical = true, value = {
   @View1("endpoint"),
   @View1(type = ViewType.PANEL, align = ViewAlign.LEFT, visibleMethod = "isCustomEndpoint",
      elements = {
         @View2(value = "templateEndpt"),
         @View2(type = ViewType.BUTTON, text = "Apply Endpoint Template",
            button = @Button(type = ButtonType.METHOD, method = "applyEndpointTemplate"))
      }),
   @View1(value = "customEndpt", visibleMethod = "isCustomEndpoint"),
   @View1("parameters"),
   @View1(value = "usePagination", visibleMethod = "isPaged"),
   @View1("additionalParameters"),
   @View1("jsonPath"),
   @View1(type = ViewType.LABEL, text = "Example: $.store.book"),
   @View1(type = ViewType.PANEL, colspan = 2, elements = {
      @View2(value = "expanded", paddingRight = 30),
      @View2(value = "expandTop", visibleMethod = "isExpanded", align = ViewAlign.FILL)
   }),
   @View1(value = "expandedPath", visibleMethod = "isExpandedPathEnabled"),
   @View1(value = "timeout"),
   @View1(type = ViewType.PANEL, col = 1, align = ViewAlign.LEFT, visibleMethod = "isLookupEnabled",
      elements = {
      @View2(type = ViewType.BUTTON, text = "Add Lookup Query", paddingRight = 20,
         button = @Button(type = ButtonType.METHOD, method = "addLookupQuery",
            enabledMethod = "isAddLookupQueryButtonEnabled")),
      @View2(type = ViewType.BUTTON, text = "Remove Lookup Query",
         button = @Button(type = ButtonType.METHOD, method = "removeLookupQuery",
            enabledMethod = "isRemoveLookupQueryButtonEnabled"))
   }),
   @View1(value = "lookupEndpoint0", visibleMethod = "isLookupEndpointVisible0"),
   @View1(value = "lookupEndpoint1", visibleMethod = "isLookupEndpointVisible1"),
   @View1(value = "lookupEndpoint2", visibleMethod = "isLookupEndpointVisible2"),
   @View1(value = "lookupEndpoint3", visibleMethod = "isLookupEndpointVisible3"),
   @View1(value = "lookupEndpoint4", visibleMethod = "isLookupEndpointVisible4"),
   @View1(type = ViewType.PANEL, colspan = 2, elements = {
      @View2(value = "lookupExpanded", visibleMethod = "isLookupEndpointVisible0", paddingRight = 30),
      @View2(value = "lookupTopLevelOnly", visibleMethod = "isLookupExpandTopVisible", align = ViewAlign.FILL)
   }),
   //Pagination for custom endpoints
   @View1(value = "paginationType", visibleMethod = "isCustomEndpoint"),
   @View1(type = ViewType.PANEL, visibleMethod = "isCustomEndpoint", elements = {
      // page count
      @View2(value = "totalPagesParamValue", visibleMethod = "isPageCountPagination", row = 0, col = 0),
      @View2(type = ViewType.EDITOR, value = "totalPagesParamType", visibleMethod = "isPageCountPagination", row = 0, col = 2),

      // iteration
      @View2(value = "hasNextParamValue", visibleMethod = "isIterationPagination", row = 1, col = 0),
      @View2(type = ViewType.EDITOR, value = "hasNextParamType", visibleMethod = "isIterationPagination", row = 1, col = 2),
      @View2(value = "pageOffsetParamToReadValue", visibleMethod = "isIterationPagination", row = 2, col = 0),
      @View2(type = ViewType.EDITOR, value = "pageOffsetParamToReadType", visibleMethod = "isIterationPagination", row = 2, col = 2),
      @View2(value = "pageOffsetParamToWriteValue", visibleMethod = "isIterationPagination", row = 3, col = 0),
      @View2(type = ViewType.EDITOR, value = "pageOffsetParamToWriteType", visibleMethod = "isIterationPagination", row = 3, col = 2),
      @View2(value = "incrementOffset", visibleMethod = "isIterationPagination", row = 4),

      // link iteration
      @View2(value = "linkParamValue", visibleMethod = "isLinkIterationPagination", row = 5, col = 0),
      @View2(type = ViewType.EDITOR, value = "linkParamType", visibleMethod = "isLinkIterationPagination", row = 5, col = 2),
      @View2(value = "linkRelation", visibleMethod = "isLinkHeaderParamDisplayed", row = 6),

      // total count
      @View2(value = "totalCountParamValue", visibleMethod = "isTotalCountPagination", row = 7, col = 0),
      @View2(type = ViewType.EDITOR, value = "totalCountParamType", visibleMethod = "isTotalCountPagination", row = 7, col = 2),

      // total count + offset
      @View2(value = "offsetParamValue", visibleMethod = "isTotalCountAndOffsetPagination", row = 8, col = 0),
      @View2(type = ViewType.EDITOR, value = "offsetParamType", visibleMethod = "isTotalCountAndOffsetPagination", row = 8, col = 2),

      // shared
      @View2(value = "pageNumberParamToWriteValue", visibleMethod = "isPageCountOrTotalCountAndPagePagination", row = 9, col = 0),
      @View2(type = ViewType.EDITOR, value = "pageNumberParamToWriteType", visibleMethod = "isPageCountOrTotalCountAndPagePagination", row = 9, col = 2),
      @View2(value = "zeroBasedPageIndex", visibleMethod = "isPageCountOrTotalCountAndPagePagination", row = 10),
      @View2(value = "maxResultsPerPage", visibleMethod = "isTotalCountPagination", row = 11),
   }),
})
public class TwitterQuery extends EndpointJsonQuery<TwitterEndpoint> {
   public TwitterQuery() {
      super(TwitterDataSource.TYPE);
      setJsonPath("$");
   }

   public Map<String, String> getQueryParameters() {
      return queryParameters;
   }

   @Property(label = "Use Pagination")
   public boolean isUsePagination() {
      return usePagination;
   }

   public void setUsePagination(boolean usePagination) {
      this.usePagination = usePagination;
   }

   @Override
   public void postprocessSuffix(SuffixTemplate suffix) {
      // clear out existing query parameters
      queryParameters.clear();

      final RestParameters restParameters = getParameters();
      final Map<String, TemplateComponent> templateComponents = suffix.getQuery();

      for(Map.Entry<String, TemplateComponent> entry : templateComponents.entrySet()) {
         final String name = entry.getKey();
         final TemplateComponent templateComponent = entry.getValue();
         final boolean variable = templateComponent.isVariable();
         final String value = templateComponent.getValue();

         if(variable) {
            final RestParameter parameter = restParameters.findParameter(value);

            if(parameter != null) {
               final String variableValue = parameter.getValue();

               if(variableValue != null && !variableValue.isEmpty()) {
                  queryParameters.put(name, variableValue);
               }
            }
         }
         else {
            queryParameters.put(name, value);
         }
      }

      final HttpParameter[] additionalParameters = getAdditionalParameters();

      if(additionalParameters != null) {
         for(HttpParameter param : additionalParameters) {
            queryParameters.put(param.getName(), param.getValue());
         }
      }

      TwitterDataSource dataSource = (TwitterDataSource) getDataSource();
      dataSource.update(suffix.build(), queryParameters);
   }

   @Override
   protected void updatePagination(TwitterEndpoint endpoint) {
      if(!usePagination) {
         paginationSpec = PaginationSpec.builder().type(PaginationType.NONE).build();
         return;
      }

      final int paged = endpoint.getPageType();

      if(paged == 1) {
         paginationSpec = PaginationSpec.builder()
            .type(PaginationType.ITERATION)
            .hasNextParam(PaginationParamType.JSON_PATH, "$.next_cursor_str")
            .hasNextParamValue("0")
            .pageOffsetParamToRead(PaginationParamType.JSON_PATH, "$.next_cursor_str")
            .pageOffsetParamToWrite(PaginationParamType.QUERY, "cursor")
            .build();
      }
      else if(paged == 2) {
         paginationSpec = PaginationSpec.builder()
            .type(PaginationType.TWITTER_ITERATION)
            .pageOffsetParamToRead(PaginationParamType.JSON_PATH, "$.search_metadata.next_results")
            .build();
      }
      else if(paged == 3) {
         paginationSpec = PaginationSpec.builder()
            .type(PaginationType.PAGE)
            .recordCountPath("$.length()")
            .pageNumberParamToWrite(PaginationParamType.QUERY, "page")
            .build();
      }
      else {
         paginationSpec = PaginationSpec.builder()
            .type(PaginationType.NONE)
            .build();
      }
   }

   @Override
   public Map<String, TwitterEndpoint> getEndpointMap() {
      if("true".equals(SreeEnv.getProperty("debug.endpoints"))) {
         return Endpoints.load(TwitterEndpoints.class);
      }

      return Singleton.INSTANCE.endpoints;
   }

   enum Singleton {
      INSTANCE;
      private final Map<String, TwitterEndpoint> endpoints =
         Endpoints.load(TwitterEndpoints.class);
   }

   @Override
   public void writeContents(PrintWriter writer) {
      super.writeContents(writer);
      writer.format("<usePagination>%s</usePagination>%n", usePagination);
   }

   @Override
   public void parseContents(Element root) throws Exception {
      super.parseContents(root);
      usePagination = Boolean.parseBoolean(CoreTool.getChildValueByTagName(root, "usePagination"));
   }

   private Map<String, String> queryParameters = new HashMap<>();
   private boolean usePagination;
}