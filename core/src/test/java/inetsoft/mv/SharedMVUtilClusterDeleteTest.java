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
package inetsoft.mv;

import inetsoft.mv.fs.internal.ClusterUtil;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #82223: after a created MV and a stored MV merge, only the removed MV's cluster files may
 * be deleted; the kept MV must keep its files.
 */
@Tag("core")
class SharedMVUtilClusterDeleteTest {
   private MVDef mv(String name) {
      MVDef def = mock(MVDef.class);
      when(def.getName()).thenReturn(name);
      when(def.isWSMV()).thenReturn(false);
      return def;
   }

   private void run(MVDef created, MVDef stored, String removedName) {
      MVManager mgr = mock(MVManager.class);
      when(mgr.list(anyBoolean(), any())).thenReturn(new MVDef[] { stored });

      try(MockedStatic<MVManager> m = mockStatic(MVManager.class);
          MockedStatic<ClusterUtil> c = mockStatic(ClusterUtil.class))
      {
         m.when(MVManager::getManager).thenReturn(mgr);
         SharedMVUtil.shareCreatedMV(created);
         c.verify(() -> ClusterUtil.deleteClusterMV(removedName), times(1));
         c.verify(() -> ClusterUtil.deleteClusterMV(anyString()), times(1));
      }
   }

   @Test
   void createdMVTakesOverDeletesStoredFiles() {
      MVDef created = mv("created");
      MVDef stored = mv("stored");
      when(created.isSharedBy(stored)).thenReturn(true);
      run(created, stored, "stored");
   }

   @Test
   void storedMVTakesOverDeletesCreatedFiles() {
      MVDef created = mv("created");
      MVDef stored = mv("stored");
      when(created.isSharedBy(stored)).thenReturn(false);
      when(stored.isSharedBy(created)).thenReturn(true);
      run(created, stored, "created");
   }
}
