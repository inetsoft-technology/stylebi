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
package inetsoft.web.wiz.pairing;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.TextVSAssembly;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.binding.service.VSBindingTreeService;
import inetsoft.web.viewsheet.command.AddVSObjectCommand;
import inetsoft.web.viewsheet.command.LoadTableDataCommand;
import inetsoft.web.viewsheet.command.RemoveVSObjectCommand;
import inetsoft.web.viewsheet.model.VSObjectModel;
import inetsoft.web.viewsheet.model.VSObjectModelFactoryService;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.CommandDispatcherService;
import inetsoft.web.viewsheet.service.CoreLifecycleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.support.GenericMessage;

import java.security.Principal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Bug #81569: an assembly the tools make visible again (set_assembly_properties visible:show, an
 * undone hide) was refreshed but never added to the paired Preview, which ignores a refresh for an
 * object it removed; and an untouched text was refreshed without its runtime display text.
 */
@Tag("core")
class SheetAgentBroadcastReShownAssembly81569Test {
   private static final Principal AGENT = TestPrincipals.user("alice", "host-org");

   private CommandDispatcherService dispatcher;
   private CoreLifecycleService lifecycle;
   private VSObjectModelFactoryService modelFactory;
   private SheetAgentBroadcastService svc;
   private RuntimeViewsheet rvs;
   private Viewsheet vs;

