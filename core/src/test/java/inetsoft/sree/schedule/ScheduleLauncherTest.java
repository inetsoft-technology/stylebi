/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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
package inetsoft.sree.schedule;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Tag("core")
class ScheduleLauncherTest {
   /**
    * Bug #79659: a missing ScheduleLauncher.txt resource must produce an error that names the
    * resource instead of a message-less NullPointerException.
    */
   @Test
   void missingClasspathResourceNamesTheResource() throws Exception {
      assumeTrue(ScheduleLauncher.class.getResource("ScheduleLauncher.txt") == null,
                 "ScheduleLauncher.txt is present on the test classpath");

      Method method = ScheduleLauncher.class.getDeclaredMethod("createClassLoader");
      method.setAccessible(true);

      InvocationTargetException ex =
         assertThrows(InvocationTargetException.class, () -> method.invoke(null));
      Throwable cause = ex.getCause();

      assertInstanceOf(IOException.class, cause, "unexpected: " + cause);
      assertNotNull(cause.getMessage());
      assertTrue(cause.getMessage().contains("ScheduleLauncher.txt"), cause.getMessage());
   }
}
