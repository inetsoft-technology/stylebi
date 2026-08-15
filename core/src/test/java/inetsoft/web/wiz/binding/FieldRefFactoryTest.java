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
package inetsoft.web.wiz.binding;

import inetsoft.web.binding.model.BAggregateRefModel;
import inetsoft.web.binding.model.BDimensionRefModel;
import inetsoft.web.wiz.binding.model.FieldRef;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards the shared field-reference vocabulary. Specs 2b–2e inherit it and spec #4's
 * highlights embed it, so its shape and its fail-loud discriminator are the deliverable.
 */
@Tag("core")
class FieldRefFactoryTest {
   /**
    * The date level is an {@code XConstants} number whose mapping nobody can guess — year is 5,
    * quarter 4, month 3, week 2, day 1 — so a caller naturally writes {@code dateLevel: "year"}.
    * That was stored verbatim, and the binding then threw {@code For input string: "year"} on the
    * NEXT unrelated write to the same assembly, naming neither the field nor the level nor the
    * call that poisoned it. Found live on local-1199 while binding a crosstab for case 29.
    */
   @Test
   void normalizesNamedDateLevelsToTheirConstants() {
      assertEquals("5", DateLevels.normalize("year"));
      assertEquals("4", DateLevels.normalize("QUARTER"));
      assertEquals("3", DateLevels.normalize("Month"));
      assertEquals("2", DateLevels.normalize("week"));
      assertEquals("1", DateLevels.normalize("day"));
      assertEquals("0", DateLevels.normalize("none"));
   }

   @Test
   void passesANumericDateLevelThrough() {
      assertEquals("5", DateLevels.normalize("5"));
      assertEquals("0", DateLevels.normalize("0"));
   }

   @Test
   void refusesADateLevelItCannotResolveRatherThanStoringIt() {
      Exception thrown = assertThrows(IllegalArgumentException.class,
                                      () -> DateLevels.normalize("fortnight"));

      assertTrue(thrown.getMessage().contains("fortnight"));
      assertTrue(thrown.getMessage().contains("year"), "the message must list what is accepted");
   }

   /** A number outside the known set is as poisonous as a word, and just as silent. */
   @Test
   void refusesAnUnknownNumericDateLevel() {
      assertThrows(IllegalArgumentException.class, () -> DateLevels.normalize("99"));
   }

   /**
    * -1 is StyleBI's own sentinel for "no date level" — {@code VSDimensionRef.setDateLevel} maps
    * it to null — so refs read back from a live binding carry it. The first version of this guard
    * refused it and broke every round trip that reads a dimension and writes it elsewhere; five
    * existing tests caught that immediately.
    */
   @Test
   void acceptsTheUnsetSentinel() {
      assertEquals("-1", DateLevels.normalize("-1"));
   }

   @Test
   void leavesAnAbsentDateLevelAlone() {
      assertNull(DateLevels.normalize(null));
   }

   @Test
   void readsADimensionAsItsColumnAndDateLevel() {
      BDimensionRefModel model = new BDimensionRefModel();
      model.setColumnValue("Order Date");
      model.setDateLevel("5");

      FieldRef ref = FieldRefFactory.from(model);

      assertEquals("Order Date", ref.column());
      assertEquals("dimension", ref.type());
      assertEquals("5", ref.dateLevel());
      assertNull(ref.aggregate(), "a dimension has no aggregate");
   }

   @Test
   void readsAMeasureAsItsColumnAndFormula() {
      BAggregateRefModel model = new BAggregateRefModel();
      model.setColumnValue("Sales");
      model.setFormula("Sum");

      FieldRef ref = FieldRefFactory.from(model);

      assertEquals("Sales", ref.column());
      assertEquals("measure", ref.type());
      assertEquals("Sum", ref.aggregate());
      assertNull(ref.dateLevel(), "a measure has no date level");
   }

   @Test
   void requireTypeRejectsAMissingDiscriminatorNamingTheField() {
      FieldRef ref = new FieldRef("Sales", null, null, null, null);

      Exception thrown = assertThrows(IllegalArgumentException.class,
                                      () -> FieldRefFactory.requireType(ref));
      assertTrue(thrown.getMessage().contains("Sales"));
      assertTrue(thrown.getMessage().contains("type"));
   }

   @Test
   void requireTypeRejectsAnUnrecognizedDiscriminator() {
      FieldRef ref = new FieldRef("Sales", "metric", null, null, null);

      Exception thrown = assertThrows(IllegalArgumentException.class,
                                      () -> FieldRefFactory.requireType(ref));
      assertTrue(thrown.getMessage().contains("metric"),
                 "the error must name what was supplied, got: " + thrown.getMessage());
   }

   @Test
   void requireTypeAcceptsBothValidDiscriminatorsCaseInsensitively() {
      FieldRefFactory.requireType(new FieldRef("A", "DIMENSION", null, null, null));
      FieldRefFactory.requireType(new FieldRef("B", "measure", null, null, null));
   }
}
