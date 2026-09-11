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
import inetsoft.uql.viewsheet.CrosstabVSAssembly;
import inetsoft.uql.viewsheet.VSCrosstabInfo;
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
}
