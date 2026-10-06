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

package inetsoft.web.admin.content.repository;

import inetsoft.mv.MVDef;
import inetsoft.mv.VSMVAnalyzer;
import inetsoft.mv.trans.TransformationDescriptor;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.uql.util.DefaultIdentity;
import inetsoft.uql.util.Identity;
import inetsoft.test.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.viewsheet.Viewsheet;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.objenesis.ObjenesisStd;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77609: AnalysisJob.createMVDef() created a design-mode sandbox per identity and never
 * disposed it. Each identity's sandbox must be disposed, including when analyze() fails and
 * the loop continues with the next identity, and when a SecurityException ends the job.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class MVSupportServiceAnalysisSandboxDisposeTest {
   @Test
   void disposesEachIdentitySandboxIncludingFailedAnalyze() throws Exception {
      // identity 1 analyzes, identity 2 fails and is skipped, identity 3 analyzes
      try(MockedStatic<SUtil> sutil = mockPrincipals();
          MockedConstruction<ViewsheetSandbox> boxes = mockConstruction(ViewsheetSandbox.class);
          MockedConstruction<VSMVAnalyzer> analyzers = mockAnalyzers(2, false))
      {
         invokeCreateMVDef(createJob(3));

         assertEquals(3, boxes.constructed().size());

         for(ViewsheetSandbox box : boxes.constructed()) {
            verify(box).dispose();
         }
      }
   }

   @Test
   void disposesSandboxWhenAnalyzeThrowsSecurityException() throws Exception {
      try(MockedStatic<SUtil> sutil = mockPrincipals();
          MockedConstruction<ViewsheetSandbox> boxes = mockConstruction(ViewsheetSandbox.class);
          MockedConstruction<VSMVAnalyzer> analyzers = mockAnalyzers(2, true))
      {
         InvocationTargetException ex = assertThrows(
            InvocationTargetException.class, () -> invokeCreateMVDef(createJob(3)));
         assertInstanceOf(java.lang.SecurityException.class, ex.getCause());

         assertEquals(2, boxes.constructed().size());

         for(ViewsheetSandbox box : boxes.constructed()) {
            verify(box).dispose();
         }
      }
   }

   private static MockedStatic<SUtil> mockPrincipals() {
      MockedStatic<SUtil> sutil = mockStatic(SUtil.class, CALLS_REAL_METHODS);
      sutil.when(() -> SUtil.getPrincipal(any(Identity.class), any(), anyBoolean()))
         .thenReturn(mock(SRPrincipal.class));
      return sutil;
   }

   // the analyzer created for the failing identity (1-based) throws from analyze()
   private static MockedConstruction<VSMVAnalyzer> mockAnalyzers(int failing, boolean security) {
      int[] count = { 0 };

      return mockConstruction(VSMVAnalyzer.class, (analyzer, context) -> {
         if(++count[0] == failing) {
            when(analyzer.analyze()).thenThrow(
               security ? new java.lang.SecurityException("sentinel77609") :
                  new RuntimeException("sentinel77609"));
         }
         else {
            when(analyzer.analyze()).thenReturn(new MVDef[0]);
         }

         when(analyzer.getDescriptor()).thenReturn(mock(TransformationDescriptor.class));
      });
   }

   // AnalysisJob's constructor reads the sheet from the repository, so build it without one
   private static Object createJob(int identityCount) throws Exception {
      Class<?> jobClass = Class.forName(MVSupportService.class.getName() + "$AnalysisJob");
      Object job = new ObjenesisStd().newInstance(jobClass);
      Viewsheet sheet = mock(Viewsheet.class);
      Worksheet ws = mock(Worksheet.class);
      when(sheet.getBaseWorksheet()).thenReturn(ws);
      when(ws.getAssemblies(true)).thenReturn(new Assembly[0]);
      List<Identity> identities = new ArrayList<>();

      for(int i = 0; i < identityCount; i++) {
         identities.add(new DefaultIdentity("user" + i, "host-org", Identity.USER));
      }

      set(job, "sheet", sheet);
      set(job, "entry", mock(AssetEntry.class));
      set(job, "candidate", new MVSupportService.MVCandidate("vs-id"));
      set(job, "identities", identities);
      set(job, "defs", new ArrayList<>());
      set(job, "exceptions", new ArrayList<>());
      set(job, "descs", new ArrayList<>());
      return job;
   }

   private static void invokeCreateMVDef(Object job) throws Exception {
      Method method = job.getClass().getDeclaredMethod("createMVDef");
      method.setAccessible(true);
      method.invoke(job);
   }

   private static void set(Object job, String name, Object value) throws Exception {
      Field field = job.getClass().getDeclaredField(name);
      field.setAccessible(true);
      field.set(job, value);
   }
}
