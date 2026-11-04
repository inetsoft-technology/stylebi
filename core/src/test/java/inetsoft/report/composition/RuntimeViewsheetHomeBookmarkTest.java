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
package inetsoft.report.composition;

import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.sree.security.IdentityID;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.test.SwapperTestConfiguration;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.AssetContent;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.util.MessageException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Bug #79993: a Composer (design-mode) runtime sheet never builds the "(Initial)" snapshot
 * ({@code ibookmark} is only built in runtime mode), so Home bookmark access must fall back to the
 * live viewsheet instead of dereferencing the null snapshot.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
               RuntimeViewsheetHomeBookmarkTest.LockConfiguration.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class RuntimeViewsheetHomeBookmarkTest {
   private static final IdentityID OWNER = new IdentityID("tester", "host-org");

   @Configuration
   static class LockConfiguration {
      @Bean
      BookmarkLockManager bookmarkLockManager() {
         return mock(BookmarkLockManager.class);
      }
   }

   private static void set(Object target, Class<?> owner, String name, Object value)
      throws Exception
   {
      Field field = owner.getDeclaredField(name);
      field.setAccessible(true);
      field.set(target, value);
   }

   /** Design-mode runtime sheet: user, vs, bookmarksMap set; ibookmark deliberately left null. */
   private static RuntimeViewsheet designModeSheet(Viewsheet vs, VSBookmark userBookmark,
                                                   AssetRepository rep) throws Exception
   {
      RuntimeViewsheet rvs = new RuntimeViewsheet();
      Map<String, VSBookmark> map = new HashMap<>();
      map.put(OWNER.convertToKey(), userBookmark);
      set(rvs, RuntimeSheet.class, "user", new XPrincipal(OWNER));
      set(rvs, RuntimeViewsheet.class, "vs", vs);
      set(rvs, RuntimeViewsheet.class, "bookmarksMap", map);
      set(rvs, RuntimeViewsheet.class, "mode", Viewsheet.SHEET_DESIGN_MODE);
      set(rvs, RuntimeViewsheet.class, "rep", rep);
      return rvs;
   }

   /** Bug #79993: export (Home) of a saved design-mode sheet is the saved state, not the live one. */
   @Test
   void getOriginalBookmarkHomeInDesignModeUsesSavedState() throws Exception {
      Viewsheet[] sheets = savedAndLive();
      AssetEntry entry = entry(AssetRepository.REPOSITORY_SCOPE);
      AssetRepository rep = mock(AssetRepository.class);
      when(rep.getSheet(eq(entry), any(), anyBoolean(), eq(AssetContent.ALL)))
         .thenReturn(sheets[0]);
      RuntimeViewsheet rvs = designModeSheet(sheets[1], new VSBookmark(), rep);
      set(rvs, RuntimeSheet.class, "entry", entry);

      Viewsheet result = assertDoesNotThrow(() -> rvs.getOriginalBookmark(VSBookmark.HOME_BOOKMARK));

      assertNotNull(result);
      assertEquals(0, selectedCount(result), "saved state has no selection");
      assertEquals(1, selectedCount(sheets[1]), "the live sheet itself is not mutated");
   }

   /** Bug #79993: a never-saved design-mode sheet has no saved Home; export refuses by name. */
   @Test
   void getOriginalBookmarkHomeInDesignModeOfUnsavedSheetFailsLoud() throws Exception {
      Viewsheet[] sheets = savedAndLive();
      RuntimeViewsheet rvs = designModeSheet(sheets[1], new VSBookmark(), mock(AssetRepository.class));
      set(rvs, RuntimeSheet.class, "entry", entry(AssetRepository.TEMPORARY_SCOPE));

      MessageException ex = assertThrows(MessageException.class,
         () -> rvs.getOriginalBookmark(VSBookmark.HOME_BOOKMARK));

      assertTrue(ex.getMessage().contains("export (Home)") && ex.getMessage().contains("saved"),
                 ex.getMessage());
   }

   @Test
   void confirmedHomeUpdateDoesNotThrowWhenInitialSnapshotIsNull() throws Exception {
      VSBookmark fresh = new VSBookmark();
      fresh.setBookmarkData(VSBookmark.HOME_BOOKMARK, new byte[] { 1 });
      fresh.setBookmarkInfo(VSBookmark.HOME_BOOKMARK, new VSBookmarkInfo(
         VSBookmark.HOME_BOOKMARK, VSBookmarkInfo.PRIVATE, OWNER, false, 100L, 100L, 100L));
      AssetRepository rep = mock(AssetRepository.class);
      when(rep.getVSBookmark(any(AssetEntry.class), any(java.security.Principal.class)))
         .thenReturn(fresh);
      RuntimeViewsheet rvs = designModeSheet(mock(Viewsheet.class), new VSBookmark(), rep);
      set(rvs, RuntimeSheet.class, "entry", mock(AssetEntry.class));

      Method method = RuntimeViewsheet.class.getDeclaredMethod(
         "updateBookmark", String.class, IdentityID.class, long.class, boolean.class);
      method.setAccessible(true);

      Object updated = assertDoesNotThrow(
         () -> method.invoke(rvs, VSBookmark.HOME_BOOKMARK, OWNER, 0L, true));
      assertEquals(Boolean.TRUE, updated);
   }

   private static SelectionListVSAssembly selectionList(Viewsheet owner, boolean selectWest) {
      SelectionListVSAssembly list = new SelectionListVSAssembly(owner, "list");
      ColumnRef column = new ColumnRef(new AttributeRef(null, "region"));
      column.setDataType(XSchema.STRING);
      list.setDataRef(column);

      if(selectWest) {
         SelectionList state = new SelectionList();
         SelectionValue west = new SelectionValue("West", "West");
         west.setSelected(true);
         state.addSelectionValue(west);
         list.setStateSelectionList(state);
      }

      return list;
   }

   private static int selectedCount(Viewsheet vs) {
      SelectionList state =
         ((SelectionListVSAssembly) vs.getAssembly("list")).getStateSelectionList();
      return state == null ? 0 : state.getSelectionValues().length;
   }

   /** Saved copy (no selection) and live viewsheet (West selected), as after a set_selection. */
   private static Viewsheet[] savedAndLive() {
      Viewsheet saved = new Viewsheet();
      saved.addAssembly(selectionList(saved, false));
      Viewsheet live = new Viewsheet();
      live.addAssembly(selectionList(live, true));
      return new Viewsheet[] { saved, live };
   }

   private static AssetEntry entry(int scope) {
      AssetEntry entry = mock(AssetEntry.class);
      when(entry.getScope()).thenReturn(scope);
      when(entry.toView()).thenReturn("vs1");
      when(entry.getPath()).thenReturn("examples/vs1");
      return entry;
   }

   /** Spy whose setViewsheet captures the applied viewsheet instead of touching the sandbox. */
   private static RuntimeViewsheet spyWithBox(RuntimeViewsheet rvs, AtomicReference<Viewsheet> out)
      throws Exception
   {
      set(rvs, RuntimeViewsheet.class, "box", mock(ViewsheetSandbox.class));
      RuntimeViewsheet spy = spy(rvs);
      doAnswer(inv -> {
         out.set(inv.getArgument(0));
         return null;
      }).when(spy).setViewsheet(any(Viewsheet.class));
      doReturn(0).when(spy).addCheckpoint(any());
      return spy;
   }

   /** Bug #79947: (Home) in a saved design-mode sheet must drop a selection made after saving. */
   @Test
   void gotoHomeInDesignModeRestoresSavedSelectionState() throws Exception {
      Viewsheet[] sheets = savedAndLive();
      AssetEntry entry = entry(AssetRepository.REPOSITORY_SCOPE);
      AssetRepository rep = mock(AssetRepository.class);
      when(rep.getVSBookmark(any(AssetEntry.class), any(java.security.Principal.class)))
         .thenReturn(new VSBookmark());
      when(rep.getSheet(eq(entry), any(), anyBoolean(), eq(AssetContent.ALL)))
         .thenReturn(sheets[0]);
      RuntimeViewsheet rvs = designModeSheet(sheets[1], new VSBookmark(), rep);
      set(rvs, RuntimeSheet.class, "entry", entry);
      AtomicReference<Viewsheet> applied = new AtomicReference<>();

      assertTrue(spyWithBox(rvs, applied).gotoBookmark(VSBookmark.HOME_BOOKMARK, OWNER, null));

      assertNotNull(applied.get());
      assertEquals(0, selectedCount(applied.get()), "saved state has no selection");
      assertEquals(1, selectedCount(sheets[1]), "the live sheet itself is not mutated");
   }

   /** Bug #79947: nothing is stored for an unsaved sheet, so (Home) must fail loud, not no-op. */
   @Test
   void gotoHomeInDesignModeOfUnsavedSheetFailsLoud() throws Exception {
      Viewsheet[] sheets = savedAndLive();
      AssetRepository rep = mock(AssetRepository.class);
      when(rep.getVSBookmark(any(AssetEntry.class), any(java.security.Principal.class)))
         .thenReturn(new VSBookmark());
      RuntimeViewsheet rvs = designModeSheet(sheets[1], new VSBookmark(), rep);
      set(rvs, RuntimeSheet.class, "entry", entry(AssetRepository.TEMPORARY_SCOPE));
      AtomicReference<Viewsheet> applied = new AtomicReference<>();
      RuntimeViewsheet spy = spyWithBox(rvs, applied);

      MessageException ex = assertThrows(MessageException.class,
         () -> spy.gotoBookmark(VSBookmark.HOME_BOOKMARK, OWNER, null));

      assertTrue(ex.getMessage().contains("(Home)") && ex.getMessage().contains("saved"),
                 ex.getMessage());
      assertNull(applied.get());
   }

   /** Runtime mode keeps using its initial-state snapshot and never reads storage. */
   @Test
   void gotoHomeInRuntimeModeStillUsesInitialSnapshot() throws Exception {
      Viewsheet[] sheets = savedAndLive();
      AssetRepository rep = mock(AssetRepository.class);
      when(rep.getVSBookmark(any(AssetEntry.class), any(java.security.Principal.class)))
         .thenReturn(new VSBookmark());
      RuntimeViewsheet rvs = designModeSheet(sheets[1], new VSBookmark(), rep);
      set(rvs, RuntimeViewsheet.class, "mode", Viewsheet.SHEET_RUNTIME_MODE);
      set(rvs, RuntimeSheet.class, "entry", entry(AssetRepository.REPOSITORY_SCOPE));
      set(rvs, RuntimeViewsheet.class, "originalVs", sheets[1]);
      VSBookmark initial = new VSBookmark();
      initial.addBookmark(VSBookmark.INITIAL_STATE, sheets[0], VSBookmarkInfo.PRIVATE, false, true);
      set(rvs, RuntimeViewsheet.class, "ibookmark", initial);
      AtomicReference<Viewsheet> applied = new AtomicReference<>();

      assertTrue(spyWithBox(rvs, applied).gotoBookmark(VSBookmark.HOME_BOOKMARK, OWNER, null));

      assertEquals(0, selectedCount(applied.get()));
      verify(rep, never()).getSheet(any(), any(), anyBoolean(), any());
   }
}
