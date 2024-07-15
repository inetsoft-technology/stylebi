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
package inetsoft.report.internal;

/**
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public final class SymbolMapper {
   private static char[] symbol_math = { (char) 0042, (char) 0000, (char) 0144,
      (char) 0044, (char) 0000, (char) 0306, (char) 0104, (char) 0321,
      (char) 0316, (char) 0317, (char) 0000, (char) 0000, (char) 0000,
      (char) 0047, (char) 0000, (char) 0120, (char) 0000, (char) 0345,
      (char) 0055, (char) 0000, (char) 0000, (char) 0244, (char) 0000,
      (char) 0052, (char) 0260, (char) 0267, (char) 0326, (char) 0000,
      (char) 0000, (char) 0265, (char) 0245, (char) 0000, (char) 0320,
      (char) 0000, (char) 0000, (char) 0275, (char) 0000, (char) 0000,
      (char) 0000, (char) 0331, (char) 0332, (char) 0307, (char) 0310,
      (char) 0362, (char) 0000, (char) 0000, (char) 0000, (char) 0000,
      (char) 0000, (char) 0000, (char) 0000, (char) 0000, (char) 0134,
      (char) 0000, (char) 0072, (char) 0000, (char) 0000, (char) 0000,
      (char) 0000, (char) 0000, (char) 0176, (char) 0000, (char) 0000,
      (char) 0000, (char) 0000, (char) 0000, (char) 0000, (char) 0000,
      (char) 0000, (char) 0100, (char) 0000, (char) 0000, (char) 0273,
      (char) 0000, (char) 0000, (char) 0000, (char) 0100, (char) 0000,
      (char) 0000, (char) 0000, (char) 0000, (char) 0000, (char) 0000,
      (char) 0000, (char) 0000, (char) 0000, (char) 0000, (char) 0000,
      (char) 0000, (char) 0000, (char) 0000, (char) 0000, (char) 0000,
      (char) 0000, (char) 0000, (char) 0000, (char) 0271, (char) 0272,
      (char) 0000, (char) 0000, (char) 0243, (char) 0263, (char) 0000,
      (char) 0000, (char) 0000, (char) 0000, (char) 0000, (char) 0000,
      (char) 0000, (char) 0000, (char) 0000, (char) 0000, (char) 0000,
      (char) 0000, (char) 0000, (char) 0000, (char) 0000, (char) 0000,
      (char) 0000, (char) 0000, (char) 0000, (char) 0000, (char) 0000,
      (char) 0000, (char) 0000, (char) 0000, (char) 0000, (char) 0000,
      (char) 0000, (char) 0000, (char) 0314, (char) 0311, (char) 0313,
      (char) 0000, (char) 0315, (char) 0312, (char) 0000, (char) 0000,
      (char) 0000, (char) 0000, (char) 0000, (char) 0000, (char) 0000,
      (char) 0000, (char) 0000, (char) 0000, (char) 0000, (char) 0000,
      (char) 0000, (char) 0305, (char) 0000, (char) 0304, (char) 0000,
      (char) 0000, (char) 0000, (char) 0000, (char) 0000, (char) 0000,
      (char) 0000, (char) 0000, (char) 0000, (char) 0000, (char) 0000,
      (char) 0000, (char) 0000, (char) 0136, (char) 0000, (char) 0000,
      (char) 0000, (char) 0000, (char) 0000, (char) 0000, (char) 0000,
      (char) 0000, (char) 0000, (char) 0000, (char) 0000, (char) 0000,
      (char) 0000, (char) 0000, (char) 0000, (char) 0000, (char) 0000,
      (char) 0000, (char) 0000, (char) 0000, (char) 0000, (char) 0000,
      (char) 0000, (char) 0000, (char) 0000, (char) 0000, (char) 0000,
      (char) 0000, (char) 0000, (char) 0000, (char) 0340, (char) 0327,
      (char) 0000, (char) 0000, (char) 0000, (char) 0000, (char) 0000,
      (char) 0000, (char) 0000, (char) 0000, (char) 0000, (char) 0000,
      (char) 0000, (char) 0000, (char) 0000, (char) 0000, (char) 0000,
      (char) 0000, (char) 0000, (char) 0000, (char) 0000, (char) 0000,
      (char) 0000, (char) 0000, (char) 0000, (char) 0000, (char) 0000,
      (char) 0000, (char) 0000, (char) 0000, (char) 0000, (char) 0000,
      (char) 0000, (char) 0000, (char) 0000, (char) 0000, (char) 0000,
      (char) 0000, (char) 0000, (char) 0000, (char) 0000, (char) 0000,
      (char) 0000, (char) 0274, };
   private static char[] symbol_greek = { (char) 0101, (char) 0102,
      (char) 0107, (char) 0104, (char) 0105, (char) 0132, (char) 0110,
      (char) 0121, (char) 0111, (char) 0113, (char) 0114, (char) 0115,
      (char) 0116, (char) 0130, (char) 0117, (char) 0120, (char) 0122,
      (char) 0000, (char) 0123, (char) 0124, (char) 0125, (char) 0106,
      (char) 0103, (char) 0131, (char) 0127, (char) 0000, (char) 0000,
      (char) 0000, (char) 0000, (char) 0000, (char) 0000, (char) 0000,
      (char) 0141, (char) 0142, (char) 0147, (char) 0144, (char) 0145,
      (char) 0172, (char) 0150, (char) 0161, (char) 0151, (char) 0153,
      (char) 0154, (char) 0155, (char) 0156, (char) 0170, (char) 0157,
      (char) 0160, (char) 0162, (char) 0126, (char) 0163, (char) 0164,
      (char) 0165, (char) 0146, (char) 0143, (char) 0171, (char) 0167,
      (char) 0000, (char) 0000, (char) 0000, (char) 0000, (char) 0000,
      (char) 0000, (char) 0000, (char) 0112, (char) 0241, (char) 0000,
      (char) 0000, (char) 0152, (char) 0166 };

   public static char map(char ch) {
      if(ch >= 0x0391) {
         if(ch <= 0x03d6) {
            return symbol_greek[ch - 0x0391];
         }
         else if(ch >= 0x2200 && ch <= 0x22ef) {
            return symbol_math[ch - 0x2200];
         }
      }

      return (char) 0;
   }
}

