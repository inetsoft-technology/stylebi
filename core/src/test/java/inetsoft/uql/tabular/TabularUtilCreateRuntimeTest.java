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
package inetsoft.uql.tabular;

import inetsoft.uql.VariableTable;
import inetsoft.uql.XDataSource;
import inetsoft.uql.XRepository;
import inetsoft.uql.XTableNode;
import inetsoft.uql.util.Config;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Covers charter assertion C4: {@link TabularUtil#createRuntime} must let a broken connector
 * constructor be told apart from "this connector never implemented the SPI" — before this round,
 * both collapsed into a {@code null} return, which {@code TabularCatalogService.resolveProvider}
 * then turned into the same {@link inetsoft.web.wiz.service.UnsupportedDatasourceException} either
 * way. There is no prior {@code TabularUtilTest} to extend; this is the first test of this method.
 */
@Tag("core")
class TabularUtilCreateRuntimeTest {

   private static final String DS_NAME = "Fake Tabular Source";

   @Test
   void createRuntime_constructorThrows_propagatesUncheckedRatherThanReturningNull()
      throws Exception
   {
      XDataSource ds = mock(XDataSource.class);
      when(ds.getType()).thenReturn("Broken");

      XRepository xrepository = mock(XRepository.class);
      when(xrepository.getDataSource(DS_NAME)).thenReturn(ds);

      Config config = mock(Config.class);
      when(config.getRuntime("Broken")).thenReturn(BrokenConstructorRuntime.class.getName());
      when(config.getClass("Broken", BrokenConstructorRuntime.class.getName()))
         .thenReturn((Class) BrokenConstructorRuntime.class);

      try(MockedStatic<XRepository> xrepositoryStatic = mockStatic(XRepository.class);
          MockedStatic<Config> configStatic = mockStatic(Config.class))
      {
         xrepositoryStatic.when(XRepository::getRepository).thenReturn(xrepository);
         configStatic.when(Config::getConfig).thenReturn(config);

         RuntimeException ex = assertThrows(RuntimeException.class,
            () -> TabularUtil.createRuntime(DS_NAME));

         // The whole point of C4: this is an unchecked RuntimeException, structurally impossible
         // to be the checked UnsupportedDatasourceException the "not implemented" path throws —
         // the two signals cannot collapse into the same catch clause at the caller.
         assertTrue(ex.getMessage().contains(DS_NAME));
         assertTrue(ex.getMessage().contains(BrokenConstructorRuntime.class.getName()));
      }
   }

   @Test
   void createRuntime_classNotLoadable_stillReturnsNull() throws Exception {
      XDataSource ds = mock(XDataSource.class);
      when(ds.getType()).thenReturn("Missing");

      XRepository xrepository = mock(XRepository.class);
      when(xrepository.getDataSource(DS_NAME)).thenReturn(ds);

      Config config = mock(Config.class);
      when(config.getRuntime("Missing")).thenReturn("no.such.RuntimeClass");
      when(config.getClass("Missing", "no.such.RuntimeClass"))
         .thenThrow(new ClassNotFoundException("no.such.RuntimeClass"));

      try(MockedStatic<XRepository> xrepositoryStatic = mockStatic(XRepository.class);
          MockedStatic<Config> configStatic = mockStatic(Config.class))
      {
         xrepositoryStatic.when(XRepository::getRepository).thenReturn(xrepository);
         configStatic.when(Config::getConfig).thenReturn(config);

         // The "not implemented / not loadable" path is untouched — still a plain null, not an
         // exception of any kind.
         assertNull(TabularUtil.createRuntime(DS_NAME));
      }
   }

   /**
    * A runtime whose constructor throws — simulates a genuinely broken connector, as opposed to
    * one that was never registered/loadable at all.
    */
   public static class BrokenConstructorRuntime extends TabularRuntime {
      public BrokenConstructorRuntime() {
         throw new IllegalStateException("boom");
      }

      @Override
      public XTableNode runQuery(TabularQuery query, VariableTable params) {
         throw new UnsupportedOperationException("not used by these tests");
      }

      @Override
      public void testDataSource(TabularDataSource<?> ds, VariableTable params) {
         throw new UnsupportedOperationException("not used by these tests");
      }
   }
}
