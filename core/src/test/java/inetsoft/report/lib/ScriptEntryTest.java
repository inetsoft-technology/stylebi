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
package inetsoft.report.lib;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("core")
class ScriptEntryTest {
   private static String sig(String func) {
      return ScriptEntry.builder().from("one", func).build().signature();
   }

   @Test
   void plainSignature() {
      assertEquals("one(a, b)", sig("function one(a, b) { }"));
   }

   @Test
   void blockCommentOpenedAndClosedOnOneLine() {
      assertEquals("one(a, b)", sig("function one(a /* x */, b) { }"));
   }

   @Test
   void multipleBlockCommentsOnOneLine() {
      assertEquals("one(a, b)", sig("function one(a /* x */, b /* y */) { }"));
   }

   @Test
   void blockCommentEndsThenAnotherOpensAndSpansLines() {
      assertEquals("one(a, b)",
                   sig("function one(a /* x */, /* y\n still comment */ b) { }"));
   }

   @Test
   void lineCommentInsideBlockComment() {
      assertEquals("one(a, b)", sig("function one(a /* // x */, b) { }"));
   }

   @Test
   void blockCommentInsideLineComment() {
      assertEquals("one(a, b)", sig("// /* x\nfunction one(a, b) { }"));
   }

   @Test
   void blockCommentSpanningLines() {
      assertEquals("one(a, b)",
                   sig("/* header\n function two() */\nfunction one(a,\n b) { }"));
   }

   @Test
   void trailingLineComment() {
      assertEquals("one(a, b)", sig("function one(a, b) { // note\n}"));
   }
}
