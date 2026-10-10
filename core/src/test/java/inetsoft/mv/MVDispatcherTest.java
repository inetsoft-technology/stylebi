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
package inetsoft.mv;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import inetsoft.report.composition.execution.AssetDataCache;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.sree.SreeEnv;
import inetsoft.uql.asset.TableAssembly;
import inetsoft.uql.asset.Worksheet;
import inetsoft.util.CancelledException;
import inetsoft.util.ThreadContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.Principal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Verifies that {@link MVDispatcher}'s multi-threaded dispatch path passes the calling thread's
 * current {@link ThreadContext} principal into each {@link MVCompositeDispatcher} it constructs,
 * rather than defaulting to {@code null} -- otherwise MV blocks built on the parallel path would
 * run with no tenant/user identity attached.
 */
@Tag("core")
class MVDispatcherTest {
   @Test
   void passesTheCurrentContextPrincipalToEachCompositeDispatcherItCreates() throws Throwable {
      MVDef def = mock(MVDef.class);
      when(def.getName()).thenReturn("mv1");

      MVDispatcher dispatcher = new MVDispatcher(def);
      Principal currentPrincipal = mock(Principal.class);
      List<List<Object>> constructedWith = new ArrayList<>();

      try(MockedStatic<SreeEnv> sreeEnvStatic = mockStatic(SreeEnv.class);
          MockedStatic<ThreadContext> threadContextStatic = mockStatic(ThreadContext.class);
          MockedConstruction<MVCompositeDispatcher> construction = mockConstruction(
             MVCompositeDispatcher.class,
             (mock, context) -> {
                constructedWith.add(new ArrayList<>(context.arguments()));
                when(mock.isCompleted()).thenReturn(true);
                when(mock.getException()).thenReturn(null);
             }))
      {
         // force exactly one composite dispatcher to be created, so there's exactly one
         // constructor call to inspect.
         sreeEnvStatic.when(() -> SreeEnv.getProperty("mv.dispatcher.count")).thenReturn("1");
         threadContextStatic.when(ThreadContext::getContextPrincipal).thenReturn(currentPrincipal);

         Method processDispatch =
            MVDispatcher.class.getDeclaredMethod("processDispatch", boolean.class);
         processDispatch.setAccessible(true);

         try {
            processDispatch.invoke(dispatcher, true);
         }
         catch(java.lang.reflect.InvocationTargetException e) {
            throw e.getCause();
         }
      }

      assertEquals(1, constructedWith.size());
      // constructor signature is (MVDef def, VariableTable vars, Principal principal)
      assertSame(currentPrincipal, constructedWith.get(0).get(2));
   }

   /**
    * Bug #77154: the composite dispatchers of one parallel build share the def's columns, so they
    * must share one set of already reset columns, otherwise each dispatcher's MVBuilder resets the
    * shared columns and wipes the range its siblings already accumulated.
    */
   @Test
   void compositeDispatchersOfOneBuildShareOneResetColumnSet() throws Throwable {
      MVDef def = mock(MVDef.class);
      when(def.getName()).thenReturn("mv1");

      MVDispatcher dispatcher = new MVDispatcher(def);
      List<MVCompositeDispatcher> constructed = new ArrayList<>();

      try(MockedStatic<SreeEnv> sreeEnvStatic = mockStatic(SreeEnv.class);
          MockedStatic<ThreadContext> threadContextStatic = mockStatic(ThreadContext.class);
          MockedConstruction<MVCompositeDispatcher> construction = mockConstruction(
             MVCompositeDispatcher.class,
             (mock, context) -> {
                constructed.add(mock);
                when(mock.isCompleted()).thenReturn(true);
                when(mock.getException()).thenReturn(null);
             }))
      {
         sreeEnvStatic.when(() -> SreeEnv.getProperty("mv.dispatcher.count")).thenReturn("3");

         Method processDispatch =
            MVDispatcher.class.getDeclaredMethod("processDispatch", boolean.class);
         processDispatch.setAccessible(true);

         try {
            processDispatch.invoke(dispatcher, true);
         }
         catch(java.lang.reflect.InvocationTargetException e) {
            throw e.getCause();
         }
      }

      assertEquals(3, constructed.size());
      assertNotNull(constructed.get(0).resetColumns);

      for(MVCompositeDispatcher composite : constructed) {
         assertSame(constructed.get(0).resetColumns, composite.resetColumns);
      }

      // a dispatcher outside a parallel build keeps resetting its builder's columns
      assertNull(new MVDispatcher(def).resetColumns);
   }

