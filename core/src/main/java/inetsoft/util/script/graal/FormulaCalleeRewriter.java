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
import com.oracle.js.parser.ir.CallNode;
import com.oracle.js.parser.ir.FunctionNode;
import com.oracle.js.parser.ir.IdentNode;
import com.oracle.js.parser.ir.LexicalContext;
import com.oracle.js.parser.ir.visitor.NodeVisitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Bug #81047: rewrites the bare calls {@code name(args)} of a table formula, where {@code name}
 * is a global script function, to {@code __inetsoft_callee__(name,"name")(args)}.
 * <p>
 * A formula runs on a row scope, where a column named like a function (YEAR, dateAdd, ...)
 * hides the function for a bare identifier. Whether an occurrence is a call or a read is
 * known only from the syntax, so it is taken from the GraalJS parse of the formula: the
 * identifier is left as it is (a read, or a call of a user function, still resolves as
 * before), and the helper falls back to the global function only when the value is not
 * callable. A column read ({@code year + 1}) is never rewritten.
 * <p>
 * Uses the parser of GraalJS (js-language, an internal API, version pinned by the pom). It
 * works on the class path; on any failure the text is returned unchanged, which is the
 * behavior without the rewrite.
 * <p>
 * Known limit: the call loses the {@code this} the {@code with} scope gave a function the scope
 * itself owns (a function a formula puts on the scope under the name of a global function) or
 * that the calc proxy resolves; a global function ignores {@code this} either way.
 */
public final class FormulaCalleeRewriter {
   private FormulaCalleeRewriter() {
   }

   /** The helper installed in every context, see GraalJavaScriptEngine. */
   public static final String CALLEE_FN = "__inetsoft_callee__";

   /**
    * @param formula the formula text.
    * @return the formula with its global function calls routed through the helper, or the
    *         formula itself when there is none or it can not be parsed.
    */
   public static String rewrite(String formula) {
      return rewrite(formula, GraalJavaScriptEngine::isGlobalFunctionName);
   }

   static String rewrite(String formula, Predicate<String> isFunction) {
      if(formula == null || formula.indexOf('(') < 0) {
         return formula;
      }

      try {
         ScriptEnvironment env = ScriptEnvironment.builder()
            .ecmaScriptVersion(ScriptEnvironment.ES_2024)
            .strict(false)
            .scripting(false)
            .syntaxExtensions(true)
            .build();
         FunctionNode root = new Parser(
            env, com.oracle.js.parser.Source.sourceFor("<formula>", formula),
            new ErrorManager.ThrowErrorManager()).parse();
         List<int[]> spans = new ArrayList<>();
         List<String> names = new ArrayList<>();

         root.accept(new NodeVisitor<LexicalContext>(new LexicalContext()) {
            @Override
            public boolean enterCallNode(CallNode call) {
               if(!call.isNew() && !call.isOptional() && !call.isTaggedTemplateLiteral() &&
                  call.getFunction() instanceof IdentNode)
               {
                  IdentNode id = (IdentNode) call.getFunction();
                  String name = id.getName();

                  if(!"eval".equals(name) && !CALLEE_FN.equals(name) &&
                     isFunction.test(name) && id.getFinish() - id.getStart() == name.length() &&
                     formula.startsWith(name, id.getStart()))
                  {
                     spans.add(new int[] { id.getStart(), id.getFinish() });
                     names.add(name);
                  }
               }

               return true;
            }
         });

         if(spans.isEmpty()) {
            return formula;
         }

         // by position, from the end, so an insertion never moves a span to come
         Integer[] order = new Integer[spans.size()];

         for(int i = 0; i < order.length; i++) {
            order[i] = i;
         }

         java.util.Arrays.sort(order, (a, b) -> Integer.compare(spans.get(b)[0], spans.get(a)[0]));
         StringBuilder sb = new StringBuilder(formula);

         for(int i : order) {
            int[] span = spans.get(i);
            String name = names.get(i);
            sb.replace(span[0], span[1], CALLEE_FN + "(" + name + ",\"" + name + "\")");
         }

         return sb.toString();
      }
      catch(Throwable ex) {
         LOG.debug("Formula not rewritten, left as it is: {}", formula, ex);
         return formula;
      }
   }

   private static final Logger LOG = LoggerFactory.getLogger(FormulaCalleeRewriter.class);
}
