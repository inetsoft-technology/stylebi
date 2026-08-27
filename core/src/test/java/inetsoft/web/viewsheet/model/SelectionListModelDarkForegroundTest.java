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
package inetsoft.web.viewsheet.model;

import inetsoft.report.TableDataPath;
import inetsoft.uql.viewsheet.internal.VizContext;
import inetsoft.uql.viewsheet.internal.VSObjectChromeDefaults;
import inetsoft.uql.viewsheet.SelectionValue;
import inetsoft.report.io.viewsheet.VSSelectionListHelper;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.LibManagerTestConfiguration;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.VSCompositeFormat;
import inetsoft.uql.viewsheet.internal.SelectionListVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.SelectionTreeVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VizMark;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A selection cell's foreground default is a fixed near-black written in setDefaultFormat, an
 * unconditional creation default that seedChromeDefaults deliberately never sees. The browser binds
 * that colour as an inline style, so on a dark-marked assembly the cell text rendered near-black on
 * the dark surface and no stylesheet could reach it. These pin the substitution at the model
 * boundary, which is where it has to happen.
 *
 * The list is passed as null and the cell format interned directly through the package-private
 * getFormatIndex: constructing a real SelectionList needs the XSwapper bean, and none of that is
 * under test here — getFormats() is.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class SelectionListModelDarkForegroundTest {
   /** The detail-cell composite the assembly's own setDefaultFormat installed. */
   private VSCompositeFormat detailFormat(VSAssemblyInfo info) {
      return info.getFormatInfo().getFormat(new TableDataPath(-1, TableDataPath.DETAIL));
   }

   /** The cell foreground the model hands the browser, for an assembly carrying the given mark. */
   private String cellForeground(VSAssemblyInfo info, VizMark mark) {
      info.setVizMark(mark);
      info.initDefaultFormat();

      return foregroundFor(info, detailFormat(info));
   }

   private String foregroundFor(VSAssemblyInfo info, VSCompositeFormat cellFormat) {
      SelectionListModel model = new SelectionListModel(null, info, 0);
      assertTrue(model.getFormatIndex(cellFormat) >= 0, "the cell format must intern");

      VSFormatModel formatModel = model.getFormats().values().iterator().next();
      return formatModel.getForeground();
   }

   @Test
   void theDetailDefaultIsTheNearBlackThisFixExistsFor() {
      SelectionListVSAssemblyInfo info = new SelectionListVSAssemblyInfo();
      info.initDefaultFormat();
      assertEquals("0x2b2b2b",
                   detailFormat(info).getDefaultFormat().getForegroundValue(),
                   "if this default moves, re-derive the fix rather than trusting these tests");
   }

   @Test
   void darkMarkedSelectionListGetsLightCellText() {
      String fg = cellForeground(new SelectionListVSAssemblyInfo(), VizMark.MODERN_DARK);
      assertNotNull(fg, "a dark-marked list must ship a cell foreground");
      assertTrue(fg.toLowerCase().contains("e6e0e9"), "expected the dark text neutral, got " + fg);
   }

   @Test
   void darkMarkedSelectionTreeGetsLightCellText() {
      String fg = cellForeground(new SelectionTreeVSAssemblyInfo(), VizMark.MODERN_DARK);
      assertNotNull(fg, "a dark-marked tree must ship a cell foreground");
      assertTrue(fg.toLowerCase().contains("e6e0e9"),
                 "the tree resolves cells through the same format map, so it must follow the list");
   }

   @Test
   void lightMarkedSelectionListKeepsItsOwnCellText() {
      String fg = cellForeground(new SelectionListVSAssemblyInfo(), VizMark.MODERN_LIGHT);
      assertFalse(fg != null && fg.toLowerCase().contains("e6e0e9"),
                  "light modern must be untouched; got " + fg);
   }

   @Test
   void unmarkedSelectionListKeepsItsOwnCellText() {
      String fg = cellForeground(new SelectionListVSAssemblyInfo(), null);
      assertFalse(fg != null && fg.toLowerCase().contains("e6e0e9"),
                  "an unmarked assembly is legacy and must be untouched; got " + fg);
   }

   /**
    * The substitution keys off the assembly's mark, not the live org property — that is the whole
    * point of the mark, and it is what makes the change reversible by Revert.
    */
   @Test
   void theMarkDecidesNotTheOrgProperty() {
      String light = cellForeground(new SelectionListVSAssemblyInfo(), VizMark.MODERN_LIGHT);
      String dark = cellForeground(new SelectionListVSAssemblyInfo(), VizMark.MODERN_DARK);
      assertNotEquals(light, dark, "the two marks must not resolve to the same cell foreground");
   }

   /** A colour the author picked wins over the dark default, in dark as everywhere else. */
   @Test
   void aUserForegroundSurvivesDark() {
      SelectionListVSAssemblyInfo info = new SelectionListVSAssemblyInfo();
      info.setVizMark(VizMark.MODERN_DARK);
      info.initDefaultFormat();

      VSCompositeFormat fmt = new VSCompositeFormat();
      fmt.getUserDefinedFormat().setForegroundValue("0x123456");

      String fg = foregroundFor(info, fmt);
      assertFalse(fg != null && fg.toLowerCase().contains("e6e0e9"),
                  "an author colour must not be replaced; got " + fg);
   }

   /** The source format is never mutated, so nothing here can reach the saved asset. */
   @Test
   void theStoredFormatIsNotMutated() {
      SelectionListVSAssemblyInfo info = new SelectionListVSAssemblyInfo();
      info.setVizMark(VizMark.MODERN_DARK);
      info.initDefaultFormat();

      VSCompositeFormat stored = detailFormat(info);
      foregroundFor(info, stored);

      assertEquals("0x2b2b2b", stored.getDefaultFormat().getForegroundValue(),
                   "the substitution must happen on a clone");
   }

   /**
    * Export parity. The browser was fixed first and that was not enough: the export painter and the
    * composer's format picker read the same near-black default through their own paths, so a PDF and
    * the format pane both disagreed with the screen. All of them route the substitution through
    * applyDarkForeground, so this pins the shared helper the export chokepoint calls rather than
    * standing up four exporters.
    */
   @Test
   void theExportChokepointSubstitutesTheSameInk() {
      SelectionListVSAssemblyInfo info = new SelectionListVSAssemblyInfo();
      info.setVizMark(VizMark.MODERN_DARK);
      info.initDefaultFormat();

      SelectionValue value = new SelectionValue("Business", "Business");
      value.setSelected(true);
      VSCompositeFormat out = VSSelectionListHelper.getValueFormat(
         value, detailFormat(info), false, VizContext.of(info));

      assertEquals(0xE6E0E9, out.getForeground().getRGB() & 0xFFFFFF,
                  "the export cell ink must equal the browser cell ink");
   }

   /** Light and unmarked go through the same call and must come back untouched. */
   @Test
   void theExportChokepointLeavesLightAlone() {
      for(VizMark mark : new VizMark[]{ VizMark.MODERN_LIGHT, null }) {
         SelectionListVSAssemblyInfo info = new SelectionListVSAssemblyInfo();
         info.setVizMark(mark);
         info.initDefaultFormat();

         SelectionValue value = new SelectionValue("Business", "Business");
         value.setSelected(true);
         VSCompositeFormat out = VSSelectionListHelper.getValueFormat(
            value, detailFormat(info), false, VizContext.of(info));

         assertEquals(0x2B2B2B, out.getForeground().getRGB() & 0xFFFFFF,
                     "mark " + mark + " must keep the legacy ink");
      }
   }

   /**
    * The dimming an excluded or unselected value gets writes the USER tier, and the dark substitution
    * yields to it. Order matters: substituting after the dimming would repaint a dimmed value as
    * ordinary text and lose the "not in this selection" signal.
    */
   @Test
   void dimmingStillWinsOverTheDarkDefault() {
      SelectionListVSAssemblyInfo info = new SelectionListVSAssemblyInfo();
      info.setVizMark(VizMark.MODERN_DARK);
      info.initDefaultFormat();

      SelectionValue excluded = new SelectionValue("Business", "Business");
      excluded.setState(SelectionValue.STATE_EXCLUDED);
      VSCompositeFormat out = VSSelectionListHelper.getValueFormat(
         excluded, detailFormat(info), false, VizContext.of(info));

      assertEquals(0x888888, out.getForeground().getRGB() & 0xFFFFFF,
                  "an excluded value keeps its dimmed grey in dark");
   }

   /** The export path must not write back into the assembly's stored format either. */
   @Test
   void theExportChokepointDoesNotMutateTheStoredFormat() {
      SelectionListVSAssemblyInfo info = new SelectionListVSAssemblyInfo();
      info.setVizMark(VizMark.MODERN_DARK);
      info.initDefaultFormat();

      VSCompositeFormat stored = detailFormat(info);
      SelectionValue value = new SelectionValue("Business", "Business");
      value.setSelected(true);
      VSSelectionListHelper.getValueFormat(value, stored, false, VizContext.of(info));

      assertEquals("0x2b2b2b", stored.getDefaultFormat().getForegroundValue(),
                   "the export helper must work on a clone");
   }

   /** The in-place variant the composer picker uses lands the same ink. */
   @Test
   void theInPlaceVariantMatchesTheCloningOne() {
      SelectionListVSAssemblyInfo info = new SelectionListVSAssemblyInfo();
      info.setVizMark(VizMark.MODERN_DARK);
      info.initDefaultFormat();

      VSCompositeFormat picker = detailFormat(info).clone();
      VSObjectChromeDefaults.applyDarkForegroundInPlace(picker, VizContext.of(info));

      assertEquals(0xE6E0E9, picker.getForeground().getRGB() & 0xFFFFFF,
                  "the composer picker must show the ink the canvas draws");
   }
}
