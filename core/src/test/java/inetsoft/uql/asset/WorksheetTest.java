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

import inetsoft.web.wiz.pairing.TestWorksheets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

import inetsoft.web.wiz.pairing.WizAgentTestSupport;

@WizAgentTestSupport
class WorksheetTest {

   /**
    * Regression for a null/blank-named assembly permanently poisoning the lazy name cache:
    * createCache() iterates every assembly and calls {@code ConcurrentHashMap.put(name, ...)},
    * which throws NPE on a null key before {@code this.amap} is reassigned, so once a
    * null-named assembly is present every future getAssembly() call (on ANY name) retries and
    * fails the same way, forever. addAssembly() must refuse a null/blank name outright so it
    * never reaches the assemblies list.
    */
   @Test
   void addAssemblyRejectsNullNameAndDoesNotPoisonLookupForOtherAssemblies() {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t = TestWorksheets.tableWithColumns(ws, "Orders", "id");
      ws.addAssembly(t);

      EmbeddedTableAssembly nullNamed = new EmbeddedTableAssembly(ws, null);
      assertFalse(ws.addAssembly(nullNamed));

      assertSame(t, ws.getAssembly("Orders"));
      assertEquals(1, ws.getAssemblies().length);
   }

   @Test
   void addAssemblyRejectsBlankName() {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly blankNamed = new EmbeddedTableAssembly(ws, "  ");
      assertFalse(ws.addAssembly(blankNamed));
      assertEquals(0, ws.getAssemblies().length);
   }
}
