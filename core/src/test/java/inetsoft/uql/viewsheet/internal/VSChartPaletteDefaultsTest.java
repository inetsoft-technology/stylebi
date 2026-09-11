package inetsoft.uql.viewsheet.internal;

import inetsoft.graph.aesthetic.CategoricalColorFrame;
import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.graph.aesthetic.ColorPalettes;
import inetsoft.util.css.CSSDictionary;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Color;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class VSChartPaletteDefaultsTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      VSChartPaletteDefaults.clearMemo();
      CSSDictionary.resetDictionaryCache();
   }

   @Test
   void gateOffReturnsFalseAndLeavesPaletteUntouched() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "false");
      assertFalse(VizContext.ofGate().modern);

      CategoricalColorFrame frame = new CategoricalColorFrame();
      VSChartPaletteDefaults.applyModernPalette(frame, VizContext.ofGate());
      // gate off => still the legacy head color
      assertEquals(CategoricalColorFrame.COLOR_PALETTE[0], frame.getColor(0));
   }

   @Test
   void gateOnSwapsToModernHeadButKeepsLegacyTail() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      assertTrue(VizContext.ofGate().modern);

      Color[] modern = VSChartPaletteDefaults.modernPalette();
      assertEquals(40, modern.length, "8 modern + 32 legacy tail = 40");
      assertEquals(new Color(0x0490FF), modern[0]);
      assertEquals(new Color(0x8ED604), modern[7]);
      // index 9+ preserves the legacy tail unchanged
      assertEquals(CategoricalColorFrame.COLOR_PALETTE[8], modern[8]);
      assertEquals(CategoricalColorFrame.COLOR_PALETTE[39], modern[39]);

      CategoricalColorFrame frame = new CategoricalColorFrame();
      VSChartPaletteDefaults.applyModernPalette(frame, VizContext.ofGate());
      assertEquals(new Color(0x0490FF), frame.getColor(0));
      assertEquals(new Color(0xFF5A35), frame.getColor(1));
   }

   // Regression: the value-based render path (getColor(Object)) resolves through the cached
   // unusedColors list, which is built lazily from defaultColors. If the frame was already used
   // (cache warm) before the palette swap, the swap must still take effect — setDefaultColors must
   // invalidate the cache. Without that, marks render the stale legacy palette while the color
   // dropdown (which reads defaultColors directly) shows modern.
   @Test
   void swapTakesEffectOnValuePathEvenWhenCacheAlreadyWarm() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");

      CategoricalColorFrame warmed = new CategoricalColorFrame();
      warmed.init("A", "B", "C");
      warmed.getColor("A");                       // warm the unusedColors cache from legacy
      VSChartPaletteDefaults.applyModernPalette(warmed, VizContext.ofGate());

      CategoricalColorFrame fresh = new CategoricalColorFrame();
      fresh.init("A", "B", "C");
      VSChartPaletteDefaults.applyModernPalette(fresh, VizContext.ofGate());   // never warmed

      // a warmed-then-swapped frame must render identically to a fresh-swapped frame — i.e. the
      // stale cache did not poison the render. Both go through the same brightness processing.
      assertEquals(fresh.getColor("A"), warmed.getColor("A"));
      assertEquals(fresh.getColor("B"), warmed.getColor("B"));
   }

   @Test
   void darkPaletteSwapsToDarkHeadKeepsLegacyTail() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      SreeEnv.setProperty("viewsheet.darkMode", "true");

      Color[] dark = VSChartPaletteDefaults.darkPalette();
      assertEquals(40, dark.length, "8 dark + 32 legacy tail = 40");
      assertEquals(new Color(0x4FA5FF), dark[0]);
      assertEquals(new Color(0x9FEB28), dark[7]);
      assertEquals(CategoricalColorFrame.COLOR_PALETTE[8], dark[8]);
      assertEquals(CategoricalColorFrame.COLOR_PALETTE[39], dark[39]);

      CategoricalColorFrame frame = new CategoricalColorFrame();
      VSChartPaletteDefaults.applyModernPalette(frame, VizContext.ofGate());
      assertEquals(new Color(0x4FA5FF), frame.getColor(0));
      assertEquals(new Color(0xFF8367), frame.getColor(1));
   }

   @Test
   void darkInertWithoutModern() {
      // dark set but modern off => palette untouched (legacy head)
      SreeEnv.setProperty("viewsheet.modernVisualization", "false");
      SreeEnv.setProperty("viewsheet.darkMode", "true");
      CategoricalColorFrame frame = new CategoricalColorFrame();
      VSChartPaletteDefaults.applyModernPalette(frame, VizContext.ofGate());
      assertEquals(CategoricalColorFrame.COLOR_PALETTE[0], frame.getColor(0));
   }

   @Test
   void spliceLegacyKeepsHeadAndTail() {
      Color[] head = { new Color(0x010203), new Color(0x040506) };
      Color[] result = VSChartPaletteDefaults.spliceLegacy(head);

      assertEquals(40, result.length);
      assertEquals(new Color(0x010203), result[0]);
      assertEquals(new Color(0x040506), result[1]);
      assertEquals(CategoricalColorFrame.COLOR_PALETTE[2], result[2]);
      assertEquals(CategoricalColorFrame.COLOR_PALETTE[39], result[39]);
   }

   @Test
   void fromFrameCopiesCompletePalette() {
      CategoricalColorFrame frame = new CategoricalColorFrame();
      Color[] declared = new Color[40];
      Arrays.fill(declared, new Color(0x123456));
      frame.setDefaultColors(declared);

      Color[] result = VSChartPaletteDefaults.fromFrame(frame, MODERN_HEAD_FIXTURE);

      assertEquals(40, result.length);
      assertEquals(new Color(0x123456), result[0]);
      assertEquals(new Color(0x123456), result[39]);
   }

   @Test
   void fromFrameFallsBackWhenFrameIsNull() {
      Color[] result = VSChartPaletteDefaults.fromFrame(null, MODERN_HEAD_FIXTURE);
      assertArrayEquals(VSChartPaletteDefaults.spliceLegacy(MODERN_HEAD_FIXTURE), result);
   }

   @Test
   void fromFrameFallsBackWhenPaletteIsShort() {
      CategoricalColorFrame frame = new CategoricalColorFrame();
      frame.setDefaultColors(new Color[] { new Color(0x111111), new Color(0x222222) });

      Color[] result = VSChartPaletteDefaults.fromFrame(frame, MODERN_HEAD_FIXTURE);

      assertArrayEquals(VSChartPaletteDefaults.spliceLegacy(MODERN_HEAD_FIXTURE), result);
   }

   // A format.css declaring only indices 1-8 and 40 yields a 40-length array with null holes.
   // A null reaching Graphics.setColor would NPE mid-render, so this must fall back wholesale.
   @Test
   void fromFrameFallsBackWhenPaletteHasNullHole() {
      CategoricalColorFrame frame = new CategoricalColorFrame();
      Color[] declared = new Color[40];
      Arrays.fill(declared, new Color(0x123456));
      declared[11] = null;
      frame.setDefaultColors(declared);

      Color[] result = VSChartPaletteDefaults.fromFrame(frame, MODERN_HEAD_FIXTURE);

      assertArrayEquals(VSChartPaletteDefaults.spliceLegacy(MODERN_HEAD_FIXTURE), result);
   }

   private static final Color[] MODERN_HEAD_FIXTURE = {
      new Color(0x0490FF), new Color(0xFF5A35), new Color(0x241C4F), new Color(0x03D9B3),
      new Color(0x9A2DDC), new Color(0xFFB020), new Color(0xE5197E), new Color(0x8ED604)
   };

   @Test
   void modernPaletteResolvesFromCss() {
      Color[] modern = VSChartPaletteDefaults.modernPalette();

      assertEquals(40, modern.length);
      assertEquals(new Color(0x0490FF), modern[0]);
      assertEquals(new Color(0x8ED604), modern[7]);
      assertEquals(CategoricalColorFrame.COLOR_PALETTE[8], modern[8]);
      assertEquals(CategoricalColorFrame.COLOR_PALETTE[39], modern[39]);
   }

   @Test
   void darkPaletteResolvesFromCss() {
      Color[] dark = VSChartPaletteDefaults.darkPalette();

      assertEquals(40, dark.length);
      assertEquals(new Color(0x4FA5FF), dark[0]);
      assertEquals(new Color(0x9FEB28), dark[7]);
      assertEquals(CategoricalColorFrame.COLOR_PALETTE[8], dark[8]);
   }

   // The memo must not hand out a shared array, or one caller mutating it would corrupt every
   // subsequent chart in the org.
   @Test
   void resolvedPaletteIsACopy() {
      Color[] first = VSChartPaletteDefaults.modernPalette();
      first[0] = Color.MAGENTA;

      Color[] second = VSChartPaletteDefaults.modernPalette();

      assertEquals(new Color(0x0490FF), second[0]);
   }

   @Test
   void activePaletteFollowsDarkMode() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      assertEquals(new Color(0x0490FF), VSChartPaletteDefaults.activePalette(VizContext.ofGate())[0]);

      SreeEnv.setProperty("viewsheet.darkMode", "true");
      assertEquals(new Color(0x4FA5FF), VSChartPaletteDefaults.activePalette(VizContext.ofGate())[0]);
   }

   @Test
   void pickerPaletteIsLegacyWhenGateOff() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "false");

      Color[] picker = VSChartPaletteDefaults.pickerPalette(VizContext.ofGate());

      assertEquals(40, picker.length);
      assertEquals(CategoricalColorFrame.COLOR_PALETTE[0], picker[0]);
      assertEquals(CategoricalColorFrame.COLOR_PALETTE[39], picker[39]);
   }

   @Test
   void pickerPaletteFollowsGateAndDarkMode() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      assertEquals(new Color(0x0490FF), VSChartPaletteDefaults.pickerPalette(VizContext.ofGate())[0]);

      SreeEnv.setProperty("viewsheet.darkMode", "true");
      assertEquals(new Color(0x4FA5FF), VSChartPaletteDefaults.pickerPalette(VizContext.ofGate())[0]);
   }

   @Test
   void seedPaletteIsTotalAcrossTheMark() {
      assertEquals(VSChartPaletteDefaults.modernPalette()[0],
                   VSChartPaletteDefaults.seedPalette(VizContext.of(VizMark.MODERN_LIGHT))[0],
                   "a marked context seeds the modern palette");
      assertEquals(VSChartPaletteDefaults.legacyPalette()[0],
                   VSChartPaletteDefaults.seedPalette(VizContext.of((VizMark) null))[0],
                   "an unmarked context seeds the classic legacy palette");
   }

   // Repeated calls with unchanged CSS must resolve to the same colors every time, but resolve()
   // clones on both a memo hit and a miss, so no caller can ever mutate the shared cached entry.
   @Test
   void repeatedResolveReturnsEqualButDistinctArrays() {
      Color[] first = VSChartPaletteDefaults.modernPalette();
      Color[] second = VSChartPaletteDefaults.modernPalette();

      assertArrayEquals(first, second);
      assertNotSame(first, second, "resolve() must clone on every call, even a memo hit");
   }

   // Reflects into VSChartPaletteDefaults' own memo field only - not CSSDictionary's - to prove
   // clearMemo() actually discards the cached entry rather than being a no-op. A colors-only
   // assertion could pass either way, since defaults.css and the fallback agree on every value.
   //
   // A companion case - an unchanged memo stamp resolves from the memo while a bumped CSS
   // timestamp forces a genuine re-resolve without calling clearMemo() - is not covered here.
   // CSSDictionary.getOrgScopedCSSLastModified throttles to a 10-second real-clock window via its
   // private ORG_LAST_CHECK/ORG_LAST_MODIFIED maps, and resetDictionaryCache() does not clear
   // them, so observing a stamp change would require either a sleep past that window or reflecting
   // into those private statics - both out of bounds here.
   @Test
   void clearMemoDiscardsTheCachedEntry() throws Exception {
      VSChartPaletteDefaults.modernPalette();

      Field memoField = VSChartPaletteDefaults.class.getDeclaredField("MEMO");
      memoField.setAccessible(true);
      Map<?, ?> memo = (Map<?, ?>) memoField.get(null);
      assertFalse(memo.isEmpty(), "a resolve() call must populate the memo");

      VSChartPaletteDefaults.clearMemo();
      assertTrue(memo.isEmpty(), "clearMemo() must discard every cached entry");

      Color[] resolved = VSChartPaletteDefaults.modernPalette();
      assertFalse(memo.isEmpty(), "the next resolve() call must repopulate the memo");
      assertEquals(new Color(0x0490FF), resolved[0]);
   }

   // Guards against defaults.css and the Java fallback drifting apart.
   @Test
   void cssHeadMatchesTheJavaFallback() throws Exception {
      assertHeadMatches("MODERN_HEAD", "Modern");
      assertHeadMatches("DARK_HEAD", "Modern Dark");
   }

   private void assertHeadMatches(String fieldName, String paletteName) throws Exception {
      Field field = VSChartPaletteDefaults.class.getDeclaredField(fieldName);
      field.setAccessible(true);
      Color[] head = (Color[]) field.get(null);
      CategoricalColorFrame css = ColorPalettes.getPalette(paletteName);

      assertEquals(8, head.length, fieldName + " must declare exactly the eight head colors");

      for(int i = 0; i < head.length; i++) {
         assertEquals(head[i], css.getDefaultColor(i),
                      paletteName + " index " + (i + 1) + " must match " + fieldName);
      }
   }

   @Test
   void hiddenNamesAreEmptyForAClassicChart() {
      assertTrue(VSChartPaletteDefaults.hiddenPaletteNames(VizContext.of((VizMark) null)).isEmpty(),
                 "a classic chart keeps every palette it has today");
   }

   @Test
   void hiddenNamesAreTheNineRampsForAModernChart() {
      Set<String> hidden = VSChartPaletteDefaults.hiddenPaletteNames(VizContext.of(VizMark.MODERN_LIGHT));

      assertEquals(Set.of("Pastel", "Heat 8", "Heat 16", "Heat 24",
                          "Blue", "Green", "Red", "Orange", "Gray"), hidden);
   }

   @Test
   void hiddenNamesAreTheSameUnderADarkMark() {
      assertEquals(VSChartPaletteDefaults.hiddenPaletteNames(VizContext.of(VizMark.MODERN_LIGHT)),
                   VSChartPaletteDefaults.hiddenPaletteNames(VizContext.of(VizMark.MODERN_DARK)),
                   "dark is a modifier of modern, not a different palette set");
   }

   // The five kept names must never appear in the hidden set.
   @Test
   void hiddenNamesNeverCoverTheKeptFive() {
      Set<String> hidden = VSChartPaletteDefaults.hiddenPaletteNames(VizContext.of(VizMark.MODERN_LIGHT));

      for(String kept : new String[]{ "Default", "Soft", "Modern", "Modern Dark", "Contrast" }) {
         assertFalse(hidden.contains(kept), kept + " must stay offered to a modern chart");
      }
   }

   @Test
   void hiddenNamesFollowTheGateWhenThereIsNoAssembly() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "false");
      assertTrue(VSChartPaletteDefaults.hiddenPaletteNames(VizContext.ofGate()).isEmpty());

      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      assertEquals(9, VSChartPaletteDefaults.hiddenPaletteNames(VizContext.ofGate()).size());
   }
}
