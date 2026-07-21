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
package inetsoft.web.wiz.script;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.util.Tool;
import inetsoft.web.service.BinaryTransferService;
import inetsoft.web.viewsheet.controller.AssemblyImageService;
import inetsoft.web.wiz.pairing.PairingException;
import inetsoft.web.wiz.service.RenderNotReadyException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.security.Principal;

/**
 * Renders a chart assembly's current state to a PNG snapshot the agent can actually look at.
 *
 * <p>Reuses {@link AssemblyImageService#downloadAssemblyImage}'s underlying mechanism — the same
 * lightweight, already-open-runtime render path the browser itself uses for every on-screen chart
 * tile (via {@code GetImageController}) and that {@code WizVisualizationService} already uses for
 * the chart wizard's live preview/thumbnail. Deliberately does NOT use the full
 * {@code VSExportService} export pipeline (clones the whole viewsheet, force-refreshes every
 * assembly, writes a temp file) — confirmed overkill for "one chart's current render," and that
 * pipeline is only meant as an expensive fallback for assembly types this path doesn't support
 * (Crosstab/Table), which is out of scope here since only charts are addressable as script
 * targets anyway.</p>
 */
@Service
public class ScriptImageService {

   // Matches WizVisualizationService.DEFAULT_RENDER_WIDTH/HEIGHT — legible legends/axis labels
   // at a reasonable base64 token cost (~40-110K chars for an 800x600 PNG).
   private static final int DEFAULT_WIDTH = 800;
   private static final int DEFAULT_HEIGHT = 600;

   // Tighter than AssemblyImageService's browser-facing callers ever need — this response goes
   // into an LLM's context budget, not a monitor.
   private static final int MAX_DIMENSION = 1600;

   // Mirrors WizVisualizationService.RENDER_MAX_ATTEMPTS/RENDER_RETRY_SLEEP_MS exactly.
   private static final int RENDER_MAX_ATTEMPTS = 4;
   private static final long RENDER_RETRY_SLEEP_MS = 500;

   @Autowired
   public ScriptImageService(AssemblyImageService assemblyImageService,
                             BinaryTransferService binaryTransferService)
   {
      this.assemblyImageService = assemblyImageService;
      this.binaryTransferService = binaryTransferService;
   }

   public record ChartImage(byte[] pngBytes, boolean isPng, int width, int height) {}

   /**
    * @throws RenderNotReadyException if the chart's graph hasn't finished computing after
    *         {@value #RENDER_MAX_ATTEMPTS} retries — caller should map this to a retryable HTTP
    *         status, not treat it as a hard failure.
    * @throws PairingException if {@code assemblyName} doesn't exist or isn't a chart.
    */
   public ChartImage getChartImage(RuntimeViewsheet rvs, String assemblyName,
                                   Integer width, Integer height, Principal principal)
      throws Exception
   {
      Viewsheet vs = rvs.getViewsheet();

      if(vs == null) {
         throw new PairingException("Viewsheet not found in runtime");
      }

      Assembly assembly = vs.getAssembly(assemblyName);

      if(!(assembly instanceof ChartVSAssembly)) {
         throw new PairingException("\"" + assemblyName + "\" is not a chart assembly");
      }

      int w = clamp(width != null && width > 0 ? width : DEFAULT_WIDTH);
      int h = clamp(height != null && height > 0 ? height : DEFAULT_HEIGHT);
      String aid = Tool.byteEncode(assemblyName);

      AssemblyImageService.ImageRenderResult result = null;

      for(int attempt = 0; attempt < RENDER_MAX_ATTEMPTS; attempt++) {
         result = assemblyImageService.processGetAssemblyImage(
            rvs, aid, w, h, w, h, null, 0, 0, 0, principal, false, true);

         if(result == null || result.getRetryAfter() <= 0) {
            break;
         }

         Thread.sleep(RENDER_RETRY_SLEEP_MS);
      }

      if(result != null && result.getRetryAfter() > 0) {
         throw new RenderNotReadyException(result.getRetryAfter());
      }

      if(result == null || result.getImageData() == null) {
         throw new PairingException("Failed to render \"" + assemblyName + "\"");
      }

      byte[] bytes = binaryTransferService.getData(result.getImageData());

      if(bytes == null || bytes.length == 0) {
         throw new PairingException("Failed to render \"" + assemblyName + "\"");
      }

      // A 1x1 image is StyleBI's placeholder for assembly types downloadAssemblyImage doesn't
      // support (see AssemblyImageService's "avoid a broken image on browser" fallback) — not
      // expected here since we already required ChartVSAssembly above, but guard anyway rather
      // than silently handing the agent a useless 1x1 PNG.
      if(result.isPng()) {
         BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(bytes));

         if(decoded == null || decoded.getWidth() <= 1 || decoded.getHeight() <= 1) {
            throw new PairingException("\"" + assemblyName + "\" could not be rendered to an image");
         }
      }

      return new ChartImage(bytes, result.isPng(), result.getWidth(), result.getHeight());
   }

   private static int clamp(int value) {
      return Math.min(value, MAX_DIMENSION);
   }

   private final AssemblyImageService assemblyImageService;
   private final BinaryTransferService binaryTransferService;
}
