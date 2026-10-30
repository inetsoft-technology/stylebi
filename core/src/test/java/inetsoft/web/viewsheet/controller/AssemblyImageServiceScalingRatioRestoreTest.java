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
package inetsoft.web.viewsheet.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.*;
import inetsoft.graph.internal.DimensionD;
import inetsoft.web.service.BinaryTransferService;
import inetsoft.web.wiz.pairing.TestPrincipals;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Dimension;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #79775: the non-chart branch of {@code AssemblyImageService.processGetAssemblyImage1}
 * set a scaling ratio of {@code canvas size / pixel size} on the live Shape/Image assembly and
 * never restored it, so a single-assembly render at a canvas size different from the assembly's
 * own (e.g. the wiz tool's default 800x600 canvas) left the assembly scaled, and every later
 * whole-sheet render or export drew it at the inflated size.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome()
@Tag("core")
class AssemblyImageServiceScalingRatioRestoreTest {
   @Test
   void lineRenderDoesNotLeaveScalingRatio() throws Exception {
      Viewsheet vs = new Viewsheet();
      LineVSAssembly line = new LineVSAssembly(vs, "Line1");
      line.setPixelSize(new Dimension(100, 1));
      vs.addAssembly(line);

      render(vs, "Line1");

      assertUnscaled(line.getScalingRatio());
   }

   @Test
   void ovalRenderDoesNotLeaveScalingRatio() throws Exception {
      Viewsheet vs = new Viewsheet();
      OvalVSAssembly oval = new OvalVSAssembly(vs, "Oval1");
      oval.setPixelSize(new Dimension(40, 40));
      vs.addAssembly(oval);

      render(vs, "Oval1");

      assertUnscaled(oval.getScalingRatio());
   }

   @Test
   void previousRatioIsRestoredNotReset() throws Exception {
      Viewsheet vs = new Viewsheet();
      OvalVSAssembly oval = new OvalVSAssembly(vs, "Oval1");
      oval.setPixelSize(new Dimension(40, 40));
      oval.setScalingRatio(new DimensionD(2, 3));
      vs.addAssembly(oval);

      render(vs, "Oval1");

      assertEquals(2.0, oval.getScalingRatio().getWidth(), 0.0001);
      assertEquals(3.0, oval.getScalingRatio().getHeight(), 0.0001);
   }

   private void assertUnscaled(DimensionD ratio) {
      assertEquals(1.0, ratio.getWidth(), 0.0001);
      assertEquals(1.0, ratio.getHeight(), 0.0001);
   }

   private void render(Viewsheet vs, String name) throws Exception {
      ViewsheetSandbox box = mock(ViewsheetSandbox.class);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(box));
      when(rvs.getID()).thenReturn("rvs1");

      AssemblyImageService svc = new AssemblyImageService(
         mock(ViewsheetService.class), mock(BinaryTransferService.class));

      // 800x600 canvas, deliberately different from the assembly's own pixel size
      svc.processGetAssemblyImage(rvs, name, 800, 600, 800, 600, null, 0, 0, 0,
                                  TestPrincipals.user("alice", "host-org"), false, false);
   }
}
