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
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.CrosstabVSAssembly;
import inetsoft.uql.viewsheet.VSAggregateRef;
import inetsoft.uql.viewsheet.VSCrosstabInfo;
import inetsoft.web.binding.model.BAggregateRefModel;
import inetsoft.web.binding.model.table.CrosstabBindingModel;
import inetsoft.web.binding.model.table.CrosstabOptionInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

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
    * ref itself. This test binds exactly that shape (an {@code AGG_CALC} field with no
    * formula and no wrapped column ref) and asserts the bind both succeeds and picks
    * {@code AggregateFormula.NONE} as the default, since an aggregate calc field is
    * already an aggregated value.
    */
   @Test
   void updateAssemblyDefaultsAnAggregateCalcFieldWithNoFormulaToNone() {
      VSCrosstabBindingFactory factory =
         new VSCrosstabBindingFactory(mock(DataRefModelFactoryService.class));
      CrosstabVSAssembly assembly = new CrosstabVSAssembly();
      CrosstabBindingModel model = new CrosstabBindingModel();
      model.setOption(new CrosstabOptionInfo());

      BAggregateRefModel calcField = new BAggregateRefModel();
      calcField.setColumnValue("calcField1");
      calcField.setRefType(DataRef.AGG_CALC);
      // No formula and no dataRefModel set -- this is exactly what the client sends
      // for an aggregate-mode calc field with no explicit aggregate formula.
      model.addAggregate(calcField);

      assertDoesNotThrow(() -> factory.updateAssembly(model, assembly));

      VSCrosstabInfo crossInfo = assembly.getVSCrosstabInfo();
      DataRef[] aggregates = crossInfo.getDesignAggregates();
      assertEquals(1, aggregates.length);
      VSAggregateRef aggr = (VSAggregateRef) aggregates[0];
      assertEquals(AggregateFormula.NONE, aggr.getFormula(),
                   "an aggregate calc field is already an aggregated value, so its " +
                   "default formula must be None rather than an arbitrary Sum/Count " +
                   "that would double-aggregate it");
   }
}
