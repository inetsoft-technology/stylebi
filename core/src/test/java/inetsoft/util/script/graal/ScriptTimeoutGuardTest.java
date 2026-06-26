/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.util.script.graal;

import org.graalvm.polyglot.*;
import org.junit.jupiter.api.*;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
class ScriptTimeoutGuardTest {
   @Test void interruptsRunawayScript() {
      try(Context ctx = Context.newBuilder("js").build()) {
         ScriptTimeoutGuard guard = new ScriptTimeoutGuard();
         PolyglotException ex = assertThrows(PolyglotException.class, () -> {
            try(var ignored = guard.guard(ctx, Duration.ofMillis(300))) {
               ctx.eval("js", "while(true){}");
            }
         });
         assertTrue(ex.isInterrupted() || ex.isCancelled());
      }
   }

   @Test void zeroDurationMeansNoTimeout() {
      try(Context ctx = Context.newBuilder("js").build()) {
         ScriptTimeoutGuard guard = new ScriptTimeoutGuard();
         try(var ignored = guard.guard(ctx, Duration.ZERO)) {
            assertEquals(3, ctx.eval("js", "1+2").asInt());
         }
      }
   }
}
