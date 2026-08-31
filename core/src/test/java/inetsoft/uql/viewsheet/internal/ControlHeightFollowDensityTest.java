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
package inetsoft.uql.viewsheet.internal;

import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.asset.internal.AssetUtil;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the four form-input types that bypass the base chrome hook (Spinner, ComboBox,
 * CheckBox, TextInput) each seed a density-derived height, exactly once, only when marked
 * modern and only while still at their own type's legacy default dimension. seedChromeDefaults()
 * is called a second time here with an explicit VizContext, the same way Modernize (and, for a
 * freshly created assembly, the two-arg AbstractVSAssembly constructor's later mark stamp) call
 * it again after construction - see VSAssemblyInfo.seedChromeDefaults()'s own Javadoc.
 *
 * Also verifies the reverse direction: VizModernizeUtil.revert() clears the mark and calls
 * seedChromeDefaults() again with ctx.modern == false, so height must go back to the legacy
 * default too, the same way round corner already does - see VSDensityDefaults.isControlHeight().
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ControlHeightFollowDensityTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void spinnerHeightFollowsDensityWhenMarkedModern() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      SpinnerVSAssemblyInfo info = new SpinnerVSAssemblyInfo();
      assertEquals(AssetUtil.defh, info.getPixelSize().height, "legacy default before marking");

      info.seedChromeDefaults(VizContext.of(VizMark.MODERN_LIGHT));

      assertEquals(30, info.getPixelSize().height, "comfortable control height after marking");
   }

   @Test
   void spinnerHeightIsUnaffectedWhenUnmarked() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      SpinnerVSAssemblyInfo info = new SpinnerVSAssemblyInfo();

      info.seedChromeDefaults(VizContext.of((VizMark) null));

      assertEquals(AssetUtil.defh, info.getPixelSize().height, "legacy stays legacy when unmarked");
   }

   @Test
   void spinnerHeightIsLeftAloneWhenAlreadyResized() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      SpinnerVSAssemblyInfo info = new SpinnerVSAssemblyInfo();
      info.setPixelSize(new Dimension(info.getPixelSize().width, 40));

      info.seedChromeDefaults(VizContext.of(VizMark.MODERN_LIGHT));

      assertEquals(40, info.getPixelSize().height, "an author-resized spinner is never substituted");
   }

   @Test
   void spinnerHeightRestoresLegacyOnRevert() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      SpinnerVSAssemblyInfo info = new SpinnerVSAssemblyInfo();
      info.seedChromeDefaults(VizContext.of(VizMark.MODERN_LIGHT));
      assertEquals(30, info.getPixelSize().height, "modernized before revert");

      // VizModernizeUtil.revert() clears the mark and re-seeds with a non-modern context
      info.seedChromeDefaults(VizContext.of((VizMark) null));

      assertEquals(AssetUtil.defh, info.getPixelSize().height, "reverted back to legacy");
   }

   @Test
   void spinnerHeightAtACustomValueIsUnaffectedByRevert() {
      SpinnerVSAssemblyInfo info = new SpinnerVSAssemblyInfo();
      info.setPixelSize(new Dimension(info.getPixelSize().width, 40));

      info.seedChromeDefaults(VizContext.of((VizMark) null));

      assertEquals(40, info.getPixelSize().height, "a genuinely custom height is never reverted");
   }

   @Test
   void comboBoxHeightFollowsDensityWhenMarkedModern() {
      SreeEnv.setProperty("viewsheet.density", "compact");
      ComboBoxVSAssemblyInfo info = new ComboBoxVSAssemblyInfo();

      info.seedChromeDefaults(VizContext.of(VizMark.MODERN_LIGHT));

      assertEquals(28, info.getPixelSize().height);
   }

   @Test
   void textInputHeightFollowsDensityWhenMarkedModern() {
      SreeEnv.setProperty("viewsheet.density", "compact");
      TextInputVSAssemblyInfo info = new TextInputVSAssemblyInfo();

      info.seedChromeDefaults(VizContext.of(VizMark.MODERN_LIGHT));

      assertEquals(28, info.getPixelSize().height);
   }

   @Test
   void checkBoxHeightScalesByItsLegacyTwoRowRatio() {
      SreeEnv.setProperty("viewsheet.density", "compact");
      CheckBoxVSAssemblyInfo info = new CheckBoxVSAssemblyInfo();
      assertEquals(2 * AssetUtil.defh, info.getPixelSize().height, "legacy default before marking");

      info.seedChromeDefaults(VizContext.of(VizMark.MODERN_LIGHT));

      assertEquals(2 * 28, info.getPixelSize().height, "both rows step up together");
   }

   @Test
   void checkBoxHeightIsLeftAloneWhenAlreadyResized() {
      SreeEnv.setProperty("viewsheet.density", "compact");
      CheckBoxVSAssemblyInfo info = new CheckBoxVSAssemblyInfo();
      info.setPixelSize(new Dimension(info.getPixelSize().width, 60));

      info.seedChromeDefaults(VizContext.of(VizMark.MODERN_LIGHT));

      assertEquals(60, info.getPixelSize().height, "an author-resized checkbox is never substituted");
   }

   @Test
   void checkBoxHeightRestoresLegacyTwoRowRatioOnRevert() {
      SreeEnv.setProperty("viewsheet.density", "compact");
      CheckBoxVSAssemblyInfo info = new CheckBoxVSAssemblyInfo();
      info.seedChromeDefaults(VizContext.of(VizMark.MODERN_LIGHT));
      assertEquals(2 * 28, info.getPixelSize().height, "modernized before revert");

      info.seedChromeDefaults(VizContext.of((VizMark) null));

      assertEquals(2 * AssetUtil.defh, info.getPixelSize().height, "reverted back to legacy 2x ratio");
   }

   @Test
   void checkBoxHeightAtAnOddValueIsUnaffectedByRevert() {
      CheckBoxVSAssemblyInfo info = new CheckBoxVSAssemblyInfo();
      // odd height can never be 2 * a control height, so the %2==0 guard must reject it outright
      info.setPixelSize(new Dimension(info.getPixelSize().width, 61));

      info.seedChromeDefaults(VizContext.of((VizMark) null));

      assertEquals(61, info.getPixelSize().height, "an odd custom height is never reverted");
   }
}
