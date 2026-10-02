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
package inetsoft.report.script;

import inetsoft.report.lens.DefaultTableLens;
import inetsoft.util.script.graal.pool.PoolTestSupport;
import inetsoft.util.script.graal.pool.WorksheetScriptEnv;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static inetsoft.util.script.graal.pool.PoolTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Spec §6.4 (bug #76960): in pool mode a table's cached row window is kept per pooled context,
 * since two contexts can read one table array at the same time.
 */
@Tag("core")
public class TableArrayWindowTest {
   @Test
   void pooledContextsGetTheirOwnRowWindow() throws Exception {
      WorksheetScriptEnv env = PoolTestSupport.env();
      Probe probe = new Probe(new TableArray(table()) {
         @Override
         protected boolean usePerSlotWindows() {
            return true;
         }
      });
      env.put("probe", probe);
      env.put("tbl", probe.array);

      assertEquals(7.0, run(env, "probe.capture(); tbl[2]['value']"));
      Object first = probe.captured;
      whileHeldElsewhere(env, () -> {
         assertEquals(7.0, run(env, "probe.capture(); tbl[2]['value']"));
      });

      assertNotNull(first);
      assertNotSame(first, probe.captured);
   }

   @Test
   void windowIsSharedWhenNotAsked() throws Exception {
      WorksheetScriptEnv env = PoolTestSupport.env();
      Probe probe = new Probe(new TableArray(table()));
      env.put("probe", probe);
      run(env, "probe.capture(); 1");
      Object first = probe.captured;
      whileHeldElsewhere(env, () -> run(env, "probe.capture(); 1"));
      assertSame(first, probe.captured);
   }

   private static DefaultTableLens table() {
      return new DefaultTableLens(new Object[][] {
         {"name", "value"}, {"a", 5}, {"b", 7}, {"c", 9}
      });
   }

   /**
    * Captures, from inside a script, the window the executing context uses.
    */
   public static final class Probe {
      Probe(TableArray array) {
         this.array = array;
      }

      public void capture() {
         captured = array.windowForTest();
      }

      final TableArray array;
      volatile Object captured;
   }
}
