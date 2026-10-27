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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #79438: a REST per-query timeout, like every other failure the connector recovers from
 * internally, left a tabular probe with no columns and its cause only in a thread-local user
 * message the wiz controller never read. {@link TabularProbeCause} is the one reader of that
 * message plus the structured timeout slot.
 *
 * <p>Touches only {@link CoreTool}'s thread-local message list, no Spring bean, so no context.</p>
 */
@Tag("core")
class TabularProbeCauseTest {
   @BeforeEach
   @AfterEach
   void drain() {
      CoreTool.clearUserMessage();
   }

   @Test
   void timeoutIsReportedFromTheStructuredSlotWithSecondsAndRemedy() {
      // The connector's own text for a cancelled preview names the query, which a probe never
      // sets: "...query timeout for: null." It must not leak into the caller's message.
      CoreTool.addUserMessage("Only partial preview data loaded due to query timeout for: null.");

      TabularProbeCause cause = TabularProbeCause.read(query(3), null);

      assertTrue(cause.isTimeout());
      String advice = cause.advice("Check the credentials.");
      assertTrue(advice.contains("timed out after 3 s"), advice);
      assertTrue(advice.contains("'timeout'"), advice);
      assertFalse(advice.contains("null"), advice);
      assertFalse(advice.contains("credentials"), advice);
      assertTrue(cause.detail().startsWith(" The request timed out after 3 s"));
   }

   @Test
   void otherRecoveredFailuresAreSurfacedAsTheConnectorMessage() {
      // connection refused / DNS / 401 / bad JSON all arrive this way
      CoreTool.addUserMessage("Error executing Rest query: Connection refused");

      TabularProbeCause cause = TabularProbeCause.read(query(0), null);

      assertFalse(cause.isTimeout());
      assertEquals(" The connector reported: Error executing Rest query: Connection refused",
                   cause.detail());
      assertEquals("Check it. The connector reported: Error executing Rest query: Connection " +
                   "refused", cause.advice("Check it."));
   }

   @Test
   void nothingKnownLeavesTheGenericAdviceUntouched() {
      TabularProbeCause cause = TabularProbeCause.read(query(0), null);

      assertEquals("", cause.detail());
      assertEquals("Check it.", cause.advice("Check it."));
   }

   @Test
   void aMessageAlreadyInTheCallersTextIsNotRepeated() {
      CoreTool.addUserMessage("Error executing Rest query: HTTP 401");

      TabularProbeCause cause = TabularProbeCause.read(query(0), "HTTP 401");

      assertEquals("", cause.detail());
   }

   @Test
   void beginProbeDropsAMessageLeftByAnEarlierProbe() {
      CoreTool.addUserMessage("Error executing Rest query: stale failure of the previous table");

      TabularProbeCause.beginProbe();

      assertEquals("", TabularProbeCause.read(query(0), null).detail());
   }

   @Test
   void readDrainsTheThreadLocalMessage() {
      CoreTool.addUserMessage("Error executing Rest query: boom");

      TabularProbeCause.read(query(0), null);

      assertNull(CoreTool.getUserMessage());
   }

   private static TabularQuery query(int timedOutSeconds) {
      TabularQuery query = mock(TabularQuery.class);
      when(query.getTimedOutSeconds()).thenReturn(timedOutSeconds);
      return query;
   }
}
