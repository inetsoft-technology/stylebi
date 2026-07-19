package inetsoft.web.wiz.service;

import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.vslayout.PrintLayout;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@Tag("core")
class WizPrintLayoutBuilderTest {
   private final WizPrintLayoutBuilder builder = new WizPrintLayoutBuilder();

   @Test
   void buildsA4PrintInfoInPortraitInches() {
      Viewsheet vs = mock(Viewsheet.class);
      PrintLayout layout = builder.build(vs, "a4", "Q39 Board", "Premium drives revenue.", List.of());
      assertEquals("A4 [210x297 mm]", layout.getPrintInfo().getPaperType());
      assertEquals("inches", layout.getPrintInfo().getUnit());
      assertFalse(layout.isHorizontalScreen()); // portrait
   }

   @Test
   void buildsLetterPrintInfo() {
      Viewsheet vs = mock(Viewsheet.class);
      PrintLayout layout = builder.build(vs, "LETTER", "Board", null, List.of());
      assertEquals("Letter [8.5x11 in]", layout.getPrintInfo().getPaperType());
   }

   @Test
   void rejectsUnknownPageSize() {
      Viewsheet vs = mock(Viewsheet.class);
      assertThrows(IllegalArgumentException.class,
         () -> builder.build(vs, "tabloid", "Board", null, List.of()));
   }

   @Test
   void headerAndFooterLayoutsAreEmpty() {
      Viewsheet vs = mock(Viewsheet.class);
      PrintLayout layout = builder.build(vs, "a4", "Board", null, List.of());
      assertTrue(layout.getHeaderLayouts().isEmpty());
      assertTrue(layout.getFooterLayouts().isEmpty());
   }
}
