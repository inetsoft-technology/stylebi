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
package inetsoft.web.binding.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.asset.NamedGroupInfo;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.web.binding.event.ApplyVSAssemblyInfoEvent;
import inetsoft.web.binding.handler.CrosstabBindingHandler;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.binding.model.table.CrosstabBindingModel;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.viewsheet.controller.table.BaseTableService;
import inetsoft.web.viewsheet.model.VSObjectModelFactoryService;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.vswizard.service.VSWizardTemporaryInfoService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Bug #79017: the production {@code setBinding} hook strips the named group of exactly the
 * requested dimensions from the clone the factory restores from.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class VSBindingModelServiceUngroupTest {
   @Test
   void setBindingHandsTheFactoryAnOldRefWithoutTheClearedGroupOnly() throws Exception {
      CrosstabVSAssembly assembly = new CrosstabVSAssembly();
      VSCrosstabInfo info = assembly.getVSCrosstabInfo();
      info.setDesignRowHeaders(new DataRef[]{ grouped("REGION"), grouped("STATE") });
      info.setDesignColHeaders(new DataRef[]{ grouped("REGION") });

      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getAssembly("Crosstab1")).thenReturn(assembly);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      ViewsheetService engine = mock(ViewsheetService.class);
      when(engine.getViewsheet(anyString(), any())).thenReturn(rvs);

      List<Boolean> seen = new ArrayList<>();
      VSBindingService bfactory = mock(VSBindingService.class);
      when(bfactory.updateAssembly(any(), any(), any())).thenAnswer(invocation -> {
         VSAssembly clone = invocation.getArgument(1);
         VSCrosstabInfo cloned = ((CrosstabVSAssembly) clone).getVSCrosstabInfo();

         for(DataRef ref : cloned.getRowHeaders()) {
            seen.add(((VSDimensionRef) ref).getNamedGroupInfo() != null);
         }

         for(DataRef ref : cloned.getColHeaders()) {
            seen.add(((VSDimensionRef) ref).getNamedGroupInfo() != null);
         }

         return clone;
      });

      VSBindingModelService service = new VSBindingModelService(
         bfactory, mock(VSAssemblyInfoHandler.class), mock(CrosstabBindingHandler.class),
         mock(VSObjectModelFactoryService.class), mock(VSWizardTemporaryInfoService.class),
         engine);

      ApplyVSAssemblyInfoEvent event = new ApplyVSAssemblyInfoEvent();
      event.setName("Crosstab1");
      event.setBinding(new CrosstabBindingModel());
      event.setUngroupDimensions(List.of("rows:region"));

      try(MockedStatic<BaseTableService> table = mockStatic(BaseTableService.class)) {
         service.setBinding("rt1", event, (Principal) () -> "admin", mock(CommandDispatcher.class));
      }

      // rows: REGION stripped, STATE kept; cols: REGION on another shelf kept
      assertEquals(List.of(false, true, true), seen);
      assertNotNull(((VSDimensionRef) info.getRowHeaders()[0]).getNamedGroupInfo(),
                    "the live assembly is untouched; only the clone is stripped");
   }

   private static VSDimensionRef grouped(String column) {
      VSDimensionRef ref = new VSDimensionRef(new AttributeRef(null, column));
      ref.setGroupColumnValue(column);
      ref.setNamedGroupInfo(new NamedGroupInfo());
      return ref;
   }
}
