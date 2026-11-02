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
package inetsoft.uql.asset;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class AggregateInfoTest {
   /**
    * Regression test for a data-corruption bug: when a table a chart's own AggregateInfo was
    * built against gets renamed (e.g. WsMergeService merging two charts sharing the same
    * physical table renames one chart's own table out from under it), renameDepended only ever
    * renamed the group refs' "assembly" field (an unrelated named-group-asset feature) and never
    * touched the aggregate refs at all. Neither actually re-qualified the underlying
    * AttributeRef's entity, so on the next viewsheet reload the query engine re-derives the
    * column selection against the NEW table name, AggregateInfo#validate() finds no match for
    * the still-old-named group/aggregate refs, and silently drops them -- surfacing downstream
    * as anything from a missing aggregate to a null order-by field NPE.
    */
   @Test
   void renameDependedRequalifiesGroupAndAggregateEntities() {
      AttributeRef dateAttr = new AttributeRef("SO", "date_order");
      dateAttr.setDataType("timestamp");
      GroupRef group = new GroupRef(dateAttr);

      AttributeRef amountAttr = new AttributeRef("SO", "amount_total");
      amountAttr.setDataType("double");
      AggregateRef aggregate = new AggregateRef(amountAttr, AggregateFormula.SUM);

      AggregateInfo info = new AggregateInfo();
      info.addGroup(group);
      info.addAggregate(aggregate);

      info.renameDepended("SO", "sale_order_3");

      DataRef renamedGroupRef = group.getDataRef();
      assertEquals("sale_order_3", renamedGroupRef.getEntity());
      assertEquals("date_order", renamedGroupRef.getAttribute());
      assertEquals("timestamp", ((AttributeRef) renamedGroupRef).getDataType(),
         "renaming a group's table qualifier must preserve its data type");

      DataRef renamedAggregateRef = aggregate.getDataRef();
      assertEquals("sale_order_3", renamedAggregateRef.getEntity());
      assertEquals("amount_total", renamedAggregateRef.getAttribute());
      assertEquals("double", ((AttributeRef) renamedAggregateRef).getDataType(),
         "renaming an aggregate's table qualifier must preserve its data type");
   }

   /**
    * A group built over a date-range bucketed column (e.g. Quarter(SO.date_order)) wraps its
    * AttributeRef in a DateRangeRef rather than holding it directly -- renameDepended must
    * descend through that wrapper to reach and requalify the base entity, matching the same
    * DateRangeRef-aware unwrapping ColumnRef#renameColumn already does for chart columns.
    */
   @Test
   void renameDependedRequalifiesDateRangeWrappedGroupEntity() {
      AttributeRef dateAttr = new AttributeRef("SO", "date_order");
      DateRangeRef quarterRef = new DateRangeRef("QuarterDate_order", dateAttr, DateRangeRef.QUARTER_INTERVAL);
      GroupRef group = new GroupRef(quarterRef);

      AggregateInfo info = new AggregateInfo();
      info.addGroup(group);

      info.renameDepended("SO", "sale_order_3");

      DataRef innerRef = ((DateRangeRef) group.getDataRef()).getDataRef();
      assertEquals("sale_order_3", innerRef.getEntity());
      assertEquals("date_order", innerRef.getAttribute());
   }

   /**
    * A group/aggregate qualified against some OTHER table must be left untouched when an
    * unrelated table is renamed.
    */
   @Test
   void renameDependedLeavesNonMatchingEntityUntouched() {
      AttributeRef attr = new AttributeRef("OTHER", "id");
      AggregateRef aggregate = new AggregateRef(attr, AggregateFormula.COUNT_ALL);

      AggregateInfo info = new AggregateInfo();
      info.addAggregate(aggregate);

      info.renameDepended("SO", "sale_order_3");

      assertEquals("OTHER", aggregate.getDataRef().getEntity());
   }

   /**
    * Regression for the L2 worksheet-filters parity audit's Group 2 finding #10: a column
    * cannot be both a group-by key and an aggregate measure at once (this is the rule
    * {@code AggregatePane.verify()} enforces client-side in the Composer's own "Group and
    * Aggregate" dialog, with no Java-side equivalent on either the native dialog's backend
    * path or the {@code inetsoft.web.wiz.worksheet} agent path -- both explicitly passed
    * {@code delSame=false} to {@link AggregateInfo#addAggregate}, which bypassed the model's
    * own {@code delSame=true} branch where this cross-list check used to live). Moved the
    * check out of the {@code delSame} conditional so it runs unconditionally, since it is
    * independent of {@code delSame}'s separate same-list dedup/replace-in-place role.
    */
   @Test
   void addAggregateRejectsColumnAlreadyUsedAsGroup() {
      AttributeRef attr = new AttributeRef("SO", "customer_id");
      GroupRef group = new GroupRef(attr);
      AggregateRef aggregate = new AggregateRef(new AttributeRef("SO", "customer_id"),
                                                AggregateFormula.COUNT_ALL);

      AggregateInfo info = new AggregateInfo();
      info.addGroup(group);

      assertFalse(info.addAggregate(aggregate, false),
         "adding an aggregate on the same column as an existing group must be rejected, " +
         "even with delSame=false");
      assertEquals(0, info.getAggregateCount());
   }

   /** Symmetric case: adding a group on a column already used as an aggregate. */
   @Test
   void addGroupRejectsColumnAlreadyUsedAsAggregate() {
      AttributeRef attr = new AttributeRef("SO", "customer_id");
      AggregateRef aggregate = new AggregateRef(attr, AggregateFormula.COUNT_ALL);
      GroupRef group = new GroupRef(new AttributeRef("SO", "customer_id"));

      AggregateInfo info = new AggregateInfo();
      info.addAggregate(aggregate);

      assertFalse(info.addGroup(group, false),
         "adding a group on the same column as an existing aggregate must be rejected, " +
         "even with delSame=false");
      assertEquals(0, info.getGroupCount());
   }

   /**
    * The cross-list guard must not regress the legitimate same-list case this same
    * {@code delSame=false} call shape exists to support: two different aggregate formulas
    * on ONE column (e.g. {@code Sum(Amount)} and {@code Average(Amount)}), which is how
    * {@code WorksheetMutationSupport#applyAggregateInfo}'s "second aggregate on the same
    * column" path (and the native dialog's identical pattern) adds a secondary aggregate.
    */
   @Test
   void addAggregateStillAllowsTwoFormulasOnSameColumn() {
      AttributeRef attr1 = new AttributeRef("SO", "amount");
      AttributeRef attr2 = new AttributeRef("SO", "amount");
      AggregateRef sum = new AggregateRef(attr1, AggregateFormula.SUM);
      AggregateRef avg = new AggregateRef(attr2, AggregateFormula.AVG);

      AggregateInfo info = new AggregateInfo();

      assertTrue(info.addAggregate(sum, false));
      assertTrue(info.addAggregate(avg, false));
      assertEquals(2, info.getAggregateCount());
   }
}
