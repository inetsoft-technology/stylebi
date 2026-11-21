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
package inetsoft.web.wiz.controller;

import inetsoft.web.wiz.controller.WizDataModelWire.Err;
import inetsoft.web.wiz.controller.WizLogicalModelWire.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * The logical model validator (design 4.4): everything the service does not check is checked
 * here, before the service is called, with a field-named, coded answer. Runs against the real
 * view and folder registry and the mocked metadata of {@link WizLogicalModelTestBase}.
 */
class WizLogicalModelValidatorTest extends WizLogicalModelTestBase {
   private final WizLogicalModelValidator validator = new WizLogicalModelValidator();

   private WizLogicalModelValidator.Result validate(Definition definition) {
      return validator.validate(new WizLogicalModelEnv(ds, dataModel, dataSourceService),
                                definition, WizLogicalModelValidator.Mode.CREATE, null);
   }

   private static List<String> fields(WizLogicalModelValidator.Result result) {
      return result.errors().stream().map(Err::field).toList();
   }

   private static List<String> codesOf(WizLogicalModelValidator.Result result) {
      return result.errors().stream().map(Err::code).toList();
   }

   private static Definition one(Attribute... attributes) {
      return def("M", entity("E", attributes));
   }

   @Test
   void aGoodModelHasNoErrors() {
      WizLogicalModelValidator.Result result = validate(one(
         col("Amount", "T1", "AMOUNT"), exp("Doubled", "T1.AMOUNT * 2", "double")));

      assertTrue(result.valid(), result.errors().toString());
   }

   @Test
   void unknownTableIsRefused() {
      WizLogicalModelValidator.Result result = validate(one(col("A", "NOPE", "ID")));

      assertEquals(List.of("entities[E].attributes[A].table"), fields(result));
      assertEquals(List.of("TABLE_NOT_IN_VIEW"), codesOf(result));
   }

   @Test
   void aTableOfAnotherViewIsRefused() {
      // T2 exists in the database metadata but is not in view pv
      WizLogicalModelValidator.Result result = validate(one(col("A", "T2", "CODE")));

      assertEquals(List.of("TABLE_NOT_IN_VIEW"), codesOf(result));
   }

   @Test
   void unknownColumnIsRefused() {
      WizLogicalModelValidator.Result result = validate(one(col("A", "T1", "NOPE")));

      assertEquals(List.of("entities[E].attributes[A].column"), fields(result));
      assertEquals(List.of("COLUMN_NOT_IN_TABLE"), codesOf(result));
   }

   @Test
   void badExpressionIsRefused() {
      WizLogicalModelValidator.Result result = validate(one(exp("X", "(T1.AMOUNT", "double")));

      assertEquals(List.of("entities[E].attributes[X].expression"), fields(result));
      assertEquals(List.of("EXPRESSION_INVALID"), codesOf(result));
   }

   /** P4 D-2: these were saved with parseable:true; the live parser path accepts them quietly. */
   @Test
   void unparsableExpressionsAreRefusedByTheRealParser() {
      for(String bad : new String[] { "1 +", "foo(", "SA.PRODUCTS.PRICE +",
                                      "SA.PRODUCTS.PRICE garbage", "((1 +", "(1", "1)",
                                      "SA.PRODUCTS.PRICE *", "1 2" })
      {
         WizLogicalModelValidator.Result result = validate(one(exp("X", bad, "double")));

         assertEquals(List.of("EXPRESSION_INVALID"), codesOf(result), bad);
         assertEquals(List.of("entities[E].attributes[X].expression"), fields(result), bad);
      }
   }

