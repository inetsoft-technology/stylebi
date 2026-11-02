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
package inetsoft.sree.schedule;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #79654: an action with no configured sender must not carry a built-in
 * stylereport@inetsoft.com address, otherwise mail.from.address is ignored.
 */
@Tag("core")
class EmailInfoDefaultFromTest {
   @Test
   void newActionHasNoBuiltInFromAddress() {
      assertNull(new EmailInfo().getFrom());
      assertNull(new ViewsheetAction().getFrom());
   }

   @Test
   void clearingFromKeepsItUnset() {
      ViewsheetAction action = new ViewsheetAction();
      action.setFrom(null);
      assertNull(action.getFrom());
   }

   @Test
   void configuredFromIsKept() {
      ViewsheetAction action = new ViewsheetAction();
      action.setFrom("me@example.com");
      assertEquals("me@example.com", action.getFrom());
   }
}
