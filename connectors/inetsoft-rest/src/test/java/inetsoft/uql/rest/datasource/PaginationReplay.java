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
      int expectedRequests = 2;

      switch(spec.getType()) {
      case TOTAL_COUNT_AND_OFFSET: {
         if(spec.getTotalCountParam().getType() != PaginationParamType.JSON_PATH ||
            spec.getOffsetParam().getType() != PaginationParamType.QUERY)
         {
            return null;
         }

         int max = spec.getMaxResultsPerPage();
         int second = Math.max(1, max / 2);
         expectedRows = max + second;
         pages = new String[] {
            page(spec, recordsKey, rootArray, max, 1, Map.of(spec.getTotalCountParam().getValue(),
                                                             expectedRows)),
            page(spec, recordsKey, rootArray, second, 1000, Map.of(
               spec.getTotalCountParam().getValue(), expectedRows)) };
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
      default:
         return null;
      }

      ScriptedHttpHandler http = new ScriptedHttpHandler().on(url -> true, pages);
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
         if(!firstHasOffset && hasParam(requests.get(0), offsetName)) {
            problems.add("the first request already has " + offsetName + ": " + requests.get(0));
         }

         for(int i = 0; checkOffsetValues && i < offsetValues.length; i++) {
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
