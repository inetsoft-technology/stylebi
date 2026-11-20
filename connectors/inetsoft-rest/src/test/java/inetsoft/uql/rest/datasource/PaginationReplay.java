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
package inetsoft.uql.rest.datasource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import inetsoft.test.ReplaySupport;
import inetsoft.test.ScriptedHttpHandler;
import inetsoft.uql.rest.AbstractRestDataSource;
import inetsoft.uql.rest.json.EndpointJsonQuery;
import inetsoft.uql.rest.pagination.PaginationParamType;
import inetsoft.uql.rest.pagination.PaginationSpec;
import inetsoft.uql.tabular.RestParameter;
import inetsoft.uql.tabular.RestParameters;

import java.util.*;

/**
 * Builds the responses of a paged endpoint from the endpoint's own pagination settings and runs the
 * query against them: where the settings say the total, the next offset or the has-more flag is,
 * the generated response puts it there. The query must then read all the pages and stop. A
 * mismatch means the settings and the way the strategy reads them disagree.
 *
 * <p>Supported: TOTAL_COUNT_AND_OFFSET, OFFSET and ITERATION with JSON path parameters, and the
 * query/header parameters named by the settings.
 */
public final class PaginationReplay {
   private PaginationReplay() {
   }

   /** The outcome of one endpoint, or {@code null} when its pagination is not generated. */
   public record Result(String type, int expectedRows, int rows, List<String> requests,
                 List<String> problems)
   {
   }

   private static final ObjectMapper MAPPER = new ObjectMapper();

   public static Result run(AbstractRestDataSource<?> dataSource, EndpointJsonQuery<?> query,
                            String endpoint)
      throws Exception
   {
      return run(dataSource, query, endpoint, true);
   }

