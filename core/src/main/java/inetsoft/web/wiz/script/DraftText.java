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
package inetsoft.web.wiz.script;

import inetsoft.web.wiz.pairing.PairingException;

import java.util.function.UnaryOperator;

/**
 * The write normalization shared by the draft-only kinds that push plain text into a dialog:
 * the worksheet variable default (expression, no leading {@code '='}), the hyperlink expression
 * (expression, WITH its leading {@code '='}) and the worksheet SQL (verbatim). Public and static
 * so the worksheet package can reuse it; {@code DynamicValueTargets.prepareWrite} keeps its own
 * extra rules and is not routed through here. Pure, so it needs no Spring bean (the deployed
 * instance runs AOT).
 */
public final class DraftText {
   private DraftText() {
   }

   /** How a leading {@code '='} is treated. */
   public enum EqualsMode {
      /** The dialog's editor holds the text without it: ONE leading {@code '='} is removed. */
      STRIP,
      /** The dialog's editor holds the text with it: one is added when missing. */
      ENSURE,
      /** The text is not an expression (SQL): pushed unchanged, no syntax check. */
      NONE
   }

   /**
    * A write text after normalization.
    *
    * @param changedEquals whether a leading {@code '='} was removed ({@link EqualsMode#STRIP}) or
    *                      added ({@link EqualsMode#ENSURE})
    */
   public record Prepared(String text, boolean changedEquals) {
   }

   /**
    * Normalizes and checks the text an agent wants pushed. Refuses, loudly and with "Nothing was
    * pushed", what would otherwise be stored as a plausible but wrong value: null, blank (also a
    * bare {@code '='}), {@code ==x} where a single {@code '='} is the prefix, and an expression
    * that does not parse.
    *
    * <p>The syntax check always runs on the text WITHOUT one leading {@code '='}, which is the
    * expression itself. {@link EqualsMode#NONE} runs none.
    *
    * @param syntaxProblem the parse error of a text, or null when it parses; never executes. Not
    *                      consulted for {@link EqualsMode#NONE}, so it may be null then
    */
   public static Prepared prepare(String text, EqualsMode mode,
                                  UnaryOperator<String> syntaxProblem)
      throws PairingException
   {
      if(text == null) {
         throw new PairingException(PairingException.Kind.INVALID_ARGUMENT, "text is required.");
      }

      if(mode == EqualsMode.NONE) {
         if(text.isBlank()) {
            throw empty();
         }

         return new Prepared(text, false);
      }

      String lead = text.stripLeading();
      String expression = lead;
      boolean changed = false;

      if(lead.startsWith("==")) {
         throw new PairingException(PairingException.Kind.INVALID_ARGUMENT,
            "The text starts with '==', which is not a valid expression start (a single leading " +
            "'=' is optional and handled for you). Nothing was pushed.");
      }

      if(lead.startsWith("=")) {
         expression = lead.substring(1).stripLeading();
         changed = mode == EqualsMode.STRIP;
      }

      if(expression.isBlank()) {
         throw empty();
      }

      String problem = syntaxProblem == null ? null : syntaxProblem.apply(expression);

      if(problem != null) {
         throw new PairingException(PairingException.Kind.INVALID_ARGUMENT,
            "The expression does not parse as a JavaScript expression (" + problem +
            "). Nothing was pushed.");
      }

      if(mode == EqualsMode.STRIP) {
         return new Prepared(expression, changed);
      }

      return lead.startsWith("=") ? new Prepared(lead, false) : new Prepared("=" + lead, true);
   }

   /**
    * The parse error of {@code text} as a JavaScript expression, or null when it parses or the
    * script engine is not initialised. Parses only: the expression is never executed.
    */
   public static String jsSyntaxProblem(String text) {
      return VsConditionValueTargets.syntaxProblem(text);
   }

   private static PairingException empty() {
      return new PairingException(PairingException.Kind.INVALID_ARGUMENT,
         "The text is empty (or only a bare '='): it would leave the field with no value. " +
         "Nothing was pushed.");
   }
}
