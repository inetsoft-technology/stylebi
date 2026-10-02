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
package inetsoft.uql.mongodb;

import inetsoft.uql.VariableTable;
import inetsoft.uql.tabular.TabularUtil;
import org.bson.BsonRegularExpression;
import org.bson.Document;
import org.bson.json.JsonParseException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Bug #76864: a variable substituted into the MongoDB query text through
 * {@link TabularUtil#replaceVariables(Object, VariableTable)} must parse (with the
 * real bson parser used by {@code MongoRuntime}) to exactly the input value, with
 * no value-supplied keys, for unquoted, single-quoted and double-quoted placeholders.
 */
class MongoQueryVariableEscapeTest {
   static Stream<String> values() {
      return Stream.of(
         "plain", "O'Brien", "a\"b", "x\\", "C:\\Users\\bob", "', admin: '1",
         "x\\', admin: '1", "\", admin: \"1", "it's \"q\" \\ end\\", "",
         // Bug #77105: bson keywords / numbers / separators must stay strings
         "MinKey", "Infinity", "null", "true", "18", "-3", "\uff13", "a.b",
         "x/, y: /z", "\u2028", "\u65e5\u672c", "\ud83d\ude00");
   }

   @ParameterizedTest
   @MethodSource("values")
   void unquotedPlaceholder_roundTripsExactly(String value) {
      assertRoundTrip("{name: $(p), other: 1}", value, value);
   }

   @ParameterizedTest
   @MethodSource("values")
   void singleQuotedPlaceholder_roundTripsExactly(String value) {
      assertRoundTrip("{name: '$(p)', other: 1}", value, value);
   }

   @ParameterizedTest
   @MethodSource("values")
   void doubleQuotedPlaceholder_roundTripsExactly(String value) {
      assertRoundTrip("{name: \"$(p)\", other: 1}", value, value);
   }

   /**
    * A value that itself contains placeholder syntax must be emitted literally, not
    * re-expanded against the variable table (no second-order substitution), and
    * non-ASCII / control characters must round-trip unchanged.
    */
   @ParameterizedTest
   @MethodSource("placeholderTemplates")
   void valueContainingPlaceholderSyntaxOrUnicode_isNotReExpanded(String match) {
      String[] values = { "$(q)", "a$(q)b)", "$(@q)", "Zoë'日本\n\t😀" };

      for(String value : values) {
         MongoQuery query = new MongoQuery();
         query.setQueryString("{aggregate: 'c', pipeline: [{$match: " + match + "}], cursor: {}}");
         VariableTable vars = new VariableTable();
         vars.put("p", value);
         vars.put("q", "', admin: '1");
         TabularUtil.replaceVariables(query, vars);

         Document doc = Document.parse(query.getQueryString());
         Document matchDoc = (Document) ((Document) ((List<?>) doc.get("pipeline")).get(0))
            .get("$match");
         assertEquals(new HashSet<>(Arrays.asList("name", "other")), matchDoc.keySet(), value);
         assertEquals(value, matchDoc.get("name"));
      }
   }

   static Stream<String> placeholderTemplates() {
      return Stream.of("{name: $(p), other: 1}", "{name: '$(p)', other: 1}",
                       "{name: \"$(p)\", other: 1}");
   }

   @ParameterizedTest
   @MethodSource("values")
   void arrayValueInScalarContext_roundTripsFirstElement(String value) {
      assertRoundTrip("{name: $(p), other: 1}", new String[] { value, "second" }, value);
   }

   /**
    * Bug #77105: the supported way to parameterise a regex is a {@code $regex}
    * string, which must decode to exactly the input value.
    */
   @ParameterizedTest
   @MethodSource("values")
   void regexOperatorString_roundTripsExactly(String value) {
      for(String match : new String[] {
         "{name: {$regex: '^$(p)'}, other: 1}",
         "{name: {$regex: \"$(p)\", $options: 'i'}, other: 1}" })
      {
         Document matchDoc = parseMatch(match, value);
         assertEquals(new HashSet<>(Arrays.asList("name", "other")), matchDoc.keySet(), match);
         Object regex = matchDoc.get("name");
         // bson reads {$regex, $options} as an extended-JSON regex
         Object pattern = regex instanceof BsonRegularExpression ?
            ((BsonRegularExpression) regex).getPattern() : ((Document) regex).get("$regex");
         assertEquals(match.contains("^") ? "^" + value : value, pattern, match);
      }
   }

