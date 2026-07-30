package inetsoft.uql.viewsheet.graph.aesthetic;

import inetsoft.graph.aesthetic.CategoricalColorFrame;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.util.css.CSSDictionary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Color;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ColorPalettesModernTest {
   @BeforeEach
   void setup() {
      CSSDictionary.resetDictionaryCache();
   }

   @Test
   void modernPalettesAreRegistered() {
      Collection<String> names = ColorPalettes.getPaletteNames();
      assertTrue(names.contains("Modern"), "Modern palette must be declared in defaults.css");
      assertTrue(names.contains("Modern Dark"), "Modern Dark palette must be declared in defaults.css");
   }

   @Test
   void modernDeclaresFortyNonNullColors() {
      assertFullPalette(ColorPalettes.getPalette("Modern"));
      assertFullPalette(ColorPalettes.getPalette("Modern Dark"));
   }

   @Test
   void modernHeadMatchesSpec() {
      CategoricalColorFrame modern = ColorPalettes.getPalette("Modern");
      assertEquals(new Color(0x00D4E8), modern.getDefaultColor(0));
      assertEquals(new Color(0x64748B), modern.getDefaultColor(7));

      CategoricalColorFrame dark = ColorPalettes.getPalette("Modern Dark");
      assertEquals(new Color(0x22D3EE), dark.getDefaultColor(0));
      assertEquals(new Color(0x94A3B8), dark.getDefaultColor(7));
   }

   // Drift guard: the CSS tail and the Java spliceLegacy fallback must agree, or swapping between
   // them (which happens whenever the fallback triggers) would silently change rendered colors.
   @Test
   void tailMatchesLegacyPalette() {
      CategoricalColorFrame modern = ColorPalettes.getPalette("Modern");
      CategoricalColorFrame dark = ColorPalettes.getPalette("Modern Dark");

      for(int i = 8; i < CategoricalColorFrame.COLOR_PALETTE.length; i++) {
         assertEquals(CategoricalColorFrame.COLOR_PALETTE[i], modern.getDefaultColor(i),
                      "Modern index " + (i + 1) + " must match the legacy tail");
         assertEquals(CategoricalColorFrame.COLOR_PALETTE[i], dark.getDefaultColor(i),
                      "Modern Dark index " + (i + 1) + " must match the legacy tail");
      }
   }

   @Test
   void defaultPaletteIsUnchanged() {
      CategoricalColorFrame def = ColorPalettes.getPalette("Default");
      assertEquals(40, def.getColorCount());

      for(int i = 0; i < CategoricalColorFrame.COLOR_PALETTE.length; i++) {
         assertEquals(CategoricalColorFrame.COLOR_PALETTE[i], def.getDefaultColor(i));
      }
   }

   private void assertFullPalette(CategoricalColorFrame frame) {
      assertNotNull(frame);
      assertEquals(40, frame.getColorCount());

      for(int i = 0; i < 40; i++) {
         assertNotNull(frame.getDefaultColor(i), "index " + (i + 1) + " must not be null");
      }
   }
}
