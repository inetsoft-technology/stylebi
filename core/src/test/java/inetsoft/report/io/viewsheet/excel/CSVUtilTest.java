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
package inetsoft.report.io.viewsheet.excel;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/*
 * Bug #79578. needsCsvQuoting() only tested the delimiter and the double quote, so a cell
 * containing a line break was written unquoted, splitting one logical record across two CSV
 * rows (RFC 4180 requires quoting a field that contains CR, LF, the delimiter, or a quote).
 */
@Tag("core")
class CSVUtilTest {
   @Test
   void quotesOnLineFeed() {
      assertTrue(CSVUtil.needsCsvQuoting("line1\nline2", ","));
   }

   @Test
   void quotesOnCarriageReturn() {
      assertTrue(CSVUtil.needsCsvQuoting("line1\rline2", ","));
   }

   @Test
   void quotesOnCrLf() {
      assertTrue(CSVUtil.needsCsvQuoting("line1\r\nline2", ","));
   }

   @Test
   void quotesOnDelimiter() {
      assertTrue(CSVUtil.needsCsvQuoting("has,comma", ","));
   }

   @Test
   void quotesOnDoubleQuote() {
      assertTrue(CSVUtil.needsCsvQuoting("has\"quote", ","));
   }

   @Test
   void doesNotQuotePlainText() {
      assertFalse(CSVUtil.needsCsvQuoting("plain", ","));
   }

   @Test
   void nullIsNotQuoted() {
      assertFalse(CSVUtil.needsCsvQuoting(null, ","));
   }
}
