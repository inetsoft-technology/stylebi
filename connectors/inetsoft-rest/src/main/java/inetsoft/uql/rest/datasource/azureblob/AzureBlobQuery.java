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
package inetsoft.uql.rest.datasource.azureblob;

import inetsoft.sree.SreeEnv;
import inetsoft.uql.rest.json.EndpointJsonQuery;
import inetsoft.uql.rest.pagination.*;
import inetsoft.uql.rest.xml.EndpointXMLQuery;
import inetsoft.uql.tabular.*;

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
})
public class AzureBlobQuery extends EndpointXMLQuery<AzureBlobEndpoint> {
   public AzureBlobQuery() {
      super(AzureBlobDataSource.TYPE);
   }

   @Override
   public String getXpath() {
      return "*";
   }

   @Property(label="URL Suffix")
   @Override
   public void setSuffix(String suffix) {
      // no-op
   }

   @Override
   protected void updatePagination(AzureBlobEndpoint endpoint) {
      if(endpoint.isPaged()) {
         paginationSpec = PaginationSpec.builder()
            .type(PaginationType.ITERATION)
            // hasNextParam unnecessary but here for reference when comparing JSON
            .hasNextParam(PaginationParamType.XPATH, "/EnumerationResults/NextMarker/text()")
            .pageOffsetParamToRead(PaginationParamType.XPATH, "/EnumerationResults/NextMarker/text()")
            .pageOffsetParamToWrite(PaginationParamType.QUERY, "marker")
            .build();
      }
      else {
         paginationSpec = PaginationSpec.builder()
            .type(PaginationType.NONE)
            .build();
      }
   }

   public Map<String, AzureBlobEndpoint> getEndpointMap() {
      if("true".equals(SreeEnv.getProperty("debug.endpoints"))) {
         return EndpointJsonQuery.Endpoints.load(AzureBlobEndpoints.class);
      }

      return Singleton.INSTANCE.endpoints;
   }

   enum Singleton {
      INSTANCE;
      private final Map<String, AzureBlobEndpoint> endpoints =
         EndpointJsonQuery.Endpoints.load(AzureBlobEndpoints.class);
   }
}
