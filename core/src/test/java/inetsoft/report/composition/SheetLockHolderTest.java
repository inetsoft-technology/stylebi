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
package inetsoft.report.composition;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #81431: the sheets of all nodes are ordered by the cluster-wide counter at the end of the
 * runtime id, so every node picks the same lock holder.
 */
@Tag("core")
class SheetLockHolderTest {
   @Test
   void sequenceIsTheNumberAfterTheLastDash() {
      assertEquals(12L, SheetLockHolder.sequenceOf("Examples/Map Points-12"));
      // a '-' in the path must not confuse the counter
      assertEquals(7L, SheetLockHolder.sequenceOf("Examples/my-sheet-7"));
      assertEquals(0L, SheetLockHolder.sequenceOf("a-0"));
   }

   @Test
   void unknownOrderSortsLast() {
      assertEquals(Long.MAX_VALUE, SheetLockHolder.sequenceOf(null));
      assertEquals(Long.MAX_VALUE, SheetLockHolder.sequenceOf(""));
      assertEquals(Long.MAX_VALUE, SheetLockHolder.sequenceOf("no-counter-here"));
      assertEquals(Long.MAX_VALUE, SheetLockHolder.sequenceOf("trailing-"));
      assertEquals(Long.MAX_VALUE, SheetLockHolder.sequenceOf("nodash"));
   }

   @Test
   void earlierOpenedSheetWins() {
      SheetLockHolder first = new SheetLockHolder("u1", "ws-5");
      SheetLockHolder second = new SheetLockHolder("u2", "ws-9");

      assertSame(first, SheetLockHolder.earlier(first, second));
      assertSame(first, SheetLockHolder.earlier(second, first));
   }

   @Test
   void numbersAreComparedAsNumbersNotAsText() {
      SheetLockHolder two = new SheetLockHolder("u1", "ws-2");
      SheetLockHolder ten = new SheetLockHolder("u2", "ws-10");

      assertSame(two, SheetLockHolder.earlier(ten, two));
   }

   @Test
   void sheetWithAKnownOrderBeatsOneWithoutAndTiesAreDeterministic() {
      SheetLockHolder known = new SheetLockHolder("u1", "ws-3");
      SheetLockHolder unknown = new SheetLockHolder("u2", "weird");
      assertSame(known, SheetLockHolder.earlier(unknown, known));

      // same number on two paths: the smaller id wins on every node whatever the argument order
      SheetLockHolder a = new SheetLockHolder("u1", "a-4");
      SheetLockHolder b = new SheetLockHolder("u2", "b-4");
      assertSame(a, SheetLockHolder.earlier(a, b));
      assertSame(a, SheetLockHolder.earlier(b, a));
   }

   @Test
   void nullIsIgnored() {
      SheetLockHolder holder = new SheetLockHolder("u1", "ws-1");

      assertSame(holder, SheetLockHolder.earlier(holder, null));
      assertSame(holder, SheetLockHolder.earlier(null, holder));
      assertNull(SheetLockHolder.earlier(null, null));
   }

   @Test
   void aSheetWithoutUserStillHoldsTheLock() {
      // the lock of a sheet that has no user belongs to nobody, as before the lookup of the
      // other nodes was added: it hides a later sheet instead of being skipped
      SheetLockHolder noUser = new SheetLockHolder(null, "ws-1");
      SheetLockHolder later = new SheetLockHolder("u2", "ws-2");

      SheetLockHolder holder = SheetLockHolder.earlier(later, noUser);

      assertSame(noUser, holder);
      assertNull(holder.getUser());
   }

   @Test
   void survivesTheTripBetweenNodes() throws Exception {
      SheetLockHolder sent = new SheetLockHolder("u1", "Examples/ws-42");

      ByteArrayOutputStream bytes = new ByteArrayOutputStream();

      try(ObjectOutputStream out = new ObjectOutputStream(bytes)) {
         out.writeObject(sent);
      }

      SheetLockHolder received;

      try(ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
         received = (SheetLockHolder) in.readObject();
      }

      assertEquals("u1", received.getUser());
      assertEquals("Examples/ws-42", received.getSheetId());
      // the order must still be known after the trip
      assertSame(received, SheetLockHolder.earlier(received, new SheetLockHolder("u2", "Examples/ws-43")));
   }
}
