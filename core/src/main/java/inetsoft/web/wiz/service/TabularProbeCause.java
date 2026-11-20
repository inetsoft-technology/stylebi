/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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

package inetsoft.web.wiz.service;

import inetsoft.uql.tabular.TabularQuery;
import inetsoft.util.CoreTool;
import inetsoft.util.UserMessage;

import java.util.List;
import java.util.Map;

/**
 * Why a tabular column probe ({@code TabularTableAssembly.loadColumnSelection}) came back with no
 * columns, when it did not throw.
 *
 * <p>A connector that fails during the probe usually recovers inside its own fetch loop and returns
 * an empty table, so the cause survives in only two places: a pending per-thread {@link CoreTool}
 * user message (connection refused, DNS, 401, unparseable body, ...), and, for a per-query timeout,
 * {@link TabularQuery#getTimedOutSeconds}. A caller that reads neither reports every one of those as
 * "returned no columns ... check credentials".</p>
 *
 * <p>Usage: call {@link #beginProbe} immediately before the probe and {@link #read} exactly once
 * after it, on the same thread. {@code read} drains the thread-local message, which is destructive.
 * A timeout is reported from the structured field, never by matching the localized timeout message;
 * the connector's own message for it ("partial preview data ... for: null") is dropped as
 * misleading. The seconds are the EFFECTIVE limit, which for a REST query with lookup queries is
 * the configured timeout times {@code lookups * 2 + 1}.</p>
 */
public final class TabularProbeCause {
   private TabularProbeCause(int timedOutSeconds, String connectorMessage) {
      this.timedOutSeconds = timedOutSeconds;
      this.connectorMessage = connectorMessage;
   }

   /**
    * Drop any user message left on this thread by an earlier probe, so it cannot be attributed to
    * the one about to run.
    */
   public static void beginProbe() {
      CoreTool.clearUserMessage();
   }

   /**
    * Collect the cause of the probe just run against {@code query}.
    *
    * @param query           the query the probe ran on (the original, not the handler's clone).
    * @param alreadyReported the {@code wizLoadColumnsError} value the caller already folds into
    *                        its message, or null. When the connector message repeats it, the
    *                        connector message is omitted so the same sentence is not printed twice.
    */
   public static TabularProbeCause read(TabularQuery query, Object alreadyReported) {
      UserMessage msg = CoreTool.getUserMessage();
      // UserMessage.merge() joins recovered messages with a newline and the text is the connector's, so
      // collapse it to one line for the one-line agent-facing refusals built from it
      String text = msg == null || msg.getMessage() == null ? "" :
         msg.getMessage().trim().replaceAll("\\s*[\\r\\n]+\\s*", " ");

      if(!text.isEmpty() && alreadyReported != null &&
         text.contains(String.valueOf(alreadyReported).trim()))
      {
         text = "";
      }

      return new TabularProbeCause(query.getTimedOutSeconds(), text);
   }

   /**
    * Whether the connector cancelled the probe for exceeding its per-query timeout.
    */
   public boolean isTimeout() {
      return timedOutSeconds > 0;
   }

   /**
    * What to append to a message that already states the probe returned no columns: the timeout
    * sentence, or the connector's own message, or "" when nothing is known. Leading space included.
    */
   public String detail() {
      if(isTimeout()) {
         return " " + timeoutSentence();
      }

      return connectorMessage.isEmpty() ? "" : " The connector reported: " + connectorMessage;
   }

   /**
    * The closing advice of a message: for a timeout, the timeout sentence in place of
    * {@code generic} (which would send the caller to credentials and parameters); otherwise
    * {@code generic} followed by the connector's message, if any.
    */
   public String advice(String generic) {
      return isTimeout() ? timeoutSentence() : generic + detail();
   }

   private String timeoutSentence() {
      return "The request timed out after " + timedOutSeconds + " s (the query's timeout limit); " +
         "raise the query's 'timeout' (seconds) or narrow the request.";
   }

   /**
    * The "request succeeded but selected zero rows" clause of an empty-columns refusal, or null
    * when that is not what happened. The three signals that say why a probe has no columns are
    * disjoint (the {@code wizLoadColumnsError} property, this class, and
    * {@link TabularQuery#getResponseShape}); this is the third, and the clause is returned only
    * when neither of the other two speaks: a load error is still the reported cause, and a timeout
    * is never reported as a success. A null result means the caller keeps its own wording.
    *
    * <p>The clause starts with a lowercase verb so each caller can lead with its own subject and
    * echoes ("The query on ..." / "The request to ... returned no columns"), and carries no
    * parameter echo.</p>
    *
    * @param query     the query the probe ran on.
    * @param loadError the {@code wizLoadColumnsError} value read from {@code query}, or null.
    * @param cause     the result of {@link #read} for the same probe.
    */
   public static String zeroRowsClause(TabularQuery query, Object loadError,
                                       TabularProbeCause cause)
   {
      Object shape = query.getResponseShape();

      if(loadError != null || cause.isTimeout()) {
         return null;
      }

      if(shape == null) {
         return completedEmptyClause(query, cause);
      }

      String rowPath = effectiveRowPath(query);
      String rowPathClause = rowPath == null ? "" : " Rows were read from '" + rowPath + "'.";
      String truncatedClause = query.isResponseShapeTruncated()
         ? " (capped — some fields may be missing from this description)" : "";
      String hintClause = arrayLocationClause(shape, rowPath);

      return "completed successfully and returned a response, but selected zero rows — for a " +
         "JSON REST endpoint, columns are derived from the rows returned, so zero rows means zero " +
         "columns." + rowPathClause + " The response's shape was: " + shape + truncatedClause + "." +
         hintClause + " This is not a connection or credentials problem — the request itself " +
         "succeeded. If this is unexpected, check the parameters (especially any date range or id " +
         "filter) against the endpoint's actual data.";
   }

