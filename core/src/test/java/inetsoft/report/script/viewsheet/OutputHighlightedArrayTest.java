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
package inetsoft.report.script.viewsheet;

import inetsoft.report.filter.*;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.internal.TextVSAssemblyInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #80976: highlighted[name] must report every matching highlight, also when
 * several highlights match (their merged result has no name).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class OutputHighlightedArrayTest {
   private static TextHighlight highlight(String name, int greaterThan, Color bg) {
      TextHighlight h = new TextHighlight();
      h.setName(name);
      h.setBackground(bg);
      AssetCondition c = new AssetCondition(XSchema.INTEGER);
      c.setOperation(XCondition.GREATER_THAN);
      c.addValue(greaterThan);
      ConditionList list = new ConditionList();
      list.append(new ConditionItem(new ColumnRef(new AttributeRef(null, "DIM")), c, 0));
      h.setConditionGroup(list);
      return h;
   }

   private static OutputHighlightedArray array(Object value, boolean withEmpty) {
      TextVSAssemblyInfo info = new TextVSAssemblyInfo();
      HighlightGroup group = new HighlightGroup();
      group.addHighlight("big", highlight("big", 5, Color.RED));
      group.addHighlight("huge", highlight("huge", 50, Color.BLUE));

      if(withEmpty) {
         TextHighlight empty = new TextHighlight();
         empty.setName("empty");
         empty.setConditionGroup(new ConditionList());
         group.addHighlight("empty", empty);
      }

      info.setHighlightGroup(group);
      info.setValue(value);
      return new OutputHighlightedArray(info);
   }

   @Test
   void noMatch() {
      OutputHighlightedArray a = array(1, false);
      assertEquals(Boolean.FALSE, a.getMember("big"));
      assertEquals(Boolean.FALSE, a.getMember("huge"));
   }

   @Test
   void singleMatch() {
      OutputHighlightedArray a = array(10, false);
      assertEquals(Boolean.TRUE, a.getMember("big"));
      assertEquals(Boolean.FALSE, a.getMember("huge"));
   }

   @Test
   void severalMatchesBothTrue() {
      OutputHighlightedArray a = array(100, false);
      assertEquals(Boolean.TRUE, a.getMember("big"));
      assertEquals(Boolean.TRUE, a.getMember("huge"));
   }

   @Test
   void emptyConditionHighlightIsNotMatched() {
      OutputHighlightedArray a = array(100, true);
      assertTrue(a.hasMember("empty"));
      assertEquals(Boolean.FALSE, a.getMember("empty"));
      assertEquals(Boolean.TRUE, a.getMember("big"));
      assertEquals(Boolean.TRUE, a.getMember("huge"));
   }

   @Test
   void nullValueDoesNotThrow() {
      OutputHighlightedArray a = array(null, false);
      assertDoesNotThrow(() -> a.getMember("big"));
      assertTrue(a.getMember("big") instanceof Boolean);
      assertTrue(a.getMember("huge") instanceof Boolean);

      // same condition semantics as findGroup for a null value
      HighlightGroup group = new HighlightGroup();
      group.addHighlight("big", highlight("big", 5, Color.RED));
      assertEquals(group.findGroup((Object) null) != null, group.isMatched("big", null));
   }

   @Test
   void unknownNameIsNotAMember() {
      OutputHighlightedArray a = array(100, false);
      assertFalse(a.hasMember("nosuch"));
      assertNull(a.getMember("nosuch"));
   }

   @Test
   void isMatchedUnknownNameOrLevel() {
      HighlightGroup group = new HighlightGroup();
      group.addHighlight("big", highlight("big", 5, Color.RED));
      assertTrue(group.isMatched("big", 10));
      assertFalse(group.isMatched("nosuch", 10));
      assertFalse(group.isMatched("otherLevel", "big", 10));
   }
}
