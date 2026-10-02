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
package inetsoft.util.script.graal.pool;

import inetsoft.util.script.ScriptSpan;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Spec §5.3 row 3 (bug #76960): RRuntime opens one span from the pre-script compile through
 * the post-script exec, so state the pre-script sets is there for the post-script.
 */
@Tag("core")
class RSpanShapeTest {
   @Test
   void preAndPostScriptsShareOneContext() throws Exception {
      WorksheetScriptEnv env = PoolTestSupport.env();

      try(ScriptSpan span = env.openSpan()) {
         Object pre = env.compile("rstate = 1; 1");
         env.exec(pre, null, null, null);
         // (the R round trip runs here)
         Object post = env.compile("rstate + 1");
         assertEquals(2.0, env.exec(post, null, null, null));
      }

      assertEquals(1, env.getMetrics().getCleans());
      assertEquals("undefined", PoolTestSupport.run(env, "typeof rstate"));
   }
}
