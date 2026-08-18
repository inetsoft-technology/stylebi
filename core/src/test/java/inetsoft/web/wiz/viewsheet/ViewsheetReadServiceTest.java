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
package inetsoft.web.wiz.viewsheet;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.GaugeVSAssembly;
import inetsoft.uql.viewsheet.TextVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.wiz.viewsheet.model.AssemblyNode;
import inetsoft.web.wiz.viewsheet.model.ViewsheetModel;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Constructing a real {@link Viewsheet} runs {@code VSAssemblyInfo}'s constructor, which reads
 * {@code SreeEnv} and therefore needs a Spring context — hence the harness annotations, which
 * mirror {@code ClipboardControllerServiceTest}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome()
@Tag("core")
class ViewsheetReadServiceTest {
   @Test
   void readsEveryAssemblyWithItsPositionAndType() {
      Viewsheet vs = new Viewsheet();
      GaugeVSAssembly gauge = new GaugeVSAssembly(vs, "Gauge1");
      gauge.setPixelOffset(new Point(10, 20));
      vs.addAssembly(gauge);
      TextVSAssembly text = new TextVSAssembly(vs, "Text1");
      text.setPixelOffset(new Point(30, 40));
      vs.addAssembly(text);

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);

      ViewsheetModel model = new ViewsheetReadService().read(rvs);

      assertEquals(2, model.assemblies().size());
      AssemblyNode first = model.assemblies().stream()
         .filter(a -> "Gauge1".equals(a.name())).findFirst().orElseThrow();
      assertEquals("Gauge", first.type());
      assertEquals(10, first.x());
      assertEquals(20, first.y());
   }

   @Test
   void reportsAnEmptyViewsheetAsNoAssembliesRatherThanFailing() {
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(new Viewsheet());

      assertTrue(new ViewsheetReadService().read(rvs).assemblies().isEmpty());
   }
}
