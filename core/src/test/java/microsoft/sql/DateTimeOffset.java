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
package microsoft.sql;

import java.time.OffsetDateTime;

/**
 * Test stand-in for the SQL Server driver's DateTimeOffset. CoreTool matches the driver class by
 * name and calls getOffsetDateTime() reflectively, so the real driver is not needed on the test
 * classpath.
 */
public class DateTimeOffset {
   public DateTimeOffset(OffsetDateTime value) {
      this.value = value;
   }

   public OffsetDateTime getOffsetDateTime() {
      return value;
   }

   private final OffsetDateTime value;
}
