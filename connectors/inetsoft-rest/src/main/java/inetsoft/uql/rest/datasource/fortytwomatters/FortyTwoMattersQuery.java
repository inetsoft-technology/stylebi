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
package inetsoft.uql.rest.datasource.fortytwomatters;

import inetsoft.uql.rest.json.EndpointJsonQuery;
import inetsoft.uql.rest.pagination.*;
import inetsoft.uql.tabular.*;
import inetsoft.sree.SreeEnv;

import java.util.Map;

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
   @View1(value = "additionalParameters", verticalAlign = ViewAlign.TOP),
   @View1(value = "requestBody", visibleMethod = "isPostRequest"),
   @View1(type = ViewType.BUTTON, text = "Populate Request Body With Template", col = 1,
      visibleMethod = "populateRequestBodyTemplateButtonVisible",
      button = @Button(
         type = ButtonType.METHOD,
         method = "populateRequestBodyTemplate"
      )),
   @View1("jsonPath"),
   @View1(type = ViewType.LABEL, text = "Example: $.store.book"),
   @View1(type = ViewType.PANEL, colspan = 2, elements = {
         @View2(value = "expanded", paddingRight = 30),
         @View2(value = "expandTop", visibleMethod = "isExpanded", align = ViewAlign.FILL)
      }),
   @View1(value = "timeout"),
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
public class FortyTwoMattersQuery extends EndpointJsonQuery<FortyTwoMattersEndpoint> {
   public FortyTwoMattersQuery() {
      super(FortyTwoMattersDataSource.TYPE);
      setJsonPath("$");
   }

   @Property(label="Request Body")
   @PropertyEditor(columns = 40, rows = 16, enabledMethod = "isPostRequest", dependsOn = "endpoint")
   @Override
   public String getRequestBody() {
      return super.getRequestBody();
   }

   @Override
   public void setRequestBody(String requestBody) {
      if(isPostRequest() && requestBody != null) {
         super.setRequestBody(requestBody);
      }
   }

   @Override
   protected void updatePagination(FortyTwoMattersEndpoint endpoint) {
      if(endpoint.isPaged()) {
         paginationSpec = PaginationSpec.builder()
            .type(PaginationType.PAGE_COUNT)
            .totalPagesParam(PaginationParamType.JSON_PATH, endpoint.getPagePath())
            .pageNumberParamToWrite(PaginationParamType.QUERY, "page")
            .maxResultsPerPageParam(PaginationParamType.QUERY, "limit")
            .maxResultsPerPage((
               (FortyTwoMattersDataSource) getDataSource()).isFreeTrial() ? endpoint.getFreePageLimit() : endpoint.getPageLimit()
            )
            .build();
      }
      else {
         paginationSpec = PaginationSpec.builder()
            .type(PaginationType.NONE)
            .build();
      }

      if(endpoint.isPost()) {
         setRequestType("POST");
         setContentType("application/json");
         bodyTemplate = endpoint.getBodyTemplate();
      }
      else {
         setRequestType("GET");
         setContentType(null);
         super.setRequestBody(null);
         bodyTemplate = null;
      }
   }

   public void populateRequestBodyTemplate(String sessionId) {
      setRequestBody(bodyTemplate);
   }

   public boolean populateRequestBodyTemplateButtonVisible() {
      return bodyTemplate != null;
   }

   @Override
   public Map<String, FortyTwoMattersEndpoint> getEndpointMap() {
      if("true".equals(SreeEnv.getProperty("debug.endpoints"))) {
         return Endpoints.load(FortyTwoMattersEndpoints.class);
      }

      return Singleton.INSTANCE.endpoints;
   }

   static FortyTwoMattersEndpoint getEndpoint(String endpoint) {
      return Singleton.INSTANCE.endpoints.get(endpoint);
   }

   enum Singleton {
      INSTANCE;
      private final Map<String, FortyTwoMattersEndpoint> endpoints =
         Endpoints.load(FortyTwoMattersEndpoints.class);
   }

   private String bodyTemplate;
}
