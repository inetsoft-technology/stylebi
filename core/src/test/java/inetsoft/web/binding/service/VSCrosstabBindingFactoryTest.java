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
package inetsoft.web.binding.service;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.asset.SNamedGroupInfo;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.CrosstabVSAssembly;
import inetsoft.uql.viewsheet.VSAggregateRef;
import inetsoft.uql.viewsheet.VSCrosstabInfo;
import inetsoft.uql.viewsheet.VSDimensionRef;
import inetsoft.web.binding.model.BAggregateRefModel;
import inetsoft.web.binding.model.table.CrosstabBindingModel;
import inetsoft.web.binding.model.table.CrosstabOptionInfo;
import inetsoft.web.wiz.binding.TableBindingMutator;
import inetsoft.web.wiz.binding.model.FieldRef;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Bug #76574, VTB-008: {@code set_table_options(summarySideBySide: true)} reported {@code
 * ok:true} and {@code get_table_binding} echoed the setting back correctly, but the rendered
 * crosstab never actually laid summary cells side by side.
 *
 * <p>{@code updateAssembly} called {@code VSCrosstabInfo.setSummarySideBySide(boolean)} -- the
 * RUNTIME-value setter ({@code sideByBySideValue.setRValue(...)}) -- instead of {@code
 * setSummarySideBySideValue(boolean)}, the DESIGN-value setter its three sibling lines
 * (percentageBy/rowTotals/colTotals) already correctly use. The mis-set runtime value never
 * survives to render: every render/export path calls {@code VSUtil.resetRuntimeValues}, which
 * nulls the runtime value before the crosstab query reads it back, falling through to the
 * untouched, still-false design default.
 *
 * <p>Asserting on {@link VSCrosstabInfo#getSummarySideBySideValue()} (the design value) rather
 * than {@link VSCrosstabInfo#isSummarySideBySide()} (the runtime-aware getter) is what makes this
 * test actually catch the regression -- the runtime getter would return {@code true} right after
 * {@code updateAssembly} returns even under the old, broken code, since nothing in this narrow
 * unit test calls {@code resetRuntimeValues()} to null it back out.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                       initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class VSCrosstabBindingFactoryTest {
   @Test
   void updateAssemblySetsTheDesignValueForSummarySideBySide() {
      VSCrosstabBindingFactory factory =
         new VSCrosstabBindingFactory(mock(DataRefModelFactoryService.class));
      CrosstabVSAssembly assembly = new CrosstabVSAssembly();
      CrosstabBindingModel model = new CrosstabBindingModel();
      CrosstabOptionInfo option = new CrosstabOptionInfo();
      option.setSummarySideBySide(true);
      model.setOption(option);

      factory.updateAssembly(model, assembly);

      VSCrosstabInfo crossInfo = assembly.getVSCrosstabInfo();
      assertTrue(crossInfo.getSummarySideBySideValue(),
                 "the design value -- the one that survives resetRuntimeValues() and is what " +
                 "every render/export path actually reads -- must be set, not just the " +
                 "transient runtime value");
   }

   /**
    * Bug #76650: binding a viewsheet-scoped aggregate-mode calc field (ref type {@code
    * DataRef.AGG_CALC}) onto a crosstab's aggregates shelf with no explicit aggregate
    * formula threw {@code NullPointerException: Cannot invoke
    * "inetsoft.uql.erm.DataRef.getRefType()" because "ref" is null} in {@code
    * VSCrosstabBindingFactory.getDefaultFormula}.
    *
    * <p>The wrapped column ref ({@code VSAggregateRef#getDataRef()}) is legitimately
    * {@code null} for such a field -- it has no separate underlying column -- and the
    * old code derived the ref type from that null ref instead of from the aggregate
    * ref itself. This test binds exactly that shape (no formula and no wrapped column
    * ref) and asserts the bind both succeeds and picks {@code AggregateFormula.NONE} as
    * the default, since an aggregate calc field/expression is already an aggregated
    * value.
    *
    * <p>Covers three ref-type shapes because {@code refType} is a bit-flag field, not
    * an enum:
    * <ul>
    *   <li>{@code AGG_CALC} alone -- the bit-exact case the first version of this fix
    *       covered.
    *   <li>{@code AGG_EXPR} alone -- the other half of the {@code ||} condition; a
    *       fix that dropped it would still pass a bit-exact-{@code AGG_CALC}-only
    *       test.
    *   <li>{@code CUBE_MEASURE | AGG_CALC} -- the composite {@code
    *       CubeTreeModelBuilder} actually constructs for a cube-sourced aggregate calc
    *       field. A bit-exact {@code refType == AGG_CALC} check (round 1 of this fix)
    *       passes the first two cases but still NPEs/throws on this one; only a
    *       bitwise {@code (refType & AGG_CALC) == AGG_CALC} test, matching the sibling
    *       {@code VSCrosstabBindingHandler#createAgg()}, handles all three.
    * </ul>
    */
   @ParameterizedTest
   @ValueSource(ints = {
      DataRef.AGG_CALC,
      DataRef.AGG_EXPR,
      DataRef.CUBE_MEASURE | DataRef.AGG_CALC
   })
   void updateAssemblyDefaultsAnAggregateCalcFieldWithNoFormulaToNone(int refType) {
      VSCrosstabBindingFactory factory =
         new VSCrosstabBindingFactory(mock(DataRefModelFactoryService.class));
      CrosstabVSAssembly assembly = new CrosstabVSAssembly();
      CrosstabBindingModel model = new CrosstabBindingModel();
      model.setOption(new CrosstabOptionInfo());

      BAggregateRefModel calcField = new BAggregateRefModel();
      calcField.setColumnValue("calcField1");
      calcField.setRefType(refType);
      // No formula and no dataRefModel set -- this is exactly what the client sends
      // for an aggregate-mode calc field with no explicit aggregate formula.
      model.addAggregate(calcField);

      assertDoesNotThrow(() -> factory.updateAssembly(model, assembly));

      VSCrosstabInfo crossInfo = assembly.getVSCrosstabInfo();
      DataRef[] aggregates = crossInfo.getDesignAggregates();
      assertEquals(1, aggregates.length);
      VSAggregateRef aggr = (VSAggregateRef) aggregates[0];
      assertEquals(AggregateFormula.NONE, aggr.getFormula(),
                   "an aggregate calc field/expression is already an aggregated " +
                   "value, so its default formula must be None rather than an " +
                   "arbitrary Sum/Count that would double-aggregate it");
   }

   /**
    * Bug #76809, VTB-019: {@code set_table_fields}'s inline {@code namedGroupValues}, attached to
    * a row dimension that is already bound on the shelf, was silently dropped -- the rendered
    * crosstab kept rendering as if no grouping had ever been applied.
    *
    * <p>{@code updateAssembly}'s row/col merge loops call {@code updateDataRefGroupInfo(nref,
    * oref)} for any shelf position that already existed before the write, to preserve a grouping
    * that was set on the live ref through some other path and that the incoming model doesn't
    * know about. That preserve logic used to be unconditional: it always overwrote the
    * freshly-built ref's {@code namedGroupInfo} with a clone of the OLD live ref's value, even
    * when the freshly-built ref already carried its own, newly-resolved, non-null
    * {@code namedGroupInfo} from the incoming model -- silently clobbering it back to the old
    * (here: ungrouped/{@code null}) value.
    *
    * <p>Reproduces the exact repro shape: an already-bound, ungrouped {@code Product:Category} on
    * rows, then a second {@code updateAssembly} call whose model resolves a fresh
    * {@code namedGroupValues}-based grouping for that same row.
    */
   @Test
   void updateAssemblyKeepsFreshNamedGroupInfoOnAnExistingRow() {
      VSCrosstabBindingFactory factory =
         new VSCrosstabBindingFactory(mock(DataRefModelFactoryService.class));
      CrosstabVSAssembly assembly = new CrosstabVSAssembly();

      CrosstabBindingModel initial = new CrosstabBindingModel();
      initial.setOption(new CrosstabOptionInfo());
      TableBindingMutator.setShelf(initial, "rows",
         List.of(new FieldRef("Product:Category", "dimension", null, null, null)));
      factory.updateAssembly(initial, assembly);

      VSDimensionRef liveRowBefore =
         (VSDimensionRef) assembly.getVSCrosstabInfo().getDesignRowHeaders()[0];
      assertNull(liveRowBefore.getNamedGroupInfo(), "sanity: no group yet");

      FieldRef.NamedGroupValues spec = new FieldRef.NamedGroupValues(
         List.of(new FieldRef.NamedGroupValues.Clause("bu", List.of("Business", "Hardware"))),
         null);
      FieldRef field = new FieldRef("Product:Category", "dimension", null, null, null, null, null,
                                    spec);
      CrosstabBindingModel update = new CrosstabBindingModel();
      update.setOption(new CrosstabOptionInfo());
      TableBindingMutator.setShelf(update, "rows", List.of(field));

      factory.updateAssembly(update, assembly);

      VSDimensionRef liveRow =
         (VSDimensionRef) assembly.getVSCrosstabInfo().getDesignRowHeaders()[0];
      assertNotNull(liveRow.getNamedGroupInfo(),
                    "an explicit namedGroupValues on an already-bound row must survive " +
                    "updateAssembly's merge into the live crosstab, not be clobbered back to " +
                    "the old, ungrouped value");
      assertInstanceOf(SNamedGroupInfo.class, liveRow.getNamedGroupInfo());
      assertEquals(List.of("Business", "Hardware"),
                   ((SNamedGroupInfo) liveRow.getNamedGroupInfo()).getGroupValue("bu"),
                   "the live grouping must actually contain the reporter's mapping, not just " +
                   "be non-null");
   }

   /**
    * Bug #76809, VTB-019 -- the required preserve-behavior guard. A fix that made the test above
    * pass by simply deleting {@code updateDataRefGroupInfo}'s preserve logic outright (rather than
    * making it conditional on the freshly-built ref not already having its own namedGroupInfo)
    * would look correct but silently reintroduce whatever this method was originally written to
    * protect: a grouping already live on a ref, resubmitted through an unrelated {@code
    * updateAssembly} call whose incoming model says nothing about grouping for that field, must
    * still be preserved rather than dropped.
    */
   @Test
   void updateAssemblyPreservesExistingNamedGroupInfoWhenModelDoesNotSpecifyOne() {
      VSCrosstabBindingFactory factory =
         new VSCrosstabBindingFactory(mock(DataRefModelFactoryService.class));
      CrosstabVSAssembly assembly = new CrosstabVSAssembly();

      FieldRef.NamedGroupValues spec = new FieldRef.NamedGroupValues(
         List.of(new FieldRef.NamedGroupValues.Clause("bu", List.of("Business", "Hardware"))),
         null);
      FieldRef groupedField = new FieldRef("Product:Category", "dimension", null, null, null,
                                           null, null, spec);
      CrosstabBindingModel grouped = new CrosstabBindingModel();
      grouped.setOption(new CrosstabOptionInfo());
      TableBindingMutator.setShelf(grouped, "rows", List.of(groupedField));
      factory.updateAssembly(grouped, assembly);

      VSDimensionRef liveRowBefore =
         (VSDimensionRef) assembly.getVSCrosstabInfo().getDesignRowHeaders()[0];
      assertNotNull(liveRowBefore.getNamedGroupInfo(), "sanity: the group is live before the " +
                    "unrelated re-bind below");

      // No namedGroup/namedGroupValues on this field at all -- the ordinary shape of a re-bind
      // that doesn't touch this field's grouping.
      CrosstabBindingModel unrelatedRebind = new CrosstabBindingModel();
      unrelatedRebind.setOption(new CrosstabOptionInfo());
      TableBindingMutator.setShelf(unrelatedRebind, "rows",
         List.of(new FieldRef("Product:Category", "dimension", null, null, null)));

      factory.updateAssembly(unrelatedRebind, assembly);

      VSDimensionRef liveRow =
         (VSDimensionRef) assembly.getVSCrosstabInfo().getDesignRowHeaders()[0];
      assertNotNull(liveRow.getNamedGroupInfo(),
                    "a grouping already live on a ref must survive a re-bind whose incoming " +
                    "model doesn't mention grouping for that field");
      assertEquals(List.of("Business", "Hardware"),
                   ((SNamedGroupInfo) liveRow.getNamedGroupInfo()).getGroupValue("bu"));
   }
}