   @Test
   void validExpressionsAreStillAccepted() {
      for(String good : new String[] { "SA.PRODUCTS.PRICE * 2", "upper(SA.PRODUCTS.NAME)",
                                       "SA.PRODUCTS.PRICE + SA.ORDERS.QTY",
                                       "case when a.b = 1 then 2 else 3 end",
                                       "cast(a.b as integer)", "(1 + 2) * 3",
                                       "coalesce(a.b, 0)", "a.b || 'x'", "-a.b", "count(*)",
                                       "sum(a.b) / count(a.c)", "'x'", "1",
                                       // R1-7: boolean / flag expressions the Portal accepts
                                       "T1.ID = 1", "T1.ID > 1 AND T1.ID < 5",
                                       "T1.NAME LIKE 'a%'", "T1.ID IN (1,2,3)",
                                       "T1.ID BETWEEN 1 AND 3", "T1.AMOUNT IS NULL",
                                       "T1.AMOUNT > 100", "N'text'", "T1.NAME || N'é'" })
      {
         WizLogicalModelValidator.Result result = validate(one(exp("X", good, "double")));

         assertTrue(result.valid(), good + ": " + result.errors());
      }
   }

   @Test
   void textLeftAfterAnExpressionIsRefused() {
      WizLogicalModelValidator.Result result = validate(one(exp("X", "T1.AMOUNT T1.ID", "double")));

      assertEquals(List.of("EXPRESSION_INVALID"), codesOf(result), result.errors().toString());
   }

   @Test
   void anExpressionAttributeNeedsADataType() {
      WizLogicalModelValidator.Result result = validate(one(exp("X", "T1.AMOUNT", null)));

      assertEquals(List.of("entities[E].attributes[X].dataType"), fields(result));
      assertEquals(List.of("DATATYPE_INVALID"), codesOf(result));
   }

   @Test
   void aDataTypeThatIsNotATypeNameIsRefused() {
      Attribute bad = new Attribute("A", null, null, null, null, "T1", "ID", null, null, null,
                                    "banana", null, null, null, null, null, null, null, null);

      assertEquals(List.of("DATATYPE_INVALID"), codesOf(validate(one(bad))));
   }

   @Test
   void aRefTypeThatIsNotOneOfTheThreeIsRefused() {
      Attribute bad = new Attribute("A", null, null, null, null, "T1", "ID", null, null, null,
                                    null, "Dimension", null, null, null, null, null, null, null);

      WizLogicalModelValidator.Result result = validate(one(bad));

      assertEquals(List.of("REFTYPE_INVALID"), codesOf(result));
      assertTrue(result.errors().get(0).message().contains("[none, dimension, measure]"));
   }

   @Test
   void columnAndExpressionTogetherAreAmbiguousNamingBoth() {
      Attribute both = new Attribute("A", null, null, null, null, "T1", "ID", "T1.ID + 1", null,
                                     null, "integer", null, null, null, null, null, null, null,
                                     null);

      assertEquals(List.of("ATTRIBUTE_BINDING_AMBIGUOUS"), codesOf(validate(one(both))));
   }

   @Test
   void anExplicitTypeThatContradictsTheBindingIsRefused() {
      Attribute wrongType = new Attribute("A", null, null, null, "expression", "T1", "ID", null,
                                          null, null, null, null, null, null, null, null, null,
                                          null, null);

      WizLogicalModelValidator.Result result = validate(one(wrongType));

      assertEquals(List.of("entities[E].attributes[A].type"), fields(result));
   }

   @Test
   void anAttributeWithNoBindingIsRefused() {
      Attribute none = new Attribute("A", null, null, null, null, null, null, null, null, null,
                                     null, null, null, null, null, null, null, null, null);

      assertEquals(List.of("ATTRIBUTE_BINDING_AMBIGUOUS"), codesOf(validate(one(none))));
   }

   // -- duplicates ------------------------------------------------------------------------------

   @Test
   void duplicateEntityIsRefused() {
      WizLogicalModelValidator.Result result = validate(def(
         "M", entity("Sales", col("A", "T1", "ID")), entity("Sales", col("B", "T1", "NAME"))));

      assertEquals(List.of("entities[Sales]"), fields(result));
      assertEquals(List.of("ENTITY_DUPLICATE"), codesOf(result));
   }

   @Test
   void duplicateAttributeIsRefused() {
      WizLogicalModelValidator.Result result = validate(one(
         col("A", "T1", "ID"), col("A", "T1", "NAME")));

      assertEquals(List.of("entities[E].attributes[A]"), fields(result));
      assertEquals(List.of("ATTRIBUTE_DUPLICATE"), codesOf(result));
   }

