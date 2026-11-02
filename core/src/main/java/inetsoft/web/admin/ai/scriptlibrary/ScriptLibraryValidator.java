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
package inetsoft.web.admin.ai.scriptlibrary;

import inetsoft.util.script.ScriptEnvRepository;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.SourceSection;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * The Script Library save gate (stylebi#7534), shared by the composer-chat REST controller and the
 * admin-chat change plan so both refuse the same names and text: a name must be a JavaScript
 * identifier that is not a reserved word, and the text must parse. Validation is applied only to
 * what is about to be stored, never to existing entries.
 */
public final class ScriptLibraryValidator {
   private ScriptLibraryValidator() {
   }

   /**
    * Parse-only syntax check of a function text; throws when the text does not compile.
    */
   @FunctionalInterface
   public interface SyntaxChecker {
      void check(String text) throws Exception;
   }

   /**
    * A failed syntax check. {@code cause} is the exception the checker threw, kept so a caller can
    * tell a syntax error from an unavailable engine.
    */
   public record SyntaxError(String message, int line, int column, Exception cause) {}

   /**
    * The script engine could not run the check, so the text is neither accepted nor known to be
    * bad.
    */
   public static final class EngineUnavailableException extends RuntimeException {
      public EngineUnavailableException(String message, Throwable cause) {
         super(message, cause);
      }
   }

   /**
    * The checker backed by the live script engine.
    */
   public static SyntaxChecker engineChecker() {
      return text -> ScriptEnvRepository.getScriptEnv().checkFunction("script", text);
   }

   /**
    * Runs the syntax check and unwraps a failure to its message and source location; null when the
    * text compiles.
    */
   public static SyntaxError diagnose(SyntaxChecker checker, String text) {
      try {
         checker.check(text);
         return null;
      }
      catch(Exception e) {
         int line = 0;
         int column = 0;
         Throwable cause = e;

         // unwrap to the GraalJS PolyglotException to recover source location, same as
         // OpenScriptController.checkScript.
         while(cause != null && !(cause instanceof PolyglotException)) {
            cause = cause.getCause();
         }

         if(cause instanceof PolyglotException polyglot && polyglot.getSourceLocation() != null) {
            SourceSection loc = polyglot.getSourceLocation();
            line = loc.getStartLine();
            column = loc.getStartColumn();
         }

         return new SyntaxError(e.getMessage(), line, column, e);
      }
   }

   /**
    * @throws IllegalArgumentException when the text does not compile
    * @throws EngineUnavailableException when the script engine could not run the check
    */
   public static void requireCompiles(SyntaxChecker checker, String field, String text) {
      SyntaxError error = diagnose(checker, text);

      if(error == null) {
         return;
      }

      if(error.cause() instanceof IllegalStateException) {
         throw new EngineUnavailableException(
            "The script engine is not available, so the function text could not be checked: " +
            firstLine(error.message()) + ". Nothing was saved.", error.cause());
      }

      String location = error.line() > 0
         ? " (line " + error.line() + ", column " + error.column() + ")" : "";
      throw new IllegalArgumentException(
         field + " does not compile: " + firstLine(error.message()) + location +
         ". Nothing was saved.");
   }

   /**
    * @throws IllegalArgumentException when the name is not a legal, non-reserved identifier. The
    *         name is checked exactly as given: surrounding whitespace is not an identifier
    *         character.
    */
   public static void requireValidName(String field, String name) {
      if(!IDENTIFIER.matcher(name).matches() || RESERVED_WORDS.contains(name)) {
         throw new IllegalArgumentException(
            field + " \"" + name + "\" is not a valid JavaScript identifier (letters, digits, " +
            "'_' and '$', not starting with a digit, not a reserved word). Nothing was saved.");
      }
   }

   private static String firstLine(String message) {
      if(message == null) {
         return "";
      }

      int nl = message.indexOf('\n');
      return (nl < 0 ? message : message.substring(0, nl)).strip();
   }

   /**
    * The native Script Library dialog's name rule (form-validators.ts), enforced here because that
    * check runs only in the browser.
    */
   private static final Pattern IDENTIFIER = Pattern.compile("^[\\p{L}_$][\\p{L}\\p{N}_$]*$");

   // ES reserved words only; contextual names (let, static, yield, await, async, of) stay legal.
   private static final Set<String> RESERVED_WORDS = Set.of(
      "break", "case", "catch", "class", "const", "continue", "debugger", "default", "delete",
      "do", "else", "enum", "export", "extends", "false", "finally", "for", "function", "if",
      "import", "in", "instanceof", "new", "null", "return", "super", "switch", "this", "throw",
      "true", "try", "typeof", "var", "void", "while", "with");
}
