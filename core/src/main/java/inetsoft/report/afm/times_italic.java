/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.report.afm;

import inetsoft.report.internal.AFontMetrics;

import java.awt.*;
import java.util.HashMap;

public class times_italic extends AFontMetrics {
   static String s_fontName = "Times-Italic";
   static String s_fullName = "Times";
   static String s_familyName = "Times";
   static String s_weight = "Medium";
   static boolean s_fixedPitch = false;
   static double s_italicAngle = -15.5;
   static int s_ascender = 683;
   static int s_descender = 205;
   static int s_advance = 1000;
   static Rectangle s_bbox = new Rectangle(-169, 883, 1179, 1100);
   static int[] s_widths = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
      0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 250, 333, 420, 500, 500,
      833, 778, 333, 333, 333, 500, 675, 250, 333, 250, 278, 500, 500, 500,
      500, 500, 500, 500, 500, 500, 500, 333, 333, 675, 675, 675, 500, 920,
      611, 611, 667, 722, 611, 611, 722, 722, 333, 444, 667, 556, 833, 667,
      722, 611, 722, 611, 500, 556, 722, 611, 833, 611, 556, 556, 389, 278,
      389, 422, 500, 333, 500, 500, 444, 500, 444, 278, 500, 500, 278, 278,
      444, 278, 722, 500, 500, 500, 500, 389, 389, 278, 500, 444, 667, 444,
      444, 389, 400, 275, 400, 541, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
      0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 389, 500,
      500, 167, 500, 500, 500, 500, 214, 556, 500, 333, 333, 500, 500, 0, 500,
      500, 500, 250, 0, 523, 350, 333, 556, 556, 500, 889, 1000, 0, 500, 0,
      333, 333, 333, 333, 333, 333, 333, 333, 0, 333, 333, 0, 333, 333, 333,
      889, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 889, 0, 276, 0, 0,
      0, 0, 556, 722, 944, 310, 0, 0, 0, 0, 0, 667, 0, 0, 0, 278, 0, 0, 278,
      500, 667, 500, 0, 0, 0, 0};
   static HashMap<String, Integer> s_pairKern = new HashMap<>();
   static {
      s_pairKern.put("" + (char) 85 + (char) 44, -25);
      s_pairKern.put("" + (char) 120 + (char) 101, 0);
      s_pairKern.put("" + (char) 107 + (char) 121, -10);
      s_pairKern.put("" + (char) 84 + (char) 65, -50);
      s_pairKern.put("" + (char) 70 + (char) 114, -55);
      s_pairKern.put("" + (char) 70 + (char) 111, -105);
      s_pairKern.put("" + (char) 84 + (char) 59, -65);
      s_pairKern.put("" + (char) 107 + (char) 111, -10);
      s_pairKern.put("" + (char) 84 + (char) 58, -55);
      s_pairKern.put("" + (char) 96 + (char) 65, 0);
      s_pairKern.put("" + (char) 70 + (char) 105, -45);
      s_pairKern.put("" + (char) 170 + (char) 65, 0);
      s_pairKern.put("" + (char) 70 + (char) 101, -75);
      s_pairKern.put("" + (char) 107 + (char) 101, -10);
      s_pairKern.put("" + (char) 119 + (char) 111, 0);
      s_pairKern.put("" + (char) 70 + (char) 97, -75);
      s_pairKern.put("" + (char) 84 + (char) 46, -74);
      s_pairKern.put("" + (char) 84 + (char) 45, -74);
      s_pairKern.put("" + (char) 121 + (char) 46, -55);
      s_pairKern.put("" + (char) 84 + (char) 44, -74);
      s_pairKern.put("" + (char) 121 + (char) 44, -55);
      s_pairKern.put("" + (char) 119 + (char) 104, 0);
      s_pairKern.put("" + (char) 119 + (char) 101, 0);
      s_pairKern.put("" + (char) 102 + (char) 245, -60);
      s_pairKern.put("" + (char) 119 + (char) 97, 0);
      s_pairKern.put("" + (char) 82 + (char) 89, -18);
      s_pairKern.put("" + (char) 71 + (char) 46, 0);
      s_pairKern.put("" + (char) 82 + (char) 87, -18);
      s_pairKern.put("" + (char) 71 + (char) 44, 0);
      s_pairKern.put("" + (char) 82 + (char) 86, -18);
      s_pairKern.put("" + (char) 82 + (char) 85, -40);
      s_pairKern.put("" + (char) 82 + (char) 84, 0);
      s_pairKern.put("" + (char) 82 + (char) 79, -40);
      s_pairKern.put("" + (char) 118 + (char) 111, 0);
      s_pairKern.put("" + (char) 32 + (char) 96, 0);
      s_pairKern.put("" + (char) 83 + (char) 46, 0);
      s_pairKern.put("" + (char) 70 + (char) 65, -115);
      s_pairKern.put("" + (char) 83 + (char) 44, 0);
      s_pairKern.put("" + (char) 46 + (char) 39, -140);
      s_pairKern.put("" + (char) 32 + (char) 89, -75);
      s_pairKern.put("" + (char) 32 + (char) 87, -40);
      s_pairKern.put("" + (char) 118 + (char) 101, 0);
      s_pairKern.put("" + (char) 32 + (char) 86, -35);
      s_pairKern.put("" + (char) 32 + (char) 84, -18);
      s_pairKern.put("" + (char) 105 + (char) 118, 0);
      s_pairKern.put("" + (char) 118 + (char) 97, 0);
      s_pairKern.put("" + (char) 70 + (char) 46, -135);
      s_pairKern.put("" + (char) 70 + (char) 44, -135);
      s_pairKern.put("" + (char) 58 + (char) 32, 0);
      s_pairKern.put("" + (char) 81 + (char) 85, -10);
      s_pairKern.put("" + (char) 80 + (char) 111, -80);
      s_pairKern.put("" + (char) 32 + (char) 65, -18);
      s_pairKern.put("" + (char) 119 + (char) 46, -74);
      s_pairKern.put("" + (char) 119 + (char) 44, -74);
      s_pairKern.put("" + (char) 65 + (char) 186, 0);
      s_pairKern.put("" + (char) 102 + (char) 186, 0);
      s_pairKern.put("" + (char) 80 + (char) 101, -80);
      s_pairKern.put("" + (char) 68 + (char) 89, -40);
      s_pairKern.put("" + (char) 104 + (char) 121, 0);
      s_pairKern.put("" + (char) 68 + (char) 87, -40);
      s_pairKern.put("" + (char) 68 + (char) 86, -40);
      s_pairKern.put("" + (char) 80 + (char) 97, -80);
      s_pairKern.put("" + (char) 81 + (char) 46, 0);
      s_pairKern.put("" + (char) 68 + (char) 65, -35);
      s_pairKern.put("" + (char) 118 + (char) 46, -74);
      s_pairKern.put("" + (char) 81 + (char) 44, 0);
      s_pairKern.put("" + (char) 118 + (char) 44, -74);
      s_pairKern.put("" + (char) 44 + (char) 39, -140);
      s_pairKern.put("" + (char) 103 + (char) 121, 0);
      s_pairKern.put("" + (char) 44 + (char) 32, 0);
      s_pairKern.put("" + (char) 80 + (char) 65, -90);
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
      s_pairKern.put("" + (char) 103 + (char) 103, -10);
      s_pairKern.put("" + (char) 103 + (char) 101, -10);
      s_pairKern.put("" + (char) 80 + (char) 46, -135);
      s_pairKern.put("" + (char) 103 + (char) 97, 0);
      s_pairKern.put("" + (char) 80 + (char) 44, -135);
      s_pairKern.put("" + (char) 65 + (char) 121, -55);
      s_pairKern.put("" + (char) 65 + (char) 119, -55);
      s_pairKern.put("" + (char) 65 + (char) 118, -55);
      s_pairKern.put("" + (char) 65 + (char) 117, -20);
      s_pairKern.put("" + (char) 66 + (char) 85, -10);
      s_pairKern.put("" + (char) 79 + (char) 65, -55);
      s_pairKern.put("" + (char) 65 + (char) 112, 0);
      s_pairKern.put("" + (char) 102 + (char) 111, 0);
      s_pairKern.put("" + (char) 114 + (char) 121, 0);
      s_pairKern.put("" + (char) 102 + (char) 108, 0);
      s_pairKern.put("" + (char) 114 + (char) 118, 0);
      s_pairKern.put("" + (char) 114 + (char) 117, 0);
      s_pairKern.put("" + (char) 114 + (char) 116, 0);
      s_pairKern.put("" + (char) 102 + (char) 105, -20);
      s_pairKern.put("" + (char) 114 + (char) 115, -10);
      s_pairKern.put("" + (char) 114 + (char) 114, 0);
      s_pairKern.put("" + (char) 114 + (char) 113, -37);
      s_pairKern.put("" + (char) 102 + (char) 102, -18);
      s_pairKern.put("" + (char) 114 + (char) 112, 0);
      s_pairKern.put("" + (char) 102 + (char) 101, 0);
      s_pairKern.put("" + (char) 114 + (char) 111, -45);
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
      s_pairKern.put("" + (char) 114 + (char) 103, -37);
      s_pairKern.put("" + (char) 65 + (char) 89, -55);
      s_pairKern.put("" + (char) 89 + (char) 111, -92);
      s_pairKern.put("" + (char) 114 + (char) 101, -37);
      s_pairKern.put("" + (char) 101 + (char) 121, -30);
      s_pairKern.put("" + (char) 114 + (char) 100, -37);
      s_pairKern.put("" + (char) 101 + (char) 120, -20);
      s_pairKern.put("" + (char) 65 + (char) 87, -95);
      s_pairKern.put("" + (char) 114 + (char) 99, -37);
      s_pairKern.put("" + (char) 101 + (char) 119, -15);
      s_pairKern.put("" + (char) 65 + (char) 86, -105);
      s_pairKern.put("" + (char) 101 + (char) 118, -15);
      s_pairKern.put("" + (char) 78 + (char) 65, -27);
      s_pairKern.put("" + (char) 65 + (char) 85, -50);
      s_pairKern.put("" + (char) 114 + (char) 97, -15);
      s_pairKern.put("" + (char) 65 + (char) 84, -37);
      s_pairKern.put("" + (char) 89 + (char) 105, -74);
      s_pairKern.put("" + (char) 65 + (char) 81, -40);
      s_pairKern.put("" + (char) 101 + (char) 112, 0);
      s_pairKern.put("" + (char) 65 + (char) 79, -40);
      s_pairKern.put("" + (char) 76 + (char) 121, -30);
      s_pairKern.put("" + (char) 89 + (char) 101, -92);
      s_pairKern.put("" + (char) 39 + (char) 118, -10);
      s_pairKern.put("" + (char) 66 + (char) 46, 0);
      s_pairKern.put("" + (char) 39 + (char) 116, -30);
      s_pairKern.put("" + (char) 39 + (char) 115, -40);
      s_pairKern.put("" + (char) 103 + (char) 46, -15);
      s_pairKern.put("" + (char) 66 + (char) 44, 0);
      s_pairKern.put("" + (char) 89 + (char) 97, -92);
      s_pairKern.put("" + (char) 39 + (char) 114, -25);
      s_pairKern.put("" + (char) 103 + (char) 44, -10);
      s_pairKern.put("" + (char) 65 + (char) 71, -35);
      s_pairKern.put("" + (char) 101 + (char) 103, -40);
      s_pairKern.put("" + (char) 39 + (char) 108, 0);
      s_pairKern.put("" + (char) 65 + (char) 67, -30);
      s_pairKern.put("" + (char) 78 + (char) 46, 0);
      s_pairKern.put("" + (char) 101 + (char) 98, 0);
      s_pairKern.put("" + (char) 78 + (char) 44, 0);
      s_pairKern.put("" + (char) 39 + (char) 100, -25);
      s_pairKern.put("" + (char) 100 + (char) 121, 0);
      s_pairKern.put("" + (char) 89 + (char) 79, -15);
      s_pairKern.put("" + (char) 100 + (char) 119, 0);
      s_pairKern.put("" + (char) 100 + (char) 118, 0);
      s_pairKern.put("" + (char) 75 + (char) 121, -40);
      s_pairKern.put("" + (char) 76 + (char) 89, -20);
      s_pairKern.put("" + (char) 112 + (char) 121, 0);
      s_pairKern.put("" + (char) 76 + (char) 87, -55);
      s_pairKern.put("" + (char) 102 + (char) 46, -15);
      s_pairKern.put("" + (char) 76 + (char) 86, -55);
      s_pairKern.put("" + (char) 75 + (char) 117, -40);
      s_pairKern.put("" + (char) 89 + (char) 65, -50);
      s_pairKern.put("" + (char) 102 + (char) 44, -10);
      s_pairKern.put("" + (char) 76 + (char) 84, -20);
      s_pairKern.put("" + (char) 65 + (char) 39, -37);
      s_pairKern.put("" + (char) 75 + (char) 111, -40);
      s_pairKern.put("" + (char) 102 + (char) 39, 92);
      s_pairKern.put("" + (char) 87 + (char) 121, -70);
      s_pairKern.put("" + (char) 89 + (char) 59, -65);
      s_pairKern.put("" + (char) 100 + (char) 100, 0);
      s_pairKern.put("" + (char) 89 + (char) 58, -65);
      s_pairKern.put("" + (char) 114 + (char) 46, -111);
      s_pairKern.put("" + (char) 87 + (char) 117, -55);
      s_pairKern.put("" + (char) 114 + (char) 45, -20);
      s_pairKern.put("" + (char) 114 + (char) 44, -111);
      s_pairKern.put("" + (char) 75 + (char) 101, -35);
      s_pairKern.put("" + (char) 87 + (char) 111, -92);
      s_pairKern.put("" + (char) 99 + (char) 121, 0);
      s_pairKern.put("" + (char) 89 + (char) 46, -92);
      s_pairKern.put("" + (char) 89 + (char) 45, -74);
      s_pairKern.put("" + (char) 89 + (char) 44, -92);
      s_pairKern.put("" + (char) 87 + (char) 105, -55);
      s_pairKern.put("" + (char) 87 + (char) 104, 0);
      s_pairKern.put("" + (char) 87 + (char) 101, -92);
      s_pairKern.put("" + (char) 111 + (char) 121, 0);
      s_pairKern.put("" + (char) 111 + (char) 120, 0);
      s_pairKern.put("" + (char) 111 + (char) 119, 0);
      s_pairKern.put("" + (char) 101 + (char) 46, -15);
      s_pairKern.put("" + (char) 99 + (char) 108, 0);
      s_pairKern.put("" + (char) 74 + (char) 117, -35);
      s_pairKern.put("" + (char) 87 + (char) 97, -92);
      s_pairKern.put("" + (char) 111 + (char) 118, -10);
      s_pairKern.put("" + (char) 99 + (char) 107, -20);
      s_pairKern.put("" + (char) 101 + (char) 44, -10);
      s_pairKern.put("" + (char) 99 + (char) 104, -15);
      s_pairKern.put("" + (char) 74 + (char) 111, -25);
      s_pairKern.put("" + (char) 75 + (char) 79, -50);
      s_pairKern.put("" + (char) 86 + (char) 117, -74);
      s_pairKern.put("" + (char) 39 + (char) 39, -111);
      s_pairKern.put("" + (char) 111 + (char) 103, -10);
      s_pairKern.put("" + (char) 76 + (char) 39, -37);
      s_pairKern.put("" + (char) 74 + (char) 101, -25);
      s_pairKern.put("" + (char) 86 + (char) 111, -111);
      s_pairKern.put("" + (char) 98 + (char) 121, 0);
      s_pairKern.put("" + (char) 87 + (char) 79, -25);
      s_pairKern.put("" + (char) 74 + (char) 97, -35);
      s_pairKern.put("" + (char) 39 + (char) 32, -111);
      s_pairKern.put("" + (char) 98 + (char) 118, 0);
      s_pairKern.put("" + (char) 98 + (char) 117, -20);
      s_pairKern.put("" + (char) 86 + (char) 105, -74);
      s_pairKern.put("" + (char) 86 + (char) 101, -111);
      s_pairKern.put("" + (char) 110 + (char) 121, 0);
      s_pairKern.put("" + (char) 100 + (char) 46, 0);
      s_pairKern.put("" + (char) 98 + (char) 108, 0);
      s_pairKern.put("" + (char) 86 + (char) 97, -111);
      s_pairKern.put("" + (char) 110 + (char) 118, -40);
      s_pairKern.put("" + (char) 87 + (char) 65, -60);
      s_pairKern.put("" + (char) 46 + (char) 186, -140);
      s_pairKern.put("" + (char) 110 + (char) 117, 0);
      s_pairKern.put("" + (char) 100 + (char) 44, 0);
      s_pairKern.put("" + (char) 87 + (char) 59, -65);
      s_pairKern.put("" + (char) 87 + (char) 58, -65);
      s_pairKern.put("" + (char) 98 + (char) 98, 0);
      s_pairKern.put("" + (char) 86 + (char) 79, -30);
      s_pairKern.put("" + (char) 97 + (char) 121, 0);
      s_pairKern.put("" + (char) 122 + (char) 111, 0);
      s_pairKern.put("" + (char) 87 + (char) 46, -92);
      s_pairKern.put("" + (char) 97 + (char) 119, 0);
      s_pairKern.put("" + (char) 97 + (char) 118, 0);
      s_pairKern.put("" + (char) 74 + (char) 65, -40);
      s_pairKern.put("" + (char) 87 + (char) 45, -37);
      s_pairKern.put("" + (char) 87 + (char) 44, -92);
      s_pairKern.put("" + (char) 97 + (char) 116, 0);
      s_pairKern.put("" + (char) 86 + (char) 71, 0);
      s_pairKern.put("" + (char) 97 + (char) 112, 0);
      s_pairKern.put("" + (char) 122 + (char) 101, 0);
      s_pairKern.put("" + (char) 186 + (char) 32, 0);
      s_pairKern.put("" + (char) 109 + (char) 121, 0);
      s_pairKern.put("" + (char) 99 + (char) 46, 0);
      s_pairKern.put("" + (char) 86 + (char) 65, -60);
      s_pairKern.put("" + (char) 109 + (char) 117, 0);
      s_pairKern.put("" + (char) 99 + (char) 44, 0);
      s_pairKern.put("" + (char) 97 + (char) 103, -10);
      s_pairKern.put("" + (char) 84 + (char) 121, -74);
      s_pairKern.put("" + (char) 86 + (char) 59, -74);
      s_pairKern.put("" + (char) 86 + (char) 58, -65);
      s_pairKern.put("" + (char) 74 + (char) 46, -25);
      s_pairKern.put("" + (char) 84 + (char) 119, -74);
      s_pairKern.put("" + (char) 97 + (char) 98, 0);
      s_pairKern.put("" + (char) 74 + (char) 44, -25);
      s_pairKern.put("" + (char) 84 + (char) 117, -55);
      s_pairKern.put("" + (char) 84 + (char) 114, -55);
      s_pairKern.put("" + (char) 84 + (char) 111, -92);
      s_pairKern.put("" + (char) 121 + (char) 111, 0);
      s_pairKern.put("" + (char) 86 + (char) 46, -129);
      s_pairKern.put("" + (char) 86 + (char) 45, -55);
      s_pairKern.put("" + (char) 86 + (char) 44, -129);
      s_pairKern.put("" + (char) 84 + (char) 105, -55);
      s_pairKern.put("" + (char) 84 + (char) 104, 0);
      s_pairKern.put("" + (char) 84 + (char) 101, -92);
      s_pairKern.put("" + (char) 121 + (char) 101, 0);
      s_pairKern.put("" + (char) 108 + (char) 121, 0);
      s_pairKern.put("" + (char) 108 + (char) 119, 0);
      s_pairKern.put("" + (char) 98 + (char) 46, -40);
      s_pairKern.put("" + (char) 84 + (char) 97, -92);
      s_pairKern.put("" + (char) 85 + (char) 65, -40);
      s_pairKern.put("" + (char) 121 + (char) 97, 0);
      s_pairKern.put("" + (char) 44 + (char) 186, -140);
      s_pairKern.put("" + (char) 98 + (char) 44, 0);
      s_pairKern.put("" + (char) 32 + (char) 170, 0);
      s_pairKern.put("" + (char) 96 + (char) 96, -111);
      s_pairKern.put("" + (char) 170 + (char) 96, 0);
      s_pairKern.put("" + (char) 84 + (char) 79, -18);
      s_pairKern.put("" + (char) 85 + (char) 46, -25);
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

