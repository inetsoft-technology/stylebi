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

import inetsoft.web.wiz.controller.WizDataModelWire.Failure;
import inetsoft.web.wiz.controller.WizLogicalModelWire.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * D-9: the whole-definition update has a lost-update hole (two edits planned from one read, the
 * second overwrites the first). {@code get} returns a {@code revision}; an update that sends it as
 * {@code baseRevision} is refused {@code STALE_MODEL} when the stored model no longer has it.
 */
class WizLogicalModelRevisionTest extends WizLogicalModelTestBase {
   private Definition withAttribute(Definition d, Attribute added) {
      List<Entity> entities = new ArrayList<>();

      for(Entity e : d.entities()) {
         List<Attribute> attributes = new ArrayList<>(e.attributes());
         attributes.add(added);
         entities.add(new Entity(e.name(), e.oldName(), e.description(), e.visible(),
                                 e.baseElement(), null, attributes));
      }

      return new Definition(d.name(), d.description(), d.view(), d.folder(), d.connection(),
                            d.parent(), d.extended(), entities);
   }

   private UpdateRequest request(String name, Definition d, String baseRevision) {
      return new UpdateRequest(ds, name, d, null, baseRevision);
   }

   private static boolean has(Definition d, String attribute) {
      return d.entities().stream().flatMap(e -> e.attributes().stream())
         .anyMatch(a -> attribute.equals(a.name()));
   }

   @Test
   void staleBaseRevisionIsRefusedAndNothingIsWritten() throws Exception {
      create(def("m", entity("Sales", col("Amount", "T1", "AMOUNT"))));
      GetResponse read = get("m");
      assertNotNull(read.revision());

      // two edits planned from the same read
      Object first = controller.update(request("m", withAttribute(
         read.definition(), exp("A1", "T1.AMOUNT + 1", "double")), read.revision()), bob);
      Object second = controller.update(request("m", withAttribute(
         read.definition(), exp("A2", "T1.AMOUNT + 2", "double")), read.revision()), bob);

      assertInstanceOf(UpdateResponse.class, first, String.valueOf(first));
      Failure stale = assertInstanceOf(Failure.class, second, String.valueOf(second));
      assertEquals(WizDataModelWire.STALE_MODEL, stale.reason());
      assertEquals("model m changed since it was read; read it again and redo the edit; " +
                      "nothing was written", stale.message());
      Definition stored = get("m").definition();
      assertTrue(has(stored, "A1"), "the first edit is still there");
      assertFalse(has(stored, "A2"), "the second was not written");
   }

   @Test
   void aStaleDryRunIsRefusedToo() throws Exception {
      create(def("m", entity("Sales", col("Amount", "T1", "AMOUNT"))));
      GetResponse read = get("m");
      controller.update(request("m", withAttribute(read.definition(),
                                                   exp("A1", "T1.AMOUNT + 1", "double")),
                                read.revision()), bob);

      Object dry = controller.update(new UpdateRequest(ds, "m", withAttribute(
         read.definition(), exp("A2", "T1.AMOUNT + 2", "double")), true, read.revision()), bob);

      assertEquals(WizDataModelWire.STALE_MODEL, assertInstanceOf(Failure.class, dry).reason());
   }

   @Test
   void matchingBaseRevisionApplies() throws Exception {
      create(def("m", entity("Sales", col("Amount", "T1", "AMOUNT"))));
      GetResponse read = get("m");

      UpdateResponse first = (UpdateResponse) controller.update(request("m", withAttribute(
         read.definition(), exp("A1", "T1.AMOUNT + 1", "double")), read.revision()), bob);

      assertNotNull(first.revision());
      assertNotEquals(read.revision(), first.revision(), "the model changed, so did its revision");
      assertEquals(get("m").revision(), first.revision(),
                   "the write response carries the revision a following get returns");
      // the next edit is planned from the write response, not from a new read
      UpdateResponse next = (UpdateResponse) controller.update(request("m", withAttribute(
         first.definition(), exp("A2", "T1.AMOUNT + 2", "double")), first.revision()), bob);
      assertTrue(has(next.definition(), "A2"));
      assertTrue(has(next.definition(), "A1"));
   }

   @Test
   void absentBaseRevisionKeepsTheOldBehavior() throws Exception {
      create(def("m", entity("Sales", col("Amount", "T1", "AMOUNT"))));
      GetResponse read = get("m");
      controller.update(request("m", withAttribute(read.definition(),
                                                   exp("A1", "T1.AMOUNT + 1", "double")),
                                null), bob);

      // planned from the same read, no revision sent: applied (it overwrites, as before)
      Object second = controller.update(request("m", withAttribute(
         read.definition(), exp("A2", "T1.AMOUNT + 2", "double")), null), bob);

      assertInstanceOf(UpdateResponse.class, second, String.valueOf(second));
      assertTrue(has(get("m").definition(), "A2"));
      assertFalse(has(get("m").definition(), "A1"), "the lost update the revision guards against");
   }

   @Test
   void aNoOpUpdateReturnsTheUnchangedRevision() throws Exception {
      create(def("m", entity("Sales", col("Amount", "T1", "AMOUNT"))));
      GetResponse read = get("m");

      UpdateResponse noop = (UpdateResponse) controller.update(
         request("m", read.definition(), read.revision()), bob);

      assertEquals(read.revision(), noop.revision());
      assertEquals(read.revision(), get("m").revision(), "reading twice is stable");
   }

   @Test
   void extendedModelRevisionStable() throws Exception {
      create(def("base", entity("Sales", col("Amount", "T1", "AMOUNT"))));
      Definition child = new Definition("child", null, null, null, null, "base", null,
                                        List.of(entity("Sales", exp("Margin", "T1.AMOUNT * 0.1",
                                                                    "double"))));
      CreateResponse created = (CreateResponse) controller.create(
         new CreateRequest(ds, child, null), bob);
      GetResponse first = (GetResponse) controller.get(new GetRequest(ds, "child", "base"), bob);
      GetResponse again = (GetResponse) controller.get(new GetRequest(ds, "child", "base"), bob);

      assertEquals(first.revision(), again.revision());
      assertEquals(first.revision(), created.revision(),
                   "the create response carries the revision a get returns");
      UpdateResponse noop = (UpdateResponse) controller.update(
         request("child", first.definition(), first.revision()), bob);
      assertEquals(first.revision(), noop.revision());

      // an own-element edit changes it; a stale edit is refused
      UpdateResponse edited = (UpdateResponse) controller.update(request("child", withAttribute(
         first.definition(), exp("Tax", "T1.AMOUNT * 0.2", "double")), first.revision()), bob);
      assertNotEquals(first.revision(), edited.revision());
      Object stale = controller.update(request("child", withAttribute(
         first.definition(), exp("Fee", "T1.AMOUNT * 0.3", "double")), first.revision()), bob);
      assertEquals(WizDataModelWire.STALE_MODEL, assertInstanceOf(Failure.class, stale).reason());
   }
}
