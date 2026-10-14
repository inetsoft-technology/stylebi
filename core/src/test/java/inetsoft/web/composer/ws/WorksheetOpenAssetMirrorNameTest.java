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
package inetsoft.web.composer.ws;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.analytic.composition.event.VSEventUtil;
import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.test.*;
import inetsoft.uql.asset.*;
import inetsoft.web.composer.ws.assembly.WorksheetEventUtil;
import inetsoft.web.composer.ws.command.*;
import inetsoft.web.composer.ws.event.ImmutableOpenAssetEvent;
import inetsoft.web.composer.ws.event.OpenAssetEvent;
import inetsoft.web.viewsheet.command.ViewsheetCommand;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78315: after dropping a worksheet, or another worksheet's columns, onto a worksheet, the
 * new mirror must be loaded, focused and refreshed by its own name, not by the name of the
 * hidden OUTER_ assembly it mirrors ({@link MirrorAssembly#getAssemblyName()}).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class WorksheetOpenAssetMirrorNameTest {
   private static final String RUNTIME_ID = "ws1";

   private AssetRepository assetRepository;
   private Worksheet worksheet;
   private WorksheetOpenAssetService service;
   private CommandDispatcher dispatcher;
   private final Principal principal = () -> "bob";

   @BeforeEach
   void setUp() throws Exception {
      assetRepository = mock(AssetRepository.class);
      worksheet = new Worksheet();
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(worksheet);
      // skips the column selection query of a new mirror table, which needs a sandbox
      when(rws.isDisposed()).thenReturn(true);
      when(rws.getEntry()).thenReturn(new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET, "Target", null));
      ViewsheetService viewsheetService = mock(ViewsheetService.class);
      when(viewsheetService.getWorksheet(RUNTIME_ID, principal)).thenReturn(rws);
      when(viewsheetService.getAssetRepository()).thenReturn(assetRepository);
      service = new WorksheetOpenAssetService(viewsheetService, null, null);
      dispatcher = mock(CommandDispatcher.class);
   }

   /** Stores a worksheet whose primary assembly is the given one, and returns its entry. */
   private AssetEntry storeWorksheet(String name, WSAssembly primary) throws Exception {
      Worksheet sheet = primary.getWorksheet();
      sheet.addAssembly(primary);
      sheet.setPrimaryAssembly(primary.getName());
      AssetEntry entry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET, name, null);
      when(assetRepository.getSheet(eq(entry), any(), anyBoolean(), any())).thenReturn(sheet);
      return entry;
   }

   private AssetEntry storeTableWorksheet() throws Exception {
      return storeWorksheet("Other", new EmbeddedTableAssembly(new Worksheet(), "Query1"));
   }

   private void openAsset(AssetEntry... entries) throws Exception {
      OpenAssetEvent event =
         ImmutableOpenAssetEvent.builder().entries(entries).top(250).left(10).build();
      service.openAsset(RUNTIME_ID, event, principal, dispatcher);
   }

   /**
    * Drops the entries with WorksheetEventUtil stubbed out, and asserts that the mirror of the
    * given name is the one loaded, focused and refreshed, and that no OUTER_ assembly is.
    */
   private void assertDropFocusesAndRefreshes(String name, AssetEntry... entries)
      throws Exception
   {
      try(MockedStatic<WorksheetEventUtil> util = mockStatic(WorksheetEventUtil.class)) {
         openAsset(entries);

         util.verify(() -> WorksheetEventUtil.loadTableData(any(), eq(name), eq(false), eq(false)));
         util.verify(() -> WorksheetEventUtil.focusAssembly(name, dispatcher));
         util.verify(() -> WorksheetEventUtil.refreshAssembly(
            any(), eq(name), eq(false), same(dispatcher), same(principal)));
         util.verify(() -> WorksheetEventUtil.focusAssembly(startsWith("OUTER_"), any()),
                     never());
      }
   }

   private MirrorAssembly mirror(String name) {
      Assembly assembly = worksheet.getAssembly(name);
      assertInstanceOf(MirrorAssembly.class, assembly);
      assertTrue(assembly.isVisible());
      return (MirrorAssembly) assembly;
   }

   @Test
   void droppingAWorksheetFocusesAndRefreshesTheNewMirror() throws Exception {
      AssetEntry other = storeTableWorksheet();

      assertDropFocusesAndRefreshes("Query11", other);
      assertTrue(mirror("Query11").getAssemblyName().startsWith("OUTER_"));
   }

   @Test
   void droppingAWorksheetAgainFocusesTheSecondMirror() throws Exception {
      AssetEntry other = storeTableWorksheet();
      assertDropFocusesAndRefreshes("Query11", other);

      // the second drop copies the existing mirror, so both mirrors wrap the same OUTER_ copy
      assertDropFocusesAndRefreshes("Query12", other);
      assertEquals(mirror("Query11").getAssemblyName(), mirror("Query12").getAssemblyName());
   }

   @Test
   void droppingAWorksheetWithAVariablePrimaryFocusesTheMirroredVariable() throws Exception {
      DefaultVariableAssembly variable = new DefaultVariableAssembly(new Worksheet(), "Var1");
      variable.setVariable(new AssetVariable("Var1"));
      AssetEntry other = storeWorksheet("OtherVariable", variable);

      assertDropFocusesAndRefreshes("Var1", other);
      assertInstanceOf(MirrorVariableAssembly.class, mirror("Var1"));
      assertTrue(mirror("Var1").getAssemblyName().startsWith("OUTER_"));
   }

   @Test
   void droppingTheColumnsOfAnotherWorksheetFocusesTheNewMirror() throws Exception {
      AssetEntry other = storeTableWorksheet();
      AssetEntry column = new AssetEntry(
         AssetRepository.QUERY_SCOPE, AssetEntry.Type.COLUMN, "Query1/col1", null);
      column.setProperty("source", VSEventUtil.BASE_WORKSHEET);
      column.setProperty("ws", other.toIdentifier());
      column.setProperty("attribute", "col1");

      assertDropFocusesAndRefreshes("Query11", column);
   }

   /**
    * Drops the entries with the real WorksheetEventUtil, so the new mirror is actually validated
    * and refreshed, and returns the commands sent to the client.
    */
   private List<ViewsheetCommand> dropAndCaptureCommands(AssetEntry... entries)
      throws Exception
   {
      clearInvocations(dispatcher);
      openAsset(entries);
      ArgumentCaptor<ViewsheetCommand> commands = ArgumentCaptor.forClass(ViewsheetCommand.class);
      verify(dispatcher, atLeastOnce()).sendCommand(commands.capture());
      return commands.getAllValues();
   }

   /** Asserts that the client is sent the new mirror, focus on it and a refresh of it. */
   private void assertAddFocusAndRefresh(List<ViewsheetCommand> commands, String name) {
      List<String> added = commands.stream()
         .filter(WSAddAssemblyCommand.class::isInstance)
         .map(c -> ((WSAddAssemblyCommand) c).assembly().getName())
         .toList();
      List<List<String>> focused = commands.stream()
         .filter(WSFocusAssembliesCommand.class::isInstance)
         .map(c -> ((WSFocusAssembliesCommand) c).assemblyNames())
         .toList();
      List<String> refreshed = commands.stream()
         .filter(WSRefreshAssemblyCommand.class::isInstance)
         .map(c -> ((WSRefreshAssemblyCommand) c).getOldName() + "->" +
                   ((WSRefreshAssemblyCommand) c).getAssembly().getName())
         .toList();

      assertEquals(List.of(name), added);
      assertEquals(List.of(List.of(name)), focused);
      assertEquals(List.of(name + "->" + name), refreshed);
   }

   @Test
   void droppingAWorksheetTwiceSendsTheFocusAndRefreshOfEachNewMirror() throws Exception {
      AssetEntry other = storeTableWorksheet();

      assertAddFocusAndRefresh(dropAndCaptureCommands(other), "Query11");
      assertAddFocusAndRefresh(dropAndCaptureCommands(other), "Query12");
   }
}
