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
package inetsoft.mv.data;

import inetsoft.util.script.graal.pool.PoolTestSupport;
import inetsoft.util.script.graal.pool.WorksheetScriptEnv;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Spec §6.6 (bug #76960): in pool mode an MV condition script reaches MV through its exec
 * scope, not an env global that every context would replay.
 */
@Tag("core")
class MVExecScopeTest {
   @Test
   void mvResolvesThroughTheExecScope() throws Exception {
      WorksheetScriptEnv env = PoolTestSupport.env();
      PoolTestSupport.MapScope parent = new PoolTestSupport.MapScope();
      parent.putMember("p", 2);
      PoolTestSupport.MapScope mv = new PoolTestSupport.MapScope();
      mv.putMember("x", 3);
      long version = env.getStateVersion();

      Object result = env.exec(env.compile("MV.x + p"),
                               new MVConditionListHandler.MVExecScope(mv, parent), null, null);

      assertEquals(5.0, result);
      assertNull(env.get("MV"));
      assertEquals(version, env.getStateVersion(), "no env variable was written");
   }

   @Test
   void otherWritesGoToTheParentScope() throws Exception {
      WorksheetScriptEnv env = PoolTestSupport.env();
      PoolTestSupport.MapScope parent = new PoolTestSupport.MapScope();
      parent.putMember("p", 2);
      env.exec(env.compile("p = 4; 1"),
               new MVConditionListHandler.MVExecScope(new PoolTestSupport.MapScope(), parent),
               null, null);
      assertEquals(4.0, parent.getMember("p"));
   }
}
