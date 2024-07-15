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
package inetsoft.uql.rest.json;

import inetsoft.uql.rest.AbstractRestQuery;
import inetsoft.uql.rest.json.lookup.*;
import inetsoft.uql.rest.pagination.*;
import inetsoft.uql.tabular.*;
import inetsoft.util.Tool;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;
import java.util.*;

@View(vertical = true, value = {
   @View1(vertical = true, type = ViewType.PANEL, elements = {
      @View2("suffix"),
      @View2("jsonPath"),
      @View2(type = ViewType.LABEL, text = "Example: $.store.book", col = 1),
      @View2(value = "expanded"),
      @View2(value = "expandTop", visibleMethod = "isExpanded"),
      @View2(value = "expandedPath", visibleMethod = "isExpandedPathEnabled"),
      @View2(value = "timeout"),
      @View2("requestType"),
      @View2(value = "contentType", visibleMethod = "isPostRequest"),
      @View2(value = "requestBody", visibleMethod = "isPostRequest"),
      @View2("paginationType"),
   }),
   @View1(type = ViewType.PANEL, align = ViewAlign.CENTER,
      elements = {
         @View2(type = ViewType.BUTTON, text = "Add Lookup Query", paddingRight = 20, align = ViewAlign.RIGHT,
            button = @Button(type = ButtonType.METHOD, method = "addLookupQuery",
               enabledMethod = "isAddLookupQueryButtonEnabled")),
         @View2(type = ViewType.BUTTON, text = "Remove Lookup Query", align = ViewAlign.LEFT,
            button = @Button(type = ButtonType.METHOD, method = "removeLookupQuery",
               enabledMethod = "isRemoveLookupQueryButtonEnabled"))
      }),
   @View1(type = ViewType.LABEL, text = "lookup.description", visibleMethod = "isLookupVisible0"),
   @View1(vertical = true, type = ViewType.PANEL, visibleMethod = "isLookupVisible0", elements = {
      @View2(value = "lookupUrl0"),
      @View2(value = "lookupJsonPath0"),
      @View2(value = "lookupKey0"),
      @View2(value = "lookupIgnoreBaseUrl0")
   }),
   @View1(vertical = true, type = ViewType.PANEL, visibleMethod = "isLookupVisible1", elements = {
      @View2(value = "lookupUrl1"),
      @View2(value = "lookupJsonPath1"),
      @View2(value = "lookupKey1"),
      @View2(value = "lookupIgnoreBaseUrl1")
   }),
   @View1(vertical = true, type = ViewType.PANEL, visibleMethod = "isLookupVisible2", elements = {
      @View2(value = "lookupUrl2"),
      @View2(value = "lookupJsonPath2"),
      @View2(value = "lookupKey2"),
      @View2(value = "lookupIgnoreBaseUrl2")
   }),
   @View1(vertical = true, type = ViewType.PANEL, visibleMethod = "isLookupVisible3", elements = {
      @View2(value = "lookupUrl3"),
      @View2(value = "lookupJsonPath3"),
      @View2(value = "lookupKey3"),
      @View2(value = "lookupIgnoreBaseUrl3")
   }),
   @View1(vertical = true, type = ViewType.PANEL, visibleMethod = "isLookupVisible4", elements = {
      @View2(value = "lookupUrl4"),
      @View2(value = "lookupJsonPath4"),
      @View2(value = "lookupKey4"),
      @View2(value = "lookupIgnoreBaseUrl4")
   }),

   // PAGINATION PANEL
   @View1(type = ViewType.PANEL, elements = {
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
public class RestJsonQuery extends AbstractRestQuery {
   public RestJsonQuery() {
      super(RestJsonDataSource.TYPE);
   }

   protected RestJsonQuery(String type) {
      super(type);
   }

   @Property(label="URL Suffix", checkEnvVariables = true)
   @Override
   public String getSuffix() {
      return super.getSuffix();
   }

   /**
    * This method is marked deprecated to avoid mistakenly calling it in the runtime code.
    *
    * @deprecated call {@link RestJsonQuery#getValidJsonPath()} instead.
    */
   @SuppressWarnings("DeprecatedIsStillUsed")
   @Deprecated
   @Property(label="Json Path")
   @PropertyEditor(columns=60)
   public String getJsonPath() {
      return jsonpath;
   }

   public void setJsonPath(String jsonpath) {
      this.jsonpath = jsonpath;
   }

   @Property(label="Pagination")
   @PropertyEditor(tags={"NONE", "PAGE_COUNT", "TOTAL_COUNT_AND_OFFSET", "TOTAL_COUNT_AND_PAGE", "ITERATION", "LINK_ITERATION"},
                   labels={"None", "Page Count", "Total Count And Offset", "Total Count And Page", "Iteration", "Link Iteration"})
   public void setPaginationType(PaginationType type) {
      paginationSpec.setType(type);
   }

   @Property(label="Total Pages Parameter")
   @PropertyEditor(dependsOn = "paginationType")
   public String getTotalPagesParamValue() {
      return getTotalPagesParam().getValue();
   }

   public void setTotalPagesParamValue(String value) {
      getTotalPagesParam().setValue(value);
   }

   @Property(label="Total Pages Parameter Type")
   @PropertyEditor(tags={"JSON_PATH", "HEADER"},
                   labels={"Json Path", "Header"},
                   dependsOn = "paginationType")
   public PaginationParamType getTotalPagesParamType() {
      return getTotalPagesParam().getType();
   }

   public void setTotalPagesParamType(PaginationParamType type) {
      getTotalPagesParam().setType(type);
   }

   @Property(label="Zero-based Page Index")
   @PropertyEditor(dependsOn = "paginationType")
   public boolean isZeroBasedPageIndex() {
      return paginationSpec.isZeroBasedPageIndex();
   }

   public void setZeroBasedPageIndex(boolean zeroBasedPageNumber) {
      paginationSpec.setZeroBasedPageIndex(zeroBasedPageNumber);
   }

   @Property(label="Page Number Parameter To Write")
   @PropertyEditor(dependsOn = "paginationType")
   public String getPageNumberParamToWriteValue() {
      return getPageNumberParamToWrite().getValue();
   }

   public void setPageNumberParamToWriteValue(String value) {
      getPageNumberParamToWrite().setValue(value);
   }

   @Property(label="Increment offset by 1 before writing")
   @PropertyEditor(dependsOn = "paginationType")
   public boolean isIncrementOffset() {
      return paginationSpec.isIncrementOffset();
   }

   public void setIncrementOffset(boolean incrementOffset) {
      paginationSpec.setIncrementOffset(incrementOffset);
   }

   @Property(label="Page Number Parameter To Write Type")
   @PropertyEditor(tags={"QUERY", "HEADER"},
                   labels={"Query", "Header"},
                   dependsOn = "paginationType")
   public PaginationParamType getPageNumberParamToWriteType() {
      return getPageNumberParamToWrite().getType();
   }

   public void setPageNumberParamToWriteType(PaginationParamType type) {
      getPageNumberParamToWrite().setType(type);
   }

   @Property(label="Has-Next Parameter")
   @PropertyEditor(dependsOn = "paginationType")
   public String getHasNextParamValue() {
      return getHasNextParam().getValue();
   }

   public void setHasNextParamValue(String value) {
      getHasNextParam().setValue(value);
   }

   @Property(label="Has-Next Parameter Type")
   @PropertyEditor(tags={"JSON_PATH", "HEADER"},
                   labels={"Json Path", "Header"},
                   dependsOn = "paginationType")
   public PaginationParamType getHasNextParamType() {
      return getHasNextParam().getType();
   }

   public void setHasNextParamType(PaginationParamType type) {
      getHasNextParam().setType(type);
   }

   @Property(label="Page Offset Parameter To Read")
   @PropertyEditor(dependsOn = "paginationType")
   public String getPageOffsetParamToReadValue() {
      return getPageOffsetParamToRead().getValue();
   }

   public void setPageOffsetParamToReadValue(String value) {
      getPageOffsetParamToRead().setValue(value);
   }

   @Property(label="Page Offset Parameter To Read Type")
   @PropertyEditor(tags={"JSON_PATH", "HEADER"},
                   labels={"Json Path", "Header"},
                   dependsOn = "paginationType")
   public PaginationParamType getPageOffsetParamToReadType() {
      return getPageOffsetParamToRead().getType();
   }

   public void setPageOffsetParamToReadType(PaginationParamType type) {
      getPageOffsetParamToRead().setType(type);
   }

   @Property(label="Page Offset Parameter To Write")
   @PropertyEditor(dependsOn = "paginationType")
   public String getPageOffsetParamToWriteValue() {
      return getPageOffsetParamToWrite().getValue();
   }

   public void setPageOffsetParamToWriteValue(String value) {
      getPageOffsetParamToWrite().setValue(value);
   }

   @Property(label="Page Offset Parameter To Write Type")
   @PropertyEditor(tags={"QUERY", "HEADER"},
                   labels={"Query", "Header"},
                   dependsOn = "paginationType")
   public PaginationParamType getPageOffsetParamToWriteType() {
      return getPageOffsetParamToWrite().getType();
   }

   public void setPageOffsetParamToWriteType(PaginationParamType type) {
      getPageOffsetParamToWrite().setType(type);
   }

   @Property(label="Total Count Parameter")
   @PropertyEditor(dependsOn = "paginationType")
   public String getTotalCountParamValue() {
      return getTotalCountParam().getValue();
   }

   public void setTotalCountParamValue(String value) {
      getTotalCountParam().setValue(value);
   }

   @Property(label="Total Count Parameter Type")
   @PropertyEditor(tags={"JSON_PATH", "HEADER"},
                   labels={"Json Path", "Header"},
                   dependsOn = "paginationType")
   public PaginationParamType getTotalCountParamType() {
      return getTotalCountParam().getType();
   }

   public void setTotalCountParamType(PaginationParamType type) {
      getTotalCountParam().setType(type);
   }

   @Property(label="Offset Parameter")
   @PropertyEditor(dependsOn = "paginationType")
   public String getOffsetParamValue() {
      return getOffsetParam().getValue();
   }

   public void setOffsetParamValue(String value) {
      getOffsetParam().setValue(value);
   }

   @Property(label="Offset Parameter Type")
   @PropertyEditor(tags={"QUERY", "HEADER", "JSON_PATH"},
                   labels={"Query", "Header", "Json Path"},
                   dependsOn = "paginationType")
   public PaginationParamType getOffsetParamType() {
      return getOffsetParam().getType();
   }

   public void setOffsetParamType(PaginationParamType type) {
      getOffsetParam().setType(type);
   }

   @Property(label="Max Results Per Page")
   @PropertyEditor(dependsOn = "paginationType")
   public int getMaxResultsPerPage() {
      return paginationSpec.getMaxResultsPerPage();
   }

   public void setMaxResultsPerPage(int maxResultsPerPage) {
      paginationSpec.setMaxResultsPerPage(maxResultsPerPage);
   }

   @Property(label="Link Parameter")
   @PropertyEditor(dependsOn = "paginationType")
   public String getLinkParamValue() {
      return getLinkParam().getValue();
   }

   public void setLinkParamValue(String value) {
      getLinkParam().setValue(value);
   }

   @Property(label="Link Parameter Type")
   @PropertyEditor(tags={"JSON_PATH", "HEADER", "LINK_HEADER"},
                   labels={"Json Path", "Header", "Link Header"},
                   dependsOn = "paginationType")
   public PaginationParamType getLinkParamType() {
      return getLinkParam().getType();
   }

   public void setLinkParamType(PaginationParamType type) {
      getLinkParam().setType(type);
   }

   @Property(label="Link Relation")
   @PropertyEditor(dependsOn = {"paginationType", "linkParamType"})
   public String getLinkRelation() {
      return getLinkParam().getLinkRelation();
   }

   @Property(label = "Lookup URL Suffix")
   @SuppressWarnings("unused")
   public String getLookupUrl0() {
      return getLookupURL(0);
   }

   @SuppressWarnings("unused")
   public void setLookupUrl0(String url) {
      setLookupURL(0, url);
   }

   @Property(label = "Json Path")
   @SuppressWarnings("unused")
   public String getLookupJsonPath0() {
      return getLookupJsonPath(0);
   }

   @SuppressWarnings("unused")
   public void setLookupJsonPath0(String jsonPath) {
      setLookupJsonPath(0, jsonPath);
   }

   @Property(label = "Key")
   @SuppressWarnings("unused")
   public String getLookupKey0() {
      return getLookupKey(0);
   }

   @SuppressWarnings("unused")
   public void setLookupKey0(String key) {
      setLookupKey(0, key);
   }

   @Property(label = "Ignore Base Data Source URL")
   @SuppressWarnings("unused")
   public boolean getLookupIgnoreBaseUrl0() {
      return getLookupIgnoreBaseUrl(0);
   }

   @SuppressWarnings("unused")
   public void setLookupIgnoreBaseUrl0(boolean ignoreBaseUrl) {
      setLookupIgnoreBaseUrl(0, ignoreBaseUrl);
   }

   @SuppressWarnings("unused")
   public boolean isLookupVisible0() {
      return lookupEndpointVisible(0);
   }

   @Property(label = "Lookup URL Suffix")
   @SuppressWarnings("unused")
   public String getLookupUrl1() {
      return getLookupURL(1);
   }

   @SuppressWarnings("unused")
   public void setLookupUrl1(String url) {
      setLookupURL(1, url);
   }

   @Property(label = "Json Path")
   @SuppressWarnings("unused")
   public String getLookupJsonPath1() {
      return getLookupJsonPath(1);
   }

   @SuppressWarnings("unused")
   public void setLookupJsonPath1(String jsonPath) {
      setLookupJsonPath(1, jsonPath);
   }

   @Property(label = "Key")
   @SuppressWarnings("unused")
   public String getLookupKey1() {
      return getLookupKey(1);
   }

   @SuppressWarnings("unused")
   public void setLookupKey1(String key) {
      setLookupKey(1, key);
   }

   @Property(label = "Ignore Base Data Source URL")
   @SuppressWarnings("unused")
   public boolean getLookupIgnoreBaseUrl1() {
      return getLookupIgnoreBaseUrl(1);
   }

   @SuppressWarnings("unused")
   public void setLookupIgnoreBaseUrl1(boolean ignoreBaseUrl) {
      setLookupIgnoreBaseUrl(1, ignoreBaseUrl);
   }

   @SuppressWarnings("unused")
   public boolean isLookupVisible1() {
      return lookupEndpointVisible(1);
   }

   @Property(label = "Lookup URL Suffix")
   @SuppressWarnings("unused")
   public String getLookupUrl2() {
      return getLookupURL(2);
   }

   @SuppressWarnings("unused")
   public void setLookupUrl2(String url) {
      setLookupURL(2, url);
   }

   @Property(label = "Json Path")
   @SuppressWarnings("unused")
   public String getLookupJsonPath2() {
      return getLookupJsonPath(2);
   }

   @SuppressWarnings("unused")
   public void setLookupJsonPath2(String jsonPath) {
      setLookupJsonPath(2, jsonPath);
   }

   @Property(label = "Key")
   @SuppressWarnings("unused")
   public String getLookupKey2() {
      return getLookupKey(2);
   }

   @SuppressWarnings("unused")
   public void setLookupKey2(String key) {
      setLookupKey(2, key);
   }

   @Property(label = "Ignore Base Data Source URL")
   @SuppressWarnings("unused")
   public boolean getLookupIgnoreBaseUrl2() {
      return getLookupIgnoreBaseUrl(2);
   }

   @SuppressWarnings("unused")
   public void setLookupIgnoreBaseUrl2(boolean ignoreBaseUrl) {
      setLookupIgnoreBaseUrl(2, ignoreBaseUrl);
   }

   @SuppressWarnings("unused")
   public boolean isLookupVisible2() {
      return lookupEndpointVisible(2);
   }

   @Property(label = "Lookup URL Suffix")
   @SuppressWarnings("unused")
   public String getLookupUrl3() {
      return getLookupURL(3);
   }

   @SuppressWarnings("unused")
   public void setLookupUrl3(String url) {
      setLookupURL(3, url);
   }

   @Property(label = "Json Path")
   @SuppressWarnings("unused")
   public String getLookupJsonPath3() {
      return getLookupJsonPath(3);
   }

   @SuppressWarnings("unused")
   public void setLookupJsonPath3(String jsonPath) {
      setLookupJsonPath(3, jsonPath);
   }

   @Property(label = "Key")
   @SuppressWarnings("unused")
   public String getLookupKey3() {
      return getLookupKey(3);
   }

   @SuppressWarnings("unused")
   public void setLookupKey3(String key) {
      setLookupKey(3, key);
   }

   @Property(label = "Ignore Base Data Source URL")
   @SuppressWarnings("unused")
   public boolean getLookupIgnoreBaseUrl3() {
      return getLookupIgnoreBaseUrl(3);
   }

   @SuppressWarnings("unused")
   public void setLookupIgnoreBaseUrl3(boolean ignoreBaseUrl) {
      setLookupIgnoreBaseUrl(3, ignoreBaseUrl);
   }

   @SuppressWarnings("unused")
   public boolean isLookupVisible3() {
      return lookupEndpointVisible(3);
   }

   @Property(label = "Lookup URL Suffix")
   @SuppressWarnings("unused")
   public String getLookupUrl4() {
      return getLookupURL(4);
   }

   @SuppressWarnings("unused")
   public void setLookupUrl4(String url) {
      setLookupURL(4, url);
   }

   @Property(label = "Json Path")
   @SuppressWarnings("unused")
   public String getLookupJsonPath4() {
      return getLookupJsonPath(4);
   }

   @SuppressWarnings("unused")
   public void setLookupJsonPath4(String jsonPath) {
      setLookupJsonPath(4, jsonPath);
   }

   @Property(label = "Key")
   @SuppressWarnings("unused")
   public String getLookupKey4() {
      return getLookupKey(4);
   }

   @SuppressWarnings("unused")
   public void setLookupKey4(String key) {
      setLookupKey(4, key);
   }

   @Property(label = "Ignore Base Data Source URL")
   @SuppressWarnings("unused")
   public boolean getLookupIgnoreBaseUrl4() {
      return getLookupIgnoreBaseUrl(4);
   }

   @SuppressWarnings("unused")
   public void setLookupIgnoreBaseUrl4(boolean ignoreBaseUrl) {
      setLookupIgnoreBaseUrl(4, ignoreBaseUrl);
   }

   @SuppressWarnings("unused")
   public boolean isLookupVisible4() {
      return lookupEndpointVisible(4);
   }

   public void addLookupQuery() {
      addLookupQuery(null);
   }

   // sessionId is just for being discovered by TabularUtil#callButtonMethods
   public void addLookupQuery(String sessionId) {
      lookupUrls.add("{param" + (lookupUrls.size() + 1) + "}");
      lookupJsonPaths.add("");
      lookupKeys.add("");
      lookupIgnoreBaseUrl.add(false);
   }

   public boolean isAddLookupQueryButtonEnabled() {
      return lookupUrls.size() < EndpointJsonQuery.LOOKUP_QUERY_LIMIT;
   }

   public void removeLookupQuery() {
      removeLookupQuery(null);
   }

   // sessionId is just for being discovered by TabularUtil#callButtonMethods
   public void removeLookupQuery(String sessionId) {
      if(!lookupUrls.isEmpty()) {
         lookupUrls.remove(lookupUrls.size() - 1);
         lookupJsonPaths.remove(lookupJsonPaths.size() - 1);
         lookupKeys.remove(lookupKeys.size() - 1);
         lookupIgnoreBaseUrl.remove(lookupIgnoreBaseUrl.size() - 1);
      }
   }

   public boolean isRemoveLookupQueryButtonEnabled() {
      return isLookupVisible0();
   }

   public void setLinkRelation(String value) {
      getLinkParam().setLinkRelation(value);
   }

   public String getValidJsonPath() {
      String jsonPath = getJsonPath();

      if(jsonPath == null || jsonPath.isEmpty()) {
         jsonPath = "$";
      }

      return jsonPath;
   }

   public boolean isPageCountPagination() {
      return paginationSpec.getType() == PaginationType.PAGE_COUNT;
   }

   public boolean isTotalCountPagination() {
      return isTotalCountAndOffsetPagination() || isTotalCountAndPagePagination();
   }

   public boolean isTotalCountAndOffsetPagination() {
      return paginationSpec.getType() == PaginationType.TOTAL_COUNT_AND_OFFSET;
   }

   public boolean isTotalCountAndPagePagination() {
      return paginationSpec.getType() == PaginationType.TOTAL_COUNT_AND_PAGE;
   }

   public boolean isPageCountOrTotalCountAndPagePagination() {
      return isPageCountPagination() || isTotalCountAndPagePagination();
   }

   public boolean isIterationPagination() {
      return paginationSpec.getType() == PaginationType.ITERATION;
   }

   public boolean isLinkIterationPagination() {
      return paginationSpec.getType() == PaginationType.LINK_ITERATION;
   }

   public boolean isLinkHeaderParamDisplayed() {
      return isLinkIterationPagination() &&
         getLinkParam().getType() == PaginationParamType.LINK_HEADER;
   }

   public String getLookupURL(int index) {
      if(index < lookupUrls.size()) {
         return lookupUrls.get(index);
      }

      return null;
   }

   public void setLookupURL(int index, String url) {
      if(url == null) {
         if(index < lookupUrls.size()) {
            lookupUrls.set(index, null);

            // don't leave null endpoints
            while(lookupUrls.size() > 0 &&
               lookupUrls.get(lookupUrls.size() - 1) == null)
            {
               lookupUrls.remove(lookupUrls.size() - 1);
            }
         }

         return;
      }

      if(index >= lookupUrls.size()) {
         if(index < EndpointJsonQuery.LOOKUP_QUERY_LIMIT) {
            while (lookupUrls.size() <= index) {
               addLookupQuery();
            }
         }
         else {
            return;
         }
      }

      lookupUrls.set(index, url);
   }

   public String getLookupJsonPath(int index) {
      if(index < lookupJsonPaths.size()) {
         return lookupJsonPaths.get(index);
      }

      return null;
   }

   public void setLookupJsonPath(int index, String jsonPath) {
      if(jsonPath == null) {
         if(index < lookupJsonPaths.size()) {
            lookupJsonPaths.set(index, null);

            // don't leave null endpoints
            while(lookupJsonPaths.size() > 0 &&
               lookupJsonPaths.get(lookupJsonPaths.size() - 1) == null)
            {
               lookupJsonPaths.remove(lookupJsonPaths.size() - 1);
            }
         }

         return;
      }

      if(index >= lookupJsonPaths.size()) {
         if(index < EndpointJsonQuery.LOOKUP_QUERY_LIMIT) {
            while (lookupJsonPaths.size() <= index) {
               addLookupQuery();
            }
         }
         else {
            return;
         }
      }

      lookupJsonPaths.set(index, jsonPath);
   }

   public String getLookupKey(int index) {
      if(index < lookupKeys.size()) {
         return lookupKeys.get(index);
      }

      return null;
   }

   public void setLookupKey(int index, String key) {
      if(key == null) {
         if(index < lookupKeys.size()) {
            lookupKeys.set(index, null);

            // don't leave null endpoints
            while(lookupKeys.size() > 0 &&
               lookupKeys.get(lookupKeys.size() - 1) == null)
            {
               lookupKeys.remove(lookupKeys.size() - 1);
            }
         }

         return;
      }

      if(index >= lookupKeys.size()) {
         if(index < EndpointJsonQuery.LOOKUP_QUERY_LIMIT) {
            while (lookupKeys.size() <= index) {
               addLookupQuery();
            }
         }
         else {
            return;
         }
      }

      lookupKeys.set(index, key);
   }

   public String getLookupValue(int index) {
      if(index < lookupValues.size()) {
         return lookupValues.get(index);
      }

      return null;
   }

   public void setLookupValue(int index, String value) {
      if(index < lookupValues.size()) {
         lookupValues.set(index, value);
      }
      else if(index == lookupValues.size()){
         lookupValues.add(value);
      }
   }

   public boolean getLookupIgnoreBaseUrl(int index) {
      if(index < lookupUrls.size()) {
         return lookupIgnoreBaseUrl.get(index);
      }

      return false;
   }

   public void setLookupIgnoreBaseUrl(int index, boolean ignoreBaseUrl) {
      if(index >= lookupIgnoreBaseUrl.size()) {
         if(index < EndpointJsonQuery.LOOKUP_QUERY_LIMIT && ignoreBaseUrl) {
            while (lookupIgnoreBaseUrl.size() <= index) {
               addLookupQuery();
            }
         }
         else {
            return;
         }
      }

      lookupIgnoreBaseUrl.set(index, ignoreBaseUrl);
   }

   public boolean lookupEndpointVisible(int index) {
      return lookupUrls.size() > index && lookupUrls.get(index) != null;
   }

   public boolean isIgnoreBaseUrl() {
      return ignoreBaseUrl;
   }

   public void setIgnoreBaseUrl(boolean ignoreBaseUrl) {
      this.ignoreBaseUrl = ignoreBaseUrl;
   }

   public int getLookupDepth() {
      return lookupDepth;
   }

   public void setLookupDepth(int lookupDepth) {
      this.lookupDepth = lookupDepth;
   }

   public int getLookupCount() {
      return lookupUrls.size();
   }

   public CustomJsonLookupEndpoint getCustomLookup(int index) {
      if(index >= lookupEndpoints.size()) {
         return null;
      }

      return this.lookupEndpoints.get(index);
   }

   public void setCustomLookup(int index, CustomJsonLookupEndpoint lookupEndpoint) {
      this.lookupEndpoints.set(index, lookupEndpoint);
   }

   public List<JsonLookupQuery> getLookupQueries() {
      if(lookupEndpoints.size() != lookupUrls.size()) {
         initEndpoints();
      }

      final JsonLookupEndpoint jsonLookupEndpoint = getCustomLookup(lookupDepth);

      if(jsonLookupEndpoint != null) {
         final JsonLookupQuery lookupQuery = new JsonLookupQuery();
         lookupQuery.setLookupEndpoint(jsonLookupEndpoint);
         lookupQuery.setExpandArrays(true);
         lookupQuery.setJsonPath("$");

         if(lookupDepth == lookupEndpoints.size() - 1) {
            lookupQuery.setExpandArrays(this.isExpanded());
            lookupQuery.setTopLevelOnly(this.isExpandTop());
         }

         return Collections.singletonList(lookupQuery);
      }
      else {
         return Collections.emptyList();
      }
   }

   private void initEndpoints() {
      lookupEndpoints = new ArrayList<>(EndpointJsonQuery.LOOKUP_QUERY_LIMIT);

      for(int i = 0; i < lookupUrls.size(); i ++) {
         lookupEndpoints.add(CustomJsonLookupEndpoint.builder()
            .url(lookupUrls.get(i))
            .endpoint("CUSTOM" + (i + 1))
            .jsonPath(lookupJsonPaths.get(i))
            .parameterName("param" + (i + 1))
            .key(lookupKeys.get(i))
            .ignoreBaseURL(lookupIgnoreBaseUrl.get(i))
            .build()
         );
      }
   }

   private PaginationParameter getTotalPagesParam() {
      return paginationSpec.getTotalPagesParam();
   }

   private PaginationParameter getPageNumberParamToWrite() {
      return paginationSpec.getPageNumberParamToWrite();
   }

   private PaginationParameter getHasNextParam() {
      return paginationSpec.getHasNextParam();
   }

   private PaginationParameter getPageOffsetParamToRead() {
      return paginationSpec.getPageOffsetParamToRead();
   }

   private PaginationParameter getPageOffsetParamToWrite() {
      return paginationSpec.getPageOffsetParamToWrite();
   }

   private PaginationParameter getTotalCountParam() {
      return paginationSpec.getTotalCountParam();
   }

   private PaginationParameter getOffsetParam() {
      return paginationSpec.getOffsetParam();
   }

   private PaginationParameter getLinkParam() {
      return paginationSpec.getLinkParam();
   }

   @Override
   public void writeAttributes(PrintWriter writer) {
      super.writeAttributes(writer);
   }

   @Override
   public void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(jsonpath != null) {
         writer.println("<jsonpath><![CDATA[" + jsonpath + "]]></jsonpath>");
      }

      if(!lookupUrls.isEmpty()) {
         writer.println("<lookupEndpoints>");

         for(int i = 0; i < lookupUrls.size(); i ++) {
            writer.println("<lookupEndpoint ignoreBaseUrl=\"" + lookupIgnoreBaseUrl.get(i) + "\" >");
            writer.print("<url><![CDATA[" + lookupUrls.get(i) + "]]></url>");
            writer.print("<jsonpath><![CDATA[" + lookupJsonPaths.get(i) + "]]></jsonpath>");
            writer.print("<key><![CDATA[" + lookupKeys.get(i) + "]]></key>");
            writer.println("</lookupEndpoint>");
         }

         writer.println("</lookupEndpoints>");
      }
   }

   @Override
   protected void parseAttributes(Element tag) throws Exception {
      super.parseAttributes(tag);
   }

   @Override
   public void parseContents(Element root) throws Exception {
      super.parseContents(root);
      Element node = Tool.getChildNodeByTagName(root, "jsonpath");
      jsonpath = Tool.getValue(node);

      if((node = Tool.getChildNodeByTagName(root, "lookupEndpoints")) != null) {
         NodeList endpoints = Tool.getChildNodesByTagName(node, "lookupEndpoint");
         lookupEndpoints = new ArrayList<>(EndpointJsonQuery.LOOKUP_QUERY_LIMIT);

         for(int i = 0; i < endpoints.getLength(); i++) {
            Element endpoint = (Element) endpoints.item(i);

            node = Tool.getChildNodeByTagName(endpoint, "url");
            String value = Tool.getValue(node) == null ? "" : Tool.getValue(node);
            lookupUrls.add(value);

            node = Tool.getChildNodeByTagName(endpoint, "jsonpath");
            value = Tool.getValue(node) == null ? "" : Tool.getValue(node);
            lookupJsonPaths.add(value);

            node = Tool.getChildNodeByTagName(endpoint, "key");
            value = Tool.getValue(node) == null ? "" : Tool.getValue(node);
            lookupKeys.add(value);

            boolean ignoreBaseUrl = "true".equals(Tool.getAttribute(endpoint, "ignoreBaseUrl"));
            lookupIgnoreBaseUrl.add(ignoreBaseUrl);
         }
      }
   }
   
   @Override
   public RestJsonQuery clone() {
      RestJsonQuery copy = (RestJsonQuery) super.clone();

      if(lookupEndpoints != null) {
         copy.lookupEndpoints = new ArrayList<>();
      }

      if(lookupUrls != null) {
         copy.lookupUrls = new ArrayList<>(lookupUrls);
      }

      if(lookupJsonPaths != null) {
         copy.lookupJsonPaths = new ArrayList<>(lookupJsonPaths);
      }

      if(lookupKeys != null) {
         copy.lookupKeys = new ArrayList<>(lookupKeys);
      }

      if(lookupValues != null) {
         copy.lookupValues = new ArrayList<>(lookupValues);
      }

      if(lookupIgnoreBaseUrl != null) {
         copy.lookupIgnoreBaseUrl = new ArrayList<>(lookupIgnoreBaseUrl);
      }

      return copy;
   }

   private String jsonpath;
   private boolean ignoreBaseUrl = false;
   private int lookupDepth;
   private List<CustomJsonLookupEndpoint> lookupEndpoints =
      new ArrayList<>(EndpointJsonQuery.LOOKUP_QUERY_LIMIT);
   private List<String> lookupUrls =
      new ArrayList<>(EndpointJsonQuery.LOOKUP_QUERY_LIMIT);
   private List<String> lookupJsonPaths =
      new ArrayList<>(EndpointJsonQuery.LOOKUP_QUERY_LIMIT);
   private List<String> lookupKeys =
      new ArrayList<>(EndpointJsonQuery.LOOKUP_QUERY_LIMIT);
   private List<String> lookupValues =
      new ArrayList<>(EndpointJsonQuery.LOOKUP_QUERY_LIMIT);
   private List<Boolean> lookupIgnoreBaseUrl =
      new ArrayList<>(EndpointJsonQuery.LOOKUP_QUERY_LIMIT);
}
