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
package inetsoft.uql.rest.datasource.youtubeanalytics;

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
   @View1("additionalParameters"),
   @View1("jsonPath"),
   @View1(type = ViewType.LABEL, text = "Example: $.store.book"),
   @View1(type = ViewType.PANEL, colspan = 2, elements = {
      @View2(value = "expanded", paddingRight = 30, visibleMethod = "isExpandedVisible"),
      @View2(value = "expandTop", visibleMethod = "isExpandTopVisible", align = ViewAlign.FILL)
   }),
   @View1("expanded"),
   @View1(value = "expandedPath", visibleMethod = "isExpanded"),
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
public class YouTubeAnalyticsQuery extends EndpointJsonQuery<YouTubeAnalyticsEndpoint> {
   public YouTubeAnalyticsQuery() {
      super(YouTubeAnalyticsDataSource.TYPE);
      setJsonPath("$");
      setExpandTop(false);
   }

   @Override
   protected void updatePagination(YouTubeAnalyticsEndpoint endpoint) {
      final int paged = endpoint.getPageType();

      if(paged == 1) {
         paginationSpec = PaginationSpec.builder()
            .type(PaginationType.OFFSET)
            .offsetParam(PaginationParamType.QUERY, "startIndex")
            .maxResultsPerPageParam(PaginationParamType.QUERY, "maxResults")
            .maxResultsPerPage(100)
            .recordCountPath("$.rows.length()")
            .baseRecordLength(0)
            .build();
         setHeadersPath("columnHeaders");
         setRowsPath("rows");
      }
      else if(paged == 2) {
         paginationSpec = PaginationSpec.builder()
            .type(PaginationType.ITERATION)
            .hasNextParam(PaginationParamType.JSON_PATH, "$.nextPageToken")
            .pageOffsetParamToRead(PaginationParamType.JSON_PATH, "$.nextPageToken")
            .pageOffsetParamToWrite(PaginationParamType.QUERY, "pageToken")
            .build();
         setHeadersPath(null);
         setRowsPath(null);
      }
      else {
         paginationSpec = PaginationSpec.builder()
            .type(PaginationType.NONE)
            .build();
         setHeadersPath(null);
         setRowsPath(null);
      }
   }

   @Override
   public Map<String, YouTubeAnalyticsEndpoint> getEndpointMap() {
      if("true".equals(SreeEnv.getProperty("debug.endpoints"))) {
         return Endpoints.load(YouTubeAnalyticsEndpoints.class);
      }

      return Singleton.INSTANCE.endpoints;
   }

   public boolean isExpandedVisible() {
      return (getHeadersPath() == null || getHeadersPath().isEmpty()) &&
         (getRowsPath() == null || getRowsPath().isEmpty());
   }

   public boolean isExpandTopVisible() {
      return isExpandedVisible() && isExpanded();
   }

   @Override
   public boolean isExpandedPathEnabled() {
      return super.isExpandedPathEnabled() && isExpandedVisible();
   }

   enum Singleton {
      INSTANCE;
      private final Map<String, YouTubeAnalyticsEndpoint> endpoints =
         Endpoints.load(YouTubeAnalyticsEndpoints.class);
    }
}
