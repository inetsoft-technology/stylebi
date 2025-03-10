package inetsoft.uql.rest.datasource.mailgun;


import inetsoft.sree.SreeEnv;
import inetsoft.uql.tabular.*;
import inetsoft.uql.rest.json.EndpointJsonQuery;
import inetsoft.uql.rest.pagination.*;

import java.util.Map;

@View(vertical = true, value = {
   @View1(value = "endpoint", affectedViews = {
      "offsetParamType",
      "linkParamValue",
      "linkParamType",
      "paginationType",
      "maxResultsPerPage",
      "totalCountParamValue"
   }),
   @View1(type = ViewType.PANEL, align = ViewAlign.LEFT, visibleMethod = "isCustomEndpoint",
      elements = {
         @View2(value = "templateEndpt"),
         @View2(type = ViewType.BUTTON, text = "Apply Endpoint Template",
            button = @Button(type = ButtonType.METHOD, method = "applyEndpointTemplate"))
      }),
   @View1(value = "customEndpt", visibleMethod = "isCustomEndpoint"),
   @View1("jsonPath"),
   @View1(type = ViewType.LABEL, text = "Example: $.store.book"),
   @View1("parameters"),
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
public class MailgunQuery extends EndpointJsonQuery<MailgunEndpoint> {
   public MailgunQuery() {
      super(MailgunDataSource.TYPE);
      setJsonPath("$");
   }

   @Override
   protected void updatePagination(MailgunEndpoint endpoint) {
      if(endpoint.getPageType() == PaginationType.TOTAL_COUNT_AND_OFFSET) {
         String totalCountParamValue = endpoint.getName().equals("Subaccounts") ?
            "$.total" : "$.total_count";
         paginationSpec = PaginationSpec.builder()
            .type(PaginationType.TOTAL_COUNT_AND_OFFSET)
            .totalCountParam(PaginationParamType.JSON_PATH, totalCountParamValue)
            .offsetParam(PaginationParamType.QUERY, "skip")
            .maxResultsPerPageParam(PaginationParamType.QUERY, "limit")
            .maxResultsPerPage(1000)
            .build();
      }
      else if(endpoint.getPageType() == PaginationType.LINK_ITERATION) {
         paginationSpec = PaginationSpec.builder()
            .type(PaginationType.LINK_ITERATION)
            .linkParam(PaginationParamType.JSON_PATH, "$.paging.next")
            .maxResultsPerPageParam(PaginationParamType.QUERY, "limit")
            .maxResultsPerPage(300)
            .build();
      }
      else {
         paginationSpec = PaginationSpec.builder()
            .type(PaginationType.NONE)
            .build();
      }
   }

   @Override
   public Map<String, MailgunEndpoint> getEndpointMap() {
      if("true".equals(SreeEnv.getProperty("debug.endpoints"))) {
         return Endpoints.load(MailgunEndpoints.class);
      }

      return MailgunQuery.Singleton.INSTANCE.endpoints;
   }

   enum Singleton {
      INSTANCE;
      private final Map<String, MailgunEndpoint> endpoints =
         Endpoints.load(MailgunEndpoints.class);
   }
}