   /**
    * The connector-neutral zero-rows clause, for a connector that records no response shape (MongoDB,
    * Elasticsearch, ...). Returned only when the runtime handed back a table ({@link
    * TabularQuery#isRunCompleted}) and nothing else speaks: a connector that fails leaves a user
    * message (and returns null), so a message here means failure, not an empty result. Deliberately
    * names no credentials: the wording must not read as a credentials complaint to a caller that
    * scans for one.
    */
   private static String completedEmptyClause(TabularQuery query, TabularProbeCause cause) {
      if(!query.isRunCompleted() || !cause.detail().isEmpty()) {
         return null;
      }

      return "completed without reporting an error but selected zero rows; columns are derived " +
         "from the rows returned, so zero rows means zero columns. If this is unexpected, check " +
         "the query (filter / match / pipeline / index) against the data.";
   }

   /**
    * Collapse to one line. {@code WorksheetTableService.rootMessage} truncates a thrown message at
    * the first '\n', so a refusal assembled from connector- or caller-controlled text is sanitized
    * once, on the fully-assembled message, at its single return point.
    */
   public static String singleLine(String message) {
      return message.replace('\n', ' ').replace('\r', ' ');
   }

   /**
    * The connector's own answer to "where are the rows", when it has one — reflective, so core never
    * imports a connector type such as {@code RestJsonQuery}. Returns null for any connector with no
    * such method (ServerFile, a generic non-JSON connector, ...), which the caller must treat as
    * "omit the clause," never as "assume '$'."
    */
   private static String effectiveRowPath(TabularQuery query) {
      try {
         Object path = query.getClass().getMethod("getValidJsonPath").invoke(query);
         return path instanceof String s && !s.isBlank() ? s : null;
      }
      catch(Exception ex) {
         return null;
      }
   }

   /**
    * A best-effort clause about the response's one top-level array, used only when the response's
    * shape names exactly one such array — an object with two or more array-valued fields, or none,
    * is ambiguous and returns "" rather than guess: a wrong guess is worse than no hint. The
    * clause suggests a row path only when the array holds elements and is not already what
    * {@code rowPath} points to; pointing at the path the caller already uses tells them nothing.
    * An empty array is never suggested — no path selects rows from it — so the clause says the
    * endpoint returned no records. The result is empty or starts with a space.
    */
   private static String arrayLocationClause(Object shape, String rowPath) {
      String arrayPath;
      boolean empty;

      if(shape instanceof List<?> list) {
         arrayPath = "$";
         empty = list.isEmpty();
      }
      else if(shape instanceof Map<?, ?> map) {
         String onlyArrayField = null;
         boolean onlyArrayEmpty = false;

         for(Map.Entry<?, ?> entry : map.entrySet()) {
            if(entry.getValue() instanceof List<?> value) {
               if(onlyArrayField != null) {
                  return "";
               }

               onlyArrayField = String.valueOf(entry.getKey());
               onlyArrayEmpty = value.isEmpty();
            }
         }

         if(onlyArrayField == null) {
            return "";
         }

         arrayPath = "$." + onlyArrayField;
         empty = onlyArrayEmpty;
      }
      else {
         return "";
      }

      boolean onRowPath = rowPath != null && stripAllElements(rowPath).equals(arrayPath);

      if(empty) {
         return onRowPath
            ? " The array at '" + rowPath + "' is empty: the endpoint returned no records for " +
               "these parameters."
            : " The response's only array, at '" + arrayPath + "', is empty: the endpoint " +
               "returned no records for these parameters.";
      }

      return onRowPath ? "" : " The rows look like they belong at '" + arrayPath +
         ("$".equals(arrayPath) ? "" : "[*]") + "'.";
   }

   /**
    * {@code $.data} and {@code $.data[*]} select the same array; drop the one trailing {@code [*]}
    * so both spellings compare equal.
    */
   private static String stripAllElements(String path) {
      return path.endsWith("[*]") ? path.substring(0, path.length() - 3) : path;
   }

   private final int timedOutSeconds;
   private final String connectorMessage;
}
