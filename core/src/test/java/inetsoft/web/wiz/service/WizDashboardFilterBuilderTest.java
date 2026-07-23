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

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class WizDashboardFilterBuilderTest {
   private final WizDashboardFilterBuilder builder = new WizDashboardFilterBuilder();

   @Test
   void categoricalFieldMakesASelectionList() {
      Viewsheet vs = new Viewsheet();
      // A field on NO table binds to nothing -> skipped, but the control TYPE is still decided
      // by data type. Assert via a package-visible helper the builder exposes for the type choice:
      AbstractSelectionVSAssembly a = builder.createControlForType(vs, "string", column("Region", "string"));
      assertTrue(a instanceof SelectionListVSAssembly);
   }

   @Test
   void dateAndNumericFieldsMakeRangeSliders() {
      Viewsheet vs = new Viewsheet();
      assertTrue(builder.createControlForType(vs, "date", column("OrderDate", "date")) instanceof TimeSliderVSAssembly);
      assertTrue(builder.createControlForType(vs, "integer", column("Qty", "integer")) instanceof TimeSliderVSAssembly);
   }

   // Helper mirrors AddFilterService.buildColumnRef (AttributeRef + ColumnRef with dataType).
   // NOTE: ColumnRef lives in inetsoft.uql.asset (not inetsoft.uql.erm as the task brief sketch
   // assumed) — confirmed against AddFilterService's imports in Step 1.
   private static inetsoft.uql.erm.DataRef column(String name, String dtype) {
      inetsoft.uql.erm.AttributeRef attr = new inetsoft.uql.erm.AttributeRef(name);
      attr.setDataType(dtype);
      inetsoft.uql.asset.ColumnRef col = new inetsoft.uql.asset.ColumnRef(attr);
      col.setDataType(dtype);
      return col;
   }
}