   @BeforeEach
   void setUp() {
      dispatcher = TestDispatchers.live();
      lifecycle = mock(CoreLifecycleService.class);
      modelFactory = mock(VSObjectModelFactoryService.class);
      svc = new SheetAgentBroadcastService(dispatcher, modelFactory,
                                           mock(VSBindingTreeService.class), lifecycle);

      rvs = mock(RuntimeViewsheet.class);
      when(rvs.getID()).thenReturn("vs-1");
      when(rvs.getSocketSessionId()).thenReturn("stomp-1");
      when(rvs.getSocketUserName()).thenReturn("alice~;~host-org");
      when(rvs.isRuntime()).thenReturn(true);

      vs = mock(Viewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
   }

   private <T extends VSAssembly> T assembly(Class<T> type, Viewsheet owner, String name,
                                             boolean visible)
   {
      T a = mock(type);
      when(a.getName()).thenReturn(name);
      when(a.getAbsoluteName()).thenReturn(name);
      when(a.isVisible()).thenReturn(visible);
      when(a.getViewsheet()).thenReturn(owner);
      return a;
   }

   private void assemblies(Assembly... all) {
      when(vs.getAssemblies(true, false)).thenReturn(all);
      when(vs.getAssemblies()).thenReturn(all);
   }

   @Test
   void drawnNamesAreTheAssembliesTheViewerDraws() {
      assemblies(assembly(TextVSAssembly.class, vs, "Shown", true),
                 assembly(TextVSAssembly.class, vs, "Hidden", false));

      assertEquals(Set.of("Shown"), svc.drawnAssemblyNames(rvs));
   }

   @Test
   void aReShownAssemblyIsAddedWithTheNativeAddressAndAKeptOneIsNot() throws Exception {
      TextVSAssembly shown = assembly(TextVSAssembly.class, vs, "Text1", true);
      TextVSAssembly kept = assembly(TextVSAssembly.class, vs, "Text2", true);
      TextVSAssembly stillHidden = assembly(TextVSAssembly.class, vs, "Text3", false);
      assemblies(shown, kept, stillHidden);

      doAnswer(inv -> {
         CommandDispatcher d = inv.getArgument(2);
         // the native service addresses a selection container child to its container
         d.sendCommand("Container1", new AddVSObjectCommand());
         return null;
      }).when(lifecycle).addDeleteVSObject(eq(rvs), eq(shown), any(CommandDispatcher.class),
                                           eq(true));

      List<CommandDispatcher.Command> tableData = svc.broadcastVisibilityChanges(
         rvs, "vs-1", AGENT, Set.of("Text2"), List.of());

      assertTrue(tableData.isEmpty());
      verify(lifecycle).addDeleteVSObject(eq(rvs), eq(shown), any(CommandDispatcher.class),
                                          eq(true));
      verify(lifecycle, never()).addDeleteVSObject(eq(rvs), eq(kept),
                                                    any(CommandDispatcher.class), anyBoolean());
      verify(lifecycle, never()).addDeleteVSObject(eq(rvs), eq(stillHidden),
                                                    any(CommandDispatcher.class), anyBoolean());

      ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
      ArgumentCaptor<MessageHeaders> headers = ArgumentCaptor.forClass(MessageHeaders.class);
      verify(dispatcher).convertAndSendToUser(
         eq("alice~;~host-org"), eq(CommandDispatcher.COMMANDS_TOPIC), payload.capture(),
         headers.capture());
      assertInstanceOf(AddVSObjectCommand.class, payload.getValue());

      SimpMessageHeaderAccessor acc = SimpMessageHeaderAccessor.wrap(
         new GenericMessage<>("", headers.getValue()));
      assertEquals("stomp-1", SimpMessageHeaderAccessor.getSessionId(headers.getValue()));
      assertEquals("vs-1", acc.getNativeHeader(CommandDispatcher.RUNTIME_ID_ATTR).get(0));
      assertEquals("Container1", acc.getNativeHeader(CommandDispatcher.ASSEMBLY_NAME_HEADER).get(0));
   }

   @Test
   void withoutASnapshotNothingIsAdded() throws Exception {
      assemblies(assembly(TextVSAssembly.class, vs, "Text1", true));

      svc.broadcastVisibilityChanges(rvs, "vs-1", AGENT, null, List.of());

      verifyNoInteractions(lifecycle);
      verify(dispatcher, never()).convertAndSendToUser(any(), any(), any(), any());
   }

   /** Undo of a show and redo of a hide restore a hidden state the viewer was never told about. */
   @Test
   void anAssemblyDrawnBeforeAndHiddenNowIsRemovedWithTheNativeAddress() throws Exception {
      TextVSAssembly hidden = assembly(TextVSAssembly.class, vs, "Text1", false);
      TextVSAssembly kept = assembly(TextVSAssembly.class, vs, "Text2", true);
      assemblies(hidden, kept);

      doAnswer(inv -> {
         CommandDispatcher d = inv.getArgument(2);
         RemoveVSObjectCommand remove = new RemoveVSObjectCommand();
         remove.setName("Text1");
         d.sendCommand("Container1", remove);
         return null;
      }).when(lifecycle).addDeleteVSObject(eq(rvs), eq(hidden), any(CommandDispatcher.class),
                                           eq(true));

      List<CommandDispatcher.Command> tableData = svc.broadcastVisibilityChanges(
         rvs, "vs-1", AGENT, Set.of("Text1", "Text2"), List.of());

      assertTrue(tableData.isEmpty());
      verify(lifecycle).addDeleteVSObject(eq(rvs), eq(hidden), any(CommandDispatcher.class),
                                          eq(true));
      verify(lifecycle, never()).addDeleteVSObject(eq(rvs), eq(kept),
                                                    any(CommandDispatcher.class), anyBoolean());
      verify(lifecycle, never()).initTable(any(), any(), any(), any());

      ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
      ArgumentCaptor<MessageHeaders> headers = ArgumentCaptor.forClass(MessageHeaders.class);
      verify(dispatcher).convertAndSendToUser(
         eq("alice~;~host-org"), eq(CommandDispatcher.COMMANDS_TOPIC), payload.capture(),
         headers.capture());
      assertInstanceOf(RemoveVSObjectCommand.class, payload.getValue());
      SimpMessageHeaderAccessor acc = SimpMessageHeaderAccessor.wrap(
         new GenericMessage<>("", headers.getValue()));
      assertEquals("Container1", acc.getNativeHeader(CommandDispatcher.ASSEMBLY_NAME_HEADER).get(0));
   }

   /** A hide through the tools already produced its Remove; the broadcast forwards that one. */
   @Test
   void anAssemblyTheMutationAlreadyRemovedIsNotRemovedAgain() throws Exception {
      TextVSAssembly hidden = assembly(TextVSAssembly.class, vs, "Text1", false);
      assemblies(hidden);

      RemoveVSObjectCommand remove = new RemoveVSObjectCommand();
      remove.setName("Text1");
      CommandDispatcher.Command produced =
         new CommandDispatcher.Command("", "RemoveVSObjectCommand", remove);

      svc.broadcastVisibilityChanges(rvs, "vs-1", AGENT, Set.of("Text1"), List.of(produced));

      verifyNoInteractions(lifecycle);
      verify(dispatcher, never()).convertAndSendToUser(any(), any(), any(), any());
   }

   /** A deleted assembly is not on the sheet any more; the removal diff of undo/redo owns it. */
   @Test
   void aDeletedAssemblyIsLeftToTheRemovalDiff() throws Exception {
      assemblies(assembly(TextVSAssembly.class, vs, "Text2", true));

      svc.broadcastVisibilityChanges(rvs, "vs-1", AGENT, Set.of("Gone", "Text2"), List.of());

      verifyNoInteractions(lifecycle);
   }

   @Test
   void theChildrenOfAHiddenEmbeddedViewsheetAreRemovedByTheEmbeddedViewsheetOnly()
      throws Exception
   {
      Viewsheet embedded = assembly(Viewsheet.class, vs, "Embed1", false);
      TextVSAssembly child = assembly(TextVSAssembly.class, embedded, "Embed1.Text1", false);
      assemblies(embedded, child);

      svc.broadcastVisibilityChanges(rvs, "vs-1", AGENT, Set.of("Embed1", "Embed1.Text1"),
                                     List.of());

      verify(lifecycle).addDeleteVSObject(eq(rvs), eq(embedded), any(CommandDispatcher.class),
                                          eq(true));
      verify(lifecycle, never()).addDeleteVSObject(eq(rvs), eq(child),
                                                    any(CommandDispatcher.class), anyBoolean());
   }

   @Test
   void theChildrenOfAReShownEmbeddedViewsheetAreAddedByTheEmbeddedViewsheetOnly()
      throws Exception
   {
      Viewsheet embedded = assembly(Viewsheet.class, vs, "Embed1", true);
      TextVSAssembly child = assembly(TextVSAssembly.class, embedded, "Embed1.Text1", true);
      assemblies(embedded, child);

      svc.broadcastVisibilityChanges(rvs, "vs-1", AGENT, Set.of(), List.of());

      verify(lifecycle).addDeleteVSObject(eq(rvs), eq(embedded), any(CommandDispatcher.class),
                                          eq(true));
      verify(lifecycle, never()).addDeleteVSObject(eq(rvs), eq(child),
                                                    any(CommandDispatcher.class), anyBoolean());
   }

   @Test
   void aReShownTableLoadsItsDataUnlessTheMutationAlreadyProducedIt() throws Exception {
      TableVSAssembly fresh = assembly(TableVSAssembly.class, vs, "Table1", true);
      TableVSAssembly loaded = assembly(TableVSAssembly.class, vs, "Table2", true);
      assemblies(fresh, loaded);

      CommandDispatcher.Command produced = new CommandDispatcher.Command(
         "Table2", "LoadTableDataCommand", mock(LoadTableDataCommand.class));

      doAnswer(inv -> {
         CommandDispatcher d = inv.getArgument(1);
         d.sendCommand("Table1", mock(LoadTableDataCommand.class));
         return null;
      }).when(lifecycle).initTable(eq(rvs), any(CommandDispatcher.class), eq(""), eq(fresh));

      List<CommandDispatcher.Command> tableData = svc.broadcastVisibilityChanges(
         rvs, "vs-1", AGENT, Set.of(), List.of(produced));

      verify(lifecycle).initTable(eq(rvs), any(CommandDispatcher.class), eq(""), eq(fresh));
      verify(lifecycle, never()).initTable(eq(rvs), any(CommandDispatcher.class), eq(""),
                                           eq(loaded));
      // returned for the caller to send after the refresh that carries the table model
      assertEquals(1, tableData.size());
      assertEquals("Table1", tableData.get(0).getAssembly());
      verify(dispatcher, never()).convertAndSendToUser(any(), any(), any(), any());
   }

   @Test
   void aFailureWhileAddingIsSwallowed() throws Exception {
      TextVSAssembly shown = assembly(TextVSAssembly.class, vs, "Text1", true);
      assemblies(shown);
      doThrow(new IllegalStateException("boom")).when(lifecycle)
         .addDeleteVSObject(any(), any(), any(CommandDispatcher.class), anyBoolean());

      assertDoesNotThrow(() -> svc.broadcastVisibilityChanges(
         rvs, "vs-1", AGENT, Set.of(), List.of()));
   }

   @Test
   void theRuntimeTextStateIsPreparedBeforeEachVisibleModelIsBuilt() throws Exception {
      TextVSAssembly untouched = assembly(TextVSAssembly.class, vs, "Text2", true);
      assemblies(untouched);
      inetsoft.report.composition.execution.ViewsheetSandbox box =
         mock(inetsoft.report.composition.execution.ViewsheetSandbox.class);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(box));
      when(modelFactory.createModel(untouched, rvs)).thenReturn(mock(VSObjectModel.class));

      svc.broadcastRefresh(rvs, SheetType.VIEWSHEET, "vs-1", AGENT);

      InOrder order = inOrder(lifecycle, modelFactory);
      order.verify(lifecycle).prepareOutputAssemblyForRefresh(untouched, rvs, box);
      order.verify(modelFactory).createModel(untouched, rvs);
   }
}
