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
package inetsoft.sree;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77694: the JVM-wide settings in {@code PropertiesEngine.EXCLUDED_ORG_PROPERTIES} are
 * always read globally, so an {@code inetsoft.org.<org>.<name>} override of one has no effect.
 * {@link PropertiesEngine#isExcludedOrgProperty(String)} identifies such an override so that the
 * property write endpoints can refuse it. It is a pure name check and needs no Spring context.
 */
@Tag("core")
class PropertiesEngineExcludedOrgPropertyTest {
   // every excluded key, written as an organization override
   @ParameterizedTest
   @ValueSource(strings = {
      "inetsoft.org.orga.security.enabled",
      "inetsoft.org.orga.sree.security.listeners",
      "inetsoft.org.orga.security.cache",
      "inetsoft.org.orga.security.cache.interval",
      "inetsoft.org.orga.inetsoft.sree.security.checkpermissionstrategy",
      "inetsoft.org.orga.swapper.critical.max.wait",
      "inetsoft.org.orga.swapper.gc.min.interval",
      "inetsoft.org.orga.swapper.idle.gc.interval",
      "inetsoft.org.orga.swapper.count",
      "inetsoft.org.orga.swapper.free.ratio",
      "inetsoft.org.orga.swappable.alive.period",
      "inetsoft.org.orga.swapper.scalingmetric.excludeeden",
      "inetsoft.org.orga.ignore.swapper.memory.state",
      "inetsoft.org.orga.replet.cache.directory",
      "inetsoft.org.orga.sree.home",
      "inetsoft.org.orga.server.type"
   })
   void orgOverrideOfExcludedKeyIsMatched(String name) {
      assertTrue(PropertiesEngine.isExcludedOrgProperty(name), name);
   }

   // names are stored case-normalized, so a mixed-case spelling is the same stored key
   @ParameterizedTest
   @ValueSource(strings = {
      "inetsoft.org.OrgA.SREE.HOME",
      "inetsoft.org.orga.Replet.Cache.Directory",
      "INETSOFT.ORG.ORGA.SERVER.TYPE",
      "inetsoft.org.host-org.Security.Enabled"
   })
   void mixedCaseOrgOverrideIsMatched(String name) {
      assertTrue(PropertiesEngine.isExcludedOrgProperty(name), name);
   }

   // an org that predates the ID rules may keep an ID with a dot in it
   @Test
   void dottedOrgIdIsMatched() {
      assertTrue(PropertiesEngine.isExcludedOrgProperty("inetsoft.org.a.b.sree.home"));
      assertTrue(PropertiesEngine.isExcludedOrgProperty("inetsoft.org.Legacy.Org.swapper.count"));
   }

   // the global keys themselves stay writable
   @ParameterizedTest
   @ValueSource(strings = {
      "sree.home", "SREE.HOME", "replet.cache.directory", "server.type", "security.enabled",
      "swapper.count"
   })
   void globalExcludedKeyIsNotMatched(String name) {
      assertFalse(PropertiesEngine.isExcludedOrgProperty(name), name);
   }

   // organization overrides of other keys are honored by reads, so they are not matched
   @ParameterizedTest
   @ValueSource(strings = {
      "inetsoft.org.orga.max.row.count",
      "inetsoft.org.orga.mail.smtp.host",
      // a key that merely shares a tail with an excluded name must be dot bounded to match
      "inetsoft.org.orga.mysree.home",
      "inetsoft.org.orga.xserver.type",
      // no org segment before the excluded name
      "inetsoft.org.sree.home",
      "inetsoft.org..sree.home",
      "inetsoft.org.",
      "inetsoft.org"
   })
   void otherNamesAreNotMatched(String name) {
      assertFalse(PropertiesEngine.isExcludedOrgProperty(name), name);
   }

   @Test
   void nullIsNotMatched() {
      assertFalse(PropertiesEngine.isExcludedOrgProperty(null));
   }
}
