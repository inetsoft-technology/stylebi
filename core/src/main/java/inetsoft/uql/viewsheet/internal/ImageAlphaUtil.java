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
package inetsoft.uql.viewsheet.internal;

/**
 * Shared parse/clamp logic for the image alpha setters on
 * {@link ImageVSAssemblyInfo} and {@link GroupContainerVSAssemblyInfo}, so a
 * design-time or runtime alpha value can never reach export as raw
 * unclamped/unparseable text (Bug #76791).
 */
final class ImageAlphaUtil {
   private ImageAlphaUtil() {
   }

   /**
    * Fully opaque -- used both as the ceiling of the valid alpha range and as
    * the fallback for a non-numeric value, so a bad value degrades to
    * visible rather than crashing export.
    */
   static final int DEFAULT_ALPHA = 100;

   /**
    * Parses an alpha percentage string, clamping it to [0, 100] and falling
    * back to {@link #DEFAULT_ALPHA} when the value isn't numeric at all.
    */
   static String normalizeAlpha(String alpha) {
      double value;

      try {
         value = Double.parseDouble(alpha);
      }
      catch(Exception e) {
         value = DEFAULT_ALPHA;
      }

      value = value < 0 ? 0 : value > 100 ? 100 : value;
      return (int) value + "";
   }
}
