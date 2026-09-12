/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.graph.element;

import inetsoft.graph.data.DefaultDataSet;
import inetsoft.util.CoreTool;
import inetsoft.util.UserMessage;
import inetsoft.web.viewsheet.command.MessageCommand.Type;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that GraphElement.getEndRow() reports row truncation to the end user.
 *
 * <p>The report used to be suppressed above a fixed limit of 100000, which meant a chart
 * using the shipped graph.point.maxcount (10000000) dropped rows silently. These pin the
 * message to both sides of that former threshold.
 */
@Tag("core")
class GraphElementTruncationMessageTest {
   @BeforeEach
   @AfterEach
   void clearUserMessages() {
      CoreTool.clearUserMessage();
   }

   /**
    * 100001 is one above the threshold that used to demote this message to LOG.debug; 500 is
    * well below it and was always reported. Both must now reach the user.
    */
   @ParameterizedTest(name = "a limit of {0} reports the truncation to the user")
   @ValueSource(ints = { 500, 100001 })
   void getEndRowReportsTruncationRegardlessOfTheLimit(int maxCount) {
      DefaultDataSet data = dataSet(maxCount + 2);
      PointElement elem = new PointElement("Cat", "m1");
      elem.setHint(GraphElement.HINT_MAX_COUNT, maxCount);

      int endRow = GraphElement.getEndRow(data, 0, -1, elem);

      assertEquals(maxCount, endRow, "the data must still be truncated to the limit");

      UserMessage message = CoreTool.getUserMessage();
      assertNotNull(message,
                    "truncating rows must be reported to the user: without it a reader " +
                    "cannot tell whether they are seeing all their data");
      // the catalog formats the limit with grouping separators (100,001), so compare digits
      assertTrue(message.getMessage().replace(",", "").contains(String.valueOf(maxCount)),
                 "the message must name the limit that was applied, but was: " +
                 message.getMessage());
   }

   @Test
   void getEndRowIsSilentWhenNothingIsTruncated() {
      DefaultDataSet data = dataSet(10);
      PointElement elem = new PointElement("Cat", "m1");
      elem.setHint(GraphElement.HINT_MAX_COUNT, 100);

      assertEquals(10, GraphElement.getEndRow(data, 0, -1, elem),
                   "a limit above the row count must not truncate");
      assertNull(CoreTool.getUserMessage(),
                 "a chart that fits under its limit must not warn about truncation");
   }

   @Test
   void getEndRowReportsTruncationOnceForRepeatedCalls() {
      // getEndRow is called repeatedly while geometry is built, so the message would be
      // noisy if CoreTool.addUserMessage did not de-duplicate. asserted through
      // getUserMessages() rather than getUserMessage(): the latter reduces with
      // UserMessage.merge(), which drops text already contained in the accumulation, so it
      // would collapse duplicates on its own and the assertion could never fail. The
      // getUserMessages() merge concatenates unconditionally for messages with no assembly
      // name, so one stored message means one line.
      DefaultDataSet data = dataSet(600);
      PointElement elem = new PointElement("Cat", "m1");
      elem.setHint(GraphElement.HINT_MAX_COUNT, 500);

      for(int i = 0; i < 4; i++) {
         GraphElement.getEndRow(data, 0, -1, elem);
      }

      List<UserMessage> messages = CoreTool.getUserMessages(Type.INFO);

      assertEquals(1, messages.size(), "the truncation must be stored as a single message");
      assertEquals(0, countLineBreaks(messages.get(0).getMessage()),
                   "four truncating calls must leave one message, not one per call, but was: " +
                   messages.get(0).getMessage());
   }

   private static int countLineBreaks(String message) {
      return message.split("\n", -1).length - 1;
   }

   private static DefaultDataSet dataSet(int rows) {
      Object[][] values = new Object[rows + 1][2];
      values[0] = new Object[]{ "Cat", "m1" };

      for(int i = 0; i < rows; i++) {
         values[i + 1] = new Object[]{ "c" + i, (double) i };
      }

      return new DefaultDataSet(values);
   }
}
