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
package inetsoft.web.viewsheet.service;

import inetsoft.util.Tool;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletResponse;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #79670: the 60 character download file name limit must be measured on the decoded name
 * (including the extension) in code points, not on the percent-encoded string.
 */
@Tag("core")
class VSExportServiceFileNameTest {
   private static final String CJK = "销售分析报表统计数据汇总图表展示";

   private static MockHttpServletResponse export(String name, String suffix) {
      MockHttpServletResponse response = new MockHttpServletResponse();
      VSExportService.setResponseHeader(new ExportResponse(response), suffix, "attachment", name,
                                        "application/octet-stream");
      return response;
   }

   private static String header(String name, String suffix) {
      return export(name, suffix).getHeader("Content-disposition");
   }

   /** The encoded value of filename*. */
   private static String encoded(String name, String suffix) {
      String h = header(name, suffix);
      int start = h.indexOf("filename*=utf-8''") + "filename*=utf-8''".length();
      return h.substring(start, h.indexOf("; filename=\"", start));
   }

   private static String decoded(String name, String suffix) {
      return URLDecoder.decode(encoded(name, suffix).replace("+", "%2B"), StandardCharsets.UTF_8);
   }

   /** The algorithm before the fix, used as the oracle for names that must not change. */
   private static String legacy(String name, String suffix) {
      String name0 = name;
      String fileName = Tool.encodeURL(Tool.normalizeFileName(name0 + "." + suffix, true));
      fileName = fileName.replaceAll("'", "%27");

      while(fileName.length() > 60) {
         if(name0.length() <= 1) {
            break;
         }

         name0 = name0.substring(0, name0.length() - 1);
         fileName = Tool.encodeURL(Tool.normalizeFileName(name0 + "." + suffix));
      }

      return fileName;
   }

   @ParameterizedTest
   @CsvSource({"4,xlsx", "6,xlsx", "7,xlsx", "10,xlsx", "12,xlsx", "16,xlsx",
               "4,pdf", "6,pdf", "7,pdf", "10,pdf", "12,pdf", "16,pdf"})
   void cjkNameIsKeptInFull(int length, String suffix) {
      String name = CJK.substring(0, length);
      assertEquals(name + "." + suffix, decoded(name, suffix));
   }

   @Test
   void cjkNameAtAndOverTheLimit() {
      String at = "报".repeat(55); // 55 + ".xlsx" = exactly 60
      assertEquals(at + ".xlsx", decoded(at, "xlsx"));

      String over = "报".repeat(56);
      assertEquals(at + ".xlsx", decoded(over, "xlsx"));
      assertEquals(60, decoded(over, "xlsx").codePointCount(0, decoded(over, "xlsx").length()));

      // pdf has a shorter extension, so one more name character fits
      assertEquals("报".repeat(56) + ".pdf", decoded(over, "pdf"));
      assertEquals("报".repeat(56) + ".pdf", decoded("报".repeat(60), "pdf"));
   }

   @Test
   void emojiAreNotSplit() {
      String name = "\uD83D\uDE00".repeat(8);
      assertEquals(name + ".xlsx", decoded(name, "xlsx"));
      assertFalse(encoded(name, "xlsx").contains("?"));

      // cut by the limit: 55 code points of name, never a lone surrogate
      String long0 = "\uD83D\uDE00".repeat(70);
      String result = decoded(long0, "xlsx");
      assertEquals("\uD83D\uDE00".repeat(55) + ".xlsx", result);
      assertFalse(encoded(long0, "xlsx").contains("%3F"));
      assertFalse(encoded(long0, "xlsx").contains("?"));
   }

   @Test
   void asciiWithSpacesAndSpecialsKeepsUpToTheLimit() {
      String name = "a b c Sales Report, 2026 (final), v2 & more words in the name";
      String result = decoded(name, "xlsx");
      assertEquals(60, result.codePointCount(0, result.length()));
      assertTrue(result.endsWith(".xlsx"));
      assertTrue(result.startsWith("a b c Sales Report_44_ 2026 (final)_44_ v2 & more"));
      // comma and apostrophe stay escaped after the cut
      String enc = encoded(name + name, "xlsx");
      assertFalse(enc.contains(","));
      assertFalse(enc.contains("'"));
   }

   @Test
   void apostropheStaysEscapedAfterCut() {
      String enc = encoded("it's ".repeat(20), "xlsx");
      assertFalse(enc.contains("'"));
      assertTrue(enc.contains("%27"));
   }

   @Test
   void exactly60AsciiIsNotCutAndMatchesLegacy() {
      String name = "x".repeat(55);
      assertEquals(name + ".xlsx", decoded(name, "xlsx"));
      assertEquals(legacy(name, "xlsx"), encoded(name, "xlsx"));

      String over = "x".repeat(56);
      assertEquals(name + ".xlsx", decoded(over, "xlsx"));
      assertEquals(legacy(over, "xlsx"), encoded(over, "xlsx"));
   }

   @ParameterizedTest
   @ValueSource(strings = {"Sales Report", "a b c", "it's", "50%off", "100%", "q\"uote",
                           "a/b\\c", "a:b*c?d<e>f|g;h", "a,b", "report...", "  ", "", " ",
                           "x\r\nSet-Cookie: a=b", "销售", "销售 Report 2026 分析"})
   void unaffectedNamesAreByteIdentical(String name) {
      assertEquals(legacy(name, "xlsx"), encoded(name, "xlsx"));
      assertEquals(legacy(name, "pdf"), encoded(name, "pdf"));
   }

   @Test
   void headerShapeIsKept() {
      MockHttpServletResponse response = export("销售", "xlsx");
      String enc = encoded("销售", "xlsx");
      assertEquals("attachment; filename*=utf-8''" + enc + "; filename=\"" + enc + "\"",
                   response.getHeader("Content-disposition"));
      assertEquals("xlsx", response.getHeader("extension"));
      assertEquals("application/octet-stream", response.getContentType());
   }

   @Test
   void crlfIsNeverRawInTheHeader() {
      String h = header("x\r\nSet-Cookie: a=b" + "y".repeat(80), "xlsx");
      assertFalse(h.contains("\r"));
      assertFalse(h.contains("\n"));
      assertEquals(1, h.chars().filter(c -> c == '"').count() / 2);
   }

   @Test
   void blankNameProducesExtensionOnly() {
      assertEquals(".xlsx", decoded("", "xlsx"));
   }
}
