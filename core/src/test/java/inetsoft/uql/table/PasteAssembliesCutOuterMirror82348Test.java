/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.uql.table;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.WorksheetService;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.storage.KeyValueStorageManager;
import inetsoft.test.*;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.asset.sync.*;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.web.composer.ws.PasteAssembliesService;
import inetsoft.web.composer.ws.PasteAssembliesServiceProxy;
import inetsoft.web.composer.ws.assembly.WorksheetEventUtil;
import inetsoft.web.composer.ws.event.WSPasteAssembliesEvent;
import inetsoft.web.composer.ws.joins.InnerJoinService;
import inetsoft.web.wiz.pairing.*;
import inetsoft.web.wiz.worksheet.PasteAssembliesOutcome;
import inetsoft.web.wiz.worksheet.WorksheetEditService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Constructor;
import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Bug #82348: a {@code paste_assemblies} cut of a mirror of another worksheet into a second
 * worksheet pastes the mirror with its hidden copies and removes the mirror, and with it its
 * hidden copies, from the source. The copies were never requested by name, so they are not
 * deleted on their own: deleting the mirror already removes them, and a second delete would
 * fail or be refused while the mirror still read them.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  PasteAssembliesCutOuterMirror82348Test.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("slow") // the Spring context with the real asset repository
class PasteAssembliesCutOuterMirror82348Test {
   private static final String MIRROR = "M";
   private static final String OWNER_TABLE = "T";
   private static final String SOURCE = "Worksheet/source";
   private static final String TARGET = "Worksheet/target";

   @Autowired
   private WorksheetService worksheetService;

   @Test
   void cuttingAMirrorOfAnotherWorksheetMovesItWithItsCopies() throws Exception {
      AssetEntry owner = entry("pcom82348_owner");
      AssetEntry source = entry("pcom82348_source");
      AssetEntry target = entry("pcom82348_target");
      saveOwner(owner);
      save(embed(owner), source);
      Worksheet sws = (Worksheet) repository().getStorage(source)
         .getXMLSerializable(source.toIdentifier(), null);
      Worksheet tws = new Worksheet();
      // a frozen copy's data has to be loaded before the paste, as opening the sheet does
      for(Assembly a : sws.getAssemblies()) {
         if(a instanceof SnapshotEmbeddedTableAssembly s && s.isOuter()) {
            s.getTable();
         }
      }

      int sourceCount = sws.getAssemblies().length;
      assertTrue(sourceCount >= 2, "the source needs the mirror and its copy: " + names(sws));

      PasteAssembliesOutcome out;

      try(MockedStatic<WorksheetEventUtil> ignored = mockStatic(WorksheetEventUtil.class)) {
         out = service(sws, source, tws, target).pasteAssemblies(
            "TOK2", "TOK1", true, List.of(MIRROR + 0), null, null, principal());
      }

      assertEquals(1, out.pasted().size(), out.toString());
      assertFalse(out.alsoCopied().isEmpty(), "the mirror's own copies come along: " + out);
      assertTrue(out.skipped().isEmpty(), out.toString());
      assertTrue(out.sourceNotRemoved() == null || out.sourceNotRemoved().isEmpty(),
                 "the mirror's copies are removed with it, not a second time: " + out);
      assertEquals(List.of(), names(sws), "the source keeps " + names(sws));
      assertEquals(sourceCount, tws.getAssemblies().length, names(tws).toString());
      assertInstanceOf(MirrorAssembly.class, tws.getAssembly(out.pasted().get(0).to()));
   }

