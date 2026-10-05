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
package inetsoft.web.admin.general;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The backup timestamp is a Gregorian yyyyMMddHHmmss stamp with ASCII digits whatever the
 * default locale (Bug #77605).
 */
@Tag("core")
class BackupSupportTest {
   @ParameterizedTest
   @ValueSource(strings = { "en-US", "th-TH", "th-TH-u-nu-thai", "ja-JP-u-ca-japanese",
                            "ja-JP-x-lvariant-JP" })
   void timestampIsGregorian(String tag) {
      Locale defaultLocale = Locale.getDefault();
      Locale formatLocale = Locale.getDefault(Locale.Category.FORMAT);

      try {
         Locale.setDefault(Locale.forLanguageTag(tag));
         String stamp = BackupSupport.createBackupTimestamp();
         int year = LocalDate.now().getYear();

         assertTrue(stamp.matches("\\d{14}"), stamp);
         int stampYear = Integer.parseInt(stamp.substring(0, 4));
         // the year may roll over between the two calls
         assertTrue(stampYear == year || stampYear == year + 1, stamp);
      }
      finally {
         Locale.setDefault(defaultLocale);
         Locale.setDefault(Locale.Category.FORMAT, formatLocale);
      }
   }
}