   /**
    * @param checkOffsetValues false to leave out the check of the offset values sent by OFFSET
    *                          paging, for a data source with a known problem there.
    */
   public static Result run(AbstractRestDataSource<?> dataSource, EndpointJsonQuery<?> query,
                            String endpoint, boolean checkOffsetValues)
      throws Exception
   {
      query.setDataSource(dataSource);
      query.setEndpoint(endpoint);

      if(query.isPostRequest()) {
         query.setRequestBody("{}");
      }

      PaginationSpec spec = query.getPaginationSpec();
      String type = String.valueOf(spec.getType());
      String recordsKey = "results";
      boolean rootArray = false;

      if(spec.getRecordCountPath() != null) {
         String path = spec.getRecordCountPath();

         if(path.equals("$.length()")) {
            rootArray = true;
         }
         else if(path.startsWith("$.") && path.endsWith(".length()")) {
            recordsKey = path.substring(2, path.length() - ".length()".length());
         }
      }

      // the next offset is the id of the last record: $.<records>[-1].<field>
      java.util.regex.Matcher lastRecord = spec.getType() == inetsoft.uql.rest.pagination.PaginationType.ITERATION &&
         spec.getPageOffsetParamToRead() != null && spec.getPageOffsetParamToRead().getValue() != null ?
         java.util.regex.Pattern.compile("^\\$\\.(\\w+)\\[-1\\]\\.(\\w+)$")
            .matcher(spec.getPageOffsetParamToRead().getValue()) : null;
      boolean offsetFromLastRecord = lastRecord != null && lastRecord.matches();

      if(offsetFromLastRecord) {
         recordsKey = lastRecord.group(1);
      }

      query.setJsonPath(rootArray ? "$" : "$." + recordsKey);
      RestParameters restParameters = query.getParameters();

      for(RestParameter parameter : restParameters.getParameters()) {
         if(parameter.isRequired()) {
            parameter.setValue("1");
         }
      }

      query.setParameters(restParameters);
      List<String> problems = new ArrayList<>();
      String[] pages;
      int expectedRows;
      String offsetName = null;
      String[] offsetValues = null;
      boolean firstHasOffset = false;
      String headerName = null;
      String headerValue = null;
      ScriptedHttpHandler.Reply[] replies = null;
      String firstValue = null;
      String secondUrl = null;
      int expectedRequests = 2;

      switch(spec.getType()) {
      case TOTAL_COUNT_AND_OFFSET: {
         boolean totalInHeader = spec.getTotalCountParam().getType() == PaginationParamType.HEADER;

         if(!totalInHeader && spec.getTotalCountParam().getType() != PaginationParamType.JSON_PATH ||
            spec.getOffsetParam().getType() != PaginationParamType.QUERY)
         {
            return null;
         }

         int max = spec.getMaxResultsPerPage();
         int second = Math.max(1, max / 2);
         expectedRows = max + second;
         pages = new String[] {
            page(spec, recordsKey, rootArray, max, 1, totalInHeader ? Map.of() : Map.of(
               spec.getTotalCountParam().getValue(), expectedRows)),
            page(spec, recordsKey, rootArray, second, 1000, totalInHeader ? Map.of() : Map.of(
               spec.getTotalCountParam().getValue(), expectedRows)) };
         if(totalInHeader) {
            headerName = spec.getTotalCountParam().getValue();
            headerValue = String.valueOf(expectedRows);
         }
         offsetName = spec.getOffsetParam().getValue();
         offsetValues = new String[] { String.valueOf(max) };
         break;
      }
      case OFFSET: {
         int max = spec.getMaxResultsPerPage();
         expectedRows = max + 30;
         // the OFFSET strategy only stops at a page that has no records
         pages = new String[] {
            page(spec, recordsKey, rootArray, max, 1, Map.of()),
            page(spec, recordsKey, rootArray, 30, 1000, Map.of()),
            page(spec, recordsKey, rootArray, 0, 2000, Map.of()) };
         offsetName = spec.getOffsetParam().getValue();
         offsetValues = new String[] { String.valueOf(max), String.valueOf(max + 30) };
         firstHasOffset = true;
         expectedRequests = 3;
         break;
      }
      case ITERATION: {
         if(spec.getHasNextParam().getType() != PaginationParamType.JSON_PATH ||
            spec.getPageOffsetParamToRead().getType() != PaginationParamType.JSON_PATH ||
            spec.getPageOffsetParamToWrite().getType() != PaginationParamType.QUERY)
         {
            return null;
         }

         expectedRows = 6;
         String hasNext = spec.getHasNextParam().getValue();
         String read = spec.getPageOffsetParamToRead().getValue();
         String hasNextValue = spec.getHasNextParamValue();
         // the has-next value is the very place the offset is read from, or contains it
         boolean covers = strip(read).equals(strip(hasNext)) ||
            strip(read).startsWith(strip(hasNext) + ".");
         Map<String, Object> first = new LinkedHashMap<>();
         Map<String, Object> last = new LinkedHashMap<>();
         if(!offsetFromLastRecord) {
            first.put(read, "NEXT-1");
         }

         if(!covers) {
            first.put(hasNext, hasNextValue != null ? "MORE" : true);
            last.put(hasNext, hasNextValue != null ? hasNextValue : false);

            if(!offsetFromLastRecord) {
               last.put(read, "STALE");
            }
         }
         else if(hasNextValue != null) {
            last.put(hasNext, hasNextValue);
         }

         pages = new String[] { page(spec, recordsKey, rootArray, 3, 1, first),
                                page(spec, recordsKey, rootArray, 3, 1000, last) };
         offsetName = spec.getPageOffsetParamToWrite().getValue();
         offsetValues = new String[] { offsetFromLastRecord ? "3" : "NEXT-1" };
         break;
      }
      case PAGE: {
         if(spec.getPageNumberParamToWrite().getType() != PaginationParamType.QUERY ||
            spec.getRecordCountPath() == null)
         {
            return null;
         }

         int first = spec.getFirstPageIndex();
         expectedRows = 5;
         pages = new String[] { page(spec, recordsKey, rootArray, 3, 1, Map.of()),
                                page(spec, recordsKey, rootArray, 2, 1000, Map.of()),
                                page(spec, recordsKey, rootArray, 0, 2000, Map.of()) };
         offsetName = spec.getPageNumberParamToWrite().getValue();
         firstValue = String.valueOf(first);
         offsetValues = new String[] { String.valueOf(first + 1), String.valueOf(first + 2) };
         firstHasOffset = true;
         expectedRequests = 3;
         break;
      }
      case PAGE_COUNT: {
         PaginationParamType totalType = spec.getTotalPagesParam().getType();

         if(spec.getPageNumberParamToWrite().getType() != PaginationParamType.QUERY ||
            totalType != PaginationParamType.JSON_PATH && totalType != PaginationParamType.HEADER)
         {
            return null;
         }

         expectedRows = 5;
         boolean inHeader = totalType == PaginationParamType.HEADER;
         pages = new String[] {
            page(spec, recordsKey, rootArray, 3, 1, inHeader ? Map.of() : Map.of(
               spec.getTotalPagesParam().getValue(), 2)),
            page(spec, recordsKey, rootArray, 2, 1000, inHeader ? Map.of() : Map.of(
               spec.getTotalPagesParam().getValue(), 2)) };

         if(inHeader) {
            headerName = spec.getTotalPagesParam().getValue();
            headerValue = "2";
         }

         offsetName = spec.getPageNumberParamToWrite().getValue();
         offsetValues = new String[] { String.valueOf(spec.getFirstPageIndex() + 1) };
         break;
      }
      case TOTAL_COUNT_AND_PAGE: {
         PaginationParamType totalType = spec.getTotalCountParam().getType();

         if(spec.getPageNumberParamToWrite().getType() != PaginationParamType.QUERY ||
            totalType != PaginationParamType.JSON_PATH && totalType != PaginationParamType.HEADER ||
            query.getDataSource() instanceof inetsoft.uql.rest.datasource.mixpanel.MixpanelDataSource)
         {
            return null;
         }

         int max = spec.getMaxResultsPerPage();
         int second = Math.max(1, max / 2);
         expectedRows = max + second;
         boolean inHeader = totalType == PaginationParamType.HEADER;
         pages = new String[] {
            page(spec, recordsKey, rootArray, max, 1, inHeader ? Map.of() : Map.of(
               spec.getTotalCountParam().getValue(), expectedRows)),
            page(spec, recordsKey, rootArray, second, 1000, inHeader ? Map.of() : Map.of(
               spec.getTotalCountParam().getValue(), expectedRows)) };

         if(inHeader) {
            headerName = spec.getTotalCountParam().getValue();
            headerValue = String.valueOf(expectedRows);
         }

         offsetName = spec.getPageNumberParamToWrite().getValue();
         offsetValues = new String[] { String.valueOf(spec.getFirstPageIndex() + 1) };
         break;
      }
      case LINK_ITERATION: {
         PaginationParamType linkType = spec.getLinkParam().getType();
         String next = "https://next.example.com/page2?cursor=abc";
         expectedRows = 6;
         secondUrl = next;

         if(linkType == PaginationParamType.LINK_HEADER) {
            String relation = spec.getLinkParam().getLinkRelation();
            pages = new String[] { page(spec, recordsKey, rootArray, 3, 1, Map.of()),
                                   page(spec, recordsKey, rootArray, 3, 1000, Map.of()) };
            replies = new ScriptedHttpHandler.Reply[] {
               ScriptedHttpHandler.reply(pages[0], spec.getLinkParam().getValue(),
                                         "<" + next + ">; rel=\"" + relation + "\""),
               ScriptedHttpHandler.reply(pages[1], spec.getLinkParam().getValue(),
                                         "<https://prev.example.com/page1>; rel=\"prev\"") };
         }
         else if(linkType == PaginationParamType.JSON_PATH) {
            String path = spec.getLinkParam().getValue();
            java.util.regex.Matcher filter = java.util.regex.Pattern.compile(
               "^\\$\\.(\\w+)\\[\\?\\(@\\.(\\w+) == '(\\w+)'\\)\\]\\.(\\w+)$").matcher(path);

            if(filter.matches()) {
               // an array of link objects, the next one is picked by its rel
               ObjectNode root = MAPPER.readValue(page(spec, recordsKey, rootArray, 3, 1,
                                                       Map.of()), ObjectNode.class);
               root.putArray(filter.group(1)).addObject().put(filter.group(2), filter.group(3))
                  .put(filter.group(4), next);
               pages = new String[] { root.toString(),
                                      page(spec, recordsKey, rootArray, 3, 1000, Map.of()) };
            }
            else if(path.contains("[")) {
               return null;
            }
            else {
               pages = new String[] {
                  page(spec, recordsKey, rootArray, 3, 1, Map.of(path, next)),
                  page(spec, recordsKey, rootArray, 3, 1000, Map.of()) };
            }
         }
         else if(linkType == PaginationParamType.HEADER) {
            pages = new String[] { page(spec, recordsKey, rootArray, 3, 1, Map.of()),
                                   page(spec, recordsKey, rootArray, 3, 1000, Map.of()) };
            replies = new ScriptedHttpHandler.Reply[] {
               ScriptedHttpHandler.reply(pages[0], spec.getLinkParam().getValue(), next),
               new ScriptedHttpHandler.Reply(pages[1], List.of(), null) };
         }
         else {
            return null;
         }

         break;
      }
      default:
         return null;
      }

      ScriptedHttpHandler http = new ScriptedHttpHandler();

      if(replies != null) {
         http.onReplies(url -> true, replies);
      }
      else if(headerName != null) {
         replies = new ScriptedHttpHandler.Reply[pages.length];

         for(int i = 0; i < pages.length; i++) {
            replies[i] = ScriptedHttpHandler.reply(pages[i], headerName, headerValue);
         }

         http.onReplies(url -> true, replies);
      }
      else {
         http.on(url -> true, pages);
      }

      int rows = ReplaySupport.run(query, http).size();
      List<String> requests = http.requests();

      if(rows != expectedRows) {
         problems.add("expected " + expectedRows + " rows but got " + rows);
      }

      if(requests.size() != expectedRequests) {
         problems.add("expected " + expectedRequests + " requests but got " + requests.size() +
                      ": " + requests);
      }
      else {
         if(!firstHasOffset && offsetName != null && hasParam(requests.get(0), offsetName)) {
            problems.add("the first request already has " + offsetName + ": " + requests.get(0));
         }

         if(secondUrl != null && !requests.get(1).equals(secondUrl)) {
            problems.add("the second request is not the link " + secondUrl + ": " +
                         requests.get(1));
         }

         if(firstValue != null && checkOffsetValues && !requests.get(0).matches(
            ".*[?&]" + java.util.regex.Pattern.quote(offsetName + "=" + firstValue) + "(&.*)?"))
         {
            problems.add("the first request lacks " + offsetName + "=" + firstValue + ": " +
                         requests.get(0));
         }

         for(int i = 0; offsetValues != null && checkOffsetValues && i < offsetValues.length; i++) {
            if(!requests.get(i + 1).matches(".*[?&]" + java.util.regex.Pattern.quote(offsetName + "=" + offsetValues[i]) + "(&.*)?")) {
               problems.add("request " + (i + 2) + " lacks " + offsetName + "=" + offsetValues[i] +
                            ": " + requests.get(i + 1));
            }
         }
      }

      return new Result(type, expectedRows, rows, requests, problems);
   }

