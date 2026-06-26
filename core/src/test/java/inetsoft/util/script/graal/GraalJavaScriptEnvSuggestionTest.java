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

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Task 6.5: verifies getSuggestion() returns the correct suggestion for the
 * real messages GraalJS produces for each error class. The errors are triggered
 * by actually executing scripts through GraalJavaScriptEnv so the matchers are
 * validated against empirically captured GraalJS message text.
 */
@Tag("core")
class GraalJavaScriptEnvSuggestionTest {
   private GraalJavaScriptEnv env;

   @BeforeEach void setup() {
      env = new GraalJavaScriptEnv();
      env.init();
   }

   /** Capture the exception thrown by executing the given script. */
   private Exception execAndCapture(String src) {
      try {
         Object script = env.compile(src);
         env.exec(script, null, null, null);
      }
      catch(Exception ex) {
         return ex;
      }

      return null;
   }

   /** ReferenceError: undefined variable. GraalJS: "ReferenceError: <name> is not defined". */
   @Test void suggestionForUndefinedVariable() {
      Exception ex = execAndCapture("nonExistentVar + 1");
      assertNotNull(ex, "expected an error for undefined variable");

      String s = env.getSuggestion(ex, null);
      assertNotNull(s, "expected a suggestion; message was: " + ex.getMessage());
      assertTrue(s.toLowerCase().contains("defined") || s.toLowerCase().contains("var"),
                 "suggestion should mention defining/declaring: " + s);
   }

   /** TypeError: calling a non-function. GraalJS: "TypeError: ... is not a function". */
   @Test void suggestionForCallingNonFunction() {
      Exception ex = execAndCapture("var x = 5; x();");
      assertNotNull(ex, "expected an error for calling a non-function");

      String s = env.getSuggestion(ex, null);
      assertNotNull(s, "expected a suggestion; message was: " + ex.getMessage());
      assertTrue(s.toLowerCase().contains("function"),
                 "suggestion should mention function: " + s);
   }

   /** TypeError: property of undefined. GraalJS: "Cannot read ... of undefined". */
   @Test void suggestionForPropertyOfUndefined() {
      Exception ex = execAndCapture("var o; o.foo;");
      assertNotNull(ex, "expected an error for property of undefined");

      String s = env.getSuggestion(ex, null);
      assertNotNull(s, "expected a suggestion; message was: " + ex.getMessage());
      assertTrue(s.toLowerCase().contains("exist") || s.toLowerCase().contains("reference"),
                 "suggestion should mention existence/reference: " + s);
   }
}
