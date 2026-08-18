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
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.viewsheet.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class VizModernizeUtilTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
   }

   private void gateOn() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
   }

   private void gateOff() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "false");
   }

   /** A sheet as a legacy asset would load: nothing marked anywhere. */
   private Viewsheet legacySheet() {
      gateOff();
      Viewsheet vs = new Viewsheet();
      vs.addAssembly(new TextVSAssembly(vs, "Text1"));
      vs.addAssembly(new TableVSAssembly(vs, "Table1"));
      return vs;
   }

   @Test
   void aLegacySheetHasUnmarkedContent() {
      assertTrue(VizModernizeUtil.hasUnmarked(legacySheet()));
   }

   @Test
   void aFullyModernSheetHasNone() {
      gateOn();
      Viewsheet vs = new Viewsheet();
      vs.addAssembly(new TextVSAssembly(vs, "Text1"));
      assertFalse(VizModernizeUtil.hasUnmarked(vs));
   }

   @Test
   void modernizeStampsEveryUnmarkedAssemblyAndTheSheet() {
      Viewsheet vs = legacySheet();
      gateOn();

      assertEquals(3, VizModernizeUtil.modernize(vs), "two assemblies plus the sheet itself");

      assertEquals(VizMark.MODERN_LIGHT, vs.getVSAssemblyInfo().getVizMark());

      for(Assembly assembly : vs.getAssemblies(true)) {
         assertEquals(VizMark.MODERN_LIGHT,
                      ((VSAssembly) assembly).getVSAssemblyInfo().getVizMark());
      }

      assertFalse(VizModernizeUtil.hasUnmarked(vs), "and the sheet is no longer modernizable");
   }

   @Test
   void modernizeAlsoAppliesTheSeeds() {
      Viewsheet vs = legacySheet();
      TableVSAssembly table = (TableVSAssembly) vs.getAssembly("Table1");
      assertEquals(0, table.getVSAssemblyInfo().getFormat().getDefaultFormat()
         .getRoundCornerValue(), "legacy: square");

      gateOn();
      VizModernizeUtil.modernize(vs);

      assertEquals(VSObjectChromeDefaults.cardCornerRadius(),
                   table.getVSAssemblyInfo().getFormat().getDefaultFormat().getRoundCornerValue());
   }

   @Test
   void modernizeIsANoOpWhenTheGateIsOff() {
      Viewsheet vs = legacySheet();
      gateOff();

      assertEquals(0, VizModernizeUtil.modernize(vs));
      assertNull(vs.getVSAssemblyInfo().getVizMark());
   }

   @Test
   void modernizeIsIdempotent() {
      Viewsheet vs = legacySheet();
      gateOn();

      assertEquals(3, VizModernizeUtil.modernize(vs));
      assertEquals(0, VizModernizeUtil.modernize(vs), "a second run touches nothing");
   }

   @Test
   void modernizeLeavesMarkedSiblingsAlone() {
      // decision 3's mixed dashboard: an already-modern assembly keeps the mark it has. A brand
      // new assembly inherits its host's mark (absence included - see AbstractVSAssembly), so a
      // sibling already carrying a mark is simulated directly, standing in for one modernized (or
      // created) in an earlier dark-mode pass.
      Viewsheet vs = legacySheet();
      TextVSAssembly dark = new TextVSAssembly(vs, "Text2");
      vs.addAssembly(dark);
      dark.getVSAssemblyInfo().setVizMark(VizMark.MODERN_DARK);

      gateOn();
      VizModernizeUtil.modernize(vs);

      assertEquals(VizMark.MODERN_DARK, dark.getVSAssemblyInfo().getVizMark(),
                   "an already-marked assembly is not re-stamped");
      assertEquals(VizMark.MODERN_LIGHT,
                   vs.getAssembly("Text1").getVSAssemblyInfo().getVizMark());
   }

   @Test
   void modernizeNeverTouchesAuthorProvenanceFlags() {
      Viewsheet vs = legacySheet();
      TableVSAssembly table = (TableVSAssembly) vs.getAssembly("Table1");
      TableVSAssemblyInfo info = (TableVSAssemblyInfo) table.getVSAssemblyInfo();
      info.setTitleHeightValue(44);
      info.setUserTitleHeight(true);

      gateOn();
      VizModernizeUtil.modernize(vs);

      assertTrue(info.isUserTitleHeight(), "userTitleHeight records an author's choice, not a mark");
      assertEquals(44, info.getTitleHeightValue(), "and the height they chose survives");
   }

   @Test
   void modernizeLeavesATypeThatInstallsItsOwnFormatAlone() {
      // Text builds its own object format and hardcodes the legacy border, so it never took the base
      // chrome at creation and must not take it from Modernize either
      Viewsheet vs = legacySheet();
      TextVSAssembly text = new TextVSAssembly(vs, "TextA");
      vs.addAssembly(text);
      // a real creation flow calls initDefaultFormat() after adding the assembly; without it
      // Text's own object format never installs a border to begin with
      text.getVSAssemblyInfo().initDefaultFormat();
      text.getVSAssemblyInfo().setVizMark(null);
      Color before = text.getVSAssemblyInfo().getFormat().getDefaultFormat()
         .getBorderColorsValue().topColor;

      gateOn();
      VizModernizeUtil.modernize(vs);

      assertEquals(VizMark.MODERN_LIGHT, text.getVSAssemblyInfo().getVizMark(),
                   "it is still stamped - the mark records provenance for every assembly");
      assertEquals(before, text.getVSAssemblyInfo().getFormat().getDefaultFormat()
         .getBorderColorsValue().topColor, "but its chrome is untouched");
   }

   @Test
   void modernizeLeavesATitleTheHookDoesNotOwnAlone() {
      Viewsheet vs = legacySheet();
      SelectionListVSAssembly list = new SelectionListVSAssembly(vs, "SelA");
      vs.addAssembly(list);
      // a real creation flow calls initDefaultFormat() after adding the assembly; without it the
      // list never installs its own TITLEPATH composite to begin with
      list.getVSAssemblyInfo().initDefaultFormat();
      list.getVSAssemblyInfo().setVizMark(null);

      gateOn();
      VizModernizeUtil.modernize(vs);

      assertEquals(new Color(0xc0c0c0),
                   list.getVSAssemblyInfo().getFormatInfo()
                      .getFormat(VSAssemblyInfo.TITLEPATH).getDefaultFormat()
                      .getBorderColorsValue().bottomColor,
                   "a selection list installs its own title composite; the hook does not own it");
   }

   @Test
   void modernizeGivesATableTheModernObjectChrome() {
      Viewsheet vs = legacySheet();
      TableVSAssembly table = (TableVSAssembly) vs.getAssembly("Table1");

      gateOn();
      VizModernizeUtil.modernize(vs);

      VizContext ctx = VizContext.ofGate();
      assertEquals(VSObjectChromeDefaults.objectBorderColor(ctx),
                   table.getVSAssemblyInfo().getFormat().getDefaultFormat()
                      .getBorderColorsValue().topColor);
      assertEquals(VSObjectChromeDefaults.cardCornerRadius(),
                   table.getVSAssemblyInfo().getFormat().getDefaultFormat().getRoundCornerValue());
   }

   @Test
   void modernizeReachesAnEmbeddedViewsheetContainerButNotItsChildren() {
      Viewsheet vs = legacySheet();
      Viewsheet embedded = new Viewsheet();
      embedded.getVSAssemblyInfo().setVizMark(null);
      embedded.getVSAssemblyInfo().setName("EmbeddedVS");
      TextVSAssembly inner = new TextVSAssembly(embedded, "InnerText");
      embedded.addAssembly(inner);
      inner.getVSAssemblyInfo().setVizMark(null);
      vs.addAssembly(embedded);

      gateOn();
      VizModernizeUtil.modernize(vs);

      assertEquals(VizMark.MODERN_LIGHT, embedded.getVSAssemblyInfo().getVizMark(),
                   "the container is this sheet's content");
      assertNull(inner.getVSAssemblyInfo().getVizMark(),
                 "its children are the embedded asset's content and stay untouched");
   }

   @Test
   void aSheetWhoseOnlyUnmarkedItemIsAnEmbeddedContainerHasUnmarkedContent() {
      // this is the case getAssemblies(true) drops entirely - it flattens the container away and
      // yields only its (already-marked, out-of-scope) children - so hasUnmarked() must reach the
      // container through the direct-children pass, or the composer would never offer the action
      gateOn();
      Viewsheet vs = new Viewsheet();
      vs.addAssembly(new TextVSAssembly(vs, "Text1"));

      Viewsheet embedded = new Viewsheet();
      embedded.getVSAssemblyInfo().setName("EmbeddedVS");
      vs.addAssembly(embedded);
      embedded.getVSAssemblyInfo().setVizMark(null);

      assertTrue(VizModernizeUtil.hasUnmarked(vs),
                 "the container is the sheet's only unmarked item");
   }
}