   private static boolean hasParam(String url, String name) {
      return url.matches(".*[?&]" + java.util.regex.Pattern.quote(name) + "=.*");
   }

   private static String strip(String path) {
      return path.startsWith("$.") ? path.substring(2) : path;
   }

   private static String page(PaginationSpec spec, String recordsKey, boolean rootArray, int count,
                              int firstId, Map<String, Object> values)
      throws Exception
   {
      ArrayNode records = MAPPER.createArrayNode();

      for(int i = 0; i < count; i++) {
         records.addObject().put("id", firstId + i);
      }

      if(rootArray) {
         return records.toString();
      }

      ObjectNode root = MAPPER.createObjectNode();
      root.set(recordsKey, records);

      for(Map.Entry<String, Object> e : values.entrySet()) {
         String[] parts = strip(e.getKey()).split("\\.");
         ObjectNode node = root;

         for(int i = 0; i < parts.length - 1; i++) {
            node = node.has(parts[i]) && node.get(parts[i]).isObject() ?
               (ObjectNode) node.get(parts[i]) : node.putObject(parts[i]);
         }

         Object value = e.getValue();
         String last = parts[parts.length - 1];

         if(value instanceof Integer) {
            node.put(last, (Integer) value);
         }
         else if(value instanceof Boolean) {
            node.put(last, (Boolean) value);
         }
         else {
            node.put(last, String.valueOf(value));
         }
      }

      return root.toString();
   }
}
