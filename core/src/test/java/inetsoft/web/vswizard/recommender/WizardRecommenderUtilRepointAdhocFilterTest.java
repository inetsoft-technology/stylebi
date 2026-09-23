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
package inetsoft.web.vswizard.recommender;

import inetsoft.test.*;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.TimeSliderVSAssemblyInfo;
import inetsoft.web.vswizard.handler.VSWizardObjectHandler;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Tests for Bug #76942: an ad hoc range filter (a {@link TimeSliderVSAssembly} created from a
 * chart bar's right-click "Filter" action, bound to its source chart via
 * {@link XSourceInfo#VS_ASSEMBLY} + {@code tableName == chart's assembly name}) was silently
 * orphaned whenever the Object Wizard cloned the chart under a temporary name, because nothing
 * repointed the filter's {@code tableName} to the clone.
 * <p>
 * These tests exercise {@link WizardRecommenderUtil#repointAdhocFilterTableName} directly (the
 * "open"/"close" side of the Wizard-scoped fix) and, separately,
 * {@link inetsoft.uql.viewsheet.internal.SelectionVSAssemblyInfo#renameDepended} through the
 * ordinary {@link Viewsheet#renameAssembly} composer-rename path (the general propagation-gap
 * fix), including the sibling dimension-sourced Selection List case that must remain unaffected.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class WizardRecommenderUtilRepointAdhocFilterTest {
   private static final String CHART = "Chart1";
   private static final String TEMP1 = WizardRecommenderUtil.nextPrimaryAssemblyName();
   private static final String TEMP2 = WizardRecommenderUtil.nextPrimaryAssemblyName();

   /**
    * The Wizard "open" scenario: the ad hoc filter still points at the original chart name; it
    * should be repointed to the wizard's freshly renamed clone.
    */
   @Test
   void repointsFilterPointingAtOriginalName() {
      Viewsheet vs = new Viewsheet();
      TimeSliderVSAssembly slider = addAdhocSlider(vs, "Filter1", XSourceInfo.VS_ASSEMBLY, CHART);

      WizardRecommenderUtil.repointAdhocFilterTableName(vs, CHART, TEMP1);

      assertEquals(TEMP1, slider.getTableName());
   }

   /**
    * The Wizard can refresh more than once in a single session (each refresh mints a new temp
    * name); a filter left pointing at a stale temp name from an earlier refresh must still be
    * repointed to the latest one.
    */
   @Test
   void repointsFilterPointingAtPriorTempName() {
      Viewsheet vs = new Viewsheet();
      TimeSliderVSAssembly slider = addAdhocSlider(vs, "Filter1", XSourceInfo.VS_ASSEMBLY, TEMP1);

      WizardRecommenderUtil.repointAdhocFilterTableName(vs, CHART, TEMP2);

      assertEquals(TEMP2, slider.getTableName());
   }

   /**
    * The Wizard "close" (cancel, or save of a same-type edit) scenario: the filter is pointing at
    * a wizard temp name and must be repointed back to the real (original) assembly name.
    */
   @Test
   void repointsFilterBackToOriginalNameOnClose() {
      Viewsheet vs = new Viewsheet();
      TimeSliderVSAssembly slider = addAdhocSlider(vs, "Filter1", XSourceInfo.VS_ASSEMBLY, TEMP1);

      WizardRecommenderUtil.repointAdhocFilterTableName(vs, CHART, CHART);

      assertEquals(CHART, slider.getTableName());
   }

   /**
    * A filter bound to an unrelated table (not the original chart name, not a wizard temp name)
    * must be left untouched.
    */
   @Test
   void doesNotRepointUnrelatedFilter() {
      Viewsheet vs = new Viewsheet();
      TimeSliderVSAssembly slider = addAdhocSlider(vs, "Filter1", XSourceInfo.VS_ASSEMBLY, "OtherTable");

      WizardRecommenderUtil.repointAdhocFilterTableName(vs, CHART, TEMP1);

      assertEquals("OtherTable", slider.getTableName());
   }

   /**
    * A TimeSlider that is not VS_ASSEMBLY-sourced (e.g. an ordinary worksheet-bound range
    * slider that merely happens to share the chart's name as its table) must never be repointed
    * by this ad hoc-filter-specific helper, even though its tableName string matches.
    */
   @Test
   void doesNotRepointNonVsAssemblySourcedSlider() {
      Viewsheet vs = new Viewsheet();
      TimeSliderVSAssembly slider = addAdhocSlider(vs, "Filter1", XSourceInfo.NONE, CHART);

      WizardRecommenderUtil.repointAdhocFilterTableName(vs, CHART, TEMP1);

      assertEquals(CHART, slider.getTableName());
   }

   /**
    * Sibling regression check (per the dimension-sourced ad hoc filter path documented in
    * 03-fix.md): a dimension-sourced ad hoc filter never sets sourceType to VS_ASSEMBLY in the
    * first place (it binds directly to the real worksheet table), so it is structurally inert to
    * this helper regardless of what its tableName happens to be. Modeled here as a
    * SelectionListVSAssembly with the default (non VS_ASSEMBLY) sourceType.
    */
   @Test
   void doesNotAffectDimensionSourcedSelectionList() {
      Viewsheet vs = new Viewsheet();
      SelectionListVSAssembly list = new SelectionListVSAssembly();
      list.getVSAssemblyInfo().setName("Filter2");
      list.setSourceType(XSourceInfo.NONE);
      list.setTableName("Product");
      vs.addAssembly(list);

      WizardRecommenderUtil.repointAdhocFilterTableName(vs, CHART, TEMP1);

      assertEquals("Product", list.getTableName());
      assertEquals(XSourceInfo.NONE, list.getSourceType());
   }

   /**
    * General propagation-gap fix: the ordinary composer "Rename" action goes through
    * {@link Viewsheet#renameAssembly}, which calls {@code renameDepended} on every sibling
    * assembly. Confirms the new SelectionVSAssemblyInfo#renameDepended override keeps a
    * VS_ASSEMBLY-sourced TimeSlider's tableName in sync when the chart it targets is renamed
    * outside the Wizard entirely.
    */
   @Test
   void viewsheetRenameAssemblyRepointsAdhocFilter() {
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(vs, CHART);
      vs.addAssembly(chart);
      TimeSliderVSAssembly slider = addAdhocSlider(vs, "Filter1", XSourceInfo.VS_ASSEMBLY, CHART);

      boolean renamed = vs.renameAssembly(CHART, "Chart2");

      assertEquals(true, renamed);
      assertEquals("Chart2", slider.getTableName());
   }

   /**
    * Same rename, but for the dimension-sourced Selection List sibling path: since its
    * sourceType is never VS_ASSEMBLY, an ordinary composer rename of an unrelated chart must not
    * touch its tableName.
    */
   @Test
   void viewsheetRenameAssemblyDoesNotAffectDimensionSourcedSelectionList() {
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(vs, CHART);
      vs.addAssembly(chart);
      SelectionListVSAssembly list = new SelectionListVSAssembly();
      list.getVSAssemblyInfo().setName("Filter2");
      list.setSourceType(XSourceInfo.NONE);
      list.setTableName("Product");
      vs.addAssembly(list);

      boolean renamed = vs.renameAssembly(CHART, "Chart2");

      assertEquals(true, renamed);
      assertEquals("Product", list.getTableName());
      assertEquals(XSourceInfo.NONE, list.getSourceType());
   }

   /**
    * Bug #76942 follow-up (Chart->Crosstab type conversion inside the Wizard, see
    * docs/teams/2026-09-23-bugs-76942-wizard-filter-loss/bug-76942/07-diagnosis-crosstab.md
    * and 09-fix-crosstab.md): the recommended-type-change path
    * (VSWizardBindingHandler#addCrosstabVSAssembly, reached when the user switches the
    * Wizard's recommended type from Chart to Crosstab) constructs its primary assembly via
    * WizardRecommenderUtil#nextPrimaryAssemblyName exactly like addOriginalAsPrimary()
    * already-fixed "Original Type" path, so it must be repointed the same way. This
    * exercises repointAdhocFilterTableName with the exact arguments the new call site in
    * VSWizardBindingHandler#updatePrimaryAssembly(VSObjectRecommendation, ...) passes.
    */
   @Test
   void repointsFilterWhenSwitchingToCrosstabRecommendation() {
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly original = new ChartVSAssembly(vs, CHART);
      vs.addAssembly(original);
      TimeSliderVSAssembly slider = addAdhocSlider(vs, "Filter1", XSourceInfo.VS_ASSEMBLY, CHART);

      String crosstabTempName = WizardRecommenderUtil.nextPrimaryAssemblyName();
      CrosstabVSAssembly crosstabClone = new CrosstabVSAssembly(vs, crosstabTempName);
      vs.addAssembly(crosstabClone);

      WizardRecommenderUtil.repointAdhocFilterTableName(vs, CHART, crosstabClone.getName());

      assertEquals(crosstabTempName, slider.getTableName());
   }

   /**
    * Bug #76942 follow-up, resolving 07-diagnosis-crosstab.md's open section 4b: does the
    * existing (round 1/round 2, already-merged-reviewed) close-time repoint call in
    * VSCloseObjectWizardService#closeHandle's save branch already self-heal a type-changed
    * ("Finish" after switching Chart -> Crosstab) save, or is a second, distinct defect
    * needed there?
    * <p>
    * This replicates VSWizardObjectHandler#updateAssemblyByTemporary0's actual name-settling
    * sequence using the real production {@link VSWizardObjectHandler#getNewAssemblyName}
    * method (not a re-implementation of its type-changed-vs-same-type ternary) plus the real
    * {@link VSAssembly#copyAssembly} / {@link Viewsheet#removeAssembly} /
    * {@link Viewsheet#addAssembly} methods, followed by the exact
    * {@code WizardRecommenderUtil.repointAdhocFilterTableName(vs, originalModel
    * .getOriginalName(), tempAssembly.getName())} call {@code closeHandle()}'s save branch
    * makes.
    * <p>
    * Starts from the filter still pointing at the ORIGINAL chart's name -- the state it is
    * left in for the entire wizard session before the preview-time fix in this same change
    * (see {@link #repointsFilterWhenSwitchingToCrosstabRecommendation}) existed, and the
    * state a session that never visits "Original Type" first is in regardless. If this
    * passes, the close-time repoint's {@code originalName}-branch match (not just its
    * temp-name-catch-all branch) already fully covers the type-changed save on its own,
    * settling 4b as "(a): no additional close-side defect" rather than "(b): a second
    * defect".
    */
   @Test
   void closeTimeRepointSettlesTypeChangedCrosstabSaveEvenIfNeverRepointedDuringPreview() {
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly original = new ChartVSAssembly(vs, CHART);
      vs.addAssembly(original);
      TimeSliderVSAssembly slider = addAdhocSlider(vs, "Filter1", XSourceInfo.VS_ASSEMBLY, CHART);

      // The wizard's live preview/temp clone for the recommended Crosstab type, still under
      // its temp name at Finish time.
      String tempName = WizardRecommenderUtil.nextPrimaryAssemblyName();
      CrosstabVSAssembly tempAssembly = new CrosstabVSAssembly(vs, tempName);
      vs.addAssembly(tempAssembly);

      // --- VSWizardObjectHandler#updateAssemblyByTemporary0's name-settling sequence ---
      VSWizardObjectHandler handler =
         new VSWizardObjectHandler(null, null, null, null, null, null);
      String newName = handler.getNewAssemblyName(vs, tempAssembly, original);
      // sanity check: a type change really does mint a fresh name, distinct from "Chart1"
      // (confirms 07-diagnosis-crosstab.md section 4a, which corrected 03-fix.md's guess).
      assertNotEquals(CHART, newName);

      vs.removeAssembly(original.getName());
      vs.removeAssembly(newName, false, true);
      VSAssembly settled = tempAssembly.copyAssembly(newName);
      vs.addAssembly(settled, true);

      // --- VSCloseObjectWizardService#closeHandle's save-branch repoint call ---
      WizardRecommenderUtil.repointAdhocFilterTableName(vs, CHART, settled.getName());

      assertEquals(settled.getName(), slider.getTableName());
   }

   /**
    * Same as {@link #closeTimeRepointSettlesTypeChangedCrosstabSaveEvenIfNeverRepointedDuringPreview}
    * but starting from the filter already repointed to the wizard's Crosstab preview temp
    * name (the state it is actually in once the preview-time fix in this change is applied),
    * proving the two halves of this change compose correctly end to end.
    */
   @Test
   void closeTimeRepointSettlesTypeChangedCrosstabSaveAfterPreviewTimeRepoint() {
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly original = new ChartVSAssembly(vs, CHART);
      vs.addAssembly(original);

      String tempName = WizardRecommenderUtil.nextPrimaryAssemblyName();
      CrosstabVSAssembly tempAssembly = new CrosstabVSAssembly(vs, tempName);
      vs.addAssembly(tempAssembly);

      // Preview-time fix already repointed the filter to the crosstab clone's temp name.
      TimeSliderVSAssembly slider = addAdhocSlider(vs, "Filter1", XSourceInfo.VS_ASSEMBLY, tempName);

      VSWizardObjectHandler handler =
         new VSWizardObjectHandler(null, null, null, null, null, null);
      String newName = handler.getNewAssemblyName(vs, tempAssembly, original);
      assertNotEquals(CHART, newName);

      vs.removeAssembly(original.getName());
      vs.removeAssembly(newName, false, true);
      VSAssembly settled = tempAssembly.copyAssembly(newName);
      vs.addAssembly(settled, true);

      WizardRecommenderUtil.repointAdhocFilterTableName(vs, CHART, settled.getName());

      assertEquals(settled.getName(), slider.getTableName());
   }

   private static TimeSliderVSAssembly addAdhocSlider(Viewsheet vs, String name, int sourceType,
                                                        String tableName)
   {
      TimeSliderVSAssembly slider = new TimeSliderVSAssembly();
      ((TimeSliderVSAssemblyInfo) slider.getVSAssemblyInfo()).setName(name);
      slider.setSourceType(sourceType);
      slider.setTableName(tableName);
      vs.addAssembly(slider);

      return slider;
   }
}
