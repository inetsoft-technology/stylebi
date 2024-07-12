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
package inetsoft.uql.rest.datasource.toggl;

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
public class TogglQuery extends EndpointJsonQuery<TogglEndpoint> {
   public TogglQuery() {
      super(TogglDataSource.TYPE);
      setJsonPath("$");
   }

   @Override
   protected void updatePagination(TogglEndpoint endpoint) {
      if(endpoint.isPaged()) {
         paginationSpec = PaginationSpec.builder()
            .type(PaginationType.TOTAL_COUNT_AND_PAGE)
            .totalCountParam(PaginationParamType.JSON_PATH, "$.total_count")
            .pageNumberParamToWrite(PaginationParamType.QUERY, "page")
            .zeroBasedPageIndex(false)
            .maxResultsPerPage(50)
            .build();
      }
      else {
         paginationSpec = PaginationSpec.builder()
            .type(PaginationType.NONE)
            .build();
      }
   }

   @Override
   public Map<String, TogglEndpoint> getEndpointMap() {
      if("true".equals(SreeEnv.getProperty("debug.endpoints"))) {
         return Endpoints.load(TogglEndpoints.class);
      }

      return Singleton.INSTANCE.endpoints;
   }

   enum Singleton {
      INSTANCE;
      private final Map<String, TogglEndpoint> endpoints =
         Endpoints.load(TogglEndpoints.class);
    }
}
