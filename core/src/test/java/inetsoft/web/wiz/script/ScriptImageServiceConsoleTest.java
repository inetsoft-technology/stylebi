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
import inetsoft.report.composition.WorksheetService;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.util.script.ScriptException;
import inetsoft.web.service.BinaryTransferService;
import inetsoft.web.viewsheet.controller.AssemblyImageService;
import inetsoft.web.viewsheet.service.ExportResponse;
import inetsoft.web.viewsheet.service.VSExportService;
import inetsoft.web.wiz.dispatch.ConsoleMessageBuffer;
import inetsoft.web.wiz.dispatch.ConsoleMessageCapture;
import inetsoft.web.wiz.pairing.TestPrincipals;
import inetsoft.web.wiz.pairing.WizAgentTestSupport;
import inetsoft.web.wiz.service.RenderNotReadyException;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #82316: an image render re-runs the assembly scripts, and a failure is recorded only into
 * a non-null {@code ASSET_EXCEPTIONS} list, which the image path never installed. So
 * {@code get_viewsheet_image} rendered a clean-looking chart and the failure was dropped.
 */
@WizAgentTestSupport
class ScriptImageServiceConsoleTest {
   private static final String FAILURE = "Script execution error in assembly: B5Chart" +
      "\nScript failed:\nReferenceError: noSuchFunctionB7 is not defined (line 1)";

   private final String runtimeId = "rt-" + UUID.randomUUID();

   private RuntimeViewsheet rvs() {
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(vs, "B5Chart");
      chart.setSourceInfo(new SourceInfo(SourceInfo.ASSET, null, "Table1"));
      vs.addAssembly(chart);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getID()).thenReturn(runtimeId);
      return rvs;
   }

   private ConsoleMessageBuffer.Snapshot read() {
      return ConsoleMessageCapture.buffer(runtimeId).read(null, 200, 0);
   }

   /** What the sandbox does when an assembly script fails: add to the list only when it exists. */
   private static void failTheScript() {
      List<Exception> list = WorksheetService.ASSET_EXCEPTIONS.get();

      if(list != null) {
         list.add(new ScriptException(FAILURE));
      }
   }

   /** The chart path, with the retry loop running the failing script on every attempt. */
   @Test
   void theChartRenderRetryLoopRecordsTheFailureOnce() throws Exception {
      RuntimeViewsheet rvs = rvs();
      AtomicInteger attempts = new AtomicInteger();
      AssemblyImageService imageService = mock(AssemblyImageService.class);
      when(imageService.processGetAssemblyImage(
         eq(rvs), anyString(), anyDouble(), anyDouble(), anyDouble(), anyDouble(),
         isNull(), eq(0), eq(0), eq(0), any(), eq(false), eq(true)))
         .thenAnswer(invocation -> {
            attempts.incrementAndGet();
            failTheScript();
            return new AssemblyImageService.ImageRenderResult(1);
         });

      ScriptImageService svc = new ScriptImageService(
         imageService, mock(BinaryTransferService.class), mock(VSExportService.class));

      assertThrows(RenderNotReadyException.class, () -> svc.getAssemblyImage(
         rvs, "B5Chart", null, null, TestPrincipals.user("alice", "host-org")));

      assertTrue(attempts.get() > 1, "the loop must have re-run the script: " + attempts);
      ConsoleMessageBuffer.Snapshot snapshot = read();
      assertEquals(1, snapshot.entries().size(), snapshot.entries().toString());
      assertEquals("INFO", snapshot.entries().get(0).type());
      assertEquals("B5Chart", snapshot.entries().get(0).assembly());
      assertNull(WorksheetService.ASSET_EXCEPTIONS.get(), "the list must not leak past the call");
   }

   /** The whole-sheet export runs on a virtual thread, so the scope has to be inside the task. */
   @Test
   void theWholeSheetImageRecordsAFailureRaisedOnTheExportThread() throws Exception {
      RuntimeViewsheet rvs = rvs();
      VSExportService exportService = mock(VSExportService.class);
      byte[] png = png();
      doAnswer(invocation -> {
         failTheScript();
         ExportResponse response = invocation.getArgument(9);
         response.getOutputStream().write(png);
         return null;
      }).when(exportService).exportViewsheet(
         any(), anyInt(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(),
         any(), anyBoolean(), any(ExportResponse.class), any());

      ScriptImageService svc = new ScriptImageService(
         mock(AssemblyImageService.class), mock(BinaryTransferService.class), exportService);

      svc.getViewsheetImage(rvs, null, null, TestPrincipals.user("alice", "host-org"));

      ConsoleMessageBuffer.Snapshot snapshot = read();
      assertEquals(1, snapshot.entries().size(), snapshot.entries().toString());
      assertEquals("INFO", snapshot.entries().get(0).type());
      assertTrue(snapshot.entries().get(0).message().contains("noSuchFunctionB7 is not defined"));
   }

   private static byte[] png() throws Exception {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      ImageIO.write(new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB), "png", out);
      return out.toByteArray();
   }
}
