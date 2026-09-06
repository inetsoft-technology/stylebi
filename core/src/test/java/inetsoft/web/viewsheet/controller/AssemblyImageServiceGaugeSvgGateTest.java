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
import inetsoft.report.gui.viewsheet.VSFaceUtil;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.GaugeVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.GaugeVSAssemblyInfo;
import inetsoft.util.graphics.SVGSupport;
import inetsoft.web.service.BinaryTransferService;
import inetsoft.web.wiz.pairing.TestPrincipals;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Document;

import java.awt.Graphics2D;
import java.security.Principal;
import java.util.Optional;

import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Answers.RETURNS_MOCKS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * VBM-004: {@code AssemblyImageService.processGetAssemblyImage1}'s Gauge branch called
 * {@code getGaugeSVG} unconditionally, before checking the caller's {@code svg} flag (unlike the
 * Chart branch immediately above it, which correctly gates on {@code if(svg)}). Since
 * {@code getGaugeSVG} succeeds for any {@code DefaultVSGauge}-family face, {@code isPNG} came back
 * false for every Gauge render regardless of what the caller asked for, so
 * {@code get_viewsheet_image(target:"<gauge>")} (which requests {@code svg=false}) returned raw
 * SVG XML instead of a PNG.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome()
@Tag("core")
class AssemblyImageServiceGaugeSvgGateTest {
   // DefaultFullVSGauge face (full-circle) -- previously affected: getGaugeSVG succeeds for it,
   // so isPNG came back false even when svg=false was requested.
   private static final int FULL_CIRCLE_FACE = 10116;

   // BulletGraphGauge is the only face that is not a DefaultVSGauge subtype, so getGaugeSVG
   // already returned null for it (its own instanceof check), and it already rendered PNG
   // correctly even before this fix -- an "already worked" control case.
   private static final int BULLET_GRAPH_FACE = 90826;

   @Test
   void rendersPngWhenSvgNotRequested() throws Exception {
      AssemblyImageService.ImageRenderResult result = renderGauge(FULL_CIRCLE_FACE, false);

      assertTrue(result.isPng(),
                 "svg=false must return a PNG for a gauge, not silently fall back to SVG");
   }

   @Test
   void rendersSvgWhenSvgRequested() throws Exception {
      // The real SVG renderer (SVGSupport.getInstance()) resolves to inetsoft.util.graphics
      // .BatikSVGSupport by reflection at runtime; that class lives in the inetsoft-xml-formats
      // module, which itself depends on core, so core's own test scope can never add it as a
      // dependency without a reactor cycle. Fake just enough of SVGSupport for the gauge's SVG
      // draw path to complete without touching real Batik/Graphics2D internals.
      Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
      // Graphics.create() returns Graphics, not Graphics2D -- DefaultFullVSGauge casts its
      // result back to Graphics2D, so the mock must return itself rather than let a bare
      // RETURNS_MOCKS answer synthesize an unrelated Graphics mock that fails that cast.
      Graphics2D svgGraphics = mock(Graphics2D.class, RETURNS_MOCKS);
      when(svgGraphics.create()).thenReturn(svgGraphics);
      SVGSupport svgSupport = mock(SVGSupport.class);
      when(svgSupport.createSVGGraphics()).thenReturn(svgGraphics);
      when(svgSupport.getSVGGraphics(any(), any(), anyBoolean(), any(), anyDouble(), anyInt()))
         .thenReturn(svgGraphics);
      when(svgSupport.isSVGGraphics(any())).thenReturn(true);
      when(svgSupport.getSVGRootElement(any())).thenReturn(doc.createElement("g"));
      when(svgSupport.getSVGDocument(any())).thenReturn(doc);
      when(svgSupport.transcodeSVGImage(any())).thenReturn("<svg/>".getBytes());

      AssemblyImageService.ImageRenderResult result;

      try(MockedStatic<SVGSupport> svgSupportStatic = mockStatic(SVGSupport.class)) {
         svgSupportStatic.when(SVGSupport::getInstance).thenReturn(svgSupport);
         result = renderGauge(FULL_CIRCLE_FACE, true);
      }

      assertFalse(result.isPng(),
                  "svg=true must still return SVG -- the browser/on-screen-tile path must be unaffected");
   }

   @Test
   void bulletGraphGaugeAlreadyRenderedPngBeforeThisFix() throws Exception {
      AssemblyImageService.ImageRenderResult result = renderGauge(BULLET_GRAPH_FACE, false);

      assertTrue(result.isPng(),
                 "BulletGraphGauge isn't a DefaultVSGauge, so getGaugeSVG already returned null " +
                 "for it and it already rendered PNG correctly before this fix");
   }

   private AssemblyImageService.ImageRenderResult renderGauge(int face, boolean svg) throws Exception {
      Viewsheet vs = new Viewsheet();
      GaugeVSAssembly gauge = new GaugeVSAssembly(vs, "Gauge1");
      // VSGauge.getGauge(id) looks faces up keyed on id + VSFaceUtil.getCurrentThemeID(), so the
      // face set here must be pre-offset by the ambient theme for the lookup to hit the entry
      // gauge.xml actually registers under `face` (this test's Spring fixture theme is not "unknown").
      ((GaugeVSAssemblyInfo) gauge.getVSAssemblyInfo()).setFace(face - VSFaceUtil.getCurrentThemeID());
      vs.addAssembly(gauge);

      ViewsheetSandbox box = mock(ViewsheetSandbox.class);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(box));
      when(rvs.getID()).thenReturn("rvs1");

      AssemblyImageService svc = new AssemblyImageService(
         mock(ViewsheetService.class), mock(BinaryTransferService.class));

      Principal principal = TestPrincipals.user("alice", "host-org");

      return svc.processGetAssemblyImage(
         rvs, "Gauge1", 200, 200, 200, 200, null, 0, 0, 0, principal, svg, false);
   }
}
