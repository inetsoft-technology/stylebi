/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.report.afm;

import inetsoft.report.internal.AFontMetrics;

import java.awt.*;
import java.util.HashMap;

public class times_bold extends AFontMetrics {
   static String s_fontName = "Times-Bold";
   static String s_fullName = "Times";
   static String s_familyName = "Times";
   static String s_weight = "Bold";
   static boolean s_fixedPitch = false;
   static double s_italicAngle = 0.0;
   static int s_ascender = 676;
   static int s_descender = 205;
   static int s_advance = 1000;
   static Rectangle s_bbox = new Rectangle(-168, 935, 1168, 1153);
   static int[] s_widths = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
      0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 250, 333, 555, 500, 500,
      1000, 833, 333, 333, 333, 500, 570, 250, 333, 250, 278, 500, 500, 500,
      500, 500, 500, 500, 500, 500, 500, 333, 333, 570, 570, 570, 500, 930,
      722, 667, 722, 722, 667, 611, 778, 778, 389, 500, 778, 667, 944, 722,
      778, 611, 778, 722, 556, 667, 722, 722, 1000, 722, 722, 667, 333, 278,
      333, 581, 500, 333, 500, 556, 444, 556, 444, 333, 500, 556, 278, 333,
      556, 278, 833, 556, 500, 556, 556, 444, 389, 333, 556, 500, 722, 500,
      500, 444, 394, 220, 394, 520, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
      0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 333, 500,
      500, 167, 500, 500, 500, 500, 278, 500, 500, 333, 333, 556, 556, 0, 500,
      500, 500, 250, 0, 540, 350, 333, 500, 500, 500, 1000, 1000, 0, 500, 0,
      333, 333, 333, 333, 333, 333, 333, 333, 0, 333, 333, 0, 333, 333, 333,
      1000, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1000, 0, 300, 0, 0,
      0, 0, 667, 778, 1000, 330, 0, 0, 0, 0, 0, 722, 0, 0, 0, 278, 0, 0, 278,
      500, 722, 556, 0, 0, 0, 0};
   static HashMap<String, Integer> s_pairKern = new HashMap<>();
   static {
      s_pairKern.put("" + (char) 85 + (char) 44, -50);
      s_pairKern.put("" + (char) 120 + (char) 101, 0);
      s_pairKern.put("" + (char) 107 + (char) 121, -15);
      s_pairKern.put("" + (char) 84 + (char) 65, -90);
      s_pairKern.put("" + (char) 70 + (char) 114, 0);
      s_pairKern.put("" + (char) 70 + (char) 111, -25);
      s_pairKern.put("" + (char) 84 + (char) 59, -74);
      s_pairKern.put("" + (char) 107 + (char) 111, -15);
      s_pairKern.put("" + (char) 84 + (char) 58, -74);
      s_pairKern.put("" + (char) 96 + (char) 65, -10);
      s_pairKern.put("" + (char) 70 + (char) 105, 0);
      s_pairKern.put("" + (char) 170 + (char) 65, -10);
      s_pairKern.put("" + (char) 70 + (char) 101, -25);
      s_pairKern.put("" + (char) 107 + (char) 101, -10);
      s_pairKern.put("" + (char) 119 + (char) 111, -10);
      s_pairKern.put("" + (char) 70 + (char) 97, -25);
      s_pairKern.put("" + (char) 84 + (char) 46, -90);
      s_pairKern.put("" + (char) 84 + (char) 45, -92);
      s_pairKern.put("" + (char) 121 + (char) 46, -70);
      s_pairKern.put("" + (char) 84 + (char) 44, -74);
      s_pairKern.put("" + (char) 121 + (char) 44, -55);
      s_pairKern.put("" + (char) 119 + (char) 104, 0);
      s_pairKern.put("" + (char) 119 + (char) 101, 0);
      s_pairKern.put("" + (char) 102 + (char) 245, -35);
      s_pairKern.put("" + (char) 119 + (char) 97, 0);
      s_pairKern.put("" + (char) 82 + (char) 89, -35);
      s_pairKern.put("" + (char) 71 + (char) 46, 0);
      s_pairKern.put("" + (char) 82 + (char) 87, -35);
      s_pairKern.put("" + (char) 71 + (char) 44, 0);
      s_pairKern.put("" + (char) 82 + (char) 86, -55);
      s_pairKern.put("" + (char) 82 + (char) 85, -30);
      s_pairKern.put("" + (char) 82 + (char) 84, -40);
      s_pairKern.put("" + (char) 82 + (char) 79, -30);
      s_pairKern.put("" + (char) 118 + (char) 111, -10);
      s_pairKern.put("" + (char) 32 + (char) 96, 0);
      s_pairKern.put("" + (char) 83 + (char) 46, 0);
      s_pairKern.put("" + (char) 70 + (char) 65, -90);
      s_pairKern.put("" + (char) 83 + (char) 44, 0);
      s_pairKern.put("" + (char) 46 + (char) 39, -55);
      s_pairKern.put("" + (char) 32 + (char) 89, -55);
      s_pairKern.put("" + (char) 32 + (char) 87, -30);
      s_pairKern.put("" + (char) 118 + (char) 101, -10);
      s_pairKern.put("" + (char) 32 + (char) 86, -45);
      s_pairKern.put("" + (char) 32 + (char) 84, -30);
      s_pairKern.put("" + (char) 105 + (char) 118, -10);
      s_pairKern.put("" + (char) 118 + (char) 97, -10);
      s_pairKern.put("" + (char) 70 + (char) 46, -110);
      s_pairKern.put("" + (char) 70 + (char) 44, -92);
      s_pairKern.put("" + (char) 58 + (char) 32, 0);
      s_pairKern.put("" + (char) 81 + (char) 85, -10);
      s_pairKern.put("" + (char) 80 + (char) 111, -20);
      s_pairKern.put("" + (char) 32 + (char) 65, -55);
      s_pairKern.put("" + (char) 119 + (char) 46, -70);
      s_pairKern.put("" + (char) 119 + (char) 44, -55);
      s_pairKern.put("" + (char) 65 + (char) 186, 0);
      s_pairKern.put("" + (char) 102 + (char) 186, 50);
      s_pairKern.put("" + (char) 80 + (char) 101, -20);
      s_pairKern.put("" + (char) 68 + (char) 89, -40);
      s_pairKern.put("" + (char) 104 + (char) 121, -15);
      s_pairKern.put("" + (char) 68 + (char) 87, -40);
      s_pairKern.put("" + (char) 68 + (char) 86, -40);
      s_pairKern.put("" + (char) 80 + (char) 97, -10);
      s_pairKern.put("" + (char) 81 + (char) 46, -20);
      s_pairKern.put("" + (char) 68 + (char) 65, -35);
      s_pairKern.put("" + (char) 118 + (char) 46, -70);
      s_pairKern.put("" + (char) 81 + (char) 44, 0);
      s_pairKern.put("" + (char) 118 + (char) 44, -55);
      s_pairKern.put("" + (char) 44 + (char) 39, -55);
      s_pairKern.put("" + (char) 103 + (char) 121, 0);
      s_pairKern.put("" + (char) 44 + (char) 32, 0);
      s_pairKern.put("" + (char) 80 + (char) 65, -74);
      s_pairKern.put("" + (char) 39 + (char) 186, 0);
      s_pairKern.put("" + (char) 76 + (char) 186, -20);
      s_pairKern.put("" + (char) 103 + (char) 114, 0);
      s_pairKern.put("" + (char) 103 + (char) 111, 0);
      s_pairKern.put("" + (char) 79 + (char) 89, -50);
      s_pairKern.put("" + (char) 68 + (char) 46, -20);
      s_pairKern.put("" + (char) 79 + (char) 88, -40);
      s_pairKern.put("" + (char) 79 + (char) 87, -50);
      s_pairKern.put("" + (char) 115 + (char) 119, 0);
      s_pairKern.put("" + (char) 68 + (char) 44, 0);
      s_pairKern.put("" + (char) 79 + (char) 86, -50);
      s_pairKern.put("" + (char) 79 + (char) 84, -40);
      s_pairKern.put("" + (char) 103 + (char) 105, 0);
      s_pairKern.put("" + (char) 103 + (char) 103, 0);
      s_pairKern.put("" + (char) 103 + (char) 101, 0);
      s_pairKern.put("" + (char) 80 + (char) 46, -110);
      s_pairKern.put("" + (char) 103 + (char) 97, 0);
      s_pairKern.put("" + (char) 80 + (char) 44, -92);
      s_pairKern.put("" + (char) 65 + (char) 121, -74);
      s_pairKern.put("" + (char) 65 + (char) 119, -90);
      s_pairKern.put("" + (char) 65 + (char) 118, -100);
      s_pairKern.put("" + (char) 65 + (char) 117, -50);
      s_pairKern.put("" + (char) 66 + (char) 85, -10);
      s_pairKern.put("" + (char) 79 + (char) 65, -40);
      s_pairKern.put("" + (char) 65 + (char) 112, -25);
      s_pairKern.put("" + (char) 102 + (char) 111, -25);
      s_pairKern.put("" + (char) 114 + (char) 121, 0);
      s_pairKern.put("" + (char) 102 + (char) 108, 0);
      s_pairKern.put("" + (char) 114 + (char) 118, -10);
      s_pairKern.put("" + (char) 114 + (char) 117, 0);
      s_pairKern.put("" + (char) 114 + (char) 116, 0);
      s_pairKern.put("" + (char) 102 + (char) 105, -25);
      s_pairKern.put("" + (char) 114 + (char) 115, 0);
      s_pairKern.put("" + (char) 114 + (char) 114, 0);
      s_pairKern.put("" + (char) 114 + (char) 113, -18);
      s_pairKern.put("" + (char) 102 + (char) 102, 0);
      s_pairKern.put("" + (char) 114 + (char) 112, -10);
      s_pairKern.put("" + (char) 102 + (char) 101, 0);
      s_pairKern.put("" + (char) 114 + (char) 111, -18);
      s_pairKern.put("" + (char) 114 + (char) 110, -15);
      s_pairKern.put("" + (char) 79 + (char) 46, 0);
      s_pairKern.put("" + (char) 114 + (char) 109, 0);
      s_pairKern.put("" + (char) 66 + (char) 65, -30);
      s_pairKern.put("" + (char) 114 + (char) 108, 0);
      s_pairKern.put("" + (char) 102 + (char) 97, 0);
      s_pairKern.put("" + (char) 79 + (char) 44, 0);
      s_pairKern.put("" + (char) 89 + (char) 117, -92);
      s_pairKern.put("" + (char) 114 + (char) 107, 0);
      s_pairKern.put("" + (char) 114 + (char) 105, 0);
      s_pairKern.put("" + (char) 114 + (char) 103, -10);
      s_pairKern.put("" + (char) 65 + (char) 89, -100);
      s_pairKern.put("" + (char) 89 + (char) 111, -111);
      s_pairKern.put("" + (char) 114 + (char) 101, -18);
      s_pairKern.put("" + (char) 101 + (char) 121, 0);
      s_pairKern.put("" + (char) 114 + (char) 100, 0);
      s_pairKern.put("" + (char) 101 + (char) 120, 0);
      s_pairKern.put("" + (char) 65 + (char) 87, -130);
      s_pairKern.put("" + (char) 114 + (char) 99, -18);
      s_pairKern.put("" + (char) 101 + (char) 119, 0);
      s_pairKern.put("" + (char) 65 + (char) 86, -145);
      s_pairKern.put("" + (char) 101 + (char) 118, -15);
      s_pairKern.put("" + (char) 78 + (char) 65, -20);
      s_pairKern.put("" + (char) 65 + (char) 85, -50);
      s_pairKern.put("" + (char) 114 + (char) 97, 0);
      s_pairKern.put("" + (char) 65 + (char) 84, -95);
      s_pairKern.put("" + (char) 89 + (char) 105, -37);
      s_pairKern.put("" + (char) 65 + (char) 81, -45);
      s_pairKern.put("" + (char) 101 + (char) 112, 0);
      s_pairKern.put("" + (char) 65 + (char) 79, -45);
      s_pairKern.put("" + (char) 76 + (char) 121, -55);
      s_pairKern.put("" + (char) 89 + (char) 101, -111);
      s_pairKern.put("" + (char) 39 + (char) 118, -20);
      s_pairKern.put("" + (char) 66 + (char) 46, 0);
      s_pairKern.put("" + (char) 39 + (char) 116, 0);
      s_pairKern.put("" + (char) 39 + (char) 115, -37);
      s_pairKern.put("" + (char) 103 + (char) 46, -15);
      s_pairKern.put("" + (char) 66 + (char) 44, 0);
      s_pairKern.put("" + (char) 89 + (char) 97, -85);
      s_pairKern.put("" + (char) 39 + (char) 114, -20);
      s_pairKern.put("" + (char) 103 + (char) 44, 0);
      s_pairKern.put("" + (char) 65 + (char) 71, -55);
      s_pairKern.put("" + (char) 101 + (char) 103, 0);
      s_pairKern.put("" + (char) 39 + (char) 108, 0);
      s_pairKern.put("" + (char) 65 + (char) 67, -55);
      s_pairKern.put("" + (char) 78 + (char) 46, 0);
      s_pairKern.put("" + (char) 101 + (char) 98, 0);
      s_pairKern.put("" + (char) 78 + (char) 44, 0);
      s_pairKern.put("" + (char) 39 + (char) 100, -20);
      s_pairKern.put("" + (char) 100 + (char) 121, 0);
      s_pairKern.put("" + (char) 89 + (char) 79, -35);
      s_pairKern.put("" + (char) 100 + (char) 119, -15);
      s_pairKern.put("" + (char) 100 + (char) 118, 0);
      s_pairKern.put("" + (char) 75 + (char) 121, -45);
      s_pairKern.put("" + (char) 76 + (char) 89, -92);
      s_pairKern.put("" + (char) 112 + (char) 121, 0);
      s_pairKern.put("" + (char) 76 + (char) 87, -92);
      s_pairKern.put("" + (char) 102 + (char) 46, -15);
      s_pairKern.put("" + (char) 76 + (char) 86, -92);
      s_pairKern.put("" + (char) 75 + (char) 117, -15);
      s_pairKern.put("" + (char) 89 + (char) 65, -110);
      s_pairKern.put("" + (char) 102 + (char) 44, -15);
      s_pairKern.put("" + (char) 76 + (char) 84, -92);
      s_pairKern.put("" + (char) 65 + (char) 39, -74);
      s_pairKern.put("" + (char) 75 + (char) 111, -25);
      s_pairKern.put("" + (char) 102 + (char) 39, 55);
      s_pairKern.put("" + (char) 87 + (char) 121, -60);
      s_pairKern.put("" + (char) 89 + (char) 59, -92);
      s_pairKern.put("" + (char) 100 + (char) 100, 0);
      s_pairKern.put("" + (char) 89 + (char) 58, -92);
      s_pairKern.put("" + (char) 114 + (char) 46, -100);
      s_pairKern.put("" + (char) 87 + (char) 117, -50);
      s_pairKern.put("" + (char) 114 + (char) 45, -37);
      s_pairKern.put("" + (char) 114 + (char) 44, -92);
      s_pairKern.put("" + (char) 75 + (char) 101, -25);
      s_pairKern.put("" + (char) 87 + (char) 111, -75);
      s_pairKern.put("" + (char) 99 + (char) 121, 0);
      s_pairKern.put("" + (char) 89 + (char) 46, -92);
      s_pairKern.put("" + (char) 89 + (char) 45, -92);
      s_pairKern.put("" + (char) 89 + (char) 44, -92);
      s_pairKern.put("" + (char) 87 + (char) 105, -18);
      s_pairKern.put("" + (char) 87 + (char) 104, 0);
      s_pairKern.put("" + (char) 87 + (char) 101, -65);
      s_pairKern.put("" + (char) 111 + (char) 121, 0);
      s_pairKern.put("" + (char) 111 + (char) 120, 0);
      s_pairKern.put("" + (char) 111 + (char) 119, -10);
      s_pairKern.put("" + (char) 101 + (char) 46, 0);
      s_pairKern.put("" + (char) 99 + (char) 108, 0);
      s_pairKern.put("" + (char) 74 + (char) 117, -15);
      s_pairKern.put("" + (char) 87 + (char) 97, -65);
      s_pairKern.put("" + (char) 111 + (char) 118, -10);
      s_pairKern.put("" + (char) 99 + (char) 107, 0);
      s_pairKern.put("" + (char) 101 + (char) 44, 0);
      s_pairKern.put("" + (char) 99 + (char) 104, 0);
      s_pairKern.put("" + (char) 74 + (char) 111, -15);
      s_pairKern.put("" + (char) 75 + (char) 79, -30);
      s_pairKern.put("" + (char) 86 + (char) 117, -92);
      s_pairKern.put("" + (char) 39 + (char) 39, -63);
      s_pairKern.put("" + (char) 111 + (char) 103, 0);
      s_pairKern.put("" + (char) 76 + (char) 39, -110);
      s_pairKern.put("" + (char) 74 + (char) 101, -15);
      s_pairKern.put("" + (char) 86 + (char) 111, -100);
      s_pairKern.put("" + (char) 98 + (char) 121, 0);
      s_pairKern.put("" + (char) 87 + (char) 79, -10);
      s_pairKern.put("" + (char) 74 + (char) 97, -15);
      s_pairKern.put("" + (char) 39 + (char) 32, -74);
      s_pairKern.put("" + (char) 98 + (char) 118, -15);
      s_pairKern.put("" + (char) 98 + (char) 117, -20);
      s_pairKern.put("" + (char) 86 + (char) 105, -37);
      s_pairKern.put("" + (char) 86 + (char) 101, -100);
      s_pairKern.put("" + (char) 110 + (char) 121, 0);
      s_pairKern.put("" + (char) 100 + (char) 46, 0);
      s_pairKern.put("" + (char) 98 + (char) 108, 0);
      s_pairKern.put("" + (char) 86 + (char) 97, -92);
      s_pairKern.put("" + (char) 110 + (char) 118, -40);
      s_pairKern.put("" + (char) 87 + (char) 65, -120);
      s_pairKern.put("" + (char) 46 + (char) 186, -55);
      s_pairKern.put("" + (char) 110 + (char) 117, 0);
      s_pairKern.put("" + (char) 100 + (char) 44, 0);
      s_pairKern.put("" + (char) 87 + (char) 59, -55);
      s_pairKern.put("" + (char) 87 + (char) 58, -55);
      s_pairKern.put("" + (char) 98 + (char) 98, -10);
      s_pairKern.put("" + (char) 86 + (char) 79, -45);
      s_pairKern.put("" + (char) 97 + (char) 121, 0);
      s_pairKern.put("" + (char) 122 + (char) 111, 0);
      s_pairKern.put("" + (char) 87 + (char) 46, -92);
      s_pairKern.put("" + (char) 97 + (char) 119, 0);
      s_pairKern.put("" + (char) 97 + (char) 118, -25);
      s_pairKern.put("" + (char) 74 + (char) 65, -30);
      s_pairKern.put("" + (char) 87 + (char) 45, -37);
      s_pairKern.put("" + (char) 87 + (char) 44, -92);
      s_pairKern.put("" + (char) 97 + (char) 116, 0);
      s_pairKern.put("" + (char) 86 + (char) 71, -30);
      s_pairKern.put("" + (char) 97 + (char) 112, 0);
      s_pairKern.put("" + (char) 122 + (char) 101, 0);
      s_pairKern.put("" + (char) 186 + (char) 32, 0);
      s_pairKern.put("" + (char) 109 + (char) 121, 0);
      s_pairKern.put("" + (char) 99 + (char) 46, 0);
      s_pairKern.put("" + (char) 86 + (char) 65, -135);
      s_pairKern.put("" + (char) 109 + (char) 117, 0);
      s_pairKern.put("" + (char) 99 + (char) 44, 0);
      s_pairKern.put("" + (char) 97 + (char) 103, 0);
      s_pairKern.put("" + (char) 84 + (char) 121, -74);
      s_pairKern.put("" + (char) 86 + (char) 59, -92);
      s_pairKern.put("" + (char) 86 + (char) 58, -92);
      s_pairKern.put("" + (char) 74 + (char) 46, -20);
      s_pairKern.put("" + (char) 84 + (char) 119, -74);
      s_pairKern.put("" + (char) 97 + (char) 98, 0);
      s_pairKern.put("" + (char) 74 + (char) 44, 0);
      s_pairKern.put("" + (char) 84 + (char) 117, -92);
      s_pairKern.put("" + (char) 84 + (char) 114, -74);
      s_pairKern.put("" + (char) 84 + (char) 111, -92);
      s_pairKern.put("" + (char) 121 + (char) 111, -25);
      s_pairKern.put("" + (char) 86 + (char) 46, -145);
      s_pairKern.put("" + (char) 86 + (char) 45, -74);
      s_pairKern.put("" + (char) 86 + (char) 44, -129);
      s_pairKern.put("" + (char) 84 + (char) 105, -18);
      s_pairKern.put("" + (char) 84 + (char) 104, 0);
      s_pairKern.put("" + (char) 84 + (char) 101, -92);
      s_pairKern.put("" + (char) 121 + (char) 101, -10);
      s_pairKern.put("" + (char) 108 + (char) 121, 0);
      s_pairKern.put("" + (char) 108 + (char) 119, 0);
      s_pairKern.put("" + (char) 98 + (char) 46, -40);
      s_pairKern.put("" + (char) 84 + (char) 97, -92);
      s_pairKern.put("" + (char) 85 + (char) 65, -60);
      s_pairKern.put("" + (char) 121 + (char) 97, 0);
      s_pairKern.put("" + (char) 44 + (char) 186, -45);
      s_pairKern.put("" + (char) 98 + (char) 44, 0);
      s_pairKern.put("" + (char) 32 + (char) 170, 0);
      s_pairKern.put("" + (char) 96 + (char) 96, -63);
      s_pairKern.put("" + (char) 170 + (char) 96, 0);
      s_pairKern.put("" + (char) 84 + (char) 79, -18);
      s_pairKern.put("" + (char) 85 + (char) 46, -50);
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

