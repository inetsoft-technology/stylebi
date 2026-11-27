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
package inetsoft.uql.xmla;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #82471: MemberObject.getDateText() is the caption, else the last segment of the bracketed
 * full caption, else of the unique name, and never throws.
 */
@Tag("core")
class MemberObjectDateTextTest {
   @Test
   void captionWins() throws Exception {
      assertEquals("01/15/26",
                   member("[a]", "01/15/26", "[26].[01/26].[01/15/26]").getDateText());
   }

   @Test
   void lastSegmentOfPath() throws Exception {
      assertEquals("01/15/26",
                   member("[a]", null, "[26].[01/26].[01/15/26]").getDateText());
      assertEquals("1997", member("[a]", null, "[1997]").getDateText());
      assertEquals("1997", member("[a]", null, "[Time].[1997]").getDateText());
   }

   @Test
   void escapedBracketsRestored() throws Exception {
      assertEquals("Widget [Pro]",
                   member("[a]", null, "[Tools].[Widget [Pro]]]").getDateText());
   }

   @Test
   void plainAndMalformedTextUnchanged() throws Exception {
      assertEquals("1997", member("[a]", null, "1997").getDateText());
      assertEquals("[1997", member("[a]", null, "[1997").getDateText());
      assertEquals("[a]x", member("[a]", null, "[a]x").getDateText());
   }

   @Test
   void fallsBackToUniqueNameAndNull() {
      assertEquals("1997", new MemberObject("[Time].[1997]").getDateText());
      assertNull(new MemberObject().getDateText());
   }

   private static MemberObject member(String uName, String caption, String fullCaption)
      throws Exception
   {
      MemberObject m = new MemberObject(uName, fullCaption);
      Field f = MemberObject.class.getDeclaredField("caption");
      f.setAccessible(true);
      f.set(m, caption);
      return m;
   }
}
