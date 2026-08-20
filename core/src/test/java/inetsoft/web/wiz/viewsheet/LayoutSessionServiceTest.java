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

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.viewsheet.TextVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.vslayout.LayoutInfo;
import inetsoft.uql.viewsheet.vslayout.PrintInfo;
import inetsoft.uql.viewsheet.vslayout.PrintLayout;
import inetsoft.uql.viewsheet.vslayout.ViewsheetLayout;
import inetsoft.web.composer.vs.controller.VSLayoutService;
import inetsoft.web.wiz.pairing.TestPrincipals;
import inetsoft.web.wiz.pairing.WizAgentTestSupport;
import org.junit.jupiter.api.Test;

import java.awt.Point;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Phase 0 of the layout implementation plan (2026-08-20-layout-implementation.md): every later
 * layout tool resolves its runtime through this service, and none of them may start until the
 * Hazard-1 exit criterion below passes -- a layout edit must never mutate the paired session's
 * own (Master) {@code Viewsheet} as a side effect.
 *
 * <p>Mocks {@code RuntimeViewsheet} directly for identity/id-level behavior (getID, getEntry,
 * setOriginalID -- none of which this service's own tests need to be "real"), but uses REAL
 * {@code Viewsheet}/{@code LayoutInfo}/{@code PrintLayout}/{@code ViewsheetLayout} objects, since
 * those are what {@code AbstractLayout.apply(Viewsheet)} actually operates on and the whole point
 * of the exit-criterion test is observing what that real method does to real objects. A bare
 * {@code new RuntimeViewsheet()} has a working layout-undo-stack ({@code layoutPoints}/
 * {@code layoutPoint} are field-initialized, not constructor-initialized) but a {@code null box},
 * which makes the real {@code setViewsheet}/{@code setOriginalID} silently no-op -- so
 * {@code getViewsheet}/{@code setViewsheet} are stubbed with a mutable holder (real behavior,
 * simulated), while {@code resetLayoutUndoRedo}/{@code addLayoutCheckPoint}/
 * {@code getLayoutPointsSize} are left as genuine, unstubbed calls on the spy.
 */
@WizAgentTestSupport
class LayoutSessionServiceTest {
   private static final Principal AGENT = TestPrincipals.user("alice", "host-org");
   private static final String PRINT_LAYOUT = "Print Layout";

   /**
    * The Hazard-1 exit criterion -- first because it's what everything else exists to satisfy.
    *
    * <p>Mints a clone for a print layout, runs a mutation that moves the clone's own assembly and
    * rescales it (exactly what a real layout edit does, and exactly what would corrupt the
    * master's Master-view rendering if this service handed the mutation the wrong runtime), then
    * re-resolves the master INDEPENDENTLY (a fresh call to {@code ViewsheetSessionService.resolve},
    * not reusing anything the mutation touched) and asserts its assembly's layout position, pixel
    * position, and per-format scale font are byte-for-byte what they were before the call.
    */
   @Test
   void mutationNeverTouchesTheMasterViewsheet() throws Exception {
      Fixture fx = new Fixture();
      fx.installPrintLayout();

      Point masterLayoutPositionBefore = fx.masterText.getVSAssemblyInfo().getLayoutPosition();
      Point masterPixelOffsetBefore = fx.masterText.getPixelOffset();
      float masterFormatScaleBefore =
         fx.masterText.getVSAssemblyInfo().getFormat().getRScaleFont();

      fx.service.mutateLayout("tok1", AGENT, PRINT_LAYOUT, (clone, master, cloneRuntimeId, dispatcher) -> {
         // A real edit: move the clone's own copy of the assembly and rescale it. If this
         // service ever hands a mutation the master runtime instead of a genuine clone, this is
         // exactly the kind of call that would silently corrupt the human's Master view.
         Viewsheet cloneVs = clone.getViewsheet();
         TextVSAssembly cloneText = (TextVSAssembly) cloneVs.getAssembly("Text1");
         cloneText.setPixelOffset(new Point(999, 999));
         cloneText.getVSAssemblyInfo().setLayoutPosition(new Point(999, 999));
         cloneVs.setRScaleFont(0.25f);
      });

      // Re-resolve the master INDEPENDENTLY -- a fresh call into the mocked
      // ViewsheetSessionService, not anything handed to us by mutateLayout above.
      RuntimeViewsheet masterAfter = fx.viewsheetSessions.resolve("tok1", AGENT);
      TextVSAssembly masterTextAfter = (TextVSAssembly) masterAfter.getViewsheet().getAssembly("Text1");

      assertEquals(masterLayoutPositionBefore, masterTextAfter.getVSAssemblyInfo().getLayoutPosition(),
                   "the master's own assembly layout-position must be untouched");
      assertEquals(masterPixelOffsetBefore, masterTextAfter.getPixelOffset(),
                   "the master's own assembly pixel position must be untouched");
      assertEquals(masterFormatScaleBefore,
                   masterTextAfter.getVSAssemblyInfo().getFormat().getRScaleFont(),
                   "the master's own per-assembly RScaleFont must be untouched");
      assertEquals(fx.masterRScaleFontBefore, fx.masterRvs.getViewsheet().getRScaleFont(),
                   "the master's own top-level RScaleFont must be untouched");
   }

   /** Two calls for the same (sessionToken, layoutName) reuse one clone, not two. */
   @Test
   void reuseResolvesToTheSameCloneRuntimeIdAcrossCalls() throws Exception {
      Fixture fx = new Fixture();
      fx.installPrintLayout();

      List<String> seenRuntimeIds = new ArrayList<>();
      fx.service.mutateLayout("tok1", AGENT, PRINT_LAYOUT,
                               (clone, master, cloneRuntimeId, dispatcher) ->
                                  seenRuntimeIds.add(cloneRuntimeId));
      fx.service.mutateLayout("tok1", AGENT, PRINT_LAYOUT,
                               (clone, master, cloneRuntimeId, dispatcher) ->
                                  seenRuntimeIds.add(cloneRuntimeId));

      assertEquals(2, seenRuntimeIds.size());
      assertEquals(seenRuntimeIds.get(0), seenRuntimeIds.get(1), "must reuse, not mint a second clone");
      // Minted exactly once: openTemporaryViewsheet is stubbed with a single fixed return value
      // in the fixture, so a second, distinct call would have returned null and NPE'd instead of
      // silently passing -- this verify is the direct proof rather than relying on that as a trap.
      verify(fx.viewsheetService, times(1))
         .openTemporaryViewsheet(anyString(), any(AssetEntry.class), eq(AGENT));
      verify(fx.viewsheetService, never()).flushRuntimeSheet(anyString());
   }

   /**
    * Switching layouts for the same token flushes the old clone and reseeds the master's layout
    * undo/redo stack with one baseline checkpoint before the new layout's edit lands.
    */
   @Test
   void switchingLayoutsFlushesTheOldCloneAndResetsTheUndoStack() throws Exception {
      Fixture fx = new Fixture();
      fx.installNamedLayout("A");
      fx.installNamedLayout("B");

      List<String> sizesSeenDuringB = new ArrayList<>();
      fx.service.mutateLayout("tok1", AGENT, "A", (clone, master, cloneRuntimeId, dispatcher) -> {});
      String cloneIdForA = fx.lastMintedCloneId();

      assertNotNull(cloneIdForA);
      verify(fx.viewsheetService, never()).flushRuntimeSheet(anyString());

      fx.service.mutateLayout("tok1", AGENT, "B", (clone, master, cloneRuntimeId, dispatcher) ->
         sizesSeenDuringB.add(String.valueOf(fx.masterRvs.getLayoutPointsSize())));

      verify(fx.viewsheetService).flushRuntimeSheet(cloneIdForA);
      assertEquals(List.of("1"), sizesSeenDuringB,
                   "one baseline checkpoint must already be in place before B's edit runs");

      // A subsequent read against the flushed clone's id fails the way a flushed runtime does --
      // the fixture wires flushRuntimeSheet to break that id's stub, mirroring the real registry.
      assertThrows(IllegalStateException.class,
                   () -> fx.viewsheetService.getViewsheet(cloneIdForA, AGENT));
   }

   /** {@code disposeAll} flushes every clone open for one token without touching another's. */
   @Test
   void disposeAllFlushesOnlyItsOwnTokensClone() throws Exception {
      Fixture fx = new Fixture();
      fx.installPrintLayout();
      // A second token paired to the same master -- disposeAll's isolation is about the
      // sessionToken key in this service's own cache, not about which master a token points to.
      when(fx.viewsheetSessions.resolve(eq("tok2"), eq(AGENT))).thenReturn(fx.masterRvs);

      fx.service.mutateLayout("tok1", AGENT, PRINT_LAYOUT, (clone, master, cloneRuntimeId, dispatcher) -> {});
      String cloneIdTok1 = fx.lastMintedCloneId();
      fx.service.mutateLayout("tok2", AGENT, PRINT_LAYOUT, (clone, master, cloneRuntimeId, dispatcher) -> {});
      String cloneIdTok2 = fx.lastMintedCloneId();

      assertNotEquals(cloneIdTok1, cloneIdTok2);

      fx.service.disposeAll("tok1");

      verify(fx.viewsheetService).flushRuntimeSheet(cloneIdTok1);
      verify(fx.viewsheetService, never()).flushRuntimeSheet(cloneIdTok2);
   }

   /**
    * {@code disposeAll} alone only fires on an explicit {@code detach_sheet} call. A session that
    * instead goes idle past its TTL -- an agent that finishes and simply stops, or crashes --
    * is reaped by {@code SheetSessionService.evictExpired()}, which has no knowledge of this
    * class's own {@code clones} map. Without {@code evictOrphanedClones}, that path would leave a
    * live {@code RuntimeViewsheet} runtime cached forever under a token nothing will ever resolve
    * again -- a real leak, heavier than a small map entry.
    */
   @Test
   void evictOrphanedClonesFlushesACloneWhoseOwningSessionIsNoLongerLive() throws Exception {
      Fixture fx = new Fixture();
      fx.installPrintLayout();

      fx.service.mutateLayout("tok1", AGENT, PRINT_LAYOUT, (clone, master, cloneRuntimeId, dispatcher) -> {});
      String cloneId = fx.lastMintedCloneId();
      when(fx.viewsheetSessions.isSessionLive("tok1")).thenReturn(false);

      fx.service.evictOrphanedClones();

      verify(fx.viewsheetService).flushRuntimeSheet(cloneId);
   }

   /** The sweep must not flush a clone whose owning session is still alive and well. */
   @Test
   void evictOrphanedClonesLeavesALiveSessionsCloneAlone() throws Exception {
      Fixture fx = new Fixture();
      fx.installPrintLayout();

      fx.service.mutateLayout("tok1", AGENT, PRINT_LAYOUT, (clone, master, cloneRuntimeId, dispatcher) -> {});
      String cloneId = fx.lastMintedCloneId();
      when(fx.viewsheetSessions.isSessionLive("tok1")).thenReturn(true);

      fx.service.evictOrphanedClones();

      verify(fx.viewsheetService, never()).flushRuntimeSheet(cloneId);
   }

   /**
    * A read-triggered switch (the shape {@code LayoutReadService.get} produces via
    * {@link LayoutSessionService#resolveForRead}) must reset the master's layout undo/redo stack
    * just as a mutation-triggered switch does -- otherwise a later {@code layout_undo}/
    * {@code layout_redo} call on the newly-focused layout would consult stale checkpoints that
    * belong to whatever layout was previously focused (Hazard 2), and -- for a switch between a
    * print layout and a device layout specifically -- {@code updateVSLayouts} would try to cast a
    * stale {@code PrintLayout} checkpoint to {@code ViewsheetLayout} (or vice versa) and throw
    * {@code ClassCastException}. {@code resolveForRead} must NOT go on to seed a fresh baseline
    * checkpoint the way a mutation-triggered switch does, though -- a read must never manufacture
    * an undo step of its own.
    */
   @Test
   void aReadTriggeredSwitchResetsTheUndoStackWithoutSeedingABaseline() throws Exception {
      Fixture fx = new Fixture();
      fx.installNamedLayout("A");
      fx.installNamedLayout("B");

      fx.service.mutateLayout("tok1", AGENT, "A", (clone, master, cloneRuntimeId, dispatcher) -> {});
      assertEquals(2, fx.masterRvs.getLayoutPointsSize(),
                   "sanity check: mint seeds one baseline, then mutateLayout's own " +
                   "makeUndoable call always appends a second checkpoint after the mutation runs");

      RuntimeViewsheet cloneB = fx.service.resolveForRead("tok1", AGENT, "B");

      assertNotNull(cloneB);
      assertEquals(0, fx.masterRvs.getLayoutPointsSize(),
                   "a read-triggered switch to B must reset the stack, not merely leave A's " +
                   "checkpoint in place");
      assertEquals(-1, fx.masterRvs.getLayoutPoint());
   }

   /** An unknown layout name fails loud, before any clone is minted or cached. */
   @Test
   void mutateLayoutOnAnUnknownLayoutNameFailsLoud() throws Exception {
      Fixture fx = new Fixture();
      fx.installPrintLayout();

      IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
         () -> fx.service.mutateLayout("tok1", AGENT, "Does Not Exist",
                                        (clone, master, cloneRuntimeId, dispatcher) -> {}));

      assertTrue(thrown.getMessage().contains("Does Not Exist"), thrown.getMessage());
   }

   // ── fixture ───────────────────────────────────────────────────────────────

   /**
    * Wires one {@code LayoutSessionService} against a mocked {@code ViewsheetSessionService}/
    * {@code ViewsheetService} and a real {@code VSLayoutService}, backed by a real master
    * {@code Viewsheet} with one real {@code TextVSAssembly} ("Text1") and a real
    * {@code LayoutInfo}.
    */
   private static final class Fixture {
      final ViewsheetSessionService viewsheetSessions = mock(ViewsheetSessionService.class);
      final ViewsheetService viewsheetService = mock(ViewsheetService.class);
      final VSLayoutService vsLayoutService = new VSLayoutService();
      final LayoutSessionService service =
         new LayoutSessionService(viewsheetSessions, viewsheetService, vsLayoutService);

      final Viewsheet masterVs = new Viewsheet();
      final TextVSAssembly masterText = new TextVSAssembly(masterVs, "Text1");
      final RuntimeViewsheet masterRvs = spy(new RuntimeViewsheet());
      final float masterRScaleFontBefore = 2.5f;
      final AssetEntry masterEntry = mock(AssetEntry.class);

      private int cloneCounter;
      private String lastCloneId;

      Fixture() throws Exception {
         masterText.setPixelOffset(new Point(10, 20));
         masterVs.addAssembly(masterText);
         masterVs.setRScaleFont(masterRScaleFontBefore);

         doReturn(masterVs).when(masterRvs).getViewsheet();
         doReturn("master-1").when(masterRvs).getID();
         doReturn(masterEntry).when(masterRvs).getEntry();
         when(masterEntry.clone()).thenReturn(masterEntry);
         when(viewsheetSessions.resolve(eq("tok1"), eq(AGENT))).thenReturn(masterRvs);

         when(viewsheetService.openTemporaryViewsheet(anyString(), any(AssetEntry.class), eq(AGENT)))
            .thenAnswer(inv -> {
               String id = "clone-" + (++cloneCounter);
               lastCloneId = id;
               RuntimeViewsheet clone = spy(new RuntimeViewsheet());

               // A genuinely separate object graph, not master's -- mirrors what
               // openTemporaryViewsheet really does (reload the persisted asset fresh). Same
               // logical shape (an assembly named "Text1") so a mutation can address it by name,
               // but a distinct instance, so mutating it can never reach master's.
               Viewsheet initialCloneVs = new Viewsheet();
               TextVSAssembly cloneText = new TextVSAssembly(initialCloneVs, "Text1");
               cloneText.setPixelOffset(new Point(10, 20));
               initialCloneVs.addAssembly(cloneText);

               Viewsheet[] cloneVsHolder = { initialCloneVs };
               doAnswer(getVs -> cloneVsHolder[0]).when(clone).getViewsheet();
               doAnswer(setVs -> {
                  cloneVsHolder[0] = setVs.getArgument(0);
                  return null;
               }).when(clone).setViewsheet(any());
               doReturn(id).when(clone).getID();

               when(viewsheetService.getViewsheet(eq(id), eq(AGENT))).thenReturn(clone);
               return id;
            });

         // Mirrors a flushed runtime: once flushRuntimeSheet(id) is called, a subsequent
         // getViewsheet(id, ...) fails instead of quietly returning the disposed clone.
         doAnswer(inv -> {
            String flushedId = inv.getArgument(0);
            when(viewsheetService.getViewsheet(eq(flushedId), any()))
               .thenThrow(new IllegalStateException("runtime " + flushedId + " has been flushed"));
            return null;
         }).when(viewsheetService).flushRuntimeSheet(anyString());
      }

      /** Adds a real, empty {@code PrintLayout} named "Print Layout" to the master's LayoutInfo. */
      void installPrintLayout() {
         LayoutInfo info = new LayoutInfo();
         info.setPrintLayout(newEmptyPrintLayout());
         masterVs.setLayoutInfo(info);
      }

      /**
       * {@code PrintLayout}/{@code AbstractLayout}'s no-arg constructors leave
       * {@code printInfo}/{@code vsAssemblyLayouts} null -- fine for XML-loaded production
       * instances (the loader always populates them) but not for one built by hand here, where
       * {@code clone()}/{@code apply()} would NPE on the un-set fields.
       */
      private static PrintLayout newEmptyPrintLayout() {
         PrintLayout layout = new PrintLayout();
         layout.setPrintInfo(new PrintInfo("Letter", new inetsoft.graph.internal.DimensionD(8.5, 11),
                                            0.5f, 0.5f, 0.5f, 0.5f, "inches"));
         layout.setVSAssemblyLayouts(new ArrayList<>());
         return layout;
      }

      /** Adds a real, empty {@code ViewsheetLayout} named {@code name} to the master's LayoutInfo. */
      void installNamedLayout(String name) {
         LayoutInfo info = masterVs.getLayoutInfo();

         if(info == null) {
            info = new LayoutInfo();
            info.setPrintLayout(newEmptyPrintLayout());
            masterVs.setLayoutInfo(info);
         }

         ViewsheetLayout layout = new ViewsheetLayout();
         layout.setName(name);
         layout.setVSAssemblyLayouts(new ArrayList<>());
         List<ViewsheetLayout> layouts = new ArrayList<>(info.getViewsheetLayouts());
         layouts.add(layout);
         info.setViewsheetLayouts(layouts);
      }

      String lastMintedCloneId() {
         return lastCloneId;
      }
   }
}