   @Test
   void caseVariantsAreDistinctValidNames() {
      // the registry's keys are case-sensitive (probe d); the plugin refuses variants itself
      WizLogicalModelValidator.Result result = validate(def(
         "M", entity("Sales", col("Amount", "T1", "ID"), col("amount", "T1", "NAME")),
         entity("sales", col("A", "T1", "ID"))));

      assertTrue(result.valid(), result.errors().toString());
   }

   // -- header ----------------------------------------------------------------------------------

   @Test
   void missingViewAndMissingFolderAndMissingConnectionAreRefused() {
      Definition noView = new Definition("M", null, "nope", "Nope", "ghost", null, null,
                                         List.of());

      WizLogicalModelValidator.Result result = validate(noView);

      assertEquals(List.of("VIEW_NOT_FOUND", "FOLDER_NOT_FOUND", "CONNECTION_NOT_FOUND"),
                   codesOf(result));
      assertEquals(List.of("view", "folder", "connection"), fields(result));
   }

   @Test
   void aReservedOrBadNameIsRefusedByCode() {
      assertEquals(List.of("NAME_RESERVED"), codesOf(validate(def("Domain"))));
      assertEquals(List.of("NAME_INVALID"), codesOf(validate(def("a/b"))));
   }

   @Test
   void everyProblemIsReportedAtOnceUpToFifty() {
      List<Attribute> bad = new ArrayList<>();

      for(int i = 0; i < 80; i++) {
         bad.add(col("A" + i, "NOPE", "ID"));
      }

      WizLogicalModelValidator.Result result = validate(one(bad.toArray(new Attribute[0])));

      assertEquals(WizLogicalModelValidator.MAX_ERRORS, result.errors().size());
   }

   @Test
   void aParentIsRefusedInPhaseOne() {
      Definition withParent = new Definition("M", null, "pv", null, null, "base", null,
                                             List.of());

      WizLogicalModelValidator.Result result = validate(withParent);

      assertEquals(List.of("EXTENDED_UNSUPPORTED"), codesOf(result));
   }

   @Test
   void aMarkerOnACreateThatIsNotItsOwnNameIsRefused() {
      WizLogicalModelValidator.Result result = validate(one(col("A", "Old", "T1", "ID")));

      assertEquals(List.of("OLDNAME_UNKNOWN"), codesOf(result));
   }

   // -- drill -----------------------------------------------------------------------------------

   @Test
   void drillProblemsAreNamed() {
      Drill drill = new Drill(List.of(
         new WizLogicalModelWire.DrillPath("", "ftp", null, null, null, null, null,
                                           List.of(new DrillParam("", null, null)), null,
                                           List.of("x"))));
      Attribute withDrill = new Attribute("A", null, null, null, null, "T1", "ID", null, null,
                                          null, null, null, null, null, null, null, drill, null,
                                          null);

      WizLogicalModelValidator.Result result = validate(one(withDrill));

      assertEquals(4, result.errors().size(), result.errors().toString());
      assertTrue(codesOf(result).stream().allMatch("DRILL_INVALID"::equals));
   }

   // -- the data type of a column is filled from the database -------------------------------------

   @Test
   void aColumnWithNoDataTypeTakesThePhysicalOneAndSaysSo() {
      WizLogicalModelValidator.Result result = validate(one(col("Amount", "T1", "AMOUNT")));

      assertEquals("double",
                   result.normalized().entities().get(0).attributes().get(0).dataType());
      assertEquals(1, result.warnings().size());
   }

   @Test
   void metadataIsReadOncePerValidation() throws Exception {
      validate(one(col("A", "T1", "ID"), col("B", "T1", "NAME"), col("C", "T1", "AMOUNT")));

      verify(metaProvider, times(1)).getMetaData(any(inetsoft.uql.erm.XPartition.class),
                                                  anyBoolean(), any());
   }
}
