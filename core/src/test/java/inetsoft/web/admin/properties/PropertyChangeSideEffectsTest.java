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
package inetsoft.web.admin.properties;

import inetsoft.report.internal.table.TableFormat;
import inetsoft.sree.PropertiesEngine;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.*;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.util.ConfigurationContext;
import inetsoft.util.Tool;
import inetsoft.util.log.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("core")
@ExtendWith(MockitoExtension.class)
class PropertyChangeSideEffectsTest {

   /**
    * TableFormat's static initializer registers property-change listeners via
    * {@code PropertiesEngine.getInstance()}, which resolves a Spring bean through
    * {@code ConfigurationContext}. Outside of a running Spring context (as in this plain
    * Mockito unit test) that throws {@code ShutdownException} and poisons the class forever
    * with {@code NoClassDefFoundError}. Force the one-time class initialization here, with a
    * throwaway fake Spring context in place just long enough to satisfy it, before Mockito's
    * {@code mockStatic(TableFormat.class)} gets a chance to trigger the same initialization
    * from inside its bytecode instrumentation (where the failure is far less legible).
    */
   @BeforeAll
   static void loadTableFormatClassWithFakeSpringContext() throws Exception {
      ConfigurationContext context = ConfigurationContext.getContext();
      ApplicationContext springContext = mock(ApplicationContext.class);
      when(springContext.getBean(PropertiesEngine.class)).thenReturn(mock(PropertiesEngine.class));
      context.setApplicationContext(springContext);

      try {
         Class.forName(TableFormat.class.getName());
      }
      finally {
         context.setApplicationContext(null);
      }
   }

   @Mock private AssetRepository assetRepository;
   @Mock private LogManager logManager;
   @Mock private SecurityEngine securityEngine;

   private PropertyChangeSideEffects sideEffects;
   private MockedStatic<SreeEnv> sreeEnvStatic;
   private MockedStatic<TableFormat> tableFormatStatic;
   private MockedStatic<Tool> toolStatic;

   @BeforeEach
   void setUp() {
      sideEffects = new PropertyChangeSideEffects(assetRepository, logManager, securityEngine);
      sreeEnvStatic = mockStatic(SreeEnv.class, withSettings().lenient());
      tableFormatStatic = mockStatic(TableFormat.class, withSettings().lenient());
      toolStatic = mockStatic(Tool.class, withSettings().lenient());
   }

   @AfterEach
   void tearDown() {
      sreeEnvStatic.close();
      tableFormatStatic.close();
      toolStatic.close();
   }

   // -------------------------------------------------------------------------
   // applyEditSideEffects
   // -------------------------------------------------------------------------

   // [format.number.round] invalidates the table format cache
   @Test
   void applyEditSideEffects_formatNumberRound_invalidatesTableFormatCache() {
      sideEffects.applyEditSideEffects("format.number.round");

      tableFormatStatic.verify(TableFormat::invalidateTableFormatCache);
   }

   // [format.percent.round] invalidates the table format cache
   @Test
   void applyEditSideEffects_formatPercentRound_invalidatesTableFormatCache() {
      sideEffects.applyEditSideEffects("format.percent.round");

      tableFormatStatic.verify(TableFormat::invalidateTableFormatCache);
   }

   // [string.compare.casesensitive] invalidates the cached case-sensitivity flag
   @Test
   void applyEditSideEffects_caseSensitive_invalidatesCaseSensitiveFlag() {
      sideEffects.applyEditSideEffects("string.compare.casesensitive");

      toolStatic.verify(Tool::invalidateCaseSensitive);
   }

   // [security.exposedefaultorgtoall] fires the asset repository event
   @Test
   void applyEditSideEffects_exposeDefaultOrgProperty_firesRepositoryEvent() {
      sideEffects.applyEditSideEffects("security.exposedefaultorgtoall");

      verify(assetRepository).fireExposeDefaultOrgPropertyChange();
   }

   // [unrelated property] no side effects fire
   @Test
   void applyEditSideEffects_unrelatedProperty_noSideEffects() {
      sideEffects.applyEditSideEffects("some.other.property");

      tableFormatStatic.verify(TableFormat::invalidateTableFormatCache, never());
      toolStatic.verify(Tool::invalidateCaseSensitive, never());
      verifyNoInteractions(assetRepository);
   }

   // -------------------------------------------------------------------------
   // applyPreRemoveSideEffects (removeLogLevel only -- must run before SreeEnv.remove())
   // -------------------------------------------------------------------------

   // [security.exposedefaultorgtoall] does NOT fire the repository event -- that is a
   // post-remove side effect
   @Test
   void applyPreRemoveSideEffects_exposeDefaultOrgProperty_doesNotFireRepositoryEvent() {
      sreeEnvStatic.when(() -> SreeEnv.getProperty("security.exposedefaultorgtoall"))
         .thenReturn(null);

      sideEffects.applyPreRemoveSideEffects("security.exposedefaultorgtoall");

      verifyNoInteractions(assetRepository);
   }

   // [matching log level property] clears the custom context log level
   @Test
   void applyPreRemoveSideEffects_matchingLogLevelProperty_clearsContextLevel() {
      String property = "log.USER.level.joe";
      sreeEnvStatic.when(() -> SreeEnv.getProperty(property)).thenReturn("DEBUG");
      LogLevelSetting setting = new LogLevelSetting(LogContext.USER, "joe", null, LogLevel.DEBUG);
      when(logManager.getContextLevels()).thenReturn(List.of(setting));

      sideEffects.applyPreRemoveSideEffects(property);

      verify(logManager).setContextLevel(LogContext.USER, "joe", null);
   }

   // [log level property already off] does not attempt to clear it again
   @Test
   void applyPreRemoveSideEffects_logLevelPropertyOff_doesNotClearLevel() {
      String property = "log.USER.level.joe";
      sreeEnvStatic.when(() -> SreeEnv.getProperty(property)).thenReturn("off");

      sideEffects.applyPreRemoveSideEffects(property);

      verify(logManager, never()).setContextLevel(any(), any(), any());
   }

   // -------------------------------------------------------------------------
   // applyPostRemoveSideEffects (exposedefault fire only -- must run after SreeEnv.save())
   // -------------------------------------------------------------------------

   // [security.exposedefaultorgtoall] fires the asset repository event
   @Test
   void applyPostRemoveSideEffects_exposeDefaultOrgProperty_firesRepositoryEvent() {
      sideEffects.applyPostRemoveSideEffects("security.exposedefaultorgtoall");

      verify(assetRepository).fireExposeDefaultOrgPropertyChange();
   }

   // [unrelated property] no repository event fires
   @Test
   void applyPostRemoveSideEffects_unrelatedProperty_noRepositoryEvent() {
      sideEffects.applyPostRemoveSideEffects("some.other.property");

      verifyNoInteractions(assetRepository);
   }

   // [log level property] does not touch logManager -- that is a pre-remove side effect
   @Test
   void applyPostRemoveSideEffects_logLevelProperty_doesNotTouchLogManager() {
      sideEffects.applyPostRemoveSideEffects("log.USER.level.joe");

      verifyNoInteractions(logManager);
   }
}
