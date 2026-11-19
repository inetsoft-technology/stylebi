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

import inetsoft.uql.util.HeadersAndRowsJsonTable;

import java.util.*;

/**
 * Reads the JSON an InfluxQL query returns:
 *
 * <pre>
 * {"results":[{"statement_id":0,"series":[{"name":"cpu","tags":{"host":"a"},
 *    "columns":["time","usage"],"values":[["2023-11-14T22:13:20Z",0.5], ...]}]}]}
 * </pre>
 *
 * <p>The column names are in {@code columns} and the rows are arrays, which is what
 * {@link HeadersAndRowsJsonTable} reads. What it does not do is the part the InfluxQL API wraps
 * around that, which the user would otherwise be told nothing about:</p>
 *
 * <ul>
 *    <li>A statement that fails still comes back as HTTP 200, with the message in
 *    {@code results[0].error}. It is raised, not shown as an empty table.</li>
 *    <li>A statement that matches nothing has no {@code series} key at all. It is an empty table.</li>
 *    <li>A {@code GROUP BY} query returns one series per group. They are all read, with the tag
 *    values as extra columns, since without them the rows could not be told apart.</li>
 *    <li>Only one statement is read. A second would have its own columns and need its own table, so
 *    several in one request are refused rather than silently dropped.</li>
 *    <li>A chunked response is a stream of separate JSON documents of which only the first is
 *    parsed, so it is refused (the {@code partial} flag marks it) rather than silently cut short.</li>
 * </ul>
 */
public class InfluxQLJsonTable extends HeadersAndRowsJsonTable {
   public InfluxQLJsonTable(String headersPath, String rowsPath) {
      super(headersPath, rowsPath);
   }

   @Override
   public void loadStreamed(Object json) {
      if(!(json instanceof Map)) {
         super.loadStreamed(json);
         return;
      }

      final Map<?, ?> body = (Map<?, ?>) json;

      if(body.get("error") != null) {
         throw new IllegalStateException("InfluxDB returned an error: " + body.get("error"));
      }

      final Object results = body.get("results");

      if(!(results instanceof List) || ((List<?>) results).isEmpty()) {
         return;
      }

      final List<?> statements = (List<?>) results;

      if(statements.size() > 1) {
         throw new IllegalStateException(
            "The InfluxQL response holds " + statements.size() + " statements; run one statement " +
            "per table.");
      }

      if(!(statements.get(0) instanceof Map)) {
         return;
      }

      final Map<?, ?> statement = (Map<?, ?>) statements.get(0);

      if(statement.get("error") != null) {
         throw new IllegalStateException("InfluxDB could not run the statement: " +
                                            statement.get("error"));
      }

      if(Boolean.TRUE.equals(statement.get("partial"))) {
         throw new IllegalStateException(
            "The InfluxQL response was split into chunks, which are not supported; set Chunked " +
            "to false.");
      }

      final Object series = statement.get("series");

      if(!(series instanceof List)) {
         return;
      }

      for(Object s : (List<?>) series) {
         if(s instanceof Map) {
            super.loadStreamed(singleSeries((Map<?, ?>) s));
         }
      }
   }

   /**
    * The response as {@link HeadersAndRowsJsonTable} reads it, holding the one series, with the
    * tag values of the series added as columns.
    */
   private Map<String, Object> singleSeries(Map<?, ?> series) {
      final List<Object> columns = new ArrayList<>();
      final Object seriesColumns = series.get("columns");

      if(seriesColumns instanceof List) {
         columns.addAll((List<?>) seriesColumns);
      }

      final Map<?, ?> tags = series.get("tags") instanceof Map ? (Map<?, ?>) series.get("tags") : null;
      final List<Object> values = new ArrayList<>();

      if(tags != null) {
         columns.addAll(tags.keySet());
      }

      if(series.get("values") instanceof List) {
         for(Object row : (List<?>) series.get("values")) {
            if(row instanceof List && tags != null) {
               final List<Object> withTags = new ArrayList<>((List<?>) row);
               withTags.addAll(tags.values());
               values.add(withTags);
            }
            else {
               values.add(row);
            }
         }
      }

      final Map<String, Object> single = new LinkedHashMap<>();
      single.put("columns", columns);
      single.put("values", values);
      final Map<String, Object> result = new LinkedHashMap<>();
      result.put("series", Collections.singletonList(single));
      final Map<String, Object> body = new LinkedHashMap<>();
      body.put("results", Collections.singletonList(result));
      return body;
   }

   /** Where InfluxQL puts the column names, in the form {@link HeadersAndRowsJsonTable} reads. */
   public static final String HEADERS_PATH = "results[0].series[0].columns";
   /** Where InfluxQL puts the rows. */
   public static final String ROWS_PATH = "results[0].series[0].values";
}
