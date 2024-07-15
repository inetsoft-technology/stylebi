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
package inetsoft.uql.jdbc.drivers;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class DriverServiceGeneratorTests {
   private final DriverServiceGenerator generator = new DriverServiceGenerator();

   @Test
   void shouldThrowExceptionWhenRootClass() {
      assertThrowsExactly(
         IllegalArgumentException.class,
         () -> generator.generateDriverServiceClass("MyDriverService"));
   }

   @Test
   void shouldThrowExceptionWhenInvalidName() {
      assertThrowsExactly(
         IllegalArgumentException.class,
         () -> generator.generateDriverServiceClass("test."));
   }

   @Test
   void shouldGenerateDriverClass() throws Exception {
      String className = "com.inetsoft.test.TestDriverService";
      byte[] classBytes = generator.generateDriverServiceClass(className);
      assertNotNull(classBytes);
      assertThat(classBytes.length, Matchers.greaterThan(0));

      Class<?> driverClass = new TestClassLoader(
         className, classBytes, getClass().getClassLoader()).loadClass(className);
      assertNotNull(driverClass);
      assertEquals(driverClass.getSuperclass(), AutoDriverService.class);

      Method[] methods = driverClass.getDeclaredMethods();
      assertEquals(0, methods.length);

      Field[] fields = driverClass.getDeclaredFields();
      assertEquals(0, fields.length);
   }

   private static final class TestClassLoader extends ClassLoader {
      private TestClassLoader(String className, byte[] classBytes, ClassLoader parent) {
         super(parent);
         this.className = className;
         this.classBytes = classBytes;
      }

      @Override
      protected Class<?> findClass(String name) throws ClassNotFoundException {
         if(className.equals(name)) {
            return defineClass(name, classBytes, 0, classBytes.length);
         }

         return super.findClass(name);
      }

      private final String className;
      private final byte[] classBytes;
   }
}