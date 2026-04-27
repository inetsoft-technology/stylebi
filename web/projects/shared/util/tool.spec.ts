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
import { Tool } from "./tool";

describe("Tool", () => {
   describe("byteEncode / byteDecode", () => {
      it("encodes and decodes special characters roundtrip", () => {
         const inputs = ["hello", "hello/world", "test#value", "path?query=1", "a[b]c", "x=y"];
         for(const input of inputs) {
            expect(Tool.byteDecode(Tool.byteEncode(input))).toBe(input);
         }
      });

      it("encodes slash by default", () => {
         const encoded = Tool.byteEncode("a/b");
         expect(encoded).not.toContain("/");
      });

      it("does not encode dot when encodeDot=false", () => {
         const encoded = Tool.byteEncode("a.b", false);
         expect(encoded).toContain(".");
      });

      it("returns empty string for null/empty input", () => {
         expect(Tool.byteEncode(null)).toBe("");
         expect(Tool.byteEncode("")).toBe("");
         expect(Tool.byteDecode(null)).toBeNull();
         expect(Tool.byteDecode("")).toBe("");
      });

      it("encodes non-ASCII characters", () => {
         const encoded = Tool.byteEncode("café");
         expect(encoded).not.toBe("café");
         expect(Tool.byteDecode(encoded)).toBe("café");
      });

      it("encodes known special chars: [ ] # < > , ; ( ) { } | + = & % ?", () => {
         const specials = ["[", "]", "#", "<", ">", ",", ";", "(", ")", "{", "}", "|", "+", "=", "&", "%", "?"];
         for(const ch of specials) {
            const encoded = Tool.byteEncode(ch);
            expect(encoded).not.toBe(ch);
            expect(Tool.byteDecode(encoded)).toBe(ch);
         }
      });
   });

   describe("byteEncodeURLComponent", () => {
      it("encodes string for URL use", () => {
         const encoded = Tool.byteEncodeURLComponent("hello world");
         expect(encoded).toContain("%20");
      });
   });

   describe("isDynamic", () => {
      it("returns true for expression starting with =", () => {
         expect(Tool.isDynamic("=someExpression")).toBe(true);
         expect(Tool.isDynamic("=1+2")).toBe(true);
      });

      it("returns true for variable starting with $", () => {
         expect(Tool.isDynamic("$variable")).toBe(true);
      });

      it("returns true for string containing ($", () => {
         expect(Tool.isDynamic("value($param)")).toBe(true);
      });

      it("returns false for static values", () => {
         expect(Tool.isDynamic("plainValue")).toBe(false);
         expect(Tool.isDynamic("123")).toBe(false);
         expect(Tool.isDynamic("")).toBe(false);
         expect(Tool.isDynamic(null)).toBe(false);
      });

      it("returns false for non-string types", () => {
         expect(Tool.isDynamic(123 as any)).toBe(false);
         expect(Tool.isDynamic({} as any)).toBe(false);
      });
   });

   describe("isVar", () => {
      it("returns true for strings starting with $", () => {
         expect(Tool.isVar("$param")).toBe(true);
         expect(Tool.isVar("$")).toBe(true);
      });

      it("returns false for other strings", () => {
         expect(Tool.isVar("=expr")).toBe(false);
         expect(Tool.isVar("plain")).toBe(false);
         expect(Tool.isVar("")).toBe(false);
         expect(Tool.isVar(null)).toBe(false);
      });
   });

   describe("isExpr", () => {
      it("returns true for strings starting with =", () => {
         expect(Tool.isExpr("=1+2")).toBe(true);
         expect(Tool.isExpr("=")).toBe(true);
      });

      it("returns false for other strings", () => {
         expect(Tool.isExpr("$var")).toBe(false);
         expect(Tool.isExpr("plain")).toBe(false);
         expect(Tool.isExpr("")).toBe(false);
         expect(Tool.isExpr(null)).toBe(false);
      });
   });

   describe("equalsIgnoreCase", () => {
      it("returns true for equal strings ignoring case", () => {
         expect(Tool.equalsIgnoreCase("hello", "HELLO")).toBe(true);
         expect(Tool.equalsIgnoreCase("Hello", "hello")).toBe(true);
         expect(Tool.equalsIgnoreCase("ABC", "abc")).toBe(true);
         expect(Tool.equalsIgnoreCase("same", "same")).toBe(true);
      });

      it("returns false for different strings", () => {
         expect(Tool.equalsIgnoreCase("hello", "world")).toBe(false);
         expect(Tool.equalsIgnoreCase("abc", "abcd")).toBe(false);
      });

      it("handles null values", () => {
         expect(Tool.equalsIgnoreCase(null, null)).toBe(true);
         expect(Tool.equalsIgnoreCase(null, "hello")).toBe(false);
         expect(Tool.equalsIgnoreCase("hello", null)).toBe(false);
      });
   });

   describe("mod", () => {
      it("computes correct modulus for positive numbers", () => {
         expect(Tool.mod(10, 3)).toBe(1);
         expect(Tool.mod(9, 3)).toBe(0);
         expect(Tool.mod(0, 5)).toBe(0);
      });

      it("returns non-negative result for negative n", () => {
         expect(Tool.mod(-1, 5)).toBe(4);
         expect(Tool.mod(-7, 3)).toBe(2);
         expect(Tool.mod(-5, 5)).toBe(0);
      });
   });

   describe("flatten", () => {
      it("flattens nested arrays", () => {
         expect(Tool.flatten([1, [2, 3], [4, [5, 6]]])).toEqual([1, 2, 3, 4, 5, 6]);
      });

      it("handles already flat array", () => {
         expect(Tool.flatten([1, 2, 3])).toEqual([1, 2, 3]);
      });

      it("handles empty array", () => {
         expect(Tool.flatten([])).toEqual([]);
      });

      it("handles deeply nested array", () => {
         expect(Tool.flatten([[[[1]]]])).toEqual([1]);
      });
   });

   describe("isValidEmail", () => {
      it("returns true for valid single email", () => {
         expect(Tool.isValidEmail("user@example.com")).toBe(true);
         expect(Tool.isValidEmail("user.name+tag@domain.co")).toBe(true);
      });

      it("returns true for multiple valid emails separated by semicolon", () => {
         expect(Tool.isValidEmail("a@example.com;b@example.com")).toBe(true);
      });

      it("returns true for empty/null input", () => {
         expect(Tool.isValidEmail("")).toBe(true);
         expect(Tool.isValidEmail(null)).toBe(true);
      });

      it("returns false for invalid email", () => {
         expect(Tool.isValidEmail("notanemail")).toBe(false);
         expect(Tool.isValidEmail("@nodomain")).toBe(false);
         expect(Tool.isValidEmail("user@")).toBe(false);
      });

      it("returns false if any email in semicolon list is invalid", () => {
         expect(Tool.isValidEmail("valid@example.com;notanemail")).toBe(false);
      });
   });

   describe("transformDate", () => {
      it("removes {ts '...'} tags", () => {
         expect(Tool.transformDate("{ts '2020-01-01 00:00:00'}")).toBe(" '2020-01-01 00:00:00'");
      });

      it("removes {t '...'} time tags", () => {
         expect(Tool.transformDate("{t '10:30:00'}")).toBe(" '10:30:00'");
      });

      it("removes {d '...'} date tags", () => {
         expect(Tool.transformDate("{d '2020-01-01'}")).toBe(" '2020-01-01'");
      });

      it("returns empty string for null/empty input", () => {
         expect(Tool.transformDate(null)).toBe("");
         expect(Tool.transformDate("")).toBe("");
      });

      it("returns plain string unchanged", () => {
         expect(Tool.transformDate("2020-01-01")).toBe("2020-01-01");
      });
   });

   describe("encodeURIPath", () => {
      it("encodes path components preserving slashes", () => {
         expect(Tool.encodeURIPath("folder/sub folder/file")).toBe("folder/sub%20folder/file");
      });

      it("encodes special characters per segment", () => {
         expect(Tool.encodeURIPath("a/b c/d#e")).toBe("a/b%20c/d%23e");
      });

      it("returns null/undefined passthrough", () => {
         expect(Tool.encodeURIPath(null)).toBeNull();
         expect(Tool.encodeURIPath(undefined)).toBeUndefined();
      });
   });

   describe("encodeURIComponentExceptSlash", () => {
      it("encodes special characters but not slashes", () => {
         const result = Tool.encodeURIComponentExceptSlash("path/to/resource?query=1");
         expect(result).toContain("/");
         expect(result).toContain("%3F");
         expect(result).not.toContain("?");
      });

      it("encodes spaces", () => {
         expect(Tool.encodeURIComponentExceptSlash("hello world")).toBe("hello%20world");
      });
   });

   describe("isNumber", () => {
      it("returns true for numeric values", () => {
         expect(Tool.isNumber(0)).toBe(true);
         expect(Tool.isNumber(42)).toBe(true);
         expect(Tool.isNumber(-5)).toBe(true);
         expect(Tool.isNumber(3.14)).toBe(true);
         expect(Tool.isNumber("42")).toBe(true);
         expect(Tool.isNumber("0")).toBe(true);
      });

      it("returns false for non-numeric values", () => {
         expect(Tool.isNumber("abc")).toBe(false);
         expect(Tool.isNumber(null)).toBe(false);
         expect(Tool.isNumber(undefined)).toBe(false);
         expect(Tool.isNumber("")).toBe(false);
         expect(Tool.isNumber(NaN)).toBe(false);
      });
   });

   describe("formatCatalogString", () => {
      it("replaces indexed placeholders", () => {
         expect(Tool.formatCatalogString("Hello %s$0, you have %s$1 messages", ["Alice", 5]))
            .toBe("Hello Alice, you have 5 messages");
      });

      it("handles single replacement", () => {
         expect(Tool.formatCatalogString("Value: %s$0", ["test"])).toBe("Value: test");
      });

      it("returns null if format is null", () => {
         expect(Tool.formatCatalogString(null, ["test"])).toBeNull();
      });

      it("handles empty string format", () => {
         expect(Tool.formatCatalogString("", [])).toBe("");
      });
   });

   describe("replaceStr", () => {
      it("replaces all occurrences of a substring", () => {
         expect(Tool.replaceStr("a.b.c.d", "\\.", "-")).toBe("a-b-c-d");
      });
   });

   describe("getAssetIdFromUrl", () => {
      it("returns null for single-element url array", () => {
         expect(Tool.getAssetIdFromUrl(["portal"])).toBeNull();
      });

      it("constructs global asset ID from url", () => {
         const result = Tool.getAssetIdFromUrl(["portal", "global", "MyFolder", "MyReport"]);
         expect(result).toBe("1^128^__NULL__^MyFolder/MyReport");
      });

      it("constructs user asset ID from url", () => {
         const result = Tool.getAssetIdFromUrl(["portal", "user", "alice", "Report1"]);
         expect(result).toBe("4^128^alice^Report1");
      });

      it("handles pre-encoded asset ID in url", () => {
         const result = Tool.getAssetIdFromUrl(["portal", "1^128^__NULL__^MyReport"]);
         expect(result).toBe("1^128^__NULL__^MyReport");
      });
   });

   describe("numberCalculate", () => {
      it("adds two numbers by default", () => {
         expect(Tool.numberCalculate(1.1, 2.2)).toBeCloseTo(3.3);
         expect(Tool.numberCalculate(0, 0)).toBe(0);
      });

      it("subtracts when isSubtract is true", () => {
         expect(Tool.numberCalculate(5, 2, true)).toBeCloseTo(3);
         expect(Tool.numberCalculate(1, 1, true)).toBeCloseTo(0);
      });

      it("handles floating-point precision via digits parameter", () => {
         expect(Tool.numberCalculate(0.1, 0.2, false, 2)).toBeCloseTo(0.3, 2);
      });
   });

   describe("getDamerauLevenshteinDistance", () => {
      it("returns 1 for identical strings", () => {
         expect(Tool.getDamerauLevenshteinDistance("hello", "hello")).toBe(1);
      });

      it("returns 0 for empty inputs", () => {
         expect(Tool.getDamerauLevenshteinDistance("", "hello")).toBe(0);
         expect(Tool.getDamerauLevenshteinDistance("hello", "")).toBe(0);
         expect(Tool.getDamerauLevenshteinDistance(null, "hello")).toBe(0);
      });

      it("returns lower value for less similar strings", () => {
         const similar = Tool.getDamerauLevenshteinDistance("hello", "helo");
         const different = Tool.getDamerauLevenshteinDistance("hello", "xyz");
         expect(similar).toBeGreaterThan(different);
      });

      it("is case-insensitive by default", () => {
         expect(Tool.getDamerauLevenshteinDistance("Hello", "hello"))
            .toBe(Tool.getDamerauLevenshteinDistance("hello", "hello"));
      });
   });

   describe("getJaroWinklerDistance", () => {
      it("returns 1 for identical strings", () => {
         expect(Tool.getJaroWinklerDistance("test", "test")).toBe(1);
      });

      it("returns 0 for empty inputs", () => {
         expect(Tool.getJaroWinklerDistance("", "hello")).toBe(0);
         expect(Tool.getJaroWinklerDistance(null, "hello")).toBe(0);
      });

      it("returns higher value for more similar strings", () => {
         const similar = Tool.getJaroWinklerDistance("MARTHA", "MARHTA");
         const different = Tool.getJaroWinklerDistance("MARTHA", "ZZZZZ");
         expect(similar).toBeGreaterThan(different);
      });
   });

   describe("getSubstringMatchDistance", () => {
      it("returns 1 for identical strings", () => {
         expect(Tool.getSubstringMatchDistance("hello", "hello")).toBe(1);
      });

      it("returns 0 for empty inputs", () => {
         expect(Tool.getSubstringMatchDistance("", "hello")).toBe(0);
         expect(Tool.getSubstringMatchDistance(null, "hello")).toBe(0);
      });

      it("returns 0 when neither is a substring of the other", () => {
         expect(Tool.getSubstringMatchDistance("abc", "xyz")).toBe(0);
      });

      it("returns value between 0 and 1 for substring match", () => {
         const result = Tool.getSubstringMatchDistance("hello world", "hello");
         expect(result).toBeGreaterThan(0);
         expect(result).toBeLessThan(1);
      });

      it("returns 0 for same-length non-equal strings (early-exit path)", () => {
         expect(Tool.getSubstringMatchDistance("hello", "world")).toBe(0);
      });
   });
});
