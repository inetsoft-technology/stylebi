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
package inetsoft.uql.jdbc;

import antlr.RecognitionException;
import antlr.TokenStreamException;
import inetsoft.uql.util.sqlparser.ParserStoppedException;

/**
 * Invariants of the ANTLR SQL parser behind {@link UniformSQL}, shared by the seed replay
 * test ({@code UniformSQLParserSeedTest}) and the enterprise fuzz harness
 * ({@code test/fuzzer}), which uses this class from the {@code inetsoft-core} tests jar.
 * <p>
 * For any input, {@link #check(String)} verifies that:
 * <ol>
 *    <li>parsing fails only with a syntax error
 *        ({@link RecognitionException}/{@link TokenStreamException});</li>
 *    <li>parsing finishes before the parser's own timeout;</li>
 *    <li>a successfully parsed, non-lossy statement can be regenerated with
 *        {@link UniformSQL#getSQLString()}, the regenerated SQL parses again, and
 *        regenerating that second parse yields the same SQL.</li>
 * </ol>
 * A violation is reported as an {@link AssertionError}; any other exception escaping the
 * parser or SQL generator is rethrown as is.
 */
public final class SQLParserProperties {
   private SQLParserProperties() {
   }

   /**
    * Check the parser invariants for one input.
    *
    * @param input the SQL text, which does not need to be valid SQL.
    */
   public static void check(String input) throws Exception {
      if(input.length() > MAX_INPUT) {
         return;
      }

      UniformSQL sql = new UniformSQL();

      if(!parse(sql, input) || sql.isLossy()) {
         return;
      }

      String generated = sql.getSQLString();

      if(generated == null || generated.isBlank()) {
         return;
      }

      UniformSQL reparsed = new UniformSQL();

      if(!parse(reparsed, generated)) {
         throw new AssertionError("Regenerated SQL does not parse.\ninput:     " + input +
                                  "\ngenerated: " + generated);
      }

      String regenerated = reparsed.getSQLString();

      if(!generated.equals(regenerated)) {
         throw new AssertionError("SQL generation is not a fixpoint.\ninput:       " + input +
                                  "\ngenerated:   " + generated +
                                  "\nregenerated: " + regenerated);
      }
   }

   /**
    * @return true if the statement parsed, false on an ordinary syntax error.
    */
   private static boolean parse(UniformSQL sql, String text) throws Exception {
      try {
         sql.parse(text, UniformSQL.PARSE_ALL, PARSE_TIMEOUT);
         return true;
      }
      catch(RecognitionException | TokenStreamException ex) {
         return false;
      }
      catch(ParserStoppedException ex) {
         throw new AssertionError("Parser hit its " + PARSE_TIMEOUT + "ms timeout on a " +
                                  text.length() + "-char input: " + text, ex);
      }
   }

   /**
    * Longer inputs are skipped so that fuzzing time goes into structure, not length.
    */
   public static final int MAX_INPUT = 2000;

   /**
    * Half of {@link UniformSQL#PARSE_PERIOD}, the budget the server gives a full parse.
    */
   private static final long PARSE_TIMEOUT = UniformSQL.PARSE_PERIOD / 2;
}
