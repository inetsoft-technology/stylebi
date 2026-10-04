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
import inetsoft.sree.security.SecurityEngine;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.util.cachefs.BinaryTransfer;
import inetsoft.web.service.BinaryTransferService;
import inetsoft.web.viewsheet.controller.AssemblyImageService;
import inetsoft.web.viewsheet.service.ExportResponse;
import inetsoft.web.viewsheet.service.VSBookmarkService;
import inetsoft.web.viewsheet.service.VSExportService;
import inetsoft.web.wiz.WizControllerErrorHandler;
import inetsoft.web.wiz.pairing.*;
import inetsoft.web.wiz.script.ScriptImageService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.security.Principal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

/**
 * Bug #77597 finding #6, end to end through HTTP: the wiz {@code image} endpoint, the real
 * {@link ScriptImageService} and {@code RenderWaitSupport}, the real
 * {@link VSExportService#beginExport} claim and the real {@link WizControllerErrorHandler}.
 * Only the body of the export is stubbed, so the abandoned first render can be held open on a
 * latch. A retry while it holds the claim must answer 503 with {@code Retry-After}, not the
 * catch-all's 500 that says retrying will fail the same way.
 */
@WizAgentTestSupport
class ViewsheetImageExportInProgressTest {
   private static final String URL = "/api/wiz/v1/agent/viewsheet/tok/image";

