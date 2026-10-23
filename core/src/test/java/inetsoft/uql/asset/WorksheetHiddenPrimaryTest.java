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

import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #79176 (item 1): {@link Worksheet#addAssembly} auto-promoted the first visible assembly to
 * primary while ignoring the table's "visible in viewsheet" flag, so a copy of a hidden table
 * added while the worksheet had no primary became a hidden primary.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class WorksheetHiddenPrimaryTest {
   @Test
   void hiddenTableAddedWithoutPrimaryDoesNotBecomePrimary() {
      Worksheet ws = new Worksheet();
      TableAssembly hidden = new PhysicalBoundTableAssembly(ws, "HIDDEN");
      hidden.setVisibleTable(false);

      assertTrue(ws.addAssembly(hidden));

      assertNull(ws.getPrimaryAssemblyName());
      assertFalse(hidden.isVisibleTable());
   }

   @Test
   void visibleTableAddedAfterHiddenOneBecomesPrimary() {
      Worksheet ws = new Worksheet();
      TableAssembly hidden = new PhysicalBoundTableAssembly(ws, "HIDDEN");
      hidden.setVisibleTable(false);
      ws.addAssembly(hidden);
      assertNull(ws.getPrimaryAssemblyName());

      TableAssembly visible = new PhysicalBoundTableAssembly(ws, "VISIBLE");
      ws.addAssembly(visible);

      assertEquals("VISIBLE", ws.getPrimaryAssemblyName());
   }

   @Test
   void visibleTableStillBecomesPrimaryWhenFirst() {
      Worksheet ws = new Worksheet();

      ws.addAssembly(new PhysicalBoundTableAssembly(ws, "FIRST"));

      assertEquals("FIRST", ws.getPrimaryAssemblyName());
   }

   @Test
   void clonedHiddenTableDoesNotBecomePrimaryAfterPrimaryWasDeleted() {
      Worksheet ws = new Worksheet();
      TableAssembly primary = new PhysicalBoundTableAssembly(ws, "PRIMARY");
      ws.addAssembly(primary);
      TableAssembly hidden = new PhysicalBoundTableAssembly(ws, "HIDDEN");
      hidden.setVisibleTable(false);
      ws.addAssembly(hidden);
      assertEquals("PRIMARY", ws.getPrimaryAssemblyName());

      // same sequence as duplicate_assembly after deleting the primary
      ws.removeAssembly("PRIMARY");
      assertNull(ws.getPrimaryAssemblyName());
      TableAssembly copy = (TableAssembly) hidden.clone();
      copy.getInfo().setName("HIDDEN_COPY");
      ws.addAssembly(copy);

      assertFalse(copy.isVisibleTable(), "the clone keeps the hidden flag");
      assertNull(ws.getPrimaryAssemblyName(), "a hidden copy must not become the primary");
   }
}
