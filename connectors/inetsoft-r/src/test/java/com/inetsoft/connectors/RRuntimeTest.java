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
package com.inetsoft.connectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;

/**
 * Unit test cases for <tt>RRuntime</tt>.
 */
class RRuntimeTest {
   /**
    * Sets the sree.home property and creates the corresponding directory, if necessary.
    */
   @BeforeAll
   static void setSreeHome() {
      File home = new File("target/sreeHome");

      if(!home.isDirectory() && !home.mkdirs()) {
         throw new RuntimeException("Failed to create test sree.home: " + home);
      }

      System.setProperty("sree.home", home.getAbsolutePath());
   }

   /**
    * Tests the <tt>runQuery()</tt> method for proper operation.
    *
    * @throws AssertionError if the test fails.
    * @throws Exception if an unexepcted error occurs.
    */
   @Test
   void testRunQuery() throws Exception {
   }

   /**
    * Tests the <tt>testDataSource()</tt> method for proper operation.
    *
    * @throws AssertionError if the test fails.
    * @throws Exception if an unexepcted error occurs.
    */
   @Test
   void testTestDataSource() throws Exception {
   }
}
