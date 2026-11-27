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
package inetsoft.sree.schedule;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import inetsoft.report.composition.ChangedAssemblyList;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.test.*;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.util.TableLoadException;
import inetsoft.uql.viewsheet.TextVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.TextVSAssemblyInfo;
import inetsoft.util.script.ScriptException;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.LoggerFactory;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #82103: a scheduled viewsheet action failed when a component script threw, while the
 * manual, Portal and API exports skip the failing script with a warning (#8467 / #81796).
 * {@code ViewsheetAction.executeViewsheet} ran each component script with
 * {@code ViewsheetScope.execute} directly, outside the export skip scope, so the
 * {@code ScriptException} aborted the task before any export. It must skip an ordinary script
 * failure like the exporter does, and still fail on a failure the exporter does not skip.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome(importResources = "/inetsoft/graph/GraphRenderTest.zip")
@Tag("core")
@Tag("integration")
class ViewsheetActionScriptFailureTest {
   private static final String TEXT = "Text82103";
   private static final String BAD = "noSuchFunction123();";

   /** The scheduled path: runViewsheetAction runs the scripts on the viewsheet's own sandbox. */
   @Test
   void failingScriptDoesNotFailTheScheduledAction() throws Exception {
      ViewsheetSandbox box = boxWithText(BAD);
      Viewsheet vs = box.getViewsheet();

      assertFalse(executeViewsheet(box, vs.getAssemblies()),
                  "the failing script is skipped and the action goes on");
      assertSkipLogged();
      assertNull(box.getExportSkippedScript(), "the pre-run's mark is not left on the sandbox");
   }

   /** A script that cancels the action before it fails still cancels it. */
   @Test
   void scheduleActionFalseBeforeTheFailureStillCancels() throws Exception {
      ViewsheetSandbox box = boxWithText("scheduleAction(false); " + BAD);

      assertTrue(executeViewsheet(box, box.getViewsheet().getAssemblies()),
                 "scheduleAction(false) still cancels the bookmark");
      assertFalse(box.isScheduleAction());
   }

   /** The alert path: checkAlerts runs a viewsheet clone's scripts on a separate sandbox. */
   @Test
   void failingScriptDoesNotFailTheAlertCheck() throws Exception {
      ViewsheetSandbox box = boxWithText(BAD);
      ViewsheetSandbox alertBox = alertBox(box);

      try {
         assertFalse(executeViewsheet(alertBox, box.getViewsheet().clone().getAssemblies()));
         assertSkipLogged();
         assertNull(alertBox.getExportSkippedScript());
      }
      finally {
         alertBox.dispose();
      }
   }

   /** A working script runs as before, on the sandbox it is given. */
   @Test
   void workingScriptStillRuns() throws Exception {
      ViewsheetSandbox box = boxWithText(TEXT + ".text = 'fromScript82103';");
      ViewsheetSandbox alertBox = alertBox(box);

      try {
         Viewsheet clone = box.getViewsheet().clone();
         assertFalse(executeViewsheet(alertBox, clone.getAssemblies()));
         assertEquals("fromScript82103", textOf(alertBox.getViewsheet()));
         assertEquals(List.of(), skipWarnings(), "nothing was skipped");
      }
      finally {
         alertBox.dispose();
      }
   }

   /** Outside the scheduled action (the viewer) a failing script still throws. */
   @Test
   void viewerStillSeesTheFailure() throws Exception {
      ViewsheetSandbox box = boxWithText(BAD);
      TextVSAssembly text = (TextVSAssembly) box.getViewsheet().getAssembly(TEXT);

      assertThrows(ScriptException.class, () -> box.executeScript(text));
   }

   /** A failure the export does not skip (a failed table load) still fails the action. */
   @Test
   void tableLoadFailureStillFailsTheScheduledAction() throws Exception {
      ViewsheetSandbox box = boxWithText("p82103thrower.go();");
      box.getScope().putMember("p82103thrower", new Thrower());

      InvocationTargetException ex = assertThrows(
         InvocationTargetException.class,
         () -> EXECUTE_VIEWSHEET.invoke(new ViewsheetAction(), box,
                                        box.getViewsheet().getAssemblies()));
      assertNotNull(TableLoadException.find(ex.getCause()), "the table load failure: " + ex.getCause());
      assertEquals(List.of(), skipWarnings(), "the failure is not skipped");
   }

   /** Java object a script calls to get a TableLoadException, like a read of a failed table. */
   public static class Thrower {
      public void go() {
         throw new TableLoadException("82103 load failed", null);
      }
   }

   private ViewsheetSandbox boxWithText(String script) {
      ViewsheetSandbox box = viewsheetResource.getRuntimeViewsheet().getViewsheetSandbox()
         .orElseThrow();
      Viewsheet vs = box.getViewsheet();
      TextVSAssembly text = new TextVSAssembly(vs, TEXT);
      text.getVSAssemblyInfo().setScript(script);
      text.getVSAssemblyInfo().setScriptEnabled(true);
      vs.addAssembly(text);
      return box;
   }

   // the sandbox checkAlerts creates per bookmark
   private ViewsheetSandbox alertBox(ViewsheetSandbox box) throws Exception {
      RuntimeViewsheet rvs = viewsheetResource.getRuntimeViewsheet();
      ViewsheetSandbox alertBox = new ViewsheetSandbox(
         box.getViewsheet().clone(), Viewsheet.SHEET_RUNTIME_MODE, rvs.getUser(), false,
         rvs.getEntry());
      alertBox.resetAll(new ChangedAssemblyList());
      return alertBox;
   }

   private static String textOf(Viewsheet vs) {
      return ((TextVSAssemblyInfo) vs.getAssembly(TEXT).getInfo()).getText();
   }

   private static boolean executeViewsheet(ViewsheetSandbox box, Assembly[] assemblies)
      throws Exception
   {
      try {
         return (Boolean) EXECUTE_VIEWSHEET.invoke(new ViewsheetAction(), box, assemblies);
      }
      catch(InvocationTargetException ex) {
         throw ex.getCause() instanceof Exception cause ? cause : ex;
      }
   }

   private void assertSkipLogged() {
      List<String> skips = skipWarnings();
      assertFalse(skips.isEmpty(), "the skip should be logged");
      assertTrue(skips.get(0).contains(TEXT), skips.get(0));
      assertTrue(skips.get(0).contains("noSuchFunction123"), skips.get(0));
   }

   private List<String> skipWarnings() {
      return logs.list.stream()
         .filter(e -> e.getLevel() == Level.WARN)
         .map(ILoggingEvent::getFormattedMessage)
         .filter(m -> m.startsWith("Skipped the failing script"))
         .toList();
   }

   @BeforeEach
   void captureLog() {
      logs = new ListAppender<>();
      logs.start();
      ((Logger) LoggerFactory.getLogger(ViewsheetSandbox.class)).addAppender(logs);
   }

   @AfterEach
   void releaseLog() {
      ((Logger) LoggerFactory.getLogger(ViewsheetSandbox.class)).detachAppender(logs);
   }

   private static OpenViewsheetEvent createOpenViewsheetEvent() {
      OpenViewsheetEvent event = new OpenViewsheetEvent();
      event.setEntryId("1^128^__NULL__^TEST_GraphRender");
      event.setViewer(true);
      return event;
   }

   private static final Method EXECUTE_VIEWSHEET;

   static {
      try {
         EXECUTE_VIEWSHEET = ViewsheetAction.class.getDeclaredMethod(
            "executeViewsheet", ViewsheetSandbox.class, Assembly[].class);
         EXECUTE_VIEWSHEET.setAccessible(true);
      }
      catch(NoSuchMethodException ex) {
         throw new ExceptionInInitializerError(ex);
      }
   }

   private ListAppender<ILoggingEvent> logs;

   @RegisterExtension
   RuntimeViewsheetExtension viewsheetResource =
      new RuntimeViewsheetExtension(createOpenViewsheetEvent());
}
