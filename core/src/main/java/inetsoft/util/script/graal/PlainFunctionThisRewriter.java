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

import com.oracle.js.parser.ErrorManager;
import com.oracle.js.parser.Parser;
import com.oracle.js.parser.ScriptEnvironment;
import com.oracle.js.parser.ir.ClassNode;
import com.oracle.js.parser.ir.FunctionNode;
import com.oracle.js.parser.ir.IdentNode;
import com.oracle.js.parser.ir.LexicalContext;
import com.oracle.js.parser.ir.Node;
import com.oracle.js.parser.ir.visitor.NodeVisitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Bug #81930: gives a plain-called function the scope as its {@code this}.
 * <p>
 * Rhino bound the {@code this} of a function called without a receiver to the top scope of the
 * script, so a helper in a component script could write {@code this.visible = false}. A
 * sloppy function of GraalJS gets the realm's global object there, which can not be replaced,
 * so the write is lost (and lands on the global shared by later scripts) and a read is
 * undefined. Every {@code this} that belongs to a non-strict, non-arrow function (an arrow
 * takes the {@code this} of the function around it) is therefore rewritten to
 * {@code __inetsoft_this__(this)}, a helper (see GraalJavaScriptEngine#installThisHelper) that
 * returns the receiver as it is, unless it is the global object or undefined, which is the
 * plain-call case, and then stands the scope in for it. A method call, {@code new F()},
 * {@code call/apply/bind} and the {@code thisArg} of {@code forEach} keep their receiver.
 * <p>
 * Left as they are: the {@code this} of the top level (the engine already binds it to the
 * scope), of a function that is strict by its own directive, and of anything inside a class
 * (strict by definition), where a plain call getting {@code undefined} is the language's rule.
 * An indirect {@code Function('return this')()} is built from a string at run time and is not
 * touched, so it still returns the real global object.
 * <p>
 * Uses the parser of GraalJS (js-language, an internal API, version pinned by the pom), as
 * {@link FormulaCalleeRewriter} does. On any failure the text is returned unchanged, which is
 * the behavior without the rewrite.
 */
public final class PlainFunctionThisRewriter {
   private PlainFunctionThisRewriter() {
   }

   /** The helper installed in every context, see GraalJavaScriptEngine. */
   public static final String THIS_FN = "__inetsoft_this__";

   /**
    * @param script the script text.
    * @return the script with the {@code this} of its plain functions routed through the
    *         helper, or the script itself when there is none or it can not be parsed.
    */
   public static String rewrite(String script) {
      if(script == null || script.indexOf("this") < 0) {
         return script;
      }

      try {
         ScriptEnvironment env = ScriptEnvironment.builder()
            .ecmaScriptVersion(ScriptEnvironment.ES_2024)
            .strict(false)
            .scripting(false)
            .syntaxExtensions(true)
            .build();
         FunctionNode root = new Parser(
            env, com.oracle.js.parser.Source.sourceFor("<script>", script),
            new ErrorManager.ThrowErrorManager()).parse();
         List<Integer> starts = new ArrayList<>();
         Deque<FunctionNode> functions = new ArrayDeque<>();
         int[] classDepth = { 0 };

         root.accept(new NodeVisitor<LexicalContext>(new LexicalContext()) {
            @Override
            public boolean enterFunctionNode(FunctionNode function) {
               functions.push(function);
               return true;
            }

            @Override
            public Node leaveFunctionNode(FunctionNode function) {
               functions.pop();
               return function;
            }

            @Override
            public boolean enterClassNode(ClassNode node) {
               classDepth[0]++;
               return true;
            }

            @Override
            public Node leaveClassNode(ClassNode node) {
               classDepth[0]--;
               return node;
            }

            @Override
            public boolean enterIdentNode(IdentNode id) {
               if(id.isThis() && classDepth[0] == 0 && id.getFinish() - id.getStart() == 4 &&
                  script.startsWith("this", id.getStart()) && isPlainFunctionThis(functions))
               {
                  starts.add(id.getStart());
               }

               return true;
            }
         });

         if(starts.isEmpty()) {
            return script;
         }

         // from the end, so an insertion never moves a position to come
         starts.sort(java.util.Collections.reverseOrder());
         StringBuilder sb = new StringBuilder(script);

         for(int start : starts) {
            sb.replace(start, start + 4, THIS_FN + "(this)");
         }

         return sb.toString();
      }
      catch(Throwable ex) {
         LOG.debug("Script not rewritten, left as it is: {}", script, ex);
         return script;
      }
   }

   /**
    * Whether a {@code this} met inside these functions (innermost first) is the receiver of a
    * sloppy non-arrow function, as opposed to the top level or a strict function.
    */
   private static boolean isPlainFunctionThis(Deque<FunctionNode> functions) {
      for(FunctionNode function : functions) {
         if(function.isArrow()) {
            continue;
         }

         return !function.isProgram() && !function.isScript() && !function.isModule() &&
            !function.isStrict() && !function.isClassConstructor() &&
            !function.isClassFieldInitializer();
      }

      return false;
   }

   private static final Logger LOG = LoggerFactory.getLogger(PlainFunctionThisRewriter.class);
}
