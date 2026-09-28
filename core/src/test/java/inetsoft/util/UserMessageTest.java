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
package inetsoft.util;

import inetsoft.uql.asset.ConfirmException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
class UserMessageTest {
   // bug #77188, a message without text, e.g. from an exception without one, is merged
   // instead of throwing
   @Test
   void mergeIntoMessageWithoutText() {
      UserMessage empty = new UserMessage(null, ConfirmException.INFO);
      UserMessage merged = empty.merge(new UserMessage("warning", ConfirmException.WARNING));

      assertEquals("warning", merged.getMessage());
      assertEquals(ConfirmException.WARNING, merged.getLevel());
   }

   @Test
   void mergeMessageWithoutText() {
      UserMessage message = new UserMessage("warning", ConfirmException.WARNING);

      assertSame(message, message.merge(new UserMessage(null, ConfirmException.INFO)));
      assertSame(message, message.merge(null));
   }

   @Test
   void mergeTexts() {
      UserMessage merged = new UserMessage("a", ConfirmException.INFO)
         .merge(new UserMessage("b", ConfirmException.WARNING));

      assertEquals("b\na", merged.getMessage());
      assertEquals(ConfirmException.WARNING, merged.getLevel());
   }

   // bug #77188, what the SummaryFilter worker collects after a script exception without
   // a message
   @Test
   void getUserMessageWithLeadingMessageWithoutText() {
      Tool.clearUserMessage();

      try {
         Tool.addUserMessage((String) null);
         Tool.addUserMessage("warning");

         UserMessage message = Tool.getUserMessage();

         assertNotNull(message);
         assertEquals("warning", message.getMessage());
      }
      finally {
         Tool.clearUserMessage();
      }
   }
}
