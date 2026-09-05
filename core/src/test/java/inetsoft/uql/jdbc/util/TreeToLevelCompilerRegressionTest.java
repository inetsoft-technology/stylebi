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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression insurance for the P4 builder of Redmine #76449 WBS-003's condition-tree redesign
 * (docs/teams/2026-09-05-bug-wbs003-condition-tree-redesign in stylebi-wiz), ported from
 * investigator-wireformat's throwaway {@code TreeToLevelCompilerPrototypeTest.java} prototype
 * (01-hypothesis-wireformat.md steps 1-2) as a permanent fixture.
 *
 * <p>These 12 cases are NOT the bug -- every one of them already passes against unmodified
 * {@link ConditionListHandler} today (that's the point: this file proves the derivation rule
 * "each nested group's own junction level = enclosing group's level + 1, regardless of whether
 * relation type matches its parent's" produces a flat (condition, junction, level) array that
 * both of StyleBI's independent condition-tree algorithms already interpret correctly). Once a
 * real compiler function exists in stylebi-wiz's {@code worksheetTools.ts} (P4), its own
 * plugin-side unit tests should assert its output against the same 12 tree shapes' expected
 * flat arrays; this file is the JVM-side half confirming those arrays, once produced, land on
 * already-correct ground.
 *
 * <p>{@code compile(Node, int)} below is test-fixture code (a tree-to-level compiler local to
 * this test), not a port of any production compiler -- none exists yet in the plugin; writing
 * one is P4's job.
 */
@Tag("core")
public class TreeToLevelCompilerRegressionTest {

   private ConditionListHandler handler;

   @org.junit.jupiter.api.BeforeEach
   void setUp() {
      handler = new ConditionListHandler();
   }

   // -----------------------------------------------------------------------
   // Tree DSL + compiler under test (fixture code, mirrors 01-hypothesis-wireformat.md step 1-2)
   // -----------------------------------------------------------------------

   private interface Node {}

   private record Leaf(String name) implements Node {}

   private record Group(String relation, List<Node> children) implements Node {}

   private static Node leaf(String name) {
      return new Leaf(name);
   }

   private static Node group(String relation, Node... children) {
      return new Group(relation, List.of(children));
   }

   private XBinaryCondition makeCond(String name) {
      return new XBinaryCondition(
         new XExpression(name, XExpression.FIELD),
         new XExpression("'x'", XExpression.EXPRESSION), "=");
   }

   /**
    * Compiles a tree into a fresh (condition, junction, level) node list. Called once per
    * algorithm run (never shared) so the two algorithm invocations below never mutate each
    * other's node objects -- the exact pitfall investigator-wireformat's own harness hit and
    * documented (walk()'s calc() mutates XSetItem.level and XSet's child list in place).
    */
   private List<HierarchyItem> compile(Node root) {
      List<HierarchyItem> out = new ArrayList<>();
      compileInto(root, 0, out);
      return out;
   }

   private void compileInto(Node node, int level, List<HierarchyItem> out) {
      if(node instanceof Leaf leaf) {
         out.add(new XFilterNodeItem(makeCond(leaf.name()), level));
         return;
      }

      Group group = (Group) node;

      for(int i = 0; i < group.children().size(); i++) {
         Node child = group.children().get(i);

         if(child instanceof Group) {
            // Every nested group's own junctions/leaf-children go one level deeper than the
            // enclosing group's, regardless of whether the relation matches.
            compileInto(child, level + 1, out);
         }
         else {
            out.add(new XFilterNodeItem(makeCond(((Leaf) child).name()), level));
         }

         if(i < group.children().size() - 1) {
            out.add(new XSetItem(new XSet(group.relation()), level));
         }
      }
   }

   // -----------------------------------------------------------------------
   // Canonical form for comparing a resolved XFilterNode tree against the caller's intent,
   // independent of sibling order and of AND/OR's own associativity (a same-relation group
   // nested inside an identical-relation group is not a distinguishable shape).
   // -----------------------------------------------------------------------

   private sealed interface Canon {}

   private record CLeaf(String name) implements Canon {}

   private record CRel(String relation, List<Canon> children) implements Canon {}

   private static Canon cleaf(String name) {
      return new CLeaf(name);
   }

   private static Canon crel(String relation, Canon... children) {
      List<Canon> flat = new ArrayList<>();

      for(Canon child : children) {
         if(child instanceof CRel r && r.relation().equals(relation)) {
            flat.addAll(r.children());
         }
         else {
            flat.add(child);
         }
      }

      flat.sort(Comparator.comparing(Object::toString));
      return new CRel(relation, flat);
   }

   private Canon canon(XFilterNode node) {
      if(node instanceof XBinaryCondition c) {
         return cleaf((String) c.getExpression1().getValue());
      }

      XSet set = (XSet) node;
      List<Canon> children = new ArrayList<>();

      for(int i = 0; i < set.getChildCount(); i++) {
         children.add(canon((XFilterNode) set.getChild(i)));
      }

      return crel(set.getRelation(), children.toArray(new Canon[0]));
   }

   // -----------------------------------------------------------------------
   // Algorithm runners
   // -----------------------------------------------------------------------

   private XFilterNode runWalk(Node tree) throws Exception {
      Method m = ConditionListHandler.class.getDeclaredMethod("walk", Queue.class);
      m.setAccessible(true);
      Queue<HierarchyItem> queue = new Queue<>();

      for(HierarchyItem item : compile(tree)) {
         queue.enqueue(item);
      }

      return (XFilterNode) m.invoke(handler, queue);
   }

   private XFilterNode runParseClause(Node tree) {
      ConditionList list = new ConditionList();

      for(HierarchyItem item : compile(tree)) {
         list.append(item);
      }

      return handler.createXFilterNode(list);
   }

   private void assertBothAlgorithmsMatch(Node tree, Canon expected) throws Exception {
      assertEquals(expected, canon(runWalk(tree)), "walk() mismatch");
      assertEquals(expected, canon(runParseClause(tree)), "createXFilterNode(HierarchyList) mismatch");
   }

   // -----------------------------------------------------------------------
   // The 12 cases (docs/teams/2026-09-05-bug-wbs003-condition-tree-redesign/
   // 01-hypothesis-wireformat.md, "Final result" table)
   // -----------------------------------------------------------------------

   @Test
   void singleLeaf_bareCondition() throws Exception {
      assertBothAlgorithmsMatch(leaf("A"), cleaf("A"));
   }

   @Test
   void flatAndChain_threeLeaves() throws Exception {
      assertBothAlgorithmsMatch(
         group(XSet.AND, leaf("A"), leaf("B"), leaf("C")),
         crel(XSet.AND, cleaf("A"), cleaf("B"), cleaf("C"))
      );
   }

   @Test
   void flatOrChain_threeLeaves() throws Exception {
      assertBothAlgorithmsMatch(
         group(XSet.OR, leaf("A"), leaf("B"), leaf("C")),
         crel(XSet.OR, cleaf("A"), cleaf("B"), cleaf("C"))
      );
   }

   @Test
   void canonicalRepro_andOuter_orInner_withSibling() throws Exception {
      // (A1 AND A2) OR (B1 AND B2), ANDed with sibling C -- WBS-003's own literal repro shape.
      Node tree = group(XSet.AND,
         group(XSet.OR,
            group(XSet.AND, leaf("A1"), leaf("A2")),
            group(XSet.AND, leaf("B1"), leaf("B2"))
         ),
         leaf("C")
      );
      Canon expected = crel(XSet.AND,
         crel(XSet.OR,
            crel(XSet.AND, cleaf("A1"), cleaf("A2")),
            crel(XSet.AND, cleaf("B1"), cleaf("B2"))
         ),
         cleaf("C")
      );
      assertBothAlgorithmsMatch(tree, expected);
   }

   @Test
   void mirrorImage_orOuter_andInner_withSibling() throws Exception {
      // (A1 OR A2) AND (B1 OR B2), OR'd with sibling C -- the direction-inverted mirror family.
      Node tree = group(XSet.OR,
         group(XSet.AND,
            group(XSet.OR, leaf("A1"), leaf("A2")),
            group(XSet.OR, leaf("B1"), leaf("B2"))
         ),
         leaf("C")
      );
      Canon expected = crel(XSet.OR,
         crel(XSet.AND,
            crel(XSet.OR, cleaf("A1"), cleaf("A2")),
            crel(XSet.OR, cleaf("B1"), cleaf("B2"))
         ),
         cleaf("C")
      );
      assertBothAlgorithmsMatch(tree, expected);
   }

   @Test
   void threeAndGroups_orChained_wrappedByAndWithSibling() throws Exception {
      // N=3 AND-groups OR-chained, wrapped by an outer AND with sibling D.
      Node tree = group(XSet.AND,
         group(XSet.OR,
            group(XSet.AND, leaf("A1"), leaf("A2")),
            group(XSet.AND, leaf("B1"), leaf("B2")),
            group(XSet.AND, leaf("C1"), leaf("C2"))
         ),
         leaf("D")
      );
      Canon expected = crel(XSet.AND,
         crel(XSet.OR,
            crel(XSet.AND, cleaf("A1"), cleaf("A2")),
            crel(XSet.AND, cleaf("B1"), cleaf("B2")),
            crel(XSet.AND, cleaf("C1"), cleaf("C2"))
         ),
         cleaf("D")
      );
      assertBothAlgorithmsMatch(tree, expected);
   }

   @Test
   void twoOrGroups_andedTogether_noOuterWrapper() throws Exception {
      // Two OR-groups ANDed together, no further outer junction.
      Node tree = group(XSet.AND,
         group(XSet.OR, leaf("A1"), leaf("A2")),
         group(XSet.OR, leaf("B1"), leaf("B2"))
      );
      Canon expected = crel(XSet.AND,
         crel(XSet.OR, cleaf("A1"), cleaf("A2")),
         crel(XSet.OR, cleaf("B1"), cleaf("B2"))
      );
      assertBothAlgorithmsMatch(tree, expected);
   }

   @Test
   void threeLevelNesting_mixedRelations() throws Exception {
      // ((A-grp OR B-grp) AND C) OR D
      Node tree = group(XSet.OR,
         group(XSet.AND,
            group(XSet.OR, leaf("A"), leaf("B")),
            leaf("C")
         ),
         leaf("D")
      );
      Canon expected = crel(XSet.OR,
         crel(XSet.AND,
            crel(XSet.OR, cleaf("A"), cleaf("B")),
            cleaf("C")
         ),
         cleaf("D")
      );
      assertBothAlgorithmsMatch(tree, expected);
   }

   @Test
   void asymmetricBranchDepth_sameRelationNestedInsideItself() throws Exception {
      // One AND branch has an extra same-relation nesting level its OR-sibling doesn't:
      // (A AND (A2 AND A3)) OR B, ANDed with sibling C.
      Node tree = group(XSet.AND,
         group(XSet.OR,
            group(XSet.AND, leaf("A1"), group(XSet.AND, leaf("A2"), leaf("A3"))),
            leaf("B")
         ),
         leaf("C")
      );
      Canon expected = crel(XSet.AND,
         crel(XSet.OR,
            crel(XSet.AND, cleaf("A1"), cleaf("A2"), cleaf("A3")),
            cleaf("B")
         ),
         cleaf("C")
      );
      assertBothAlgorithmsMatch(tree, expected);
   }

   @Test
   void fourLevelAlternatingNesting() throws Exception {
      // 4 levels of strictly alternating AND/OR/AND/OR:
      // ((( A OR B ) AND C) OR D) AND E
      Node tree = group(XSet.AND,
         group(XSet.OR,
            group(XSet.AND,
               group(XSet.OR, leaf("A"), leaf("B")),
               leaf("C")
            ),
            leaf("D")
         ),
         leaf("E")
      );
      Canon expected = crel(XSet.AND,
         crel(XSet.OR,
            crel(XSet.AND,
               crel(XSet.OR, cleaf("A"), cleaf("B")),
               cleaf("C")
            ),
            cleaf("D")
         ),
         cleaf("E")
      );
      assertBothAlgorithmsMatch(tree, expected);
   }

   @Test
   void mixedLeafAndGroupSiblings() throws Exception {
      // One AND with 3 operands: an OR-group plus two plain leaves.
      Node tree = group(XSet.AND,
         group(XSet.OR, leaf("A1"), leaf("A2")),
         leaf("B"),
         leaf("C")
      );
      Canon expected = crel(XSet.AND,
         crel(XSet.OR, cleaf("A1"), cleaf("A2")),
         cleaf("B"),
         cleaf("C")
      );
      assertBothAlgorithmsMatch(tree, expected);
   }

   @Test
   void doubleRelationSwitchOneBranch() throws Exception {
      // One OR branch relation-switches twice (AND containing an OR containing a leaf sibling)
      // down one path while the other branch stays shallow: (( A AND ( B OR C )) OR D) AND E
      Node tree = group(XSet.AND,
         group(XSet.OR,
            group(XSet.AND, leaf("A"), group(XSet.OR, leaf("B"), leaf("C"))),
            leaf("D")
         ),
         leaf("E")
      );
      Canon expected = crel(XSet.AND,
         crel(XSet.OR,
            crel(XSet.AND, cleaf("A"), crel(XSet.OR, cleaf("B"), cleaf("C"))),
            cleaf("D")
         ),
         cleaf("E")
      );
      assertBothAlgorithmsMatch(tree, expected);
   }
}
