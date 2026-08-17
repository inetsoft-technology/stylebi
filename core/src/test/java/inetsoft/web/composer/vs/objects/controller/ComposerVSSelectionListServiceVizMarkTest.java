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
package inetsoft.web.composer.vs.objects.controller;

import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.SelectionListVSAssembly;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.VizMark;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Converting a selection list to a range slider (time slider) must keep the selection list's own
 * provenance mark, not the host viewsheet's - the two-arg constructor used to build the new
 * slider would otherwise silently re-mark it from the host.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ComposerVSSelectionListServiceVizMarkTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
   }

   @Test
   void convertingToRangeSliderKeepsTheListOwnMark() throws Exception {
      // the gate is open, so the host takes the modern mark on construction
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      Viewsheet host = new Viewsheet();

      SelectionListVSAssembly list = new SelectionListVSAssembly(host, "List1");
      // the list's own mark differs from the host it currently sits in
      list.getVSAssemblyInfo().setVizMark(VizMark.MODERN_DARK);

      ComposerVSSelectionListService service = new ComposerVSSelectionListService(null, null);
      Method create = ComposerVSSelectionListService.class
         .getDeclaredMethod("createTimeSliderVSAssembly", Viewsheet.class, VSAssembly.class);
      create.setAccessible(true);
      VSAssembly slider = (VSAssembly) create.invoke(service, host, list);

      assertEquals(VizMark.MODERN_DARK, slider.getVSAssemblyInfo().getVizMark(),
                   "the converted range slider must keep the list's own mark, not the host's");
   }
}