   /**
    * Bug #77105: templates on which VarSQL's SQL-oriented lexer and bson's lexer
    * disagree about whether a placeholder is inside a string. Axis: these template
    * positions (placeholder in a regex literal; bare placeholder after an unpaired
    * quote in a regex literal, or after a template backslash-backslash-quote; the
    * wrapped scalar landing inside a bson string) &times; String values. Each
    * value must either fail to parse, or parse to exactly the template's keys with
    * the placeholder field keeping its type (regex text, or a String equal to the
    * input) - never extra keys, and never a MinKey/Infinity/null/boolean/number.
    */
   @ParameterizedTest
   @MethodSource("desyncCases")
   void lexerDesyncTemplate_failsClosed(String match, Set<String> keys, String field,
                                        boolean regexField, Object value)
   {
      Document matchDoc;

      try {
         matchDoc = parseMatch(match, value);
      }
      catch(JsonParseException e) {
         return; // fail-closed
      }

      String input = value instanceof String[] ? ((String[]) value)[0] : (String) value;
      assertEquals(keys, matchDoc.keySet(), match + " <- " + input);
      Object parsed = matchDoc.get(field);

      if(parsed instanceof Document) { // {$gt: $(p)}
         assertEquals(Collections.singleton("$gt"), ((Document) parsed).keySet(), input);
         parsed = ((Document) parsed).get("$gt");
      }

      if(regexField) {
         assertInstanceOf(BsonRegularExpression.class, parsed, match + " <- " + input);
      }
      else {
         assertEquals(input, parsed, match + " <- " + input);
      }
   }

   static Stream<Arguments> desyncCases() {
      Set<String> a = Collections.singleton("a");
      Set<String> aName = new HashSet<>(Arrays.asList("a", "name"));
      Set<String> aAge = new HashSet<>(Arrays.asList("a", "age"));
      Object[][] templates = {
         // R1: placeholder inside a regex literal (VarSQL wraps it as a string)
         { "{a: /^$(p)/}", a, "a", true },
         // R1q: quoted placeholder inside a regex literal
         { "{a: /^'$(p)'/}", a, "a", true },
         // R2: unpaired ' inside a regex literal, then a bare placeholder
         { "{a: /it's/, name: $(p)}", aName, "name", false },
         // R2dq: unpaired " inside a regex literal
         { "{a: /say \"/, name: $(p)}", aName, "name", false },
         // R2q: unpaired ' in regex, then a quoted placeholder
         { "{a: /it's/, name: '$(p)'}", aName, "name", false },
         // R2gt: R2 with the placeholder as an operator operand
         { "{a: /it's/, age: {$gt: $(p)}}", aAge, "age", false },
         // R4: template \\' - VarSQL toggles the quote, bson sees an escaped quote
         { "{a: 'it\\\\'s', name: $(p)}", aName, "name", false },
         // R4-wrap: VarSQL outside, bson inside a string; the wrapper closes it
         { "{a: 'x\\\\', name: $(p)}", aName, "name", false },
      };
      String[] values = {
         // structural-only values (no quotes): would add keys if spliced bare
         "1, admin: 1", "x/, admin: 1, b: /y", "x/, admin: 1, b: /y'",
         // single tokens that bson would read as a non-string literal
         "MinKey", "Infinity", "null", "true", "18", "-3", "３",
         "plain", "a.b", "O'Brien", "", " ", "日本"
      };
      List<Arguments> args = new ArrayList<>();

      for(Object[] t : templates) {
         for(String v : values) {
            args.add(Arguments.of(t[0], t[1], t[2], t[3], v));
         }

         // R1arr: array value in the scalar position
         if("{a: /^$(p)/}".equals(t[0])) {
            args.add(Arguments.of(t[0], t[1], t[2], t[3],
                                  new String[] { "x/, admin: 1, b: /y", "z" }));
         }
      }

      return args.stream();
   }

   /**
    * Bug #77105: the first-char rule applies to String values only, so a numeric
    * variable keeps working both in a normal unquoted position and in a desync
    * template where VarSQL believes it is inside a quote.
    */
   @Test
   void numericValue_keepsNumberType() {
      Document normal = parseMatch("{age: {$gt: $(p)}, other: 1}", 18);
      assertEquals(18, ((Document) normal.get("age")).get("$gt"));

      Document desync = parseMatch("{a: /it's/, age: {$gt: $(p)}}", 18);
      assertEquals(new HashSet<>(Arrays.asList("a", "age")), desync.keySet());
      assertEquals(18, ((Document) desync.get("age")).get("$gt"));
   }

