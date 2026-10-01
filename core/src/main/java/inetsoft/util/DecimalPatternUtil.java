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
package inetsoft.util;

/**
 * Decimal format pattern helpers. This class has no dependencies, so it can be used by any
 * format class without triggering other static initialization.
 */
public final class DecimalPatternUtil {
   /**
    * The pattern used in place of an empty decimal pattern.
    */
   public static final String DEFAULT_PATTERN = "#,##0.###";

   private DecimalPatternUtil() {
   }

   /**
    * Map an empty decimal pattern to the default pattern. DecimalFormat given exactly "" has
    * Integer.MAX_VALUE maximum fraction digits, and toPattern() would then build a string of
    * about 2^31 chars. Any other pattern, including null, is returned unchanged.
    */
   public static String normalizeEmptyPattern(String pattern) {
      return pattern != null && pattern.isEmpty() ? DEFAULT_PATTERN : pattern;
   }
}
