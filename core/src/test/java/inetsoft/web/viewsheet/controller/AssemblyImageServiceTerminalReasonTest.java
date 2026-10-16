package inetsoft.web.viewsheet.controller;

import inetsoft.graph.data.DataSet;
import inetsoft.report.composition.graph.VGraphPair;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static inetsoft.web.viewsheet.controller.AssemblyImageService.ImageRenderResult.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Bug #78662: which unplotted graph pairs are terminal (waiting cannot help). */
@Tag("core")
class AssemblyImageServiceTerminalReasonTest {
   private static VGraphPair pair(boolean completed, boolean cancelled, boolean failed,
                                  boolean plotted, boolean hasData)
   {
      VGraphPair pair = mock(VGraphPair.class);
      when(pair.isCompleted()).thenReturn(completed);
      when(pair.isCancelled()).thenReturn(cancelled);
      when(pair.isFailed()).thenReturn(failed);
      when(pair.isPlotted()).thenReturn(plotted);
      when(pair.getData()).thenReturn(hasData ? mock(DataSet.class) : null);
      return pair;
   }

   @Test
   void nullAndInProgressAndCancelledAndFailedAndPlottedAreNotTerminal() {
      assertEquals(NOT_TERMINAL, AssemblyImageService.terminalReason(null, false));
      assertEquals(NOT_TERMINAL,
                   AssemblyImageService.terminalReason(pair(false, false, false, false, false), false));
      assertEquals(NOT_TERMINAL,
                   AssemblyImageService.terminalReason(pair(true, true, false, false, false), false));
      assertEquals(NOT_TERMINAL,
                   AssemblyImageService.terminalReason(pair(true, false, true, false, false), false));
      assertEquals(NOT_TERMINAL,
                   AssemblyImageService.terminalReason(pair(true, false, false, true, true), false));
   }

   @Test
   void mvPendingStaysRetryable() {
      assertEquals(NOT_TERMINAL,
                   AssemblyImageService.terminalReason(pair(true, false, false, false, false), true));
   }

   @Test
   void completedUnplottedSplitsOnWhetherThereWasAnyData() {
      assertEquals(TERMINAL_NO_GRAPH,
                   AssemblyImageService.terminalReason(pair(true, false, false, false, false), false));
      assertEquals(TERMINAL_NO_DATA,
                   AssemblyImageService.terminalReason(pair(true, false, false, false, true), false));
   }
}