   /**
    * Bug #77105 round 1: Number and Boolean variables keep their bson type in
    * every template - normal and lexer-desync - because their text is emitted
    * unescaped (it cannot close a string or regex literal). Expected value is
    * what bson reads for the same literal text in a normal template; in a quoted
    * position it is the value's text as a String; in a regex literal the pattern
    * keeps the text (R2q and R1 reach bson through toSQLConstant, which pads the
    * text with spaces - pre-existing, not changed here). R4-wrap cannot parse for
    * any number: bson is inside a string that only a String value's quote
    * wrapper would close.
    */
   @ParameterizedTest
   @MethodSource("nonStringScalarCases")
   void numberAndBooleanValue_keepTypeInEveryTemplate(String match, String field,
                                                      String kind, Object value)
   {
      String text = value.toString();

      if("fail".equals(kind)) {
         org.junit.jupiter.api.Assertions.assertThrows(JsonParseException.class,
            () -> parseMatch(match, value), match + " <- " + text);
         return;
      }

      Document matchDoc = parseMatch(match, value);
      Object parsed = matchDoc.get(field);

      if(parsed instanceof Document) { // {$gt: $(p)}
         assertEquals(Collections.singleton("$gt"), ((Document) parsed).keySet(), match);
         parsed = ((Document) parsed).get("$gt");
      }

      switch(kind) {
      case "typed":
         Object expected = Document.parse("{v: " + text + "}").get("v");
         assertEquals(expected, parsed, match + " <- " + text);
         assertInstanceOf((Class<?>) (value instanceof Boolean ? Boolean.class : Number.class), parsed,
                          match + " <- " + text);
         break;
      case "string":
         assertEquals(text, parsed, match + " <- " + text);
         break;
      case "paddedString":
         // VarSQL is outside a quote here, so the value goes through
         // toSQLConstant, which pads it with a space on each side (pre-existing)
         assertEquals(" " + text + " ", parsed, match + " <- " + text);
         break;
      default: // regex, also via the padded toSQLConstant path
         assertInstanceOf(BsonRegularExpression.class, parsed, match + " <- " + text);
         assertEquals("^ " + text + " ", ((BsonRegularExpression) parsed).getPattern(), match);
      }
   }

   static Stream<Arguments> nonStringScalarCases() {
      Object[] values = { 18, -3, 1.5, 1.5E20, new java.math.BigDecimal("1E+5"),
                          new java.math.BigDecimal("-0.25"), 18L, Boolean.TRUE,
                          Boolean.FALSE };
      String[][] templates = {
         // normal templates
         { "{age: {$gt: $(p)}, other: 1}", "age", "typed" },
         { "{age: $(p), other: 1}", "age", "typed" },
         { "{age: '$(p)', other: 1}", "age", "string" },
         // lexer-desync templates (VarSQL believes the placeholder is quoted)
         { "{a: /it's/, age: {$gt: $(p)}}", "age", "typed" },    // R2gt
         { "{a: /it's/, age: $(p)}", "age", "typed" },           // R2
         { "{a: /say \"/, age: $(p)}", "age", "typed" },         // R2dq
         { "{a: 'it\\\\'s', age: $(p)}", "age", "typed" },       // R4
         // R4-wrap: bson is inside a string VarSQL thinks is closed; a number has
         // no wrapper to close it, so it cannot parse (before and after the fix)
         { "{a: 'x\\\\', age: $(p)}", "age", "fail" },           // R4-wrap
         { "{a: /it's/, age: '$(p)'}", "age", "paddedString" },  // R2q
         { "{a: /^$(p)/}", "a", "regex" },                       // R1
      };
      List<Arguments> args = new ArrayList<>();

      for(String[] t : templates) {
         for(Object v : values) {
            args.add(Arguments.of(t[0], t[1], t[2], v));
         }
      }

      return args.stream();
   }

   /**
    * Bug #77105 round 1: a non-String, non-Number/Boolean value (here a
    * StringBuilder whose text is a bson keyword) is encoded like a String, so in
    * a desync template it fails to parse instead of becoming a MinKey.
    */
   @Test
   void otherObjectValue_inDesyncTemplate_failsClosed() {
      org.junit.jupiter.api.Assertions.assertThrows(JsonParseException.class,
         () -> parseMatch("{a: /it's/, name: $(p)}", new StringBuilder("MinKey")));
   }

   private static Document parseMatch(String match, Object value) {
      MongoQuery query = new MongoQuery();
      query.setQueryString("{aggregate: 'c', pipeline: [{$match: " + match + "}], cursor: {}}");
      VariableTable vars = new VariableTable();
      vars.put("p", value);
      TabularUtil.replaceVariables(query, vars);

      Document doc = Document.parse(query.getQueryString());
      assertEquals(new HashSet<>(Arrays.asList("aggregate", "pipeline", "cursor")), doc.keySet());
      List<?> pipeline = (List<?>) doc.get("pipeline");
      assertEquals(1, pipeline.size());
      Document stage = (Document) pipeline.get(0);
      assertEquals(Collections.singleton("$match"), stage.keySet());
      return (Document) stage.get("$match");
   }

   private static void assertRoundTrip(String match, Object value, String expected) {
      MongoQuery query = new MongoQuery();
      query.setQueryString("{aggregate: 'c', pipeline: [{$match: " + match + "}], cursor: {}}");
      VariableTable vars = new VariableTable();
      vars.put("p", value);
      TabularUtil.replaceVariables(query, vars);

      Document doc = Document.parse(query.getQueryString());
      assertEquals(new HashSet<>(Arrays.asList("aggregate", "pipeline", "cursor")), doc.keySet());
      List<?> pipeline = (List<?>) doc.get("pipeline");
      assertEquals(1, pipeline.size());
      Document stage = (Document) pipeline.get(0);
      assertEquals(Collections.singleton("$match"), stage.keySet());
      Document matchDoc = (Document) stage.get("$match");
      assertEquals(new HashSet<>(Arrays.asList("name", "other")), matchDoc.keySet());
      assertEquals(expected, matchDoc.get("name"));
      assertEquals(1, matchDoc.get("other"));
   }
}
