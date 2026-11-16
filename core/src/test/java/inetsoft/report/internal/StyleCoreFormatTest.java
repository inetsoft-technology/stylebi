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

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("core")
class StyleCoreFormatTest {
   @Test
   void formattedPageNumberAndCount() {
      assertEquals("Page 002 of 1,234",
                   StyleCore.format("Page {P,number,000} of {N,number,#,###}", 2, 1234,
                                    new Date(), Locale.US));
   }

   @Test
   void plainPageNumberAndCount() {
      assertEquals("Page 2 of 1234",
                   StyleCore.format("Page {P} of {N}", 2, 1234, new Date(), Locale.US));
   }
}
