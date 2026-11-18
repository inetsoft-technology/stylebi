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
package inetsoft.report.io.excel;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bug #81284: the Excel export type was a JVM-wide static, so one export built after another
 * one was created could be written in the wrong format. ExcelConstants must hold constants only.
 */
@Tag("core")
class ExcelConstantsTest {
   @Test
   void holdsNoMutableStaticState() {
      for(Field field : ExcelConstants.class.getDeclaredFields()) {
         if(Modifier.isStatic(field.getModifiers())) {
            assertTrue(Modifier.isFinal(field.getModifiers()),
                       "ExcelConstants." + field.getName() + " must be final");
         }
      }
   }
}