   /**
    * Bug #78227: a failed data query of an association MV must keep its cause and be reported as
    * a failure, not as a cause-less cancel.
    */
   @Test
   void associationMVQueryFailureKeepsItsCauseAndIsNotACancel() throws Throwable {
      SQLException sqlFailure = new SQLException("Connection refused: db.example:5432");
      Logger logger = (Logger) LoggerFactory.getLogger(MVDispatcher.class);
      ListAppender<ILoggingEvent> appender = new ListAppender<>();
      appender.start();
      logger.addAppender(appender);
      Throwable thrown;

      try {
         thrown = getAssociationData(sqlFailure, false);
      }
      finally {
         logger.detachAppender(appender);
      }

      assertFalse(thrown instanceof CancelledException, "not a cancel: " + thrown);
      assertNull(CancelledException.find(thrown));
      assertInstanceOf(MVLoadFailedException.class, thrown);
      assertSame(sqlFailure, thrown.getCause());
      assertTrue(thrown.getMessage().contains("Connection refused"), thrown.getMessage());
      assertTrue(appender.list.stream().anyMatch(
         e -> e.getLevel() == Level.WARN && e.getThrowableProxy() != null &&
            e.getFormattedMessage().contains("mv1")), "a WARN with the cause is logged");
   }

   /**
    * Bug #78227: a real cancel thrown by the data query of an association MV stays a cancel.
    */
   @Test
   void associationMVQueryCancelStaysACancel() throws Throwable {
      CancelledException cancel = new CancelledException("statement cancelled");
      Throwable thrown = getAssociationData(new RuntimeException(cancel), false);

      assertSame(cancel, thrown);
   }

   /**
    * Bug #78227: a failure after the dispatcher was cancelled is reported as a cancel.
    */
   @Test
   void associationMVFailureAfterCancelIsACancel() throws Throwable {
      NullPointerException failure = new NullPointerException();
      Throwable thrown = getAssociationData(failure, true);

      assertInstanceOf(CancelledException.class, thrown);
      assertSame(failure, thrown.getCause());
   }

   /**
    * Calls getData() of a dispatcher of an association MV built on a base runtime MV, whose
    * data query throws the given exception, and returns what getData() threw.
    */
   private static Throwable getAssociationData(Throwable queryFailure, boolean canceled)
      throws Throwable
   {
      MVDef def = mock(MVDef.class);
      Worksheet ws = mock(Worksheet.class);
      TableAssembly assembly = mock(TableAssembly.class);
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      AssetDataCache cache = mock(AssetDataCache.class);

      when(def.getName()).thenReturn("mv1");
      when(def.getWorksheet()).thenReturn(ws);
      when(def.getMVTable()).thenReturn("T1");
      when(def.isAssociationMV()).thenReturn(true);
      when(ws.getAssembly("T1")).thenReturn(assembly);
      when(assembly.clone()).thenReturn(assembly);
      when(assembly.getRuntimeMV()).thenReturn(mock(RuntimeMV.class));
      when(cache.getData(any(), any(), any(), any(), anyInt(), anyBoolean(), anyLong(), any()))
         .thenThrow(queryFailure);

      MVDispatcher dispatcher = new MVDispatcher(def);

      if(canceled) {
         Field field = MVDispatcher.class.getDeclaredField("canceled");
         field.setAccessible(true);
         field.setBoolean(dispatcher, true);
      }

      try(MockedStatic<MVCreatorUtil> creatorUtil = mockStatic(MVCreatorUtil.class);
          MockedStatic<AssetDataCache> cacheStatic = mockStatic(AssetDataCache.class))
      {
         creatorUtil.when(() -> MVCreatorUtil.createAssetQuerySandbox(any(), any(), any()))
            .thenReturn(box);
         cacheStatic.when(AssetDataCache::getCache).thenReturn(cache);

         return assertThrows(Throwable.class, () -> dispatcher.getData(false, null));
      }
   }
}
