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
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.asset.*;
import inetsoft.uql.viewsheet.TextVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.TextVSAssemblyInfo;
import inetsoft.web.service.BinaryTransferService;
import inetsoft.web.viewsheet.controller.AssemblyImageService;
import inetsoft.web.viewsheet.service.ExportInProgressException;
import inetsoft.web.viewsheet.service.VSExportService;
import inetsoft.web.wiz.WizControllerErrorHandler;
import inetsoft.web.wiz.pairing.TestPrincipals;
import inetsoft.web.wiz.pairing.WizAgentTestSupport;
import inetsoft.web.wiz.service.RenderNotReadyException;
import inetsoft.web.wiz.service.RenderWaitSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.lang.reflect.Field;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #81364: {@code get_viewsheet_image} with no target never returns an image for a dashboard
 * whose export costs more than the 2000 ms wait budget of
 * {@link ScriptImageService#getViewsheetImage}.
 *
 * <p>The harness: the real {@link ScriptImageService#getViewsheetImage} and
 * {@link RenderWaitSupport}, over a real {@link ViewsheetSandbox} whose root onLoad script
 * busy-waits {@code COST_MS}. The real {@code VSExportService} cannot run here (its PNG exporter
 * needs BatikSVGSupport, not on core's test classpath), so the export is a stand-in doing the
 * steps that matter in order: the real {@code beginExport} claim, the real
 * {@code ViewsheetSandbox.prepareForExport} (what {@code AbstractVSExporter.prepareSheet} calls),
 * a PNG, {@code endExport}. {@code prepareForExport} is instrumented by a subclass that counts
 * the call, records its stack and elapsed time, then runs the real thing.
 *
 * <p>{@link #aRetryAfterTheAbandonedExportFinishedConvergesOrTheFailureIsHonest} states the correct
 * post-fix behaviour and is RED until the bug is fixed; the other tests record measured facts
 * about the current behaviour (R*, C*, E* rows of the 81364 hypothesis tables).
 */
@WizAgentTestSupport
class ViewsheetImageSlowExport81364Test {
   /** Export cost, over the 2000 ms budget on every call. -Dbug81364.costMs overrides. */
   private static final long COST_MS = Long.getLong("bug81364.costMs", 2600L);
   private static final Principal USER = TestPrincipals.user("alice", "host-org");

   private final List<ViewsheetSandbox> boxes = new ArrayList<>();

   @AfterEach
   void dispose() {
      InFlightViewsheetExport.clear();
      boxes.forEach(ViewsheetSandbox::dispose);
      boxes.clear();
   }

   /** A sandbox that records every prepareForExport (count, stack, elapsed) around the real one. */
   private static final class Probe extends ViewsheetSandbox {
      final AtomicInteger prepares = new AtomicInteger();
      final AtomicInteger finished = new AtomicInteger();
      final List<StackTraceElement[]> stacks = new CopyOnWriteArrayList<>();
      final List<Long> elapsed = new CopyOnWriteArrayList<>();

      Probe(Viewsheet vs, AssetEntry entry) {
         super(vs, AbstractSheet.SHEET_RUNTIME_MODE, null, false, entry);
      }

      @Override
      public void prepareForExport() {
         prepares.incrementAndGet();
         stacks.add(Thread.currentThread().getStackTrace());
         long t0 = System.currentTimeMillis();

         try {
            super.prepareForExport();
         }
         finally {
            elapsed.add(System.currentTimeMillis() - t0);
         }
      }
   }

   private static final class Fixture {
      final Viewsheet vs;
      final Probe box;
      final RuntimeViewsheet rvs;
      final VSExportService export;
      final AtomicInteger exportCalls = new AtomicInteger();
      final AtomicInteger exportDone = new AtomicInteger();
      volatile long renderMs;
      final ScriptImageService svc;

      Fixture(Viewsheet vs, Probe box, RuntimeViewsheet rvs, VSExportService export,
              ScriptImageService svc)
      {
         this.vs = vs;
         this.box = box;
         this.rvs = rvs;
         this.export = export;
         this.svc = svc;
      }
   }

   /** onLoadMs of 0 means no onLoad script at all. */
   private Fixture fixture(long onLoadMs) throws Exception {
      return fixture(onLoadMs, "");
   }

   /** extraOnLoad runs after the busy wait, e.g. to write state that differs on every run. */
   private Fixture fixture(long onLoadMs, String extraOnLoad) throws Exception {
      Viewsheet vs = new Viewsheet();
      Field wsField = Viewsheet.class.getDeclaredField("ws");
      wsField.setAccessible(true);
      wsField.set(vs, new Worksheet());
      AssetEntry entry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET, "test/Bug81364", null);
      vs.setEntry(entry);
      vs.getViewsheetInfo().setScriptEnabled(true);

      if(onLoadMs > 0) {
         vs.getViewsheetInfo().setOnLoad(
            "var s = new Date().getTime(); while(new Date().getTime() - s < " + onLoadMs + ") {}" +
            extraOnLoad);
      }

      TextVSAssembly text = new TextVSAssembly(vs, "Text1");
      ((TextVSAssemblyInfo) text.getVSAssemblyInfo()).setText("hello");
      vs.addAssembly(text);

      Probe box = new Probe(vs, entry);
      boxes.add(box);

      RuntimeViewsheet rvs = spy(new RuntimeViewsheet());
      java.util.concurrent.atomic.AtomicReference<Viewsheet> current =
         new java.util.concurrent.atomic.AtomicReference<>(vs);
      doAnswer(inv -> current.get()).when(rvs).getViewsheet();
      doReturn(java.util.Optional.of(box)).when(rvs).getViewsheetSandbox();
      doReturn("rvs81364").when(rvs).getID();

      // The real VSExportService.doExportViewsheet cannot run in core's test classpath (its PNG
      // exporter needs BatikSVGSupport from utils/inetsoft-xml-formats), so the export is a stand-in
      // that performs the same steps that matter here, in the same order: the real beginExport
      // claim, the real ViewsheetSandbox.prepareForExport (what AbstractVSExporter.prepareSheet
      // calls), a PNG whose width encodes the Text1 caption it saw, and endExport in a finally.
      // Like VSExportService.doExportViewsheet(current=true) it runs under synchronized(rvs) on a
      // CLONE of the viewsheet that is swapped into rvs and the sandbox, and puts the original
      // back in a finally (review r3: onLoad's writes land on the clone, not on the original).
      VSExportService export = mock(VSExportService.class);
      ScriptImageService svc = new ScriptImageService(
         mock(AssemblyImageService.class), mock(BinaryTransferService.class), export);
      Fixture f = new Fixture(vs, box, rvs, export, svc);
      doAnswer(inv -> {
         f.exportCalls.incrementAndGet();
         VSExportService.beginExport(rvs, inv.getArgument(10));

         try {
            synchronized(rvs) {
               Viewsheet original = current.get();
               Viewsheet clone = original.clone();
               current.set(clone);
               box.setViewsheet(clone, false);

               try {
                  box.prepareForExport();
                  Thread.sleep(f.renderMs);
                  String caption = ((TextVSAssemblyInfo)
                     ((TextVSAssembly) clone.getAssembly("Text1")).getVSAssemblyInfo()).getText();
                  inetsoft.web.viewsheet.service.ExportResponse response = inv.getArgument(9);
                  response.getOutputStream().write(png(10 + caption.length(), 8));
               }
               finally {
                  box.setViewsheet(original, false);
                  current.set(original);
               }
            }

            return null;
         }
         finally {
            rvs.endExport();
            f.exportDone.incrementAndGet();
         }
      }).when(export).exportViewsheet(
         any(), anyInt(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(),
         any(), anyBoolean(), any(inetsoft.web.viewsheet.service.ExportResponse.class), any());
      return f;
   }

   private static void awaitDone(Fixture f, int exports) throws Exception {
      long deadline = System.currentTimeMillis() + 60_000;

      while(f.exportDone.get() < exports && System.currentTimeMillis() < deadline) {
         Thread.sleep(25);
      }

      assertTrue(f.exportDone.get() >= exports, "abandoned export did not finish");
   }

   private static ScriptImageService.ChartImage call(Fixture f) throws Exception {
      return f.svc.getViewsheetImage(f.rvs, null, null, USER);
   }

   private static String handlerText(Exception e) {
      WizControllerErrorHandler h = new WizControllerErrorHandler();
      ResponseEntity<Map<String, String>> r = e instanceof RenderNotReadyException rn
         ? h.handleRenderNotReady(rn)
         : h.handleExportInProgress((ExportInProgressException) e);
      return r.getBody().get("error");
   }

   /**
    * THE RED TEST. Export cost exceeds the budget on EVERY call. The agent retries three times,
    * each time after the previous (abandoned) export had finished. Correct behaviour: either a
    * retry converges to an image, or the failure is honest, i.e. it no longer tells the agent
    * "this is not a failure, and the same call should succeed once it is done" for a call that
    * cannot ever succeed. Today every call throws RenderNotReadyException(2, CHART) with that
    * promise and no call returns an image.
    *
    * <p>Rules out: a text-only change that keeps the promise, a larger gap/retry-after, caching
    * only the exception, and "keyed reuse" that is never actually consulted. It does NOT rule out
    * a plain budget raise above COST_MS (then the first call converges, which is legitimate for
    * this cost; run with -Dbug81364.costMs=12000 to defeat a small raise).
    */
   @Test
   void aRetryAfterTheAbandonedExportFinishedConvergesOrTheFailureIsHonest() throws Exception {
      Fixture f = fixture(COST_MS);
      List<String> log = new ArrayList<>();
      ScriptImageService.ChartImage image = null;
      Exception last = null;

      for(int i = 1; i <= 3 && image == null; i++) {
         long t0 = System.currentTimeMillis();

         try {
            image = call(f);
            log.add("call " + i + ": IMAGE after " + (System.currentTimeMillis() - t0) + " ms");
         }
         catch(RenderNotReadyException | ExportInProgressException e) {
            last = e;
            log.add("call " + i + ": " + e.getClass().getSimpleName() + " after " +
                    (System.currentTimeMillis() - t0) + " ms; exports started=" +
                    f.exportCalls.get() + " finished=" + f.exportDone.get() +
                    " onLoad prepares=" + f.box.prepares.get());
            // retry gap >= export cost: wait for the abandoned export to end, then call again
            awaitDone(f, f.exportCalls.get());
         }
      }

      if(image != null) {
         assertTrue(image.pngBytes().length > 0);
         return;
      }

      String text = handlerText(last);
      assertFalse(text.contains("same call should succeed"),
         "no call returned an image although each retry came after the previous export had " +
         "finished, and the failure text still promises convergence. Calls: " + log +
         ". Last text: " + text);
   }

   // ----------------------------------------------------------------- measurements

   /**
    * R1/R6/R2/R3: one real onLoad per export, from prepareSheet. After the fix the abandoned
    * export's PNG is reachable: the retry collects it, so 2 calls cost ONE export, not two.
    */
   @Test
   void measure_R1_R2_R3_C1_C8_theRetryCollectsTheAbandonedExportInsteadOfRestarting()
      throws Exception
   {
      Fixture f = fixture(COST_MS);
      assertThrows(RenderNotReadyException.class, () -> call(f));
      awaitDone(f, 1);
      ScriptImageService.ChartImage img = call(f);

      assertNotNull(img);
      assertEquals(1, f.exportCalls.get(), "the retry reused the first export");
      assertEquals(1, f.box.prepares.get(), "onLoad ran once for the two calls");
      assertTrue(f.box.elapsed.get(0) >= COST_MS - 100,
         "onLoad ran for real: " + f.box.elapsed);

      // consume-once: a third call is a new export, not a replay of the same PNG
      assertThrows(RenderNotReadyException.class, () -> call(f));
      assertEquals(2, f.exportCalls.get());
      awaitDone(f, 2);
   }

   /** A state change between the failing call and the retry must not be answered with the old PNG. */
   @Test
   void aStateChangeBeforeTheRetryDiscardsTheFinishedExport() throws Exception {
      Fixture f = fixture(COST_MS);
      assertThrows(RenderNotReadyException.class, () -> call(f));
      awaitDone(f, 1);
      ((TextVSAssemblyInfo) ((TextVSAssembly) f.vs.getAssembly("Text1")).getVSAssemblyInfo())
         .setText("changed after the first export finished");

      assertThrows(RenderNotReadyException.class, () -> call(f));
      assertEquals(2, f.exportCalls.get(), "the retry started a fresh export, not the stale PNG");
      awaitDone(f, 2);
      ScriptImageService.ChartImage img = call(f);
      assertEquals(10 + "changed after the first export finished".length(), img.width(),
         "the image shows the changed state");
   }

   /**
    * Review r1 finding 1 (baseline moved after onLoad by r2): a change that lands after onLoad
    * finished, while the export is still rendering, is detected.
    */
   @Test
   void aChangeWhileTheExportRendersMakesTheCollectingRetryStartOver() throws Exception {
      Fixture f = fixture(COST_MS);
      f.renderMs = 1500;
      assertThrows(RenderNotReadyException.class, () -> call(f));
      long deadline = System.currentTimeMillis() + 30_000;

      while(f.box.elapsed.isEmpty() && System.currentTimeMillis() < deadline) {
         Thread.sleep(10);
      }

      assertFalse(f.box.elapsed.isEmpty(), "onLoad did not finish");
      ((TextVSAssemblyInfo) ((TextVSAssembly) f.vs.getAssembly("Text1")).getVSAssemblyInfo())
         .setText("changed while the first export was rendering");
      awaitDone(f, 1);

      assertThrows(RenderNotReadyException.class, () -> call(f));
      assertEquals(2, f.exportCalls.get(), "the retry did not collect the possibly stale PNG");
      awaitDone(f, 2);
   }

   /**
    * Review r4 F1: a state change made after the export started and before a retry JOINS the
    * still-running export must not be answered with the old state; a fresh export starts.
    */
   @Test
   void aChangeBeforeTheRetryJoinsTheRunningExportStartsAFreshExport() throws Exception {
      Fixture f = fixture(COST_MS);
      assertThrows(RenderNotReadyException.class, () -> call(f));
      ((TextVSAssemblyInfo) ((TextVSAssembly) f.vs.getAssembly("Text1")).getVSAssemblyInfo())
         .setText("changed while the first export was running");
      assertEquals(0, f.exportDone.get(), "the first export must still be running at the join");

      ScriptImageService.ChartImage img = null;

      for(int i = 0; i < 8 && img == null; i++) {
         try {
            img = call(f);
         }
         catch(RenderNotReadyException e) {
            // the joined export finished stale and was dropped, or the fresh one is running
         }
      }

      assertNotNull(img, "a fresh export converged");
      assertEquals(10 + "changed while the first export was running".length(), img.width(),
         "never the old caption");
      assertEquals(2, f.exportCalls.get(), "a fresh export was started");
      awaitDone(f, 2);
   }

   /** Review r4 F1: joining a running export with no change in between shares that export. */
   @Test
   void joiningTheRunningExportWithNoChangeSharesIt() throws Exception {
      Fixture f = fixture(COST_MS);
      assertThrows(RenderNotReadyException.class, () -> call(f));
      assertEquals(0, f.exportDone.get(), "the first export must still be running at the join");
      ScriptImageService.ChartImage img = null;

      for(int i = 0; i < 8 && img == null; i++) {
         try {
            img = call(f);
         }
         catch(RenderNotReadyException e) {
         }
      }

      assertNotNull(img);
      assertEquals(10 + "hello".length(), img.width());
      assertEquals(1, f.exportCalls.get(), "one export served both calls");
      awaitDone(f, 1);
   }

   /** Review r4 F2: a digest that is null once (concurrent edit) is retried, not trusted. */
   @Test
   void aDigestThatIsNullOnceIsRetriedBeforeTheUnverifiedWindow() throws Exception {
      java.util.concurrent.atomic.AtomicInteger n = new java.util.concurrent.atomic.AtomicInteger();
      java.util.function.Function<RuntimeViewsheet, String> real = InFlightViewsheetExport.DIGEST;
      InFlightViewsheetExport.DIGEST = r -> n.getAndIncrement() == 1 ? null : real.apply(r);
      Fixture f = fixture(COST_MS);
      assertThrows(RenderNotReadyException.class, () -> call(f));
      awaitDone(f, 1);
      assertNotNull(call(f));
      assertTrue(n.get() >= 3, "the null digest was recomputed");
      assertEquals(1, f.exportCalls.get());
   }

   /** Review r1 finding 2: no digest means unknown freshness; serve briefly, then start fresh. */
   @Test
   void anUncomputableDigestServesTheFinishedExportOnlyWithinTheShortWindow() throws Exception {
      InFlightViewsheetExport.DIGEST = r -> null;
      Fixture f = fixture(COST_MS);
      assertThrows(RenderNotReadyException.class, () -> call(f));
      awaitDone(f, 1);
      assertNotNull(call(f), "converged without a digest");
      assertEquals(1, f.exportCalls.get());

      InFlightViewsheetExport.unverifiedTtlMs = 0;
      Fixture g = fixture(COST_MS);
      assertThrows(RenderNotReadyException.class, () -> g.svc.getViewsheetImage(
         g.rvs, null, null, TestPrincipals.user("bob", "host-org")));
      awaitDone(g, 1);
      Thread.sleep(20);
      assertThrows(RenderNotReadyException.class, () -> g.svc.getViewsheetImage(
         g.rvs, null, null, TestPrincipals.user("bob", "host-org")));
      assertEquals(2, g.exportCalls.get(), "past the short window the PNG is not served");
      awaitDone(g, 2);
   }

   /** Review note: a viewsheet parameter changes the digest although the XML does not. */
   @Test
   void aParameterChangeChangesTheDigest() throws Exception {
      Fixture f = fixture(0);
      String before = InFlightViewsheetExport.fingerprint(f.rvs);
      assertNotNull(before);
      f.box.getVariableTable().put("p81364", "x");
      assertNotEquals(before, InFlightViewsheetExport.fingerprint(f.rvs));
   }

   /**
    * Review r2: an onLoad that writes a different value on every run (a clock stamp in a
    * parameter and in an assembly) must not defeat the reuse: its own writes are part of the
    * baseline, so the retry after the abandoned export finished collects it.
    */
   @Test
   void anOnLoadThatChangesStateOnEveryRunStillConverges() throws Exception {
      Fixture f = fixture(COST_MS,
         "; parameter.stamp81364 = new Date().getTime(); Text1.text = 'v' + new Date().getTime();");
      ScriptImageService.ChartImage image = null;

      for(int i = 1; i <= 3 && image == null; i++) {
         try {
            image = call(f);
         }
         catch(RenderNotReadyException e) {
            awaitDone(f, f.exportCalls.get());
         }
      }

      assertNotNull(image, "no image within 3 calls; exports=" + f.exportCalls.get());
      assertEquals(1, f.exportCalls.get(), "the abandoned export was collected, not restarted");
   }

   /**
    * Review r3: the stand-in really swaps a clone in for the export, so onLoad's assembly write
    * goes to the clone and the original keeps its caption; the reuse still converges.
    */
   @Test
   void anOnLoadWriteOnTheCloneNeverReachesTheOriginalAndDoesNotDefeatTheReuse() throws Exception {
      Fixture f = fixture(COST_MS, "; Text1.text = 'written by onLoad';");
      assertThrows(RenderNotReadyException.class, () -> call(f));
      awaitDone(f, 1);
      assertEquals("hello", ((TextVSAssemblyInfo)
         ((TextVSAssembly) f.vs.getAssembly("Text1")).getVSAssemblyInfo()).getText(),
         "onLoad wrote to the original, so the stand-in is not mimicking the clone swap");
      assertNotNull(call(f));
      assertEquals(1, f.exportCalls.get());
   }

   /** A finished PNG nobody collects is dropped after the TTL. */
   @Test
   void anUncollectedExportExpires() throws Exception {
      long old = InFlightViewsheetExport.ttlMs;
      InFlightViewsheetExport.ttlMs = 50;

      try {
         Fixture f = fixture(COST_MS);
         assertThrows(RenderNotReadyException.class, () -> call(f));
         awaitDone(f, 1);
         Thread.sleep(150);
         assertThrows(RenderNotReadyException.class, () -> call(f));
         assertEquals(2, f.exportCalls.get(), "expired entry was not served");
         awaitDone(f, 2);
      }
      finally {
         InFlightViewsheetExport.ttlMs = old;
      }
   }

   /** C3: handler text for the three outcomes. */
   @Test
   void measure_C3_handlerText() {
      WizControllerErrorHandler h = new WizControllerErrorHandler();
      String chart = h.handleRenderNotReady(
         new RenderNotReadyException(2, RenderNotReadyException.Subject.CHART)).getBody()
         .get("error");
      String inProgress = h.handleExportInProgress(new ExportInProgressException(
         "Export in progress", inetsoft.util.log.LogLevel.INFO, false)).getBody().get("error");
      System.out.println("[81364 C3] CHART: " + chart + "\n[81364 C3] EXPORT_IN_PROGRESS: " +
                         inProgress);
      assertTrue(chart.contains("the same call should succeed"));
      assertTrue(inProgress.contains("the same call should succeed"));
   }

   private static byte[] png(int w, int h) throws Exception {
      java.awt.image.BufferedImage img =
         new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB);
      java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
      javax.imageio.ImageIO.write(img, "png", out);
      return out.toByteArray();
   }

   private static byte[] exportBytes(Fixture f) throws Exception {
      java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
      f.export.exportViewsheet(f.rvs, inetsoft.uql.viewsheet.FileFormatInfo.EXPORT_TYPE_PNG, true, false,
         true, false, false, null, false, new inetsoft.web.viewsheet.service.ExportResponse(baos),
         USER);
      return baos.toByteArray();
   }

   /**
    * exportpath 6 / C6 (proxy): does viewsheet state changing between two exports alter the
    * PNG? If yes, serving a retained finished PNG after such a change would be stale.
    */
   @Test
   void measure_staleness_stateChangeAltersOutput() throws Exception {
      Fixture f = fixture(0);
      byte[] a = exportBytes(f);
      byte[] a2 = exportBytes(f);
      ((TextVSAssemblyInfo) ((TextVSAssembly) f.vs.getAssembly("Text1")).getVSAssemblyInfo())
         .setText("a much longer changed caption");
      byte[] b = exportBytes(f);
      System.out.println("[81364 C6] png sizes: first=" + a.length + " repeat=" + a2.length +
                         " afterTextChange=" + b.length + "; repeat identical=" +
                         java.util.Arrays.equals(a, a2) + "; changed differs=" +
                         !java.util.Arrays.equals(a, b));
      assertFalse(java.util.Arrays.equals(a, b), "state change did not alter the exported image");
   }
}
