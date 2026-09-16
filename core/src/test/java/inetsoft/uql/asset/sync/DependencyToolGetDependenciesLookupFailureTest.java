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
package inetsoft.uql.asset.sync;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetObject;
import inetsoft.uql.asset.AssetRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression for Redmine #76728: a viewsheet/data-source force-delete's post-apply "advisory"
 * field is derived directly from {@link DependencyTool#getDependencies}'s return value, with no
 * way for the caller to tell "genuinely no dependents" apart from "the lookup itself silently
 * failed". {@code getDependencies}'s {@code catch(Exception ignored)} used to swallow every
 * exception from the underlying {@link DependencyStorageService#getWithOrg} call with no logging
 * at all -- unlike {@code DependencyStorageService.remove()}/{@code rename()} in the same area,
 * which do log their failures. This pins the logging half of that fix: the return contract (an
 * empty list) is intentionally unchanged, since every other caller of {@code getDependencies}
 * (schedule-task dependents, etc.) relies on it, but a swallowed exception must now be logged
 * naming the entry id so the failure is diagnosable instead of silently reading as "no
 * dependents".
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class,
               DependencyToolGetDependenciesLookupFailureTest.DependencyStorageConfig.class },
   initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DependencyToolGetDependenciesLookupFailureTest {
   @Configuration
   static class DependencyStorageConfig {
      // DependencyStorageService is a @Service picked up by component scan in production, so it
      // is not in this test's context (see CheckSheetRemoveableStaleStoreTest's identical note).
      @Bean
      public DependencyStorageService dependencyStorageService() throws Exception {
         DependencyStorageService service = mock(DependencyStorageService.class);
         when(service.getWithOrg(anyString(), anyString()))
            .thenThrow(new IOException("dependency storage not yet loaded"));
         return service;
      }
   }

   @Test
   void lookupThrows_logsWarningNamingTheEntry_andStillReturnsEmptyList() {
      String entryId = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
         "Examples/HurricaneL", null, "host-org").toIdentifier(true);

      ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger)
         org.slf4j.LoggerFactory.getLogger(DependencyTool.class);
      ListAppender<ILoggingEvent> appender = new ListAppender<>();
      appender.start();
      logger.addAppender(appender);

      try {
         List<AssetObject> dependencies = DependencyTool.getDependencies(entryId);

         assertTrue(dependencies.isEmpty(),
            "a failed lookup must still return an empty list -- the return contract every other "
            + "caller of getDependencies relies on (schedule-task dependents, etc.) must not "
            + "change");

         List<ILoggingEvent> warnings = appender.list.stream()
            .filter(e -> e.getLevel() == Level.WARN)
            .filter(e -> e.getFormattedMessage().contains(entryId))
            .toList();

         assertEquals(1, warnings.size(),
            "bug 76728: a swallowed dependency-lookup exception must be logged naming the entry "
            + "id -- silently degrading to an empty list is what let a real force-delete's "
            + "advisory read as null the same as a genuinely dependency-free delete");
      }
      finally {
         logger.detachAppender(appender);
      }
   }
}
