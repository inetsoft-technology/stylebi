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
package inetsoft.uql.erm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.awt.Rectangle;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bug #82273: after an Auto Alias is applied, the original table (and every table reached by the
 * alias' outgoing links) is gone from the applied partition, and
 * {@link XPartition#getAutoAliasReplacements} names the aliases that replaced it.
 */
@Tag("core")
class XPartitionAutoAliasReplacementsTest {
   private XPartition partition;
   private XPartition applied;

   /**
    * film joins language (the auto alias key) and film2 joins it too; language - store,
    * customer - store, cust2 (an alias of customer) - store, city - film. The first incoming join
    * keeps outgoing links, the second does not.
    */
   @BeforeEach
   void setUp() {
      partition = new XPartition();
      partition.setName("View");

      for(String t : new String[] { "film", "film2", "language", "store", "customer", "cust2",
                                    "city" })
      {
         partition.addTable(t, new Rectangle(0, 0, 10, 10));
      }

      partition.setAliasTable("cust2", "customer");
      partition.addRelationship(new XRelationship("film", "l", "language", "l", "="));
      partition.addRelationship(new XRelationship("film2", "l", "language", "l", "="));
      partition.addRelationship(new XRelationship("language", "s", "store", "s", "="));
      partition.addRelationship(new XRelationship("customer", "s", "store", "s", "="));
      partition.addRelationship(new XRelationship("cust2", "s", "store", "s", "="));
      partition.addRelationship(new XRelationship("city", "f", "film", "f", "="));

      AutoAlias auto = new AutoAlias();
      auto.addIncomingJoin(join("film", "test1", true));
      auto.addIncomingJoin(join("film2", "test2", false));
      partition.setAutoAlias("language", auto);
      applied = partition.applyAutoAliases();
   }

   private static AutoAlias.IncomingJoin join(String source, String alias, boolean keep) {
      AutoAlias.IncomingJoin join = new AutoAlias.IncomingJoin();
      join.setSourceTable(source);
      join.setAlias(alias);
      join.setKeepOutgoing(keep);
      return join;
   }

   @Test
   void premiseTheOriginalsAreGoneFromTheAppliedPartition() {
      for(String t : new String[] { "language", "store", "customer", "cust2" }) {
         assertTrue(partition.getPartitionTable(t) != null, t);
         assertEquals(null, applied.getPartitionTable(t), t);
      }
   }

   @Test
   void keyTableIsReplacedByEveryIncomingJoinAlias() {
      // test2 does not keep outgoing links, so its source table is null; it is found through the
      // auto alias definition
      assertEquals(List.of("test1", "test2"),
                   partition.getAutoAliasReplacements(applied, "language"));
   }

   @Test
   void outgoingPhysicalTableIsReplacedByTheAliasOfTheBranchThatKeptIt() {
      List<String> store = partition.getAutoAliasReplacements(applied, "store");
      List<String> customer = partition.getAutoAliasReplacements(applied, "customer");

      assertEquals(1, store.size(), store.toString());
      assertTrue(store.get(0).startsWith("test1"), store.toString());
      assertEquals(1, customer.size(), customer.toString());
      assertTrue(customer.get(0).startsWith("test1"), customer.toString());
   }

   @Test
   void outgoingPlainAliasTableIsReplacedByItsOwnAliasOnly() {
      List<String> result = partition.getAutoAliasReplacements(applied, "cust2");

      assertEquals(1, result.size(), result.toString());
      assertTrue(result.get(0).startsWith("test1") && result.get(0).endsWith("cust2"),
                 result.toString());
   }

   @Test
   void tablesStillInTheAppliedPartitionAreNotReplaced() {
      assertEquals(List.of(), partition.getAutoAliasReplacements(applied, "film"));
      assertEquals(List.of(), partition.getAutoAliasReplacements(applied, "film2"));
      assertEquals(List.of(), partition.getAutoAliasReplacements(applied, "city"));
   }

   @Test
   void tableNotInThePartitionHasNoReplacements() {
      assertEquals(List.of(), partition.getAutoAliasReplacements(applied, "nosuch"));
      assertEquals(List.of(), partition.getAutoAliasReplacements(applied, null));
   }

   @Test
   void partitionWithoutAutoAliasHasNoReplacements() {
      XPartition plain = new XPartition();
      plain.setName("Plain");
      plain.addTable("a", new Rectangle(0, 0, 10, 10));
      XPartition plainApplied = plain.applyAutoAliases();

      assertEquals(List.of(), plain.getAutoAliasReplacements(plainApplied, "a"));
   }
}
