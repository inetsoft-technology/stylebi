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

public class symbol extends AFontMetrics {
   static String s_fontName = "Symbol";
   static String s_fullName = "Symbol";
   static String s_familyName = "Symbol";
   static String s_weight = "Medium";
   static boolean s_fixedPitch = false;
   static double s_italicAngle = 0.0;
   static int s_ascender = 0;
   static int s_descender = 0;
   static int s_advance = 1042;
   static Rectangle s_bbox = new Rectangle(-180, 1010, 1270, 1303);
   static int[] s_widths = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
      0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 250, 333, 713, 500, 549,
      833, 778, 439, 333, 333, 500, 549, 250, 549, 250, 278, 500, 500, 500,
      500, 500, 500, 500, 500, 500, 500, 278, 278, 549, 549, 549, 444, 549,
      722, 667, 722, 612, 611, 763, 603, 722, 333, 631, 722, 686, 889, 722,
      722, 768, 741, 556, 592, 611, 690, 439, 768, 645, 795, 611, 333, 863,
      333, 658, 500, 500, 631, 549, 549, 494, 439, 521, 411, 603, 329, 603,
      549, 549, 576, 521, 549, 549, 521, 549, 603, 439, 576, 713, 686, 493,
      686, 494, 480, 200, 480, 549, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
      0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 620, 247,
      549, 167, 713, 500, 753, 753, 753, 753, 1042, 987, 603, 987, 603, 400,
      549, 411, 549, 549, 713, 494, 460, 549, 549, 549, 549, 1000, 603, 1000,
      658, 823, 686, 795, 987, 768, 768, 823, 768, 768, 713, 713, 713, 713,
      713, 713, 713, 768, 713, 790, 790, 890, 823, 549, 250, 713, 603, 603,
      1042, 987, 603, 987, 603, 494, 329, 790, 790, 786, 713, 384, 384, 384,
      384, 384, 384, 494, 494, 494, 494, 0, 329, 274, 686, 686, 686, 384, 384,
      384, 384, 384, 384, 494, 494, 494, 0};
   static HashMap s_pairKern = new HashMap();
   static {
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

