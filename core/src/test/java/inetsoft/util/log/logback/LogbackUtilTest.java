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
package inetsoft.util.log.logback;

/*
 * Test strategy
 *
 * isFluentdEnabled() is the single point every reader of log.provider goes through, so the
 * two inputs -- the stored value and the license -- are covered exhaustively.
 *
 * The case that matters is [fluentd, non-enterprise]. The forwarder is loaded reflectively
 * from inetsoft.enterprise.log.fluentd, so on a community build the selection cannot take
 * effect: createForwardAppender throws ClassNotFoundException and LogbackInitializer falls
 * back to the file appender. Before this predicate existed, five call sites compared the raw
 * property value instead and so believed a provider that was not running -- the Logging page
 * offered no way back to the file provider, and the Logs monitoring page hid the very file
 * the records were being written to (Redmine #76045).
 *
 * Behavioral guarantees covered:
 *
 * [G1] fluentd + enterprise      -> enabled. The licensed case still works.
 * [G2] fluentd + non-enterprise  -> not enabled, which is what makes the fallback visible
 *      rather than silent.
 * [G3] file + enterprise         -> not enabled. A license alone does not turn it on.
 * [G4] unset + enterprise        -> not enabled. Absent is the file provider, not fluentd.
 * [G5] an unrelated value        -> not enabled, on either build.
 * [G6] the comparison is exact: neither case nor surrounding whitespace is normalized here,
 *      matching the bare comparisons this predicate replaced.
 */

import inetsoft.report.internal.license.LicenseManager;
import inetsoft.sree.SreeEnv;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("core")
class LogbackUtilTest {
   private MockedStatic<SreeEnv> sreeEnvStatic;
   private MockedStatic<LicenseManager> licenseManagerStatic;

   @BeforeEach
   void setUp() {
      sreeEnvStatic = mockStatic(SreeEnv.class, withSettings().lenient());
      licenseManagerStatic = mockStatic(LicenseManager.class, withSettings().lenient());
   }

   @AfterEach
   void tearDown() {
      licenseManagerStatic.close();
      sreeEnvStatic.close();
   }

   // [G1] the licensed case
   @Test
   void fluentdIsEnabledOnAnEnterpriseBuild() {
      givenProvider("fluentd");
      givenEnterprise(true);

      assertTrue(LogbackUtil.isFluentdEnabled());
   }

   // [G2] the reported defect: the setting is accepted but cannot take effect
   @Test
   void fluentdIsNotEnabledOnACommunityBuild() {
      givenProvider("fluentd");
      givenEnterprise(false);

      assertFalse(LogbackUtil.isFluentdEnabled());
   }

   // [G3] a license alone does not select forwarding
   @Test
   void fileProviderIsNotFluentdOnAnEnterpriseBuild() {
      givenProvider("file");
      givenEnterprise(true);

      assertFalse(LogbackUtil.isFluentdEnabled());
   }

   // [G4] an unset property is the file provider
   @Test
   void unsetProviderIsNotFluentd() {
      givenProvider(null);
      givenEnterprise(true);

      assertFalse(LogbackUtil.isFluentdEnabled());
   }

   // [G5] an unrecognized value is not treated as forwarding on either build
   @Test
   void unrecognizedProviderIsNotFluentd() {
      givenProvider("syslog");
      givenEnterprise(true);

      assertFalse(LogbackUtil.isFluentdEnabled());

      givenEnterprise(false);

      assertFalse(LogbackUtil.isFluentdEnabled());
   }

   // [G6] the comparison is exact, as it was before this predicate existed
   @Test
   void comparisonIsExact() {
      givenEnterprise(true);

      givenProvider("Fluentd");
      assertFalse(LogbackUtil.isFluentdEnabled());

      givenProvider(" fluentd ");
      assertFalse(LogbackUtil.isFluentdEnabled());
   }

   private void givenProvider(String provider) {
      sreeEnvStatic.when(() -> SreeEnv.getProperty("log.provider")).thenReturn(provider);
   }

   private void givenEnterprise(boolean enterprise) {
      licenseManagerStatic.when(LicenseManager::isEnterprise).thenReturn(enterprise);
   }
}