   private WorksheetEditService service(Worksheet sws, AssetEntry source, Worksheet tws,
                                        AssetEntry target)
      throws Exception
   {
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.checkPermission(
         any(), any(ResourceType.class), any(String.class), any(ResourceAction.class)))
         .thenReturn(true);
      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess runtimeAccess = mock(SheetRuntimeAccess.class);
      SheetAgentBroadcastService broadcast = mock(SheetAgentBroadcastService.class);
      RuntimeWorksheet srws = runtime(sws, SOURCE, source);
      RuntimeWorksheet trws = runtime(tws, TARGET, target);
      when(sessions.resolve(eq("TOK1"), any())).thenReturn(session("TOK1", SOURCE));
      when(sessions.resolve(eq("TOK2"), any())).thenReturn(session("TOK2", TARGET));
      when(runtimeAccess.getSheetForPairing(eq(SheetType.WORKSHEET), eq(SOURCE), any()))
         .thenReturn(srws);
      when(runtimeAccess.getSheetForPairing(eq(SheetType.WORKSHEET), eq(TARGET), any()))
         .thenReturn(trws);
      ViewsheetService engine = mock(ViewsheetService.class);
      when(engine.getWorksheet(eq(SOURCE), any())).thenReturn(srws);
      when(engine.getWorksheet(eq(TARGET), any())).thenReturn(trws);
      PasteAssembliesService paste = new PasteAssembliesService(engine, null);
      PasteAssembliesServiceProxy proxy = mock(PasteAssembliesServiceProxy.class);
      when(proxy.pasteAssemblies(any(), any(WSPasteAssembliesEvent.class), any(), any()))
         .thenAnswer(inv -> paste.pasteAssemblies(
            inv.getArgument(0), inv.getArgument(1), inv.getArgument(2), inv.getArgument(3)));
      return new WorksheetEditService(sessions, runtimeAccess, broadcast, securityEngine,
                                      new InnerJoinService(null, null), null, null, null, proxy);
   }

   private static Principal principal() {
      return TestPrincipals.user("alice", "host-org");
   }

   private static RuntimeWorksheet runtime(Worksheet ws, String id, AssetEntry entry) {
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      when(rws.getWorksheet()).thenReturn(ws);
      when(rws.getID()).thenReturn(id);
      when(rws.getEntry()).thenReturn(entry);
      return rws;
   }

   private static JoinSession session(String token, String runtimeId) {
      return new JoinSession(token, runtimeId, "alice~;~host-org", SheetType.WORKSHEET, 0L,
                             Long.MAX_VALUE, JoinSession.ConnectionMode.PAIRED, null, null, null);
   }

   /** Embeds the worksheet as dragging it into a worksheet in the composer does (frozen). */
   private static Worksheet embed(AssetEntry entry) throws Exception {
      Worksheet ws = new Worksheet();
      WSAssembly[] created = AssetUtil.copyOuterAssemblies(repository(), entry, null, ws, null);
      MirrorTableAssembly mirror = new MirrorTableAssembly(
         ws, MIRROR + 0, entry, true, created[created.length - 1]);
      mirror.setAutoUpdate(false);
      ws.addAssembly(mirror);
      ws.setPrimaryAssembly(MIRROR + 0);
      return ws;
   }

   private void saveOwner(AssetEntry entry) throws Exception {
      Worksheet ws = new Worksheet();
      SnapshotEmbeddedTableAssembly table = new SnapshotEmbeddedTableAssembly(ws, OWNER_TABLE);
      ws.addAssembly(table);
      ws.setPrimaryAssembly(OWNER_TABLE);
      XSwappableTable data = new XSwappableTable(2, false);
      data.addRow(new Object[] { "a", "b" });
      data.addRow(new Object[] { 1, "x" });
      data.complete();
      table.setEmbeddedData(new XEmbeddedTable(data));
      save(ws, entry);
   }

   private void save(Worksheet ws, AssetEntry entry) throws Exception {
      worksheetService.setWorksheet(ws, entry, null, true, true);
   }

   private static List<String> names(Worksheet ws) {
      return Arrays.stream(ws.getAssemblies()).map(Assembly::getName)
         .collect(Collectors.toList());
   }

   private static AbstractAssetEngine repository() {
      return (AbstractAssetEngine) AssetUtil.getAssetRepository(false);
   }

   private static AssetEntry entry(String name) {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET, name, null);
   }

   @Configuration
   static class Beans {
      // the constructor is package private; saving a sheet updates the dependency storage
      @Bean
      public DependencyStorageService dependencyStorageService(KeyValueStorageManager storage)
         throws Exception
      {
         Constructor<DependencyStorageService> ctor =
            DependencyStorageService.class.getDeclaredConstructor(KeyValueStorageManager.class);
         ctor.setAccessible(true);
         return ctor.newInstance(storage);
      }

      // the real dependency handler (the base configuration mocks it)
      @Bean
      @Primary
      public DependencyHandler realDependencyHandler(XRepository xRepository) {
         return new LocalDependencyHandler(xRepository);
      }

      // the real rename transform, run right away instead of through the cluster queue
      @Bean
      public RenameTransformHandler renameTransformHandler() {
         return new RenameTransformHandler(null, null) {
            @Override
            public void addTransformTask(RenameDependencyInfo dinfo, boolean waitDone) {
               DependencyTransformer.renameDep(dinfo);
            }
         };
      }
   }
}
