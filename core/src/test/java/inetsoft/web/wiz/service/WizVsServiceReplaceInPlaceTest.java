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
package inetsoft.web.wiz.service;

import inetsoft.uql.viewsheet.VSAssembly;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * What {@code CreateVisualizationModel.assemblyName} means on the standard create/rebind path: the
 * chart the call is ABOUT, with {@code copy} deciding whether that chart is replaced or kept.
 *
 * <p>One field for both intents, because both need the same thing — knowing WHICH chart the call is
 * about, so the binding it rebuilds from is that chart's and not whichever happens to be primary (a
 * session shares one viewsheet across every turn, so primary is always the latest chart). A click on a
 * card's own chart-type menu replaces that card; a chat turn about it keeps it as history.
 */
@Tag("core")
class WizVsServiceReplaceInPlaceTest {
   @Test
   void aNamedTargetIsReplacedWhenNotCopying() {
      assertTrue(WizVsService.replaceInPlace(mock(VSAssembly.class), false, false));
   }

   /** copy=true keeps the named card and adds a new one — the chat-driven type change. */
   @Test
   void aNamedTargetIsKeptWhenCopying() {
      assertFalse(WizVsService.replaceInPlace(mock(VSAssembly.class), false, true));
   }

   /** No name: nothing to replace, whatever copy says. */
   @Test
   void nothingIsReplacedWithoutANamedTarget() {
      assertFalse(WizVsService.replaceInPlace(null, false, false));
      assertFalse(WizVsService.replaceInPlace(null, false, true));
   }

   /** Sync mode names the config SOURCE; its result is by definition a new assembly. */
   @Test
   void syncModeNeverReplaces() {
      assertFalse(WizVsService.replaceInPlace(mock(VSAssembly.class), true, false));
      assertFalse(WizVsService.replaceInPlace(mock(VSAssembly.class), true, true));
   }

   /**
    * keepCondition follows the NAMED chart, not the displaced one. On a copy the displaced assembly is
    * whichever was primary — the latest chart — so carrying its filter onto a type change made about an
    * earlier card would apply a filter the user never asked for there.
    */
   @Test
   void theConditionComesFromTheNamedChart() {
      VSAssembly named = mock(VSAssembly.class);
      VSAssembly previousPrimary = mock(VSAssembly.class);

      assertSame(named, WizVsService.resolveConditionSource(false, named, null, previousPrimary));
   }

   /** With no name it stays on the displaced assembly: replaced first, else the demoted primary. */
   @Test
   void withoutANameItFollowsTheDisplacedAssembly() {
      VSAssembly replaced = mock(VSAssembly.class);
      VSAssembly previousPrimary = mock(VSAssembly.class);

      assertSame(replaced, WizVsService.resolveConditionSource(false, null, replaced, previousPrimary));
      assertSame(previousPrimary,
                 WizVsService.resolveConditionSource(false, null, null, previousPrimary));
   }

   /** Sync mode falls through — syncConfigs carries the condition there instead. */
   @Test
   void syncModeIgnoresTheNamedChart() {
      VSAssembly named = mock(VSAssembly.class);
      VSAssembly previousPrimary = mock(VSAssembly.class);

      assertSame(previousPrimary,
                 WizVsService.resolveConditionSource(true, named, null, previousPrimary));
   }
}
