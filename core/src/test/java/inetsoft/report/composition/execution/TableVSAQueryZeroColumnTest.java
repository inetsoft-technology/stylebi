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
package inetsoft.report.composition.execution;

import inetsoft.test.*;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.*;
import inetsoft.uql.erm.AttributeRef;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78805: a Table with a source and no bound column must not run the base data query.
 * {@link TableVSAQuery#getTableLens()} returns no lens exactly when
 * {@code hasVisibleColumn(table)} is false for the bound table assembly it builds.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class TableVSAQueryZeroColumnTest {
   private static BoundTableAssembly table(boolean... visible) {
      BoundTableAssembly table = new BoundTableAssembly(new Worksheet(), "T");
      ColumnSelection columns = new ColumnSelection();

      for(int i = 0; i < visible.length; i++) {
         ColumnRef ref = new ColumnRef(new AttributeRef("T", "c" + i));
         ref.setVisible(visible[i]);
         columns.addAttribute(ref);
      }

      table.setColumnSelection(columns);
      return table;
   }

   @Test
   void noColumnsHasNoVisibleColumn() {
      assertFalse(TableVSAQuery.hasVisibleColumn(table()));
   }

   @Test
   void onlyHiddenBaseColumnsHaveNoVisibleColumn() {
      assertFalse(TableVSAQuery.hasVisibleColumn(table(false, false)));
   }

   @Test
   void oneVisibleColumnIsUnchanged() {
      assertTrue(TableVSAQuery.hasVisibleColumn(table(false, true)));
   }
}
