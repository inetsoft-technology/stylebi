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

import inetsoft.sree.security.IdentityID;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.viewsheet.VSBookmark;
import inetsoft.uql.viewsheet.VSBookmarkInfo;
import inetsoft.uql.viewsheet.Viewsheet;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #79993: a Composer (design-mode) runtime sheet never builds the "(Initial)" snapshot
 * ({@code ibookmark} is only built in runtime mode), so Home bookmark access must fall back to the
 * live viewsheet instead of dereferencing the null snapshot.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class RuntimeViewsheetHomeBookmarkTest {
   private static final IdentityID OWNER = new IdentityID("tester", "host-org");

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

   @Test
   void getOriginalBookmarkHomeFallsBackToLiveViewsheetWhenInitialSnapshotIsNull()
      throws Exception
   {
      Viewsheet vs = mock(Viewsheet.class);
      Viewsheet clone = mock(Viewsheet.class);
      when(vs.clone()).thenReturn(clone);
      RuntimeViewsheet rvs = designModeSheet(vs, new VSBookmark(), null);

      Viewsheet result = assertDoesNotThrow(() -> rvs.getOriginalBookmark(VSBookmark.HOME_BOOKMARK));

      assertNotNull(result);
      assertSame(clone, result, "with no Home annotations the live-viewsheet clone is returned");
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
}
