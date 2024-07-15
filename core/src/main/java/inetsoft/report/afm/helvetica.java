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

public class helvetica extends AFontMetrics {
   static String s_fontName = "Helvetica";
   static String s_fullName = "Helvetica";
   static String s_familyName = "Helvetica";
   static String s_weight = "Medium";
   static boolean s_fixedPitch = false;
   static double s_italicAngle = 0.0;
   static int s_ascender = 718;
   static int s_descender = 207;
   static int s_advance = 1015;
   static Rectangle s_bbox = new Rectangle(-166, 931, 1166, 1156);
   static int[] s_widths = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
      0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 278, 278, 355, 556, 556,
      889, 667, 222, 333, 333, 389, 584, 278, 333, 278, 278, 556, 556, 556,
      556, 556, 556, 556, 556, 556, 556, 278, 278, 584, 584, 584, 556, 1015,
      667, 667, 722, 722, 667, 611, 778, 722, 278, 500, 667, 556, 833, 722,
      778, 667, 778, 722, 667, 611, 722, 667, 944, 667, 667, 611, 278, 278,
      278, 469, 556, 222, 556, 556, 500, 556, 556, 278, 556, 556, 222, 222,
      500, 222, 833, 556, 556, 556, 556, 333, 500, 278, 556, 500, 722, 500,
      500, 500, 334, 260, 334, 584, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
      0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 333, 556,
      556, 167, 556, 556, 556, 556, 191, 333, 556, 333, 333, 500, 500, 0, 556,
      556, 556, 278, 0, 537, 350, 222, 333, 333, 556, 1000, 1000, 0, 611, 0,
      333, 333, 333, 333, 333, 333, 333, 333, 0, 333, 333, 0, 333, 333, 333,
      1000, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1000, 0, 370, 0, 0,
      0, 0, 556, 778, 1000, 365, 0, 0, 0, 0, 0, 889, 0, 0, 0, 278, 0, 0, 222,
      611, 944, 611, 0, 0, 0, 0};
   static HashMap<String, Integer> s_pairKern = new HashMap<>();
   static {
      s_pairKern.put("" + (char) 85 + (char) 44, -40);
      s_pairKern.put("" + (char) 120 + (char) 101, -30);
      s_pairKern.put("" + (char) 84 + (char) 65, -120);
      s_pairKern.put("" + (char) 70 + (char) 114, -45);
      s_pairKern.put("" + (char) 70 + (char) 111, -30);
      s_pairKern.put("" + (char) 84 + (char) 59, -20);
      s_pairKern.put("" + (char) 107 + (char) 111, -20);
      s_pairKern.put("" + (char) 84 + (char) 58, -20);
      s_pairKern.put("" + (char) 70 + (char) 101, -30);
      s_pairKern.put("" + (char) 107 + (char) 101, -20);
      s_pairKern.put("" + (char) 119 + (char) 111, -10);
      s_pairKern.put("" + (char) 70 + (char) 97, -50);
      s_pairKern.put("" + (char) 84 + (char) 46, -120);
      s_pairKern.put("" + (char) 84 + (char) 45, -140);
      s_pairKern.put("" + (char) 121 + (char) 46, -100);
      s_pairKern.put("" + (char) 84 + (char) 44, -120);
      s_pairKern.put("" + (char) 121 + (char) 44, -100);
      s_pairKern.put("" + (char) 119 + (char) 101, -10);
      s_pairKern.put("" + (char) 102 + (char) 245, -28);
      s_pairKern.put("" + (char) 119 + (char) 97, -15);
      s_pairKern.put("" + (char) 82 + (char) 89, -50);
      s_pairKern.put("" + (char) 82 + (char) 87, -30);
      s_pairKern.put("" + (char) 82 + (char) 86, -50);
      s_pairKern.put("" + (char) 59 + (char) 32, -50);
      s_pairKern.put("" + (char) 82 + (char) 85, -40);
      s_pairKern.put("" + (char) 82 + (char) 84, -30);
      s_pairKern.put("" + (char) 82 + (char) 79, -20);
      s_pairKern.put("" + (char) 118 + (char) 111, -25);
      s_pairKern.put("" + (char) 32 + (char) 96, -60);
      s_pairKern.put("" + (char) 83 + (char) 46, -20);
      s_pairKern.put("" + (char) 70 + (char) 65, -80);
      s_pairKern.put("" + (char) 83 + (char) 44, -20);
      s_pairKern.put("" + (char) 46 + (char) 39, -100);
      s_pairKern.put("" + (char) 32 + (char) 89, -90);
      s_pairKern.put("" + (char) 32 + (char) 87, -40);
      s_pairKern.put("" + (char) 118 + (char) 101, -25);
      s_pairKern.put("" + (char) 32 + (char) 86, -50);
      s_pairKern.put("" + (char) 32 + (char) 84, -50);
      s_pairKern.put("" + (char) 46 + (char) 32, -60);
      s_pairKern.put("" + (char) 118 + (char) 97, -25);
      s_pairKern.put("" + (char) 70 + (char) 46, -150);
      s_pairKern.put("" + (char) 70 + (char) 44, -150);
      s_pairKern.put("" + (char) 58 + (char) 32, -50);
      s_pairKern.put("" + (char) 81 + (char) 85, -10);
      s_pairKern.put("" + (char) 80 + (char) 111, -50);
      s_pairKern.put("" + (char) 119 + (char) 46, -60);
      s_pairKern.put("" + (char) 119 + (char) 44, -60);
      s_pairKern.put("" + (char) 102 + (char) 186, 60);
      s_pairKern.put("" + (char) 80 + (char) 101, -50);
      s_pairKern.put("" + (char) 68 + (char) 89, -90);
      s_pairKern.put("" + (char) 104 + (char) 121, -30);
      s_pairKern.put("" + (char) 68 + (char) 87, -40);
      s_pairKern.put("" + (char) 68 + (char) 86, -70);
      s_pairKern.put("" + (char) 80 + (char) 97, -40);
      s_pairKern.put("" + (char) 68 + (char) 65, -40);
      s_pairKern.put("" + (char) 118 + (char) 46, -80);
      s_pairKern.put("" + (char) 118 + (char) 44, -80);
      s_pairKern.put("" + (char) 44 + (char) 39, -100);
      s_pairKern.put("" + (char) 80 + (char) 65, -120);
      s_pairKern.put("" + (char) 76 + (char) 186, -140);
      s_pairKern.put("" + (char) 103 + (char) 114, -10);
      s_pairKern.put("" + (char) 79 + (char) 89, -70);
      s_pairKern.put("" + (char) 68 + (char) 46, -70);
      s_pairKern.put("" + (char) 79 + (char) 88, -60);
      s_pairKern.put("" + (char) 79 + (char) 87, -30);
      s_pairKern.put("" + (char) 115 + (char) 119, -30);
      s_pairKern.put("" + (char) 68 + (char) 44, -70);
      s_pairKern.put("" + (char) 79 + (char) 86, -50);
      s_pairKern.put("" + (char) 79 + (char) 84, -40);
      s_pairKern.put("" + (char) 80 + (char) 46, -180);
      s_pairKern.put("" + (char) 80 + (char) 44, -180);
      s_pairKern.put("" + (char) 65 + (char) 121, -40);
      s_pairKern.put("" + (char) 65 + (char) 119, -40);
      s_pairKern.put("" + (char) 65 + (char) 118, -40);
      s_pairKern.put("" + (char) 65 + (char) 117, -30);
      s_pairKern.put("" + (char) 66 + (char) 85, -10);
      s_pairKern.put("" + (char) 79 + (char) 65, -20);
      s_pairKern.put("" + (char) 102 + (char) 111, -30);
      s_pairKern.put("" + (char) 114 + (char) 121, 30);
      s_pairKern.put("" + (char) 67 + (char) 46, -30);
      s_pairKern.put("" + (char) 67 + (char) 44, -30);
      s_pairKern.put("" + (char) 114 + (char) 118, 30);
      s_pairKern.put("" + (char) 114 + (char) 117, 15);
      s_pairKern.put("" + (char) 114 + (char) 116, 40);
      s_pairKern.put("" + (char) 114 + (char) 112, 30);
      s_pairKern.put("" + (char) 102 + (char) 101, -30);
      s_pairKern.put("" + (char) 114 + (char) 110, 25);
      s_pairKern.put("" + (char) 79 + (char) 46, -40);
      s_pairKern.put("" + (char) 114 + (char) 109, 25);
      s_pairKern.put("" + (char) 114 + (char) 108, 15);
      s_pairKern.put("" + (char) 102 + (char) 97, -30);
      s_pairKern.put("" + (char) 79 + (char) 44, -40);
      s_pairKern.put("" + (char) 89 + (char) 117, -110);
      s_pairKern.put("" + (char) 114 + (char) 107, 15);
      s_pairKern.put("" + (char) 114 + (char) 105, 15);
      s_pairKern.put("" + (char) 65 + (char) 89, -100);
      s_pairKern.put("" + (char) 89 + (char) 111, -140);
      s_pairKern.put("" + (char) 101 + (char) 121, -20);
      s_pairKern.put("" + (char) 65 + (char) 87, -50);
      s_pairKern.put("" + (char) 101 + (char) 120, -30);
      s_pairKern.put("" + (char) 65 + (char) 86, -70);
      s_pairKern.put("" + (char) 101 + (char) 119, -20);
      s_pairKern.put("" + (char) 101 + (char) 118, -30);
      s_pairKern.put("" + (char) 65 + (char) 85, -50);
      s_pairKern.put("" + (char) 114 + (char) 97, -10);
      s_pairKern.put("" + (char) 65 + (char) 84, -120);
      s_pairKern.put("" + (char) 89 + (char) 105, -20);
      s_pairKern.put("" + (char) 249 + (char) 122, -55);
      s_pairKern.put("" + (char) 65 + (char) 81, -30);
      s_pairKern.put("" + (char) 249 + (char) 121, -70);
      s_pairKern.put("" + (char) 249 + (char) 120, -85);
      s_pairKern.put("" + (char) 65 + (char) 79, -30);
      s_pairKern.put("" + (char) 76 + (char) 121, -30);
      s_pairKern.put("" + (char) 89 + (char) 101, -140);
      s_pairKern.put("" + (char) 249 + (char) 119, -70);
      s_pairKern.put("" + (char) 249 + (char) 118, -70);
      s_pairKern.put("" + (char) 66 + (char) 46, -20);
      s_pairKern.put("" + (char) 249 + (char) 117, -55);
      s_pairKern.put("" + (char) 39 + (char) 115, -50);
      s_pairKern.put("" + (char) 249 + (char) 116, -55);
      s_pairKern.put("" + (char) 66 + (char) 44, -20);
      s_pairKern.put("" + (char) 89 + (char) 97, -140);
      s_pairKern.put("" + (char) 39 + (char) 114, -50);
      s_pairKern.put("" + (char) 249 + (char) 115, -55);
      s_pairKern.put("" + (char) 249 + (char) 114, -55);
      s_pairKern.put("" + (char) 249 + (char) 113, -55);
      s_pairKern.put("" + (char) 249 + (char) 112, -55);
      s_pairKern.put("" + (char) 65 + (char) 71, -30);
      s_pairKern.put("" + (char) 249 + (char) 111, -55);
      s_pairKern.put("" + (char) 249 + (char) 110, -55);
      s_pairKern.put("" + (char) 249 + (char) 109, -55);
      s_pairKern.put("" + (char) 249 + (char) 108, -55);
      s_pairKern.put("" + (char) 65 + (char) 67, -30);
      s_pairKern.put("" + (char) 249 + (char) 107, -55);
      s_pairKern.put("" + (char) 249 + (char) 106, -55);
      s_pairKern.put("" + (char) 115 + (char) 46, -15);
      s_pairKern.put("" + (char) 249 + (char) 105, -55);
      s_pairKern.put("" + (char) 249 + (char) 104, -55);
      s_pairKern.put("" + (char) 115 + (char) 44, -15);
      s_pairKern.put("" + (char) 249 + (char) 103, -55);
      s_pairKern.put("" + (char) 249 + (char) 102, -55);
      s_pairKern.put("" + (char) 39 + (char) 100, -50);
      s_pairKern.put("" + (char) 249 + (char) 101, -55);
      s_pairKern.put("" + (char) 249 + (char) 100, -55);
      s_pairKern.put("" + (char) 249 + (char) 99, -55);
      s_pairKern.put("" + (char) 249 + (char) 98, -55);
      s_pairKern.put("" + (char) 89 + (char) 79, -85);
      s_pairKern.put("" + (char) 249 + (char) 97, -55);
      s_pairKern.put("" + (char) 75 + (char) 121, -50);
      s_pairKern.put("" + (char) 76 + (char) 89, -140);
      s_pairKern.put("" + (char) 114 + (char) 59, 30);
      s_pairKern.put("" + (char) 112 + (char) 121, -30);
      s_pairKern.put("" + (char) 114 + (char) 58, 30);
      s_pairKern.put("" + (char) 76 + (char) 87, -70);
      s_pairKern.put("" + (char) 102 + (char) 46, -30);
      s_pairKern.put("" + (char) 76 + (char) 86, -110);
      s_pairKern.put("" + (char) 75 + (char) 117, -30);
      s_pairKern.put("" + (char) 89 + (char) 65, -110);
      s_pairKern.put("" + (char) 102 + (char) 44, -30);
      s_pairKern.put("" + (char) 76 + (char) 84, -110);
      s_pairKern.put("" + (char) 75 + (char) 111, -40);
      s_pairKern.put("" + (char) 102 + (char) 39, 50);
      s_pairKern.put("" + (char) 87 + (char) 121, -20);
      s_pairKern.put("" + (char) 89 + (char) 59, -60);
      s_pairKern.put("" + (char) 89 + (char) 58, -60);
      s_pairKern.put("" + (char) 114 + (char) 46, -50);
      s_pairKern.put("" + (char) 87 + (char) 117, -30);
      s_pairKern.put("" + (char) 114 + (char) 44, -50);
      s_pairKern.put("" + (char) 75 + (char) 101, -40);
      s_pairKern.put("" + (char) 87 + (char) 111, -30);
      s_pairKern.put("" + (char) 89 + (char) 46, -140);
      s_pairKern.put("" + (char) 89 + (char) 45, -140);
      s_pairKern.put("" + (char) 89 + (char) 44, -140);
      s_pairKern.put("" + (char) 87 + (char) 101, -30);
      s_pairKern.put("" + (char) 111 + (char) 121, -30);
      s_pairKern.put("" + (char) 111 + (char) 120, -30);
      s_pairKern.put("" + (char) 111 + (char) 119, -15);
      s_pairKern.put("" + (char) 74 + (char) 117, -20);
      s_pairKern.put("" + (char) 87 + (char) 97, -40);
      s_pairKern.put("" + (char) 101 + (char) 46, -15);
      s_pairKern.put("" + (char) 111 + (char) 118, -15);
      s_pairKern.put("" + (char) 99 + (char) 107, -20);
      s_pairKern.put("" + (char) 101 + (char) 44, -15);
      s_pairKern.put("" + (char) 75 + (char) 79, -50);
      s_pairKern.put("" + (char) 249 + (char) 46, -95);
      s_pairKern.put("" + (char) 249 + (char) 44, -95);
      s_pairKern.put("" + (char) 86 + (char) 117, -70);
      s_pairKern.put("" + (char) 39 + (char) 39, -57);
      s_pairKern.put("" + (char) 76 + (char) 39, -160);
      s_pairKern.put("" + (char) 86 + (char) 111, -80);
      s_pairKern.put("" + (char) 87 + (char) 79, -20);
      s_pairKern.put("" + (char) 98 + (char) 121, -20);
      s_pairKern.put("" + (char) 74 + (char) 97, -20);
      s_pairKern.put("" + (char) 39 + (char) 32, -70);
      s_pairKern.put("" + (char) 98 + (char) 118, -20);
      s_pairKern.put("" + (char) 98 + (char) 117, -20);
      s_pairKern.put("" + (char) 86 + (char) 101, -80);
      s_pairKern.put("" + (char) 110 + (char) 121, -15);
      s_pairKern.put("" + (char) 86 + (char) 97, -70);
      s_pairKern.put("" + (char) 98 + (char) 108, -20);
      s_pairKern.put("" + (char) 110 + (char) 118, -20);
      s_pairKern.put("" + (char) 87 + (char) 65, -50);
      s_pairKern.put("" + (char) 46 + (char) 186, -100);
      s_pairKern.put("" + (char) 110 + (char) 117, -10);
      s_pairKern.put("" + (char) 98 + (char) 98, -10);
      s_pairKern.put("" + (char) 112 + (char) 46, -35);
      s_pairKern.put("" + (char) 112 + (char) 44, -35);
      s_pairKern.put("" + (char) 86 + (char) 79, -40);
      s_pairKern.put("" + (char) 97 + (char) 121, -30);
      s_pairKern.put("" + (char) 122 + (char) 111, -15);
      s_pairKern.put("" + (char) 87 + (char) 46, -80);
      s_pairKern.put("" + (char) 97 + (char) 119, -20);
      s_pairKern.put("" + (char) 74 + (char) 65, -20);
      s_pairKern.put("" + (char) 87 + (char) 45, -40);
      s_pairKern.put("" + (char) 97 + (char) 118, -20);
      s_pairKern.put("" + (char) 87 + (char) 44, -80);
      s_pairKern.put("" + (char) 86 + (char) 71, -40);
      s_pairKern.put("" + (char) 122 + (char) 101, -15);
      s_pairKern.put("" + (char) 186 + (char) 32, -40);
      s_pairKern.put("" + (char) 109 + (char) 121, -15);
      s_pairKern.put("" + (char) 86 + (char) 65, -80);
      s_pairKern.put("" + (char) 109 + (char) 117, -10);
      s_pairKern.put("" + (char) 99 + (char) 44, -15);
      s_pairKern.put("" + (char) 84 + (char) 121, -120);
      s_pairKern.put("" + (char) 86 + (char) 59, -40);
      s_pairKern.put("" + (char) 86 + (char) 58, -40);
      s_pairKern.put("" + (char) 74 + (char) 46, -30);
      s_pairKern.put("" + (char) 84 + (char) 119, -120);
      s_pairKern.put("" + (char) 111 + (char) 46, -40);
      s_pairKern.put("" + (char) 74 + (char) 44, -30);
      s_pairKern.put("" + (char) 84 + (char) 117, -120);
      s_pairKern.put("" + (char) 111 + (char) 44, -40);
      s_pairKern.put("" + (char) 84 + (char) 114, -120);
      s_pairKern.put("" + (char) 84 + (char) 111, -120);
      s_pairKern.put("" + (char) 121 + (char) 111, -20);
      s_pairKern.put("" + (char) 86 + (char) 46, -125);
      s_pairKern.put("" + (char) 86 + (char) 45, -80);
      s_pairKern.put("" + (char) 86 + (char) 44, -125);
      s_pairKern.put("" + (char) 84 + (char) 101, -120);
      s_pairKern.put("" + (char) 121 + (char) 101, -20);
      s_pairKern.put("" + (char) 84 + (char) 97, -120);
      s_pairKern.put("" + (char) 98 + (char) 46, -40);
      s_pairKern.put("" + (char) 85 + (char) 65, -40);
      s_pairKern.put("" + (char) 121 + (char) 97, -20);
      s_pairKern.put("" + (char) 44 + (char) 186, -100);
      s_pairKern.put("" + (char) 98 + (char) 44, -40);
      s_pairKern.put("" + (char) 32 + (char) 170, -30);
      s_pairKern.put("" + (char) 96 + (char) 96, -57);
      s_pairKern.put("" + (char) 84 + (char) 79, -40);
      s_pairKern.put("" + (char) 85 + (char) 46, -40);
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

