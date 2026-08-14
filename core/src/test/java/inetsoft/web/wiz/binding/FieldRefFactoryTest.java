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
