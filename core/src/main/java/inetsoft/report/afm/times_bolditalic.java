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
package inetsoft.report.afm;

import inetsoft.report.internal.AFontMetrics;

import java.awt.*;
import java.util.HashMap;

public class times_bolditalic extends AFontMetrics {
   static String s_fontName = "Times-BoldItalic";
   static String s_fullName = "Times";
   static String s_familyName = "Times";
   static String s_weight = "Bold";
   static boolean s_fixedPitch = false;
   static double s_italicAngle = -15.0;
   static int s_ascender = 699;
   static int s_descender = 205;
   static int s_advance = 1000;
   static Rectangle s_bbox = new Rectangle(-200, 921, 1196, 1139);
   static int[] s_widths = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
      0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 250, 389, 555, 500, 500,
      833, 778, 333, 333, 333, 500, 570, 250, 333, 250, 278, 500, 500, 500,
      500, 500, 500, 500, 500, 500, 500, 333, 333, 570, 570, 570, 500, 832,
      667, 667, 667, 722, 667, 667, 722, 778, 389, 500, 667, 611, 889, 722,
      722, 611, 722, 667, 556, 611, 722, 667, 889, 667, 611, 611, 333, 278,
      333, 570, 500, 333, 500, 500, 444, 500, 444, 333, 500, 556, 278, 278,
      500, 278, 778, 556, 500, 500, 500, 389, 389, 278, 556, 444, 667, 500,
      444, 389, 348, 220, 348, 570, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
      0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 389, 500,
      500, 167, 500, 500, 500, 500, 278, 500, 500, 333, 333, 556, 556, 0, 500,
      500, 500, 250, 0, 500, 350, 333, 500, 500, 500, 1000, 1000, 0, 500, 0,
      333, 333, 333, 333, 333, 333, 333, 333, 0, 333, 333, 0, 333, 333, 333,
      1000, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 944, 0, 266, 0, 0,
      0, 0, 611, 722, 944, 300, 0, 0, 0, 0, 0, 722, 0, 0, 0, 278, 0, 0, 278,
      500, 722, 500, 0, 0, 0, 0};
   static HashMap<String, Integer> s_pairKern = new HashMap<>();
   static {
      s_pairKern.put("" + (char) 85 + (char) 44, 0);
      s_pairKern.put("" + (char) 120 + (char) 101, -10);
      s_pairKern.put("" + (char) 107 + (char) 121, 0);
      s_pairKern.put("" + (char) 84 + (char) 65, -55);
      s_pairKern.put("" + (char) 70 + (char) 114, -50);
      s_pairKern.put("" + (char) 70 + (char) 111, -70);
      s_pairKern.put("" + (char) 84 + (char) 59, -74);
      s_pairKern.put("" + (char) 107 + (char) 111, -10);
      s_pairKern.put("" + (char) 84 + (char) 58, -74);
      s_pairKern.put("" + (char) 96 + (char) 65, 0);
      s_pairKern.put("" + (char) 70 + (char) 105, -40);
      s_pairKern.put("" + (char) 170 + (char) 65, 0);
      s_pairKern.put("" + (char) 70 + (char) 101, -100);
      s_pairKern.put("" + (char) 107 + (char) 101, -30);
      s_pairKern.put("" + (char) 119 + (char) 111, -15);
      s_pairKern.put("" + (char) 70 + (char) 97, -95);
      s_pairKern.put("" + (char) 84 + (char) 46, -92);
      s_pairKern.put("" + (char) 84 + (char) 45, -92);
      s_pairKern.put("" + (char) 121 + (char) 46, -37);
      s_pairKern.put("" + (char) 84 + (char) 44, -92);
      s_pairKern.put("" + (char) 121 + (char) 44, -37);
      s_pairKern.put("" + (char) 119 + (char) 104, 0);
      s_pairKern.put("" + (char) 119 + (char) 101, -10);
      s_pairKern.put("" + (char) 102 + (char) 245, -30);
      s_pairKern.put("" + (char) 119 + (char) 97, -10);
      s_pairKern.put("" + (char) 82 + (char) 89, -18);
      s_pairKern.put("" + (char) 71 + (char) 46, 0);
      s_pairKern.put("" + (char) 82 + (char) 87, -18);
      s_pairKern.put("" + (char) 71 + (char) 44, 0);
      s_pairKern.put("" + (char) 82 + (char) 86, -18);
      s_pairKern.put("" + (char) 82 + (char) 85, -40);
      s_pairKern.put("" + (char) 82 + (char) 84, -30);
      s_pairKern.put("" + (char) 82 + (char) 79, -40);
      s_pairKern.put("" + (char) 118 + (char) 111, -15);
      s_pairKern.put("" + (char) 32 + (char) 96, 0);
      s_pairKern.put("" + (char) 83 + (char) 46, 0);
      s_pairKern.put("" + (char) 70 + (char) 65, -100);
      s_pairKern.put("" + (char) 83 + (char) 44, 0);
      s_pairKern.put("" + (char) 46 + (char) 39, -95);
      s_pairKern.put("" + (char) 32 + (char) 89, -70);
      s_pairKern.put("" + (char) 32 + (char) 87, -70);
      s_pairKern.put("" + (char) 118 + (char) 101, -15);
      s_pairKern.put("" + (char) 32 + (char) 86, -70);
      s_pairKern.put("" + (char) 32 + (char) 84, 0);
      s_pairKern.put("" + (char) 105 + (char) 118, 0);
      s_pairKern.put("" + (char) 118 + (char) 97, 0);
      s_pairKern.put("" + (char) 70 + (char) 46, -129);
      s_pairKern.put("" + (char) 70 + (char) 44, -129);
      s_pairKern.put("" + (char) 58 + (char) 32, 0);
      s_pairKern.put("" + (char) 81 + (char) 85, -10);
      s_pairKern.put("" + (char) 80 + (char) 111, -55);
      s_pairKern.put("" + (char) 32 + (char) 65, -37);
      s_pairKern.put("" + (char) 119 + (char) 46, -37);
      s_pairKern.put("" + (char) 119 + (char) 44, -37);
      s_pairKern.put("" + (char) 65 + (char) 186, 0);
      s_pairKern.put("" + (char) 102 + (char) 186, 0);
      s_pairKern.put("" + (char) 80 + (char) 101, -50);
      s_pairKern.put("" + (char) 68 + (char) 89, -50);
      s_pairKern.put("" + (char) 104 + (char) 121, 0);
      s_pairKern.put("" + (char) 68 + (char) 87, -40);
      s_pairKern.put("" + (char) 68 + (char) 86, -50);
      s_pairKern.put("" + (char) 80 + (char) 97, -40);
      s_pairKern.put("" + (char) 81 + (char) 46, 0);
      s_pairKern.put("" + (char) 68 + (char) 65, -25);
      s_pairKern.put("" + (char) 118 + (char) 46, -37);
      s_pairKern.put("" + (char) 81 + (char) 44, 0);
      s_pairKern.put("" + (char) 118 + (char) 44, -37);
      s_pairKern.put("" + (char) 44 + (char) 39, -95);
      s_pairKern.put("" + (char) 103 + (char) 121, 0);
      s_pairKern.put("" + (char) 44 + (char) 32, 0);
      s_pairKern.put("" + (char) 80 + (char) 65, -85);
      s_pairKern.put("" + (char) 39 + (char) 186, 0);
      s_pairKern.put("" + (char) 76 + (char) 186, 0);
      s_pairKern.put("" + (char) 103 + (char) 114, 0);
      s_pairKern.put("" + (char) 103 + (char) 111, 0);
      s_pairKern.put("" + (char) 79 + (char) 89, -50);
      s_pairKern.put("" + (char) 68 + (char) 46, 0);
      s_pairKern.put("" + (char) 79 + (char) 88, -40);
      s_pairKern.put("" + (char) 79 + (char) 87, -50);
      s_pairKern.put("" + (char) 115 + (char) 119, 0);
      s_pairKern.put("" + (char) 68 + (char) 44, 0);
      s_pairKern.put("" + (char) 79 + (char) 86, -50);
      s_pairKern.put("" + (char) 79 + (char) 84, -40);
      s_pairKern.put("" + (char) 103 + (char) 105, 0);
      s_pairKern.put("" + (char) 103 + (char) 103, 0);
      s_pairKern.put("" + (char) 103 + (char) 101, 0);
      s_pairKern.put("" + (char) 80 + (char) 46, -129);
      s_pairKern.put("" + (char) 103 + (char) 97, 0);
      s_pairKern.put("" + (char) 80 + (char) 44, -129);
      s_pairKern.put("" + (char) 65 + (char) 121, -74);
      s_pairKern.put("" + (char) 65 + (char) 119, -74);
      s_pairKern.put("" + (char) 65 + (char) 118, -74);
      s_pairKern.put("" + (char) 65 + (char) 117, -30);
      s_pairKern.put("" + (char) 66 + (char) 85, -10);
      s_pairKern.put("" + (char) 79 + (char) 65, -40);
      s_pairKern.put("" + (char) 65 + (char) 112, 0);
      s_pairKern.put("" + (char) 102 + (char) 111, -10);
      s_pairKern.put("" + (char) 114 + (char) 121, 0);
      s_pairKern.put("" + (char) 102 + (char) 108, 0);
      s_pairKern.put("" + (char) 114 + (char) 118, 0);
      s_pairKern.put("" + (char) 114 + (char) 117, 0);
      s_pairKern.put("" + (char) 114 + (char) 116, 0);
      s_pairKern.put("" + (char) 102 + (char) 105, 0);
      s_pairKern.put("" + (char) 114 + (char) 115, 0);
      s_pairKern.put("" + (char) 114 + (char) 114, 0);
      s_pairKern.put("" + (char) 114 + (char) 113, 0);
      s_pairKern.put("" + (char) 102 + (char) 102, -18);
      s_pairKern.put("" + (char) 114 + (char) 112, 0);
      s_pairKern.put("" + (char) 102 + (char) 101, -10);
      s_pairKern.put("" + (char) 114 + (char) 111, 0);
      s_pairKern.put("" + (char) 114 + (char) 110, 0);
      s_pairKern.put("" + (char) 79 + (char) 46, 0);
      s_pairKern.put("" + (char) 114 + (char) 109, 0);
      s_pairKern.put("" + (char) 66 + (char) 65, -25);
      s_pairKern.put("" + (char) 114 + (char) 108, 0);
      s_pairKern.put("" + (char) 102 + (char) 97, 0);
      s_pairKern.put("" + (char) 79 + (char) 44, 0);
      s_pairKern.put("" + (char) 89 + (char) 117, -92);
      s_pairKern.put("" + (char) 114 + (char) 107, 0);
      s_pairKern.put("" + (char) 114 + (char) 105, 0);
      s_pairKern.put("" + (char) 114 + (char) 103, 0);
      s_pairKern.put("" + (char) 65 + (char) 89, -70);
      s_pairKern.put("" + (char) 89 + (char) 111, -111);
      s_pairKern.put("" + (char) 114 + (char) 101, 0);
      s_pairKern.put("" + (char) 101 + (char) 121, 0);
      s_pairKern.put("" + (char) 114 + (char) 100, 0);
      s_pairKern.put("" + (char) 101 + (char) 120, 0);
      s_pairKern.put("" + (char) 65 + (char) 87, -100);
      s_pairKern.put("" + (char) 114 + (char) 99, 0);
      s_pairKern.put("" + (char) 101 + (char) 119, 0);
      s_pairKern.put("" + (char) 65 + (char) 86, -95);
      s_pairKern.put("" + (char) 101 + (char) 118, 0);
      s_pairKern.put("" + (char) 78 + (char) 65, -30);
      s_pairKern.put("" + (char) 65 + (char) 85, -50);
      s_pairKern.put("" + (char) 114 + (char) 97, 0);
      s_pairKern.put("" + (char) 65 + (char) 84, -55);
      s_pairKern.put("" + (char) 89 + (char) 105, -55);
      s_pairKern.put("" + (char) 65 + (char) 81, -55);
      s_pairKern.put("" + (char) 101 + (char) 112, 0);
      s_pairKern.put("" + (char) 65 + (char) 79, -50);
      s_pairKern.put("" + (char) 76 + (char) 121, -37);
      s_pairKern.put("" + (char) 89 + (char) 101, -111);
      s_pairKern.put("" + (char) 39 + (char) 118, -15);
      s_pairKern.put("" + (char) 66 + (char) 46, 0);
      s_pairKern.put("" + (char) 39 + (char) 116, -37);
      s_pairKern.put("" + (char) 39 + (char) 115, -74);
      s_pairKern.put("" + (char) 103 + (char) 46, 0);
      s_pairKern.put("" + (char) 66 + (char) 44, 0);
      s_pairKern.put("" + (char) 89 + (char) 97, -92);
      s_pairKern.put("" + (char) 39 + (char) 114, -15);
      s_pairKern.put("" + (char) 103 + (char) 44, 0);
      s_pairKern.put("" + (char) 65 + (char) 71, -60);
      s_pairKern.put("" + (char) 101 + (char) 103, 0);
      s_pairKern.put("" + (char) 39 + (char) 108, 0);
      s_pairKern.put("" + (char) 65 + (char) 67, -65);
      s_pairKern.put("" + (char) 78 + (char) 46, 0);
      s_pairKern.put("" + (char) 101 + (char) 98, -10);
      s_pairKern.put("" + (char) 78 + (char) 44, 0);
      s_pairKern.put("" + (char) 39 + (char) 100, -15);
      s_pairKern.put("" + (char) 100 + (char) 121, 0);
      s_pairKern.put("" + (char) 89 + (char) 79, -25);
      s_pairKern.put("" + (char) 100 + (char) 119, 0);
      s_pairKern.put("" + (char) 100 + (char) 118, 0);
      s_pairKern.put("" + (char) 75 + (char) 121, -20);
      s_pairKern.put("" + (char) 76 + (char) 89, -37);
      s_pairKern.put("" + (char) 112 + (char) 121, 0);
      s_pairKern.put("" + (char) 76 + (char) 87, -37);
      s_pairKern.put("" + (char) 102 + (char) 46, -10);
      s_pairKern.put("" + (char) 76 + (char) 86, -37);
      s_pairKern.put("" + (char) 75 + (char) 117, -20);
      s_pairKern.put("" + (char) 89 + (char) 65, -74);
      s_pairKern.put("" + (char) 102 + (char) 44, -10);
      s_pairKern.put("" + (char) 76 + (char) 84, -18);
      s_pairKern.put("" + (char) 65 + (char) 39, -74);
      s_pairKern.put("" + (char) 75 + (char) 111, -25);
      s_pairKern.put("" + (char) 102 + (char) 39, 55);
      s_pairKern.put("" + (char) 87 + (char) 121, -55);
      s_pairKern.put("" + (char) 89 + (char) 59, -92);
      s_pairKern.put("" + (char) 100 + (char) 100, 0);
      s_pairKern.put("" + (char) 89 + (char) 58, -92);
      s_pairKern.put("" + (char) 114 + (char) 46, -65);
      s_pairKern.put("" + (char) 87 + (char) 117, -55);
      s_pairKern.put("" + (char) 114 + (char) 45, 0);
      s_pairKern.put("" + (char) 114 + (char) 44, -65);
      s_pairKern.put("" + (char) 75 + (char) 101, -25);
      s_pairKern.put("" + (char) 87 + (char) 111, -80);
      s_pairKern.put("" + (char) 99 + (char) 121, 0);
      s_pairKern.put("" + (char) 89 + (char) 46, -74);
      s_pairKern.put("" + (char) 89 + (char) 45, -92);
      s_pairKern.put("" + (char) 89 + (char) 44, -92);
      s_pairKern.put("" + (char) 87 + (char) 105, -37);
      s_pairKern.put("" + (char) 87 + (char) 104, 0);
      s_pairKern.put("" + (char) 87 + (char) 101, -90);
      s_pairKern.put("" + (char) 111 + (char) 121, -10);
      s_pairKern.put("" + (char) 111 + (char) 120, -10);
      s_pairKern.put("" + (char) 111 + (char) 119, -25);
      s_pairKern.put("" + (char) 101 + (char) 46, 0);
      s_pairKern.put("" + (char) 99 + (char) 108, 0);
      s_pairKern.put("" + (char) 74 + (char) 117, -40);
      s_pairKern.put("" + (char) 87 + (char) 97, -85);
      s_pairKern.put("" + (char) 111 + (char) 118, -15);
      s_pairKern.put("" + (char) 99 + (char) 107, -10);
      s_pairKern.put("" + (char) 101 + (char) 44, 0);
      s_pairKern.put("" + (char) 99 + (char) 104, -10);
      s_pairKern.put("" + (char) 74 + (char) 111, -40);
      s_pairKern.put("" + (char) 75 + (char) 79, -30);
      s_pairKern.put("" + (char) 86 + (char) 117, -55);
      s_pairKern.put("" + (char) 39 + (char) 39, -74);
      s_pairKern.put("" + (char) 111 + (char) 103, 0);
      s_pairKern.put("" + (char) 76 + (char) 39, -55);
      s_pairKern.put("" + (char) 74 + (char) 101, -40);
      s_pairKern.put("" + (char) 86 + (char) 111, -111);
      s_pairKern.put("" + (char) 98 + (char) 121, 0);
      s_pairKern.put("" + (char) 87 + (char) 79, -15);
      s_pairKern.put("" + (char) 74 + (char) 97, -40);
      s_pairKern.put("" + (char) 39 + (char) 32, -74);
      s_pairKern.put("" + (char) 98 + (char) 118, 0);
      s_pairKern.put("" + (char) 98 + (char) 117, -20);
      s_pairKern.put("" + (char) 86 + (char) 105, -55);
      s_pairKern.put("" + (char) 86 + (char) 101, -111);
      s_pairKern.put("" + (char) 110 + (char) 121, 0);
      s_pairKern.put("" + (char) 100 + (char) 46, 0);
      s_pairKern.put("" + (char) 98 + (char) 108, 0);
      s_pairKern.put("" + (char) 86 + (char) 97, -111);
      s_pairKern.put("" + (char) 110 + (char) 118, -40);
      s_pairKern.put("" + (char) 87 + (char) 65, -74);
      s_pairKern.put("" + (char) 46 + (char) 186, -95);
      s_pairKern.put("" + (char) 110 + (char) 117, 0);
      s_pairKern.put("" + (char) 100 + (char) 44, 0);
      s_pairKern.put("" + (char) 87 + (char) 59, -55);
      s_pairKern.put("" + (char) 87 + (char) 58, -55);
      s_pairKern.put("" + (char) 98 + (char) 98, -10);
      s_pairKern.put("" + (char) 86 + (char) 79, -30);
      s_pairKern.put("" + (char) 97 + (char) 121, 0);
      s_pairKern.put("" + (char) 122 + (char) 111, 0);
      s_pairKern.put("" + (char) 87 + (char) 46, -74);
      s_pairKern.put("" + (char) 97 + (char) 119, 0);
      s_pairKern.put("" + (char) 97 + (char) 118, 0);
      s_pairKern.put("" + (char) 74 + (char) 65, -25);
      s_pairKern.put("" + (char) 87 + (char) 45, -50);
      s_pairKern.put("" + (char) 87 + (char) 44, -74);
      s_pairKern.put("" + (char) 97 + (char) 116, 0);
      s_pairKern.put("" + (char) 86 + (char) 71, -10);
      s_pairKern.put("" + (char) 97 + (char) 112, 0);
      s_pairKern.put("" + (char) 122 + (char) 101, 0);
      s_pairKern.put("" + (char) 186 + (char) 32, 0);
      s_pairKern.put("" + (char) 109 + (char) 121, 0);
      s_pairKern.put("" + (char) 99 + (char) 46, 0);
      s_pairKern.put("" + (char) 86 + (char) 65, -85);
      s_pairKern.put("" + (char) 109 + (char) 117, 0);
      s_pairKern.put("" + (char) 99 + (char) 44, 0);
      s_pairKern.put("" + (char) 97 + (char) 103, 0);
      s_pairKern.put("" + (char) 84 + (char) 121, -37);
      s_pairKern.put("" + (char) 86 + (char) 59, -74);
      s_pairKern.put("" + (char) 86 + (char) 58, -74);
      s_pairKern.put("" + (char) 74 + (char) 46, -10);
      s_pairKern.put("" + (char) 84 + (char) 119, -37);
      s_pairKern.put("" + (char) 97 + (char) 98, 0);
      s_pairKern.put("" + (char) 74 + (char) 44, -10);
      s_pairKern.put("" + (char) 84 + (char) 117, -37);
      s_pairKern.put("" + (char) 84 + (char) 114, -37);
      s_pairKern.put("" + (char) 84 + (char) 111, -95);
      s_pairKern.put("" + (char) 121 + (char) 111, 0);
      s_pairKern.put("" + (char) 86 + (char) 46, -129);
      s_pairKern.put("" + (char) 86 + (char) 45, -70);
      s_pairKern.put("" + (char) 86 + (char) 44, -129);
      s_pairKern.put("" + (char) 84 + (char) 105, -37);
      s_pairKern.put("" + (char) 84 + (char) 104, 0);
      s_pairKern.put("" + (char) 84 + (char) 101, -92);
      s_pairKern.put("" + (char) 121 + (char) 101, 0);
      s_pairKern.put("" + (char) 108 + (char) 121, 0);
      s_pairKern.put("" + (char) 108 + (char) 119, 0);
      s_pairKern.put("" + (char) 98 + (char) 46, -40);
      s_pairKern.put("" + (char) 84 + (char) 97, -92);
      s_pairKern.put("" + (char) 85 + (char) 65, -45);
      s_pairKern.put("" + (char) 121 + (char) 97, 0);
      s_pairKern.put("" + (char) 44 + (char) 186, -95);
      s_pairKern.put("" + (char) 98 + (char) 44, 0);
      s_pairKern.put("" + (char) 32 + (char) 170, 0);
      s_pairKern.put("" + (char) 96 + (char) 96, -74);
      s_pairKern.put("" + (char) 170 + (char) 96, 0);
      s_pairKern.put("" + (char) 84 + (char) 79, -18);
      s_pairKern.put("" + (char) 85 + (char) 46, 0);
   }

   ; {
      fontName = s_fontName;
      fullName = s_fullName;
      familyName = s_familyName;
      weight = s_weight;
      fixedPitch = s_fixedPitch;
      italicAngle = s_italicAngle;
      ascender = s_ascender;
      descender = s_descender;
      widths = s_widths;
      pairKern = s_pairKern;
      advance = s_advance;
      bbox = s_bbox;
   }

   ;
}

