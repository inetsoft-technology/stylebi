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
package inetsoft.web.viewsheet.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.util.XSessionService;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.composer.ws.assembly.VariableAssemblyModelInfo;
import inetsoft.web.viewsheet.event.CollectParametersOverEvent;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.CoreLifecycleService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77429: /vs/collectParameters tested and connected any data source named by a client
 * {@code _Db_Password_<name>} variable, so a viewer could test credentials against any data
 * source. Only the db login variables the viewsheet's own worksheets (root and embedded) prompt
 * for may be accepted.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class VSCollectParametersDbLoginTest {
   @BeforeEach
   void setUp() {
      viewsheetService = mock(ViewsheetService.class);
      xRepository = mock(XRepository.class);
      service = new VSCollectParametersService(
         mock(CoreLifecycleService.class), viewsheetService, mock(AssetRepository.class),
         mock(XSessionService.class), xRepository);
   }

   // the db logins the root and embedded worksheets prompt for still log in, while a
   // forged one for another data source is ignored
   @Test
   void collectParametersAcceptsOnlyPromptedDbLogins() throws Exception {
      JDBCDataSource rootDs = mock(JDBCDataSource.class);
      JDBCDataSource embeddedDs = mock(JDBCDataSource.class);
      when(xRepository.getDataSource("RootDs")).thenReturn(rootDs);
      when(xRepository.getDataSource("F/EmbeddedDs")).thenReturn(embeddedDs);

      Viewsheet embedded = mock(Viewsheet.class);
      when(embedded.getAbsoluteName()).thenReturn("Embedded1");
      when(embedded.getAssemblies()).thenReturn(new Assembly[0]);
      Viewsheet root = mock(Viewsheet.class);
      when(root.getAssemblies()).thenReturn(new Assembly[] { embedded });

      ViewsheetSandbox embeddedBox = sandbox("_Db_User_F/EmbeddedDs", "_Db_Password_F/EmbeddedDs");
      ViewsheetSandbox rootBox = sandbox("_Db_Password_RootDs", "region");
      when(rootBox.getSandbox("Embedded1")).thenReturn(embeddedBox);

      XPrincipal user = mock(XPrincipal.class);
      AssetEntry entry = mock(AssetEntry.class);
      when(entry.getSheetName()).thenReturn("plain");
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getUser()).thenReturn(user);
      when(rvs.getViewsheet()).thenReturn(root);
      when(rvs.getEntry()).thenReturn(entry);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(rootBox));
      when(viewsheetService.getViewsheet("vs-1", user)).thenReturn(rvs);

      CollectParametersOverEvent event = CollectParametersOverEvent.builder()
         .disableAudit(true)
         .variables(List.of(
            variable("_Db_User_RootDs", "root"),
            variable("_Db_Password_RootDs", "rootpw"),
            variable("_Db_User_F/EmbeddedDs", "emb"),
            variable("_Db_Password_F/EmbeddedDs", "embpw"),
            variable("_Db_User_Secret", "sa"),
            variable("_Db_Password_Secret", "guess")))
         .build();

      service.collectParameters("vs-1", event, user, null, mock(CommandDispatcher.class));

      verify(xRepository).testDataSource(any(), same(embeddedDs), any());
      verify(xRepository).connect(any(), eq(":F/EmbeddedDs"), any());
      verify(user).setProperty("_Db_User_F/EmbeddedDs", "emb");
      verify(user).setProperty("_Db_Password_F/EmbeddedDs", "embpw");
      // the root worksheet prompts only for the password, the stored user is not overridden
      verify(xRepository).testDataSource(any(), same(rootDs), any());
      verify(xRepository).connect(any(), eq(":RootDs"), any());
      verify(user, never()).setProperty(eq("_Db_User_RootDs"), anyString());

      verify(xRepository, never()).getDataSource("Secret");
      verify(xRepository, never()).connect(any(), eq(":Secret"), any());
      verify(user, never()).setProperty(contains("Secret"), anyString());
      verify(viewsheetService, never())
         .setCachedProperty(any(), contains("Secret"), any());
   }

   private static ViewsheetSandbox sandbox(String... variableNames) {
      UserVariable[] vars = Arrays.stream(variableNames)
         .map(UserVariable::new)
         .toArray(UserVariable[]::new);
      Worksheet ws = mock(Worksheet.class);
      when(ws.getAllVariables()).thenReturn(vars);
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      when(box.getWorksheet()).thenReturn(ws);
      ViewsheetSandbox vbox = mock(ViewsheetSandbox.class);
      when(vbox.getAssetQuerySandbox()).thenReturn(box);
      return vbox;
   }

   private static VariableAssemblyModelInfo variable(String name, String value) {
      VariableAssemblyModelInfo info = new VariableAssemblyModelInfo();
      info.setName(name);
      info.setType("string");
      info.setValue(new Object[] { value });
      return info;
   }

   private ViewsheetService viewsheetService;
   private XRepository xRepository;
   private VSCollectParametersService service;
}
