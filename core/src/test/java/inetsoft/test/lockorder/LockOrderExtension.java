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
package inetsoft.test.lockorder;

import org.junit.jupiter.api.extension.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/**
 * Records the lock order of a whole test run (bug #77123, reliability plan task 5). Loaded by
 * JUnit's extension auto-detection only, and active only with {@code -Dlockorder.record=<label>}:
 * <pre>
 * ./mvnw -o test -pl core -Dtest='...' -Djunit.jupiter.extensions.autodetection.enabled=true -Dlockorder.record=pool-off
 * </pre>
 * The recorder is installed before the first test class and runs until the end of the run; the
 * report is written to {@code target/lockorder/<label>.txt} and its summary line printed.
 */
public final class LockOrderExtension implements BeforeAllCallback, BeforeEachCallback {
   @Override
   public void beforeAll(ExtensionContext context) {
      String label = System.getProperty(PROPERTY);

      if(label == null || label.isBlank()) {
         return;
      }

      context.getRoot().getStore(ExtensionContext.Namespace.GLOBAL)
         .getOrComputeIfAbsent(LockOrderExtension.class, key -> start(label));
      LockOrderRecorder.setCurrentTest(context.getRequiredTestClass().getSimpleName());
   }

   @Override
   public void beforeEach(ExtensionContext context) {
      if(System.getProperty(PROPERTY) != null) {
         LockOrderRecorder.setCurrentTest(context.getRequiredTestClass().getSimpleName() + "#" +
                                          context.getRequiredTestMethod().getName());
      }
   }

   private static ExtensionContext.Store.CloseableResource start(String label) {
      LockOrderRecorder recorder = LockOrderRecorder.install();
      recorder.reset();
      recorder.start();

      return () -> {
         recorder.stop();
         String report = recorder.report(label);
         Path file = Paths.get("target", "lockorder", label + ".txt");
         Files.createDirectories(file.getParent());
         Files.writeString(file, report, StandardCharsets.UTF_8);
         System.err.println(recorder.summary(label) + " report=" + file.toAbsolutePath());
      };
   }

   static final String PROPERTY = "lockorder.record";
}
