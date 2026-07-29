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
import inetsoft.uql.viewsheet.FileFormatInfo;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.util.Tool;
import inetsoft.web.service.BinaryTransferService;
import inetsoft.web.viewsheet.controller.AssemblyImageService;
import inetsoft.web.viewsheet.service.ExportResponse;
import inetsoft.web.viewsheet.service.VSExportService;
import inetsoft.web.wiz.pairing.PairingException;
import inetsoft.web.wiz.service.RenderNotReadyException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.Principal;

/**
 * Renders a viewsheet assembly — or the whole viewsheet — to a PNG snapshot the agent can
 * actually look at.
 *
 * <p>For assembly types {@link AssemblyImageService#downloadAssemblyImage} supports directly
 * (charts, gauges, thermometers, cylinders, sliding scales, images, shapes, group containers),
 * reuses that mechanism — the same lightweight, already-open-runtime render path the browser
 * itself uses for every on-screen assembly tile (via {@code GetImageController}) and that
 * {@code WizVisualizationService} already uses for the chart wizard's live preview/thumbnail.</p>
 *
 * <p>For types that path doesn't support (tables/crosstabs), falls back to rendering the whole
 * viewsheet instead of trying to crop the single assembly out of a full-page export. An earlier
 * version cropped to {@code assembly.getPixelOffset()}/{@code getPixelSize()} (mirroring
 * {@code WizVisualizationService.renderFallbackThumbnail}), but live testing found that those
 * values aren't guaranteed to agree with the exporter's own canvas-sizing/draw coordinates — the
 * exporter's {@code Viewsheet.getPreferredBounds()} can prefer a stale {@code layoutPosition}/
 * {@code layoutSize} left over from a Print Layout or device layout when sizing the canvas, while
 * the paint step always uses the raw pixel box, so the two can silently disagree and produce a
 * crop of the wrong region. Whole-viewsheet rendering doesn't compute any position of its own —
 * it's just what the exporter actually painted — so it has no equivalent failure mode. (The same
 * crop math is used by {@code WizVisualizationService.renderFallbackThumbnail}, which likely has
 * the same latent bug; out of scope to fix here.)</p>
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
                             BinaryTransferService binaryTransferService,
                             VSExportService vsExportService)
   {
      this.assemblyImageService = assemblyImageService;
      this.binaryTransferService = binaryTransferService;
      this.vsExportService = vsExportService;
   }

   /**
    * @param note non-null only when the requested assembly couldn't be rendered directly and this
    *        is a whole-viewsheet image instead — the caller should surface this to the user so
    *        they understand why the image shows more than just the assembly they asked for.
    */
   public record ChartImage(byte[] pngBytes, boolean isPng, int width, int height, String note) {}

   /**
    * @throws RenderNotReadyException if the graph hasn't finished computing after
    *         {@value #RENDER_MAX_ATTEMPTS} retries — caller should map this to a retryable HTTP
    *         status, not treat it as a hard failure.
    * @throws PairingException if {@code assemblyName} doesn't exist or can't be rendered at all.
    */
   public ChartImage getAssemblyImage(RuntimeViewsheet rvs, String assemblyName,
                                      Integer width, Integer height, Principal principal)
      throws Exception
   {
      Viewsheet vs = rvs.getViewsheet();

      if(vs == null) {
         throw new PairingException("Viewsheet not found in runtime");
      }

      Assembly assembly = vs.getAssembly(assemblyName);

      if(assembly == null) {
         throw new PairingException("No such assembly \"" + assemblyName + "\"");
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
      // support directly (tables/crosstabs) — fall back to rendering the whole viewsheet instead
      // of guessing a crop rectangle (see class doc for why cropping isn't reliable here).
      if(result.isPng()) {
         BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(bytes));

         if(decoded == null || decoded.getWidth() <= 1 || decoded.getHeight() <= 1) {
            ChartImage whole = getViewsheetImage(rvs, width, height, principal);
            return new ChartImage(whole.pngBytes(), whole.isPng(), whole.width(), whole.height(),
               "\"" + assemblyName + "\" can't be rendered on its own — showing the whole " +
               "viewsheet instead.");
         }
      }

      return new ChartImage(bytes, result.isPng(), result.getWidth(), result.getHeight(), null);
   }

   /**
    * Renders the whole viewsheet — every visible assembly, composed as it actually looks — to a
    * single PNG. There's no lightweight single-call equivalent to
    * {@link AssemblyImageService#downloadAssemblyImage} for "the whole sheet" (that path is
    * inherently per-assembly), so this always uses the full export pipeline. Acceptable here since
    * this is an explicit, occasional request, not something called after every script edit.
    */
   public ChartImage getViewsheetImage(RuntimeViewsheet rvs, Integer width, Integer height,
                                       Principal principal)
      throws Exception
   {
      int w = clamp(width != null && width > 0 ? width : DEFAULT_WIDTH);
      int h = clamp(height != null && height > 0 ? height : DEFAULT_HEIGHT);

      byte[] pngBytes = exportViewsheetToPng(rvs, principal);
      BufferedImage full = decodePng(pngBytes, "the viewsheet");
      BufferedImage scaled = scaleToFit(full, w, h);
      byte[] encoded = encodePng(scaled, "the viewsheet");

      return new ChartImage(encoded, true, scaled.getWidth(), scaled.getHeight(), null);
   }

   /**
    * The exact call {@code WizVisualizationService.renderFallbackThumbnail} uses to export the
    * whole live viewsheet to an in-memory PNG: {@code match=true} for pixel-accurate geometry,
    * {@code current=true} because with no bookmarks and {@code current=false} the exporter's
    * {@code write()} produces 0 bytes.
    */
   private byte[] exportViewsheetToPng(RuntimeViewsheet rvs, Principal principal) throws Exception {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      vsExportService.exportViewsheet(rvs, FileFormatInfo.EXPORT_TYPE_PNG,
         true, false, true, false, false, null, false,
         new ExportResponse(baos), principal);
      byte[] bytes = baos.toByteArray();

      if(bytes.length == 0) {
         throw new PairingException("Failed to export the viewsheet");
      }

      return bytes;
   }

   private static BufferedImage decodePng(byte[] bytes, String label) throws Exception {
      BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));

      if(image == null) {
         throw new PairingException("Failed to decode the exported image for \"" + label + "\"");
      }

      return image;
   }

   private static byte[] encodePng(BufferedImage image, String label) throws Exception {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      ImageIO.write(image, "PNG", out);
      byte[] bytes = out.toByteArray();

      if(bytes.length == 0) {
         throw new PairingException("Failed to encode the rendered image for \"" + label + "\"");
      }

      return bytes;
   }

   /** Downscales (never upscales) to fit within {@code maxWidth}x{@code maxHeight}, preserving aspect ratio. */
   private static BufferedImage scaleToFit(BufferedImage image, int maxWidth, int maxHeight) {
      if(image.getWidth() <= maxWidth && image.getHeight() <= maxHeight) {
         return image;
      }

      double scale = Math.min((double) maxWidth / image.getWidth(), (double) maxHeight / image.getHeight());
      int w = Math.max(1, (int) (image.getWidth() * scale));
      int h = Math.max(1, (int) (image.getHeight() * scale));
      BufferedImage scaled = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = scaled.createGraphics();

      try {
         g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
         g.drawImage(image, 0, 0, w, h, null);
      }
      finally {
         g.dispose();
      }

      return scaled;
   }

   private static int clamp(int value) {
      return Math.min(value, MAX_DIMENSION);
   }

   private final AssemblyImageService assemblyImageService;
   private final BinaryTransferService binaryTransferService;
   private final VSExportService vsExportService;
}
