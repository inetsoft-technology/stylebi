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
package inetsoft.test;

import org.junit.jupiter.params.provider.Arguments;

import java.nio.file.*;
import java.util.List;
import java.util.stream.Stream;

/**
 * Loads a seed corpus: a resource directory next to a test class whose files are replayed
 * one per parameterized test case. The enterprise fuzzer (test/fuzzer) starts from the
 * same directories.
 */
public final class SeedCorpus {
   private SeedCorpus() {
   }

   /**
    * @param testClass the class whose package contains the directory.
    * @param dir       the directory name, relative to that package.
    *
    * @return one {@code (file name, file bytes)} argument pair per file, sorted by name.
    */
   public static Stream<Arguments> load(Class<?> testClass, String dir) throws Exception {
      Path path = Path.of(testClass.getResource(dir).toURI());
      List<Path> files;

      try(Stream<Path> list = Files.list(path)) {
         files = list.filter(Files::isRegularFile).sorted().toList();
      }

      return files.stream().map(p -> Arguments.of(p.getFileName().toString(), read(p)));
   }

   private static byte[] read(Path file) {
      try {
         return Files.readAllBytes(file);
      }
      catch(Exception ex) {
         throw new IllegalStateException("Failed to read seed " + file, ex);
      }
   }
}