   @Test
   void wholeSheetAndTableTargetRetriesAnswer503WhileTheAbandonedExportHoldsTheClaim()
      throws Exception
   {
      RuntimeViewsheet rvs = claimableViewsheet();
      CountDownLatch release = new CountDownLatch(1);
      CountDownLatch firstEnded = new CountDownLatch(1);
      AtomicInteger exports = new AtomicInteger();
      VSExportService exportService = mock(VSExportService.class);

      doAnswer(invocation -> {
         VSExportService.beginExport(rvs, invocation.getArgument(10));
         int n = exports.incrementAndGet();

         try {
            if(n == 1) {
               release.await(30, TimeUnit.SECONDS);
            }

            ExportResponse response = invocation.getArgument(9);
            response.getOutputStream().write(fakePng(400, 300));
            return null;
         }
         finally {
            rvs.endExport();

            if(n == 1) {
               firstEnded.countDown();
            }
         }
      }).when(exportService).exportViewsheet(
         any(), anyInt(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(),
         any(), anyBoolean(), any(ExportResponse.class), any());

      // a table target renders as a 1x1 placeholder, which falls back to the whole sheet
      BinaryTransfer transfer = mock(BinaryTransfer.class);
      AssemblyImageService assemblyImages = mock(AssemblyImageService.class);
      when(assemblyImages.processGetAssemblyImage(
         eq(rvs), anyString(), anyDouble(), anyDouble(), anyDouble(), anyDouble(),
         isNull(), eq(0), eq(0), eq(0), any(), eq(false), eq(true)))
         .thenReturn(new AssemblyImageService.ImageRenderResult(true, transfer, 1, 1));
      BinaryTransferService binary = mock(BinaryTransferService.class);
      when(binary.getData(transfer)).thenReturn(fakePng(1, 1));

      ScriptImageService images = new ScriptImageService(assemblyImages, binary, exportService);
      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
      when(sessions.resolve(eq("tok"), any())).thenReturn(rvs);

      MockMvc mvc = standaloneSetup(controller(sessions, images))
         .setControllerAdvice(new WizControllerErrorHandler())
         .build();
      Principal user = TestPrincipals.user("alice", "host-org");

      try {
         // 1st call: the export outlasts the 2s wait, the documented "not ready" 503
         MvcResult first = mvc.perform(get(URL).principal(user).accept(MediaType.APPLICATION_JSON))
            .andReturn();
         assertEquals(503, first.getResponse().getStatus(), body(first));
         assertTrue(body(first).contains("not ready"), body(first));

         // 2nd call (the agent's retry): the first export still holds the claim
         MvcResult retry = mvc.perform(get(URL).principal(user).accept(MediaType.APPLICATION_JSON))
            .andReturn();
         assertEquals(503, retry.getResponse().getStatus(), body(retry));
         assertEquals("2", retry.getResponse().getHeader("Retry-After"));
         assertTrue(body(retry).contains("EXPORT_IN_PROGRESS"), body(retry));
         assertTrue(body(retry).contains("Exporting Dashboard in progress"), body(retry));
         assertFalse(body(retry).contains("retrying will fail"), body(retry));

         // a table target falls back to the same whole-sheet export, so the same answer
         MvcResult table = mvc.perform(get(URL).param("target", "TableView1").principal(user)
                                          .accept(MediaType.APPLICATION_JSON))
            .andReturn();
         assertEquals(503, table.getResponse().getStatus(), body(table));
         assertTrue(body(table).contains("EXPORT_IN_PROGRESS"), body(table));
      }
      finally {
         release.countDown();
      }

      // once the abandoned export ends and releases the claim, the same call succeeds
      assertTrue(firstEnded.await(30, TimeUnit.SECONDS), "the first export did not end");
      MvcResult after = mvc.perform(get(URL).principal(user).accept(MediaType.APPLICATION_JSON))
         .andReturn();
      assertEquals(200, after.getResponse().getStatus(), body(after));
      assertTrue(body(after).contains("\"format\":\"png\""), body(after));
   }

   private static String body(MvcResult r) throws Exception {
      return r.getResponse().getContentAsString();
   }

   private static RuntimeViewsheet claimableViewsheet() {
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(vs, "TableView1");
      chart.setSourceInfo(new SourceInfo(SourceInfo.ASSET, null, "Table1"));
      vs.addAssembly(chart);

      RuntimeViewsheet rvs = spy(new RuntimeViewsheet());
      doReturn(vs).when(rvs).getViewsheet();
      return rvs;
   }

   private static byte[] fakePng(int w, int h) throws Exception {
      BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      ImageIO.write(img, "png", out);
      return out.toByteArray();
   }

   private static ViewsheetAssemblyAgentController controller(ViewsheetSessionService sessions,
                                                              ScriptImageService images)
   {
      SheetAgentFeature feature = mock(SheetAgentFeature.class);
      when(feature.isEnabled()).thenReturn(true);

      return new ViewsheetAssemblyAgentController(feature, mock(SheetJoinService.class),
         mock(SheetSessionService.class), sessions, mock(ViewsheetReadService.class),
         mock(ViewsheetEditService.class), mock(ViewsheetFormatService.class), images,
         mock(AssemblyPropertyService.class), mock(SheetPropertyService.class),
         mock(AssemblyHyperlinkService.class), mock(ChartElementService.class),
         mock(ChartRegionPropertyService.class), mock(ChartTargetLineService.class),
         mock(HierarchyDimensionService.class), mock(AssemblyConditionService.class),
         mock(AssemblyHighlightService.class), mock(DateComparisonService.class),
         mock(AssemblyConvertService.class), mock(SelectionRuntimeService.class),
         mock(CalendarDisplayService.class), mock(InputValueService.class),
         mock(AssemblyMaxModeService.class), mock(FormTableRowService.class),
         mock(ColumnOptionService.class), mock(ParameterCollectionService.class),
         mock(ParameterValueService.class),
         mock(inetsoft.analytic.composition.ViewsheetService.class),
         mock(SheetAgentBroadcastService.class), mock(SheetOpenService.class),
         mock(LayoutSessionService.class), mock(LayoutReadService.class),
         mock(PrintDeviceLayoutPropertyService.class), mock(LayoutMutationService.class),
         mock(LayoutUndoService.class), mock(VSBookmarkService.class), mock(VSExportService.class),
         mock(SecurityEngine.class),
         mock(inetsoft.web.composer.vs.dialog.ViewsheetPropertyDialogService.class));
   }
}
