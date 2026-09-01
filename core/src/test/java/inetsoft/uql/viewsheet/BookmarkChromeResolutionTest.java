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
package inetsoft.uql.viewsheet;

import inetsoft.graph.aesthetic.CategoricalColorFrame;
import inetsoft.report.StyleConstants;
import inetsoft.report.internal.binding.BaseField;
import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.graph.aesthetic.SharedFrameParameters;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.Tool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.Color;
import java.io.*;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class BookmarkChromeResolutionTest {
   @AfterEach
   void resetGate() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
   }

   @Test
   void aBookmarkTakenWhileMarkedDoesNotUnRevertTheChart() throws Exception {
      // the defect: bookmark a modern chart, Revert the dashboard, restore the bookmark. The
      // assembly is unmarked but its chrome came back modern.
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = newChart(vs, VizMark.MODERN_LIGHT);
      String state = writeState(chart);

      // Revert: clear the mark and re-seed legacy, exactly as VizModernizeUtil.revert does
      ChartVSAssemblyInfo info = (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
      info.setVizMark(null);
      chart.parseState(parseXml(state));

      assertEquals(0.0, info.getChartDescriptor().getPlotDescriptor().getBarCornerRadius(), 0.001,
                   "the reverted chart must not get the modern bar corners back from the bookmark");
   }

   @Test
   void aBookmarkTakenWhileUnmarkedDoesNotLeaveLegacyChromeOnAMarkedChart() throws Exception {
      // the other direction: bookmark a legacy chart, Modernize, restore.
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = newChart(vs, null);
      String state = writeState(chart);

      ChartVSAssemblyInfo info = (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
      info.setVizMark(VizMark.MODERN_LIGHT);
      chart.parseState(parseXml(state));

      assertEquals(0.3, info.getChartDescriptor().getPlotDescriptor().getBarCornerRadius(), 0.001,
                   "the modernized chart must not keep the bookmark's legacy bar corners");
   }

   @Test
   void aBookmarkTakenWhileMarkedDoesNotUnRevertTheChartsPalette() throws Exception {
      // the palette is the value that took several bug-fix rounds, and it rides in the chart's
      // own aesthetic state rather than the assembly's format. Corners passing does not imply
      // colour passing. This is also the widest failure mode: parseStateContent's state_info
      // branch replaces the whole VSChartInfo - and with it every aesthetic ref and colour frame -
      // wholesale, exactly like the table's setFormatInfo. That branch only fires when the chart
      // has a bound source matching the serialized <state_table>, so one is bound here on purpose.
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(vs, "Chart1");
      chart.setSourceInfo(new SourceInfo(SourceInfo.ASSET, "ws", "Chart1Table"));
      chart.getVSAssemblyInfo().initDefaultFormat();
      bindColorAesthetic(chart);
      vs.addAssembly(chart);
      modernizeViaGate(vs);

      ChartVSAssemblyInfo info = (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
      CategoricalColorFrame frameBeforeRestore =
         (CategoricalColorFrame) info.getVSChartInfo().getColorField().getVisualFrame();

      String state = writeState(chart);
      assertTrue(state.contains("<state_table>"),
                 "precondition: without a bound source, setVSChartInfo never replaces the frame " +
                 "and this test would silently stop covering that branch");

      info.setVizMark(null);
      chart.parseState(parseXml(state));

      CategoricalColorFrame frameAfterRestore =
         (CategoricalColorFrame) info.getVSChartInfo().getColorField().getVisualFrame();
      assertNotSame(frameBeforeRestore, frameAfterRestore,
                    "precondition: restore must have replaced the VSChartInfo - and its colour " +
                    "frame - wholesale, or this is only testing the surviving-object case");

      assertEquals(legacyFirstColor(), firstAestheticColor(info),
                   "the reverted chart must not keep the bookmark's modern palette");
   }

   @Test
   void aTableGetsItsChromeBackAfterRestoreReplacesItsWholeFormatInfo() throws Exception {
      // the widest case: TableVSAssembly's parseStateContent calls setFormatInfo, replacing the
      // whole object the seed wrote into, so all three format values are exposed at once.
      Viewsheet vs = new Viewsheet();
      TableVSAssembly table = new TableVSAssembly(vs, "Table1");
      table.getVSAssemblyInfo().initDefaultFormat();
      vs.addAssembly(table);
      TableVSAssemblyInfo info = (TableVSAssemblyInfo) table.getVSAssemblyInfo();
      modernizeViaGate(vs);

      int modernRadius = info.getFormat().getDefaultFormat().getRoundCornerValue();
      assertNotEquals(0, modernRadius, "precondition: a marked table carries a card radius");

      String state = writeState(table);
      info.setVizMark(null);
      table.parseState(parseXml(state));

      assertEquals(0, info.getFormat().getDefaultFormat().getRoundCornerValue(),
                   "the card radius must follow the cleared mark, not the restored FormatInfo");
   }

   @Test
   void aStaleBookmarkDoesNotUnRevertTheTitleLane() throws Exception {
      // the title chrome rides in state_tableformat, which parseStateContent installs wholesale;
      // only the re-seed that follows parseState resolves it against the live mark
      Viewsheet vs = new Viewsheet();
      TableVSAssembly table = new TableVSAssembly(vs, "Table1");
      table.getVSAssemblyInfo().initDefaultFormat();
      vs.addAssembly(table);
      modernizeViaGate(vs);

      String state = writeState(table);
      assertTrue(state.contains("<state_tableformat>"),
                 "precondition: the bookmark actually carries the title format");

      VizModernizeUtil.revert(vs);
      table.parseState(parseXml(state));

      VSFormat def = table.getVSAssemblyInfo().getFormatInfo()
         .getFormat(VSAssemblyInfo.TITLEPATH).getDefaultFormat();
      assertEquals(VSAssemblyInfo.DEFAULT_TITLE_BG, def.getBackgroundValue(),
                   "the restored title lane is filled again, as a classic table's is");
      assertEquals(StyleConstants.THIN_LINE, def.getBordersValue().top,
                   "and carries the four-side box, not the modern rule");
   }

   @Test
   void theSelectionFamilyCarriesNoFormatInItsBookmarkState() throws Exception {
      // the seeded title lane needs no restore-side resolution for the list or the tree: their
      // state carries no title format at all. The container's own title carries none either - its
      // writeStateContent writes nothing of its own before the per-child loop runs - but each
      // serialized child is a whole assembly.writeXML(writer), which does carry a per-data-path
      // VSCompositeFormat, title included. That's a known, pre-existing restore hole (children are
      // re-created from that same blob by Viewsheet.parseState, which only re-seeds the
      // container's own info) recorded as an open item - this test passing does not close it.
      //
      // neither list nor tree here has any selected value, so state_selectionList / state_selectionValue
      // never serialize - those do carry a per-value VSCompositeFormat when populated
      // (SelectionList.writeFormats / SelectionValue.writeContents), but that is a per-item value
      // format, a different concept from title chrome, and out of scope for this guard
      Viewsheet vs = new Viewsheet();
      SelectionListVSAssembly list = new SelectionListVSAssembly(vs, "Selection1");
      list.getVSAssemblyInfo().initDefaultFormat();
      vs.addAssembly(list);

      // the list's own writeStateContent re-implements a subset of SelectionVSAssemblyInfo's
      // fields directly rather than delegating to it. Only the tree's writeStateContent actually
      // calls SelectionVSAssemblyInfo.writeStateContent - the method this guard is about - and
      // only wraps it in <state_info> on the runtime branch, so the tree is needed too or the
      // guard never reaches the method it names
      SelectionTreeVSAssembly tree = new SelectionTreeVSAssembly(vs, "Selection2");
      tree.getVSAssemblyInfo().initDefaultFormat();
      vs.addAssembly(tree);

      // a populated container - a child selection list - so the per-child loop in
      // CurrentSelectionVSAssembly.writeStateContent actually runs
      CurrentSelectionVSAssembly container = new CurrentSelectionVSAssembly(vs, "Container1");
      container.getVSAssemblyInfo().initDefaultFormat();
      vs.addAssembly(container);
      SelectionListVSAssembly child = new SelectionListVSAssembly(vs, "Selection3");
      child.getVSAssemblyInfo().initDefaultFormat();
      vs.addAssembly(child);
      container.setAssemblies(new String[] { child.getName() });

      modernizeViaGate(vs);

      String listState = writeState(list);
      assertFalse(listState.contains("<state_format>"), "no object format in the list's state");
      assertFalse(listState.contains("VSCompositeFormat"), "and no title format in it either");

      String treeState = writeState(tree);
      assertTrue(treeState.contains("<state_info>"),
                 "precondition: the tree's runtime state must wrap " +
                 "SelectionVSAssemblyInfo.writeStateContent in state_info, or this test isn't " +
                 "exercising the branch it's named for");
      assertFalse(treeState.contains("<state_format>"), "no object format in the tree's state_info");
      assertFalse(treeState.contains("VSCompositeFormat"), "and no title format in it either");

      String containerState = writeState(container);
      int firstChild = containerState.indexOf("<oneAssembly>");
      assertTrue(firstChild >= 0,
                 "precondition: the container must actually serialize a child, or this test isn't "
                 + "exercising the branch it's named for");
      String ownPortion = containerState.substring(0, firstChild);
      assertFalse(ownPortion.contains("<state_format>"),
                  "no object format in the container's own state");
      assertFalse(ownPortion.contains("VSCompositeFormat"),
                  "and no format for the container's own title either");
      assertTrue(containerState.contains("VSCompositeFormat"),
                 "the serialized child does carry its own per-assembly format, title included - "
                 + "resolved on restore by the mark hand-off in Viewsheet.parseState");
   }

   @Test
   void aStaleBookmarkDoesNotUnRevertAContainersChildren() throws Exception {
      // a container writes each child as a whole assembly, mark and formats included, and
      // Viewsheet.parseState removes the live children before the container re-creates them from
      // the blob - so a child never passes through AbstractVSAssembly.parseState and needs the
      // live mark handed to it there instead
      Viewsheet vs = new Viewsheet();
      CurrentSelectionVSAssembly container = new CurrentSelectionVSAssembly(vs, "Container1");
      container.getVSAssemblyInfo().initDefaultFormat();
      vs.addAssembly(container);

      SelectionListVSAssembly child = new SelectionListVSAssembly(vs, "Child1");
      child.getVSAssemblyInfo().initDefaultFormat();
      vs.addAssembly(child);
      container.setAssemblies(new String[]{ "Child1" });

      modernizeViaGate(vs);
      assertEquals(VizMark.MODERN_LIGHT, child.getVSAssemblyInfo().getVizMark(),
                   "precondition: the child is marked before the bookmark is taken");

      String state = writeState(vs);
      VizModernizeUtil.revert(vs);
      vs.parseState(parseXml(state));

      VSAssembly restored = (VSAssembly) vs.getAssembly("Child1");
      assertNotNull(restored, "precondition: the child came back");
      assertNull(restored.getVSAssemblyInfo().getVizMark(),
                 "a child re-created from the blob takes the live mark, not the bookmark's");
      assertEquals(new Color(0xc0c0c0),
                   restored.getVSAssemblyInfo().getFormatInfo()
                      .getFormat(VSAssemblyInfo.TITLEPATH).getDefaultFormat()
                      .getBorderColorsValue().bottomColor,
                   "and its chrome resolves against that mark rather than staying modern");
   }

   @Test
   void anUnmarkedAssemblyIsUnchangedByARoundTrip() throws Exception {
      // the governing constraint. Nothing may move on unmarked content. An author-modified
      // descriptor value and the sheet's persisted dimensionColors are included because a
      // freshly created chart's own values would stay zero/absent even if the re-seed reached
      // them - this must catch a re-seed that reaches in and overwrites what somebody set.
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = newChart(vs, null);
      ChartVSAssemblyInfo info = (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
      info.getChartDescriptor().getPlotDescriptor().setBarCornerRadius(0.15);
      info.getChartDescriptor().getPlotDescriptor().setSmoothLines(true);
      // the provenance flags are what make these two an "author-modified" value rather than a
      // coincidence seedChromeDefaults is free to overwrite - without them an unmarked chart's
      // plain descriptor fields have no tier of their own and are expected to resolve to the
      // legacy default on every restore, flag or no flag
      info.setUserBarCornerRadius(true);
      info.setUserSmoothLines(true);
      String backgroundBefore = info.getFormat().getDefaultFormat().getBackgroundValue();
      Color borderBefore = info.getFormat().getDefaultFormat().getBorderColorsValue().topColor;
      int cardRadiusBefore = info.getFormat().getDefaultFormat().getRoundCornerValue();
      double barRadiusBefore = info.getChartDescriptor().getPlotDescriptor().getBarCornerRadius();
      boolean smoothBefore = info.getChartDescriptor().getPlotDescriptor().isSmoothLines();
      vs.setDimensionColors("State", Map.of("CA", Color.RED));

      // round-trip at the sheet level, not just the assembly's, so the sheet's own restore path
      // (where dimensionColors lives) is the one under test. Every "after" value is re-fetched
      // through info rather than reusing a captured object: restore replaces the format and the
      // descriptor wholesale (state_format/state_descriptor), so a captured reference goes stale
      // and would pass without ever seeing what the restore actually did.
      vs.parseState(parseXml(writeState(vs)));

      assertEquals(backgroundBefore, info.getFormat().getDefaultFormat().getBackgroundValue(),
                   "the object background must not move on unmarked content");
      assertEquals(borderBefore, info.getFormat().getDefaultFormat().getBorderColorsValue().topColor,
                   "the object border colour must not move on unmarked content");
      assertEquals(cardRadiusBefore, info.getFormat().getDefaultFormat().getRoundCornerValue());
      assertEquals(barRadiusBefore,
                   info.getChartDescriptor().getPlotDescriptor().getBarCornerRadius(), 0.001,
                   "an author-modified bar corner radius must not move on unmarked content");
      assertEquals(smoothBefore, info.getChartDescriptor().getPlotDescriptor().isSmoothLines(),
                   "an author-modified smooth-lines value must not move on unmarked content");
      assertEquals(Color.RED, vs.getDimensionColors("State").get("CA"),
                   "dimensionColors is persisted user data and must survive a restore untouched");
   }

   @Test
   void anAuthorsOwnFormatSurvivesTheReseed() throws Exception {
      // seedChromeDefaults writes DEFAULT tiers only, which is what makes re-seeding safe. An
      // author's border colour must not be touched.
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = newChart(vs, VizMark.MODERN_LIGHT);
      ChartVSAssemblyInfo info = (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
      BorderColors authors = new BorderColors(Color.RED, Color.RED, Color.RED, Color.RED);
      info.getFormat().getUserDefinedFormat().setBorderColors(authors);

      chart.parseState(parseXml(writeState(chart)));

      assertEquals(authors, info.getFormat().getUserDefinedFormat().getBorderColors(),
                   "the author's border colour is on the USER tier and must be left alone");
   }

   @Test
   void aTypeThatBypassesBaseChromeIsUnharmed() throws Exception {
      // TextVSAssemblyInfo is in bypassesBaseChrome(), so the re-seed is a no-op. It must not throw.
      Viewsheet vs = new Viewsheet();
      TextVSAssembly text = new TextVSAssembly(vs, "Text1");
      text.getVSAssemblyInfo().initDefaultFormat();
      text.getVSAssemblyInfo().setVizMark(VizMark.MODERN_LIGHT);

      assertDoesNotThrow(() -> text.parseState(parseXml(writeState(text))));
   }

   @Test
   void theHelperToleratesANullInfo() {
      assertDoesNotThrow(() -> VizModernizeUtil.reseedAfterRestore(null));
   }

   @Test
   void restoringASheetClearsTheSharedColourFramesTheRenderWouldPrefer() throws Exception {
      // The per-assembly reseed writes the assembly's own frame, but a render clones the
      // sheet's shared frame in preference. Without the sheet-level clear the stale shared frame
      // survives and the palette stays wrong.
      //
      // getSharedFrames() only returns the live cache; it is not lazily populated by being
      // called, so a real render's population is stood in for here by putting an entry
      // directly into the same map the render would have written into.
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = newChart(vs, VizMark.MODERN_LIGHT);
      vs.addAssembly(chart);

      String state = writeState(vs);
      vs.getSharedFrames().put(new SharedFrameParameters(), new CategoricalColorFrame());
      ((ChartVSAssemblyInfo) chart.getVSAssemblyInfo()).setVizMark(null);
      vs.parseState(parseXml(state));

      assertTrue(vs.getSharedFrames().isEmpty(),
                 "the sheet's shared frames must be cleared on restore, or the render clones a "
                 + "stale frame in preference to the assembly's re-seeded one");
   }

   @Test
   void aCalcTableBookmarkTakenWhileMarkedDoesNotUnRevertIt() throws Exception {
      // C3: CalcTableVSAssembly writes and parses its whole info as state, mark included, so a
      // bookmark taken while marked would install the stale mark on restore and
      // reseedAfterRestore would re-apply the modern chrome the Revert below removed - unless
      // the live assembly's mark is restored first.
      Viewsheet vs = new Viewsheet();
      CalcTableVSAssembly table = new CalcTableVSAssembly(vs, "Calc1");
      table.getVSAssemblyInfo().initDefaultFormat();
      vs.addAssembly(table);
      modernizeViaGate(vs);

      CalcTableVSAssemblyInfo info = (CalcTableVSAssemblyInfo) table.getVSAssemblyInfo();
      assertNotEquals(0, info.getFormat().getDefaultFormat().getRoundCornerValue(),
                       "precondition: a marked calc table carries a card radius");

      String state = writeState(table);
      assertTrue(state.contains("<state_calctable>"),
                 "precondition: the calc table's whole info, mark included, must be in state or "
                 + "this test isn't exercising the branch it's named for");

      // Revert: clear the mark, exactly as VizModernizeUtil.revert does before re-seeding
      info.setVizMark(null);
      table.parseState(parseXml(state));

      // setVSAssemblyInfo (which parseStateContent calls with the freshly-parsed info) merges via
      // copyInfo, which mutates the live object in place rather than swapping the reference - but
      // copyViewInfo's own vizMark = info.vizMark still installs the stale mark transiently, which
      // is what the capture-before/restore-after in AbstractVSAssembly.parseState has to undo
      CalcTableVSAssemblyInfo restored = (CalcTableVSAssemblyInfo) table.getVSAssemblyInfo();
      assertNull(restored.getVizMark(),
                 "the bookmark's stale mark must not survive restore onto the reverted table");
      assertEquals(0, restored.getFormat().getDefaultFormat().getRoundCornerValue(),
                   "the reverted calc table must not get the modern card radius back from the "
                   + "bookmark");
   }

   @Test
   void aViewsheetScopedAnnotationBookmarkTakenWhileMarkedDoesNotUnRevertIt() throws Exception {
      // I5: a viewsheet-scoped annotation (and its rectangle) write their whole info, mark
      // included, and Viewsheet.parseState re-creates them from that XML rather than calling
      // their own parseState - so without an explicit fix they get neither the mark correction
      // nor the reseed that every other assembly type gets.
      Viewsheet vs = new Viewsheet();
      AnnotationRectangleVSAssembly rect = new AnnotationRectangleVSAssembly(vs, "Rect1");
      rect.getVSAssemblyInfo().initDefaultFormat();
      vs.addAssembly(rect);

      AnnotationVSAssembly anno = new AnnotationVSAssembly(vs, "Anno1");
      AnnotationVSAssemblyInfo ainfo = (AnnotationVSAssemblyInfo) anno.getVSAssemblyInfo();
      ainfo.initDefaultFormat();
      ainfo.setType(AnnotationVSAssemblyInfo.VIEWSHEET);
      ainfo.setRectangle("Rect1");
      vs.addAssembly(anno);

      modernizeViaGate(vs);
      assertNotEquals(VSAssemblyInfo.DEFAULT_BORDER_COLOR,
                       ainfo.getFormat().getDefaultFormat().getBorderColorsValue().topColor,
                       "precondition: a marked annotation carries the modern border colour");

      String state = writeState(vs);
      assertTrue(state.contains("AnnotationVSAssembly") && state.contains("<assemblyInfo"),
                 "precondition: the annotation's whole info, mark included, must be in state or "
                 + "this test isn't exercising the branch it's named for");

      // Revert: clear the mark on both, exactly as VizModernizeUtil.revert does
      ainfo.setVizMark(null);
      rect.getVSAssemblyInfo().setVizMark(null);
      vs.parseState(parseXml(state));

      VSAssembly restoredAnno = vs.getAssembly("Anno1");
      assertNull(restoredAnno.getVSAssemblyInfo().getVizMark(),
                 "the bookmark's stale mark must not survive restore onto the reverted annotation");
      assertEquals(VSAssemblyInfo.DEFAULT_BORDER_COLOR,
                   restoredAnno.getVSAssemblyInfo().getFormat().getDefaultFormat()
                      .getBorderColorsValue().topColor,
                   "the reverted annotation must not get the modern border back from the bookmark");
   }

   @Test
   void anAuthorSetSmoothLinesSurvivesARoundTripOnAnUnmarkedChart() throws Exception {
      // C2: smoothLines is a plain PlotDescriptor field with no tier of its own, so without the
      // userSmoothLines flag seedChromeDefaults' legacy branch silently unchecks it on restore.
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = newChart(vs, null);
      ChartVSAssemblyInfo info = (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
      info.getChartDescriptor().getPlotDescriptor().setSmoothLines(true);
      info.setUserSmoothLines(true);

      chart.parseState(parseXml(writeState(chart)));

      assertTrue(info.getChartDescriptor().getPlotDescriptor().isSmoothLines(),
                 "the author's smooth-lines choice must survive the re-seed");
   }

   @Test
   void anAuthorSetBarCornerRadiusSurvivesARoundTripOnAMarkedChart() throws Exception {
      // C2, the other direction: a marked chart's modern branch must not overwrite an author's
      // own radius either, or Modernize/re-seed destroys the value instead of restore doing it.
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = newChart(vs, VizMark.MODERN_LIGHT);
      ChartVSAssemblyInfo info = (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
      info.getChartDescriptor().getPlotDescriptor().setBarCornerRadius(0.45);
      info.setUserBarCornerRadius(true);

      chart.parseState(parseXml(writeState(chart)));

      assertEquals(0.45, info.getChartDescriptor().getPlotDescriptor().getBarCornerRadius(), 0.001,
                   "the author's bar corner radius must survive the re-seed");
   }

   @Test
   void theUserPlotFlagsSurviveTheDialogsMergeOntoTheLiveAssembly() {
      // the flag has to travel with the value across copyInfo, or the live info reports false
      // and the next restore or Revert overwrites what the author set - exactly the Critical
      // this branch already shipped and had to fix for userPadding
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(vs, "Chart1");
      ChartVSAssemblyInfo live = (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
      live.initDefaultFormat();
      live.setVizMark(VizMark.MODERN_LIGHT);

      ChartVSAssemblyInfo edited = (ChartVSAssemblyInfo) Tool.clone(live);
      edited.getChartDescriptor().getPlotDescriptor().setSmoothLines(false);
      edited.setUserSmoothLines(true);
      edited.getChartDescriptor().getPlotDescriptor().setBarCornerRadius(0.1);
      edited.setUserBarCornerRadius(true);
      chart.setVSAssemblyInfo(edited);

      ChartVSAssemblyInfo merged = (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();

      assertTrue(merged.isUserSmoothLines(), "the smooth-lines flag crossed the merge");
      assertTrue(merged.isUserBarCornerRadius(), "the bar-corner-radius flag crossed the merge");
      assertFalse(merged.getChartDescriptor().getPlotDescriptor().isSmoothLines());
      assertEquals(0.1, merged.getChartDescriptor().getPlotDescriptor().getBarCornerRadius(), 0.001);
   }

   /**
    * Bind a categorical colour aesthetic so the chart has a CategoricalColorFrame for
    * seedColorPalette to write. Left unmarked and unseeded here; the caller seeds it via
    * modernizeViaGate so the one seeding pass also reaches this frame.
    *
    * seedColorPalette walks getAestheticRefs(runtime) looking for a CategoricalColorFrame; a chart
    * with no bound colour field has none, so the palette assertions need this.
    */
   private static void bindColorAesthetic(ChartVSAssembly chart) {
      ChartVSAssemblyInfo info = (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
      VSChartInfo cinfo = info.getVSChartInfo();
      VSChartDimensionRef colorDim = new VSChartDimensionRef(new BaseField("State"));
      VSAestheticRef colorRef = new VSAestheticRef();
      colorRef.setDataRef(colorDim);
      colorRef.setVisualFrame(new CategoricalColorFrame());
      cinfo.setColorField(colorRef);
   }

   /**
    * The first colour the chart's bound colour frame would hand a render.
    *
    * CategoricalColorFrame.defaultColors is private with no getter, so this reads through the
    * public getColor(Object). That path needs a scale to resolve a value to an index, and a
    * freshly parsed frame carries none - the scale is runtime-derived from data, never persisted -
    * so this calls init() with the same value the frame was seeded for first, giving getColor(Object)
    * a scale to resolve "CA" through defaultColors[0] without adding a getter to the class.
    */
   private static Color firstAestheticColor(ChartVSAssemblyInfo info) {
      CategoricalColorFrame frame =
         (CategoricalColorFrame) info.getVSChartInfo().getColorField().getVisualFrame();
      frame.init(new Object[] { "CA" });
      return frame.getColor("CA");
   }

   /** The first colour of the legacy palette, i.e. what an unmarked chart must resolve to. */
   private static Color legacyFirstColor() {
      return VSChartPaletteDefaults.seedPalette(VizContext.of((VizMark) null))[0];
   }

   /**
    * A separated bar chart with one x dimension and one y measure, seeded for the given mark.
    *
    * seedChromeDefaults is protected in inetsoft.uql.viewsheet.internal and this test is in
    * inetsoft.uql.viewsheet, so a non-null mark is applied through the public
    * VizModernizeUtil.modernize(vs) - forcing the org gate on for the call - rather than calling
    * the protected method directly.
    */
   private static ChartVSAssembly newChart(Viewsheet vs, VizMark mark) {
      ChartVSAssembly chart = new ChartVSAssembly(vs, "Chart1");
      chart.getVSAssemblyInfo().initDefaultFormat();
      vs.addAssembly(chart);

      if(mark == VizMark.MODERN_LIGHT) {
         modernizeViaGate(vs);
      }

      return chart;
   }

   /**
    * Force the org gate to modern-light for one VizModernizeUtil.modernize(vs) call, then restore
    * the gate. Every assembly of vs's own that is still unmarked at the time of the call is stamped
    * and seeded - this is the public route the product uses, since seedChromeDefaults itself is not
    * visible from this package.
    */
   private static void modernizeViaGate(Viewsheet vs) {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      SreeEnv.setProperty("viewsheet.darkMode", "false");

      try {
         VizModernizeUtil.modernize(vs);
      }
      finally {
         SreeEnv.setProperty("viewsheet.modernVisualization", null);
         SreeEnv.setProperty("viewsheet.darkMode", null);
      }
   }

   /**
    * Serialize state the way a real bookmark does: VSBookmark.addBookmark and
    * RuntimeViewsheet.addBookmark always write with runtime true, and VSBookmark.getBookmark
    * always parses with runtime true (AbstractVSAssembly.parseState's single-arg
    * parseStateContent(elem) is hardcoded to runtime true, so parse already assumes this).
    *
    * Writing with runtime false never emits state_table, state_info, state_descriptor,
    * state_format or the table's state_tableformat, so none of the "restore replaces the container
    * the seed wrote into" branches are reachable and every test here would pass without exercising
    * the one it is named for. Every one of those elements is gated behind runtime, not just the
    * chart's colour-frame path.
    */
   private static String writeState(VSAssembly assembly) {
      StringWriter sw = new StringWriter();
      assembly.writeState(new PrintWriter(sw), true);
      return sw.toString();
   }

   private static Element parseXml(String xml) throws Exception {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
      Document doc = factory.newDocumentBuilder()
         .parse(new ByteArrayInputStream(xml.getBytes()));
      return doc.getDocumentElement();
   }
}
