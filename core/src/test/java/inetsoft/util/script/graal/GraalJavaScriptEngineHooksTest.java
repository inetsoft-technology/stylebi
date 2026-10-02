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
package inetsoft.util.script.graal;

import inetsoft.util.script.ScriptException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The GraalJavaScriptEngine hooks the worksheet context pool builds on (bug #76960); their
 * defaults keep the engine's own behaviour.
 */
@Tag("core")
class GraalJavaScriptEngineHooksTest {
   @Test
   void librarySourcesOverrideIsInstalled() throws Exception {
      GraalJavaScriptEngine engine = new GraalJavaScriptEngine() {
         @Override
         protected Map<String, String> librarySources() {
            return Map.of("libSeven", "function libSeven(){ return 7; }");
         }
      };
      engine.init(null);

      try {
         assertEquals(7.0, engine.exec(engine.compile("libSeven()"), null, null));
      }
      finally {
         engine.close();
      }
   }

   @Test
   void defaultLibrarySourcesMeansLiveLibManager() {
      assertNull(new GraalJavaScriptEngine().librarySources());
   }

   @Test
   void errorCountsOverrideSurvivesReinit() throws Exception {
      Map<Object, Integer> shared = Collections.synchronizedMap(new WeakHashMap<>());
      GraalJavaScriptEngine engine = new GraalJavaScriptEngine() {
         @Override
         protected Map<Object, Integer> errorCounts() {
            return shared;
         }

         @Override
         protected void resetErrorCounts() {
         }
      };
      engine.init(null);

      try {
         Object bad = engine.compile("undefinedName_76960 + 1");
         assertThrows(ScriptException.class, () -> engine.exec(bad, null, null));
         assertEquals(1, shared.get(bad));
         engine.init(null);
         assertEquals(1, shared.get(bad));
      }
      finally {
         engine.close();
      }
   }
}
