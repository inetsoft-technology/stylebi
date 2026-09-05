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
package inetsoft.uql.jdbc.util;

import inetsoft.uql.ConditionList;
import inetsoft.uql.HierarchyItem;
import inetsoft.uql.jdbc.*;
import inetsoft.util.Queue;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Confirmed-red repro for Redmine #76449 WBS-003's condition-tree scope defect, ported from
 * stylebi-wiz's docs/teams/2026-09-05-bug-wbs003-condition-tree-redesign/03-repro.md (P3,
 * verifier-wbs003). This is the StyleBI-side half of the confirmed-red demonstration; the
 * plugin-side half (the same node sequence's level assignment is accepted silently, with no
 * error, by set_conditions/set_post_conditions's own validation) is
 * plugin/composer/test/tools/worksheetTools.test.ts's "silently accepts the exact broken
 * trigger..." case in the stylebi-wiz repo.
 *
 * <p>Both fixed node sequences below are byte-for-byte the array
 * investigator-plugin-boundary's falsifiable claim describes
 * (docs/teams/2026-09-05-bug-wbs003-condition-tree-redesign/01-hypothesis-plugin-boundary.md):
 * "(A1 AND A2) OR (B1 AND B2), ANDed with a sibling C", with the OR junction given the SAME
 * level (0) as the outer AND and C, instead of its own distinct level -- the exact trigger
 * set_conditions's own (corrected) worked example now names in prose but does not, and cannot,
 * enforce.
 *
 * <p>No product code is changed by this file -- it is a regression/characterization fixture
 * confirming unmodified {@link ConditionListHandler} still exhibits the reported defect, per
 * this ticket's own convergence (docs/teams/2026-09-05-bug-wbs003-condition-tree-redesign/
 * 02-root-cause.md): "No StyleBI Java change is required or beneficial here."
 */
@Tag("core")
public class Wbs003CapabilityGapReproTest {

   private ConditionListHandler handler;

   @org.junit.jupiter.api.BeforeEach
   void setUp() {
      handler = new ConditionListHandler();
   }

   private XBinaryCondition cond(String name) {
      return new XBinaryCondition(
         new XExpression(name, XExpression.FIELD),
         new XExpression("'x'", XExpression.EXPRESSION), "=");
   }

   /**
    * Builds the broken node sequence fresh (new XSet/XSetItem/XFilterNodeItem instances every
    * call) so the two algorithm runs below never share mutable node objects -- the exact pitfall
    * investigator-wireformat hit and documented in 01-hypothesis-wireformat.md step 2 (walk()'s
    * calc() mutates XSetItem.level and XSet's child list in place).
    */
   private List<HierarchyItem> brokenSequence() {
      return List.of(
         new XFilterNodeItem(cond("A1"), 2),
         new XSetItem(new XSet(XSet.AND), 2),
         new XFilterNodeItem(cond("A2"), 2),
         new XSetItem(new XSet(XSet.OR), 0),
         new XFilterNodeItem(cond("B1"), 2),
         new XSetItem(new XSet(XSet.AND), 2),
         new XFilterNodeItem(cond("B2"), 2),
         new XSetItem(new XSet(XSet.AND), 0),
         new XFilterNodeItem(cond("C"), 0)
      );
   }

   @SuppressWarnings("unchecked")
   private XFilterNode runWalk(List<HierarchyItem> items) throws Exception {
      Method m = ConditionListHandler.class.getDeclaredMethod("walk", Queue.class);
      m.setAccessible(true);
      Queue<HierarchyItem> queue = new Queue<>();

      for(HierarchyItem item : items) {
         queue.enqueue(item);
      }

      return (XFilterNode) m.invoke(handler, queue);
   }

   private XFilterNode runParseClause(List<HierarchyItem> items) {
      ConditionList list = new ConditionList();

      for(HierarchyItem item : items) {
         list.append(item);
      }

      return handler.createXFilterNode(list);
   }

   /** Returns the field name of a leaf condition, for identifying which leaf ended up where. */
   private String leafName(XFilterNode node) {
      return (String) ((XBinaryCondition) node).getExpression1().getValue();
   }

   @Test
   void walk_brokenTrigger_bindsOrToOnlyBGroup_stealingScopeFromFinalAnd() throws Exception {
      XFilterNode result = runWalk(brokenSequence());

      assertNotNull(result);
      assertInstanceOf(XSet.class, result);
      XSet root = (XSet) result;
      // The caller's actual intent is an outer AND (( A1 AND A2 ) OR ( B1 AND B2 )) AND C -- i.e.
      // the ROOT should be AND, with C as a direct sibling operand. Confirmed-red: it is not.
      assertEquals(
         XSet.OR, root.getRelation(),
         "walk() resolved the broken level assignment to an OR root -- confirming the reported " +
         "defect (A_group OR (B_group AND C)) rather than the caller's intended " +
         "(A_group OR B_group) AND C. C has silently zero effect on rows satisfying A_group alone."
      );
      assertFalse(
         directChildIsLeafNamed(root, "C"),
         "C ended up nested under an AND with only the B-group, not as a sibling operand of the " +
         "whole tree -- exactly the silent-scope-loss this ticket reports."
      );
   }

   @Test
   void createXFilterNode_brokenTrigger_bindsOrToOnlyBGroup_stealingScopeFromFinalAnd() {
      XFilterNode result = runParseClause(brokenSequence());

      assertNotNull(result);
      assertInstanceOf(XSet.class, result);
      XSet root = (XSet) result;
      assertEquals(
         XSet.OR, root.getRelation(),
         "createXFilterNode(HierarchyList) (parseClause/splitAndParseClause) independently " +
         "resolves the same broken level assignment to an OR root too -- the defect is not " +
         "specific to walk()'s stack-shunting mechanism."
      );
      assertFalse(directChildIsLeafNamed(root, "C"));
   }

   private boolean directChildIsLeafNamed(XSet set, String name) {
      for(int i = 0; i < set.getChildCount(); i++) {
         XFilterNode child = (XFilterNode) set.getChild(i);

         if(child instanceof XBinaryCondition && leafName(child).equals(name)) {
            return true;
         }
      }

      return false;
   }
}
