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
      String text = msg == null || msg.getMessage() == null ? "" : msg.getMessage().trim();

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

   private final int timedOutSeconds;
   private final String connectorMessage;
}
