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

import inetsoft.uql.ColumnSelection;
import inetsoft.uql.Condition;
import inetsoft.uql.ConditionItem;
import inetsoft.uql.ConditionList;
import inetsoft.uql.XCondition;
import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.asset.AggregateRef;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Resolving a highlight condition's field against the columns the view actually renders.
 *
 * A highlight condition is evaluated POST-aggregation, against headers named by each field's FULL name
 * ("Sum(SALES)"). A caller naming the base column plus an aggregate — the shape a HAVING filter takes —
 * produces an {@link AggregateRef} whose {@code getName()} is the BASE column, and no such header exists
 * at render time. Left unresolved the condition matches nothing: the highlight is stored on the right
 * cell, the request answers 200 with no error, and not one cell is colored. That is the failure this
 * rebind exists to remove, and the reason it must FAIL LOUD when it cannot rebind rather than pass a
 * condition through that can never resolve.
 *
 * The unit under test is the resolution itself. applyHighlight's crosstab branch reaches it through a
 * live {@code VSTableLens} and the static {@code TableHighlightAttr.getAvailableFields}, which is why
 * {@link WizVsServiceApplyHighlightCopyTest} drives that method with a TEXT assembly instead; the
 * end-to-end lens path stays out of scope here.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class WizVsServiceHighlightRebindTest {
   private final WizVsService service = new WizVsService(null, null, null, null, null);

   private static ColumnSelection viewColumns(String... names) {
      ColumnSelection cols = new ColumnSelection();

      for(String name : names) {
         ColumnRef col = new ColumnRef(new AttributeRef(null, name));
         col.setDataType(XSchema.INTEGER);
         cols.addAttribute(col);
      }

      return cols;
   }

   /** A one-item condition list whose field is an aggregate over {@code base} — the HAVING-ish shape. */
   private static ConditionList aggregateCondition(String base, AggregateFormula formula) {
      return aggregateCondition(new AggregateRef(new ColumnRef(new AttributeRef(null, base)), null, formula));
   }

   private static ConditionList aggregateCondition(AggregateRef ref) {
      Condition condition = new Condition();
      condition.setOperation(XCondition.GREATER_THAN);
      condition.addValue(20);

      ConditionList conds = new ConditionList();
      conds.append(new ConditionItem(ref, condition, 0));
      return conds;
   }

   /** A one-item condition list on a plain column, the shape a dimension condition takes. */
   private static ConditionList plainCondition(String field) {
      Condition condition = new Condition();
      condition.setOperation(XCondition.EQUAL_TO);
      condition.addValue("A");

      ConditionList conds = new ConditionList();
      conds.append(new ConditionItem(new AttributeRef(null, field), condition, 0));
      return conds;
   }

   private static String fieldOf(ConditionList conds) {
      DataRef attr = conds.getConditionItem(0).getAttribute();
      return attr == null ? null : attr.getName();
   }

   // ── rebinding ──────────────────────────────────────────────────────────────

   @Test
   void rebindsABaseColumnOntoItsAggregatedHeader() {
      ConditionList conds = aggregateCondition("SALES", AggregateFormula.SUM);
      Set<String> unresolved =
         service.rebindConditionFieldsToViewColumns(conds, viewColumns("STATE", "Sum(SALES)"));

      assertTrue(unresolved.isEmpty(), "a bound measure must resolve");
      assertEquals("Sum(SALES)", fieldOf(conds));
   }

   @Test
   void leavesAnExactHeaderMatchAlone() {
      ConditionList conds = plainCondition("Sum(SALES)");
      Set<String> unresolved =
         service.rebindConditionFieldsToViewColumns(conds, viewColumns("STATE", "Sum(SALES)"));

      assertTrue(unresolved.isEmpty());
      assertEquals("Sum(SALES)", fieldOf(conds));
   }

   @Test
   void resolvesADimensionByItsOwnName() {
      ConditionList conds = plainCondition("STATE");
      Set<String> unresolved =
         service.rebindConditionFieldsToViewColumns(conds, viewColumns("STATE", "Sum(SALES)"));

      assertTrue(unresolved.isEmpty());
      assertEquals("STATE", fieldOf(conds));
   }

   @Test
   void reportsAFieldTheViewDoesNotCarry() {
      // Reported rather than silently dropped: the caller turns this into an IllegalArgumentException
      // naming the field and the available headers, so an unresolvable condition never reaches a cell.
      ConditionList conds = plainCondition("PROFIT");
      Set<String> unresolved =
         service.rebindConditionFieldsToViewColumns(conds, viewColumns("STATE", "Sum(SALES)"));

      assertEquals(Set.of("PROFIT"), unresolved);
   }

   @Test
   void picksTheAggregateTheConditionNamedWhenTheBaseColumnIsBoundTwice() {
      // The regression this guards: matching on the base name alone answered with whichever header the
      // view listed FIRST, so an Average condition landed on the Sum column and the highlight colored
      // cells chosen by a number nobody asked about.
      ConditionList conds = aggregateCondition("SALES", AggregateFormula.AVG);
      Set<String> unresolved =
         service.rebindConditionFieldsToViewColumns(conds, viewColumns("Sum(SALES)", "Average(SALES)"));

      assertTrue(unresolved.isEmpty());
      assertEquals("Average(SALES)", fieldOf(conds));
   }

   @Test
   void fallsBackToTheBaseNameScanWhenTheNamedAggregateIsNotBound() {
      // The scan is still the right answer when the base column is bound exactly once: the condition
      // names the measure the view has, just under a different aggregate than the caller assumed.
      ConditionList conds = aggregateCondition("SALES", AggregateFormula.AVG);
      Set<String> unresolved =
         service.rebindConditionFieldsToViewColumns(conds, viewColumns("STATE", "Sum(SALES)"));

      assertTrue(unresolved.isEmpty());
      assertEquals("Sum(SALES)", fieldOf(conds));
   }

   // ── header composition ─────────────────────────────────────────────────────
   //
   // Each arity AggregateRef.toView() composes, because the preferred name is compared for EQUALITY
   // against a header that method produced. A missed match is not an error — it degrades to the loose
   // base-name scan — which is exactly why it has to be pinned: the degradation is invisible.

   @Test
   void composesAOneColumnHeader() {
      assertEquals("Sum(SALES)", WizVsService.aggregateHeaderOf(
         new AggregateRef(new ColumnRef(new AttributeRef(null, "SALES")), null, AggregateFormula.SUM)));
   }

   @Test
   void composesATwoColumnHeaderWithItsSecondaryField() {
      assertEquals("Correlation(SALES, PROFIT)", WizVsService.aggregateHeaderOf(
         new AggregateRef(new ColumnRef(new AttributeRef(null, "SALES")),
                          new ColumnRef(new AttributeRef(null, "PROFIT")),
                          AggregateFormula.CORRELATION)));
   }

   @Test
   void composesNoHeaderForATwoColumnFormulaMissingItsSecondaryField() {
      // No view binds a two-column formula without its second column, so there is no header to prefer;
      // the caller falls back to the base-name scan rather than matching an invented spelling.
      assertEquals(null, WizVsService.aggregateHeaderOf(
         new AggregateRef(new ColumnRef(new AttributeRef(null, "SALES")), null,
                          AggregateFormula.CORRELATION)));
   }

   @Test
   void composesAnNParameterHeaderWithItsN() {
      AggregateRef ref = new AggregateRef(
         new ColumnRef(new AttributeRef(null, "SALES")), null, AggregateFormula.NTH_LARGEST);
      ref.setN(3);

      assertEquals("NthLargest(SALES, 3)", WizVsService.aggregateHeaderOf(ref));
   }

   @Test
   void resolvesATwoColumnFormulaAgainstItsRealHeader() {
      // The end this composition serves: two two-column aggregates over the SAME base column are
      // separated only by the secondary field, so a one-argument name would fall back to the scan and
      // pick whichever came first.
      ConditionList conds = aggregateCondition(new AggregateRef(
         new ColumnRef(new AttributeRef(null, "SALES")),
         new ColumnRef(new AttributeRef(null, "DISCOUNT")),
         AggregateFormula.CORRELATION));

      Set<String> unresolved = service.rebindConditionFieldsToViewColumns(
         conds, viewColumns("Correlation(SALES, PROFIT)", "Correlation(SALES, DISCOUNT)"));

      assertTrue(unresolved.isEmpty());
      assertEquals("Correlation(SALES, DISCOUNT)", fieldOf(conds));
   }

   @Test
   void namesNothingForARefThatCarriesNoFormula() {
      assertEquals(null, WizVsService.aggregateHeaderOf(new ColumnRef(new AttributeRef(null, "SALES"))));
   }
}
