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
package inetsoft.uql.viewsheet;

import inetsoft.test.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.viewsheet.internal.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #80070: {@link Viewsheet#getAssembly(String)} must return null, not throw
 * ClassCastException, when the dotted prefix names a non-viewsheet assembly.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ViewsheetGetAssemblyDottedNameTest {
   @Test
   void nonViewsheetPrefixReturnsNull() {
      Viewsheet vs = new Viewsheet();
      vs.addAssembly(new ChartVSAssembly(vs, "C1"));

      assertNotNull(vs.getAssembly("C1"));
      assertNull(vs.getAssembly("C1.Nope"));
      assertNull(vs.getAssembly("C1.A.B"));
   }

   @Test
   void unknownPrefixReturnsNull() {
      Viewsheet vs = new Viewsheet();
      assertNull(vs.getAssembly("Missing.Nope"));
      assertNull(vs.getAssembly("Missing.A.B"));
   }

   @Test
   void embeddedDottedNameStillResolves() {
      Viewsheet child = new Viewsheet();
      child.addAssembly(new ChartVSAssembly(child, "Chart1"));
      Viewsheet root = new Viewsheet();
      Viewsheet embedded = child.clone();
      embedded.setEntry(new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
                                       "child", null));
      embedded.getVSAssemblyInfo().setName("Embedded1");
      root.addAssembly(embedded);

      assertNotNull(root.getAssembly("Embedded1.Chart1"));
      assertNull(root.getAssembly("Embedded1.Nope"));
      assertNull(root.getAssembly("Embedded1.Chart1.Nope"));
   }
}
