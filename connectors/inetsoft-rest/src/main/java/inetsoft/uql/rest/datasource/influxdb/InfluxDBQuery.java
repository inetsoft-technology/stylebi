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
package inetsoft.uql.rest.datasource.influxdb;

import inetsoft.uql.rest.json.EndpointJsonQuery;
import inetsoft.uql.rest.pagination.PaginationParamType;
import inetsoft.uql.rest.pagination.PaginationSpec;
import inetsoft.uql.rest.pagination.PaginationType;
import inetsoft.uql.tabular.*;
import inetsoft.uql.util.HeadersAndRowsJsonTable;
import inetsoft.sree.SreeEnv;
import org.w3c.dom.Element;

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
   @View1("jsonPath"),
   @View1(type = ViewType.LABEL, text = "Example: $.store.book"),
   @View1(type = ViewType.PANEL, colspan = 2, elements = {
         @View2(value = "expanded", paddingRight = 30),
         @View2(value = "expandTop", visibleMethod = "isExpanded", align = ViewAlign.FILL)
      }),
   @View1(value = "expandedPath", visibleMethod = "isExpandedPathEnabled"),
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
public class InfluxDBQuery extends EndpointJsonQuery<InfluxDBEndpoint> {
   public InfluxDBQuery() {
      super(InfluxDBDataSource.TYPE);
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
   protected void updatePagination(InfluxDBEndpoint endpoint) {
      setHeadersPath(null);
      setRowsPath(null);

      if(endpoint.isPost()) {
         setRequestType("POST");
         setContentType("application/json");
      }
      else {
         setRequestType("GET");
         setContentType(null);
         super.setRequestBody(null);

         if(endpoint.isInfluxql()) {
            setHeadersPath(InfluxQLJsonTable.HEADERS_PATH);
            setRowsPath(InfluxQLJsonTable.ROWS_PATH);
         }
      }

      paginationSpec = createPaginationSpec(endpoint);
   }

   /**
    * The paging a catalogue endpoint needs, which InfluxDB does not do the same way everywhere:
    * the list endpoints take {@code limit}/{@code offset} and say nothing about a next page (users
    * and orgs carry no {@code links.next} at all), so they are read until a page comes back short
    * or empty (a lookup of one record by name or id ignores {@code offset} and answers with that
    * record again, so it can only be read once);
    * tasks take only an {@code after} cursor and do hand back {@code links.next} while the page is
    * full. The page size is the most each endpoint accepts: 100, or 500 for tasks, where it is
    * already in the suffix.
    */
   private PaginationSpec createPaginationSpec(InfluxDBEndpoint endpoint) {
      if(!endpoint.isPost() && endpoint.isPaged()) {
         if(endpoint.getPageType() == InfluxDBEndpoint.PAGE_OFFSET &&
            endpoint.getPageItems() != null && !endpoint.getPageItems().isEmpty())
         {
            return PaginationSpec.builder()
               .type(PaginationType.OFFSET)
               .offsetParam(PaginationParamType.QUERY, "offset")
               .maxResultsPerPageParam(PaginationParamType.QUERY, "limit")
               .maxResultsPerPage(100)
               .recordCountPath("$." + endpoint.getPageItems() + ".length()")
               .baseRecordLength(0)
               .zeroBasedPageIndex(true)
               .stopOnShortPage(true)
               .build();
         }

         if(endpoint.getPageType() == InfluxDBEndpoint.PAGE_LINK) {
            return PaginationSpec.builder()
               .type(PaginationType.LINK_ITERATION)
               .linkParam(PaginationParamType.JSON_PATH, "$.links.next")
               .build();
         }
      }

      return PaginationSpec.builder()
         .type(PaginationType.NONE)
         .build();
   }

   /**
    * A saved query keeps the paging and column paths it was built with, and they are only worked
    * out again when the endpoint changes. Work them out on load for a catalogue endpoint so a query
    * saved before they were right reads correctly too.
    */
   @Override
   public void parseContents(Element root) throws Exception {
      super.parseContents(root);
      final String name = getEndpoint();

      if(name != null && !isCustomEndpoint()) {
         final InfluxDBEndpoint endpoint = getEndpointMap().get(name);

         if(endpoint != null) {
            updatePagination(endpoint);
         }
      }
   }

   /**
    * The InfluxQL table reads {@code results[0].series[0]} of the whole response, so it must be
    * handed the whole response. A JSON path that narrows it first, such as
    * {@code $.results[0].series[0].values[*]}, would leave it rows with no column names.
    */
   @Override
   public String getValidJsonPath() {
      if(isEmpty(getHeadersPath()) || isEmpty(getRowsPath())) {
         return super.getValidJsonPath();
      }

      return "$";
   }

   @Override
   public HeadersAndRowsJsonTable createHeadersAndRowsTable() {
      return new InfluxQLJsonTable(getHeadersPath(), getRowsPath());
   }

   private static boolean isEmpty(String s) {
      return s == null || s.isEmpty();
   }


   @Override
   public Map<String, InfluxDBEndpoint> getEndpointMap() {
      if("true".equals(SreeEnv.getProperty("debug.endpoints"))) {
         return Endpoints.load(InfluxDBEndpoints.class);
      }

      return Singleton.INSTANCE.endpoints;
   }

   enum Singleton {
      INSTANCE;
      private final Map<String, InfluxDBEndpoint> endpoints =
         Endpoints.load(InfluxDBEndpoints.class);
    }
}
