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
package inetsoft.mv.data;

import inetsoft.mv.MVColumn;
import inetsoft.mv.MVDef;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Characterizes {@code MVScriptable.getMember()} post-migration from Rhino's
 * {@code ScriptableObject} to the {@code ScriptScope} interface.
 *
 * <p><b>Finding:</b> when the backing MV has not been built yet (storage lookup fails, e.g. a
 * fresh/never-materialized def), {@code getMember("MaxValue")}/{@code getMember("MinValue")}
 * throw an uncaught {@link NullPointerException} from {@code max()}/{@code min()} dereferencing
 * the null {@code mv} field, rather than returning a sentinel or {@code null} gracefully. This is
 * pinned below as the current behavior (unrelated to the Rhino migration -- {@code max()}/
 * {@code min()} were not touched by it) but may be worth a separate bug report if a production
 * code path actually reaches this state (e.g. an incremental MV condition script evaluated before
 * the MV finishes building).</p>
 */
@Tag("core")
class MVScriptableTest {
   @Test
   void unknownPropertyReturnsNull() {
      MVScriptable scriptable = newScriptableWithUnbuiltMv();

      assertNull(scriptable.getMember("UnknownProperty"));
   }

   @Test
   void hasMemberIsTrueOnlyForTheThreeKnownProperties() {
      MVScriptable scriptable = newScriptableWithUnbuiltMv();

      assertTrue(scriptable.hasMember("LastUpdateTime"));
      assertTrue(scriptable.hasMember("MaxValue"));
      assertTrue(scriptable.hasMember("MinValue"));
      assertFalse(scriptable.hasMember("UnknownProperty"));
   }

   @Test
   void lastUpdateTimeWorksEvenWhenMvIsUnbuilt() {
      MVDef mvdef = mock(MVDef.class);
      when(mvdef.getName()).thenReturn("mv1");
      when(mvdef.getLastUpdateTime()).thenReturn(1700000000000L);
      MVColumn mvcol = mock(MVColumn.class);

      MVScriptable scriptable = new MVScriptable(mvdef, mvcol);

      assertEquals(new Timestamp(1700000000000L), scriptable.getMember("LastUpdateTime"));
   }

   /**
    * Pins the current behavior described in the class-level javadoc: this is a characterization
    * test, not an endorsement -- it exists so this NPE doesn't silently change (e.g. into a
    * different exception type, or into a swallowed null) without the change being noticed.
    */
   @Test
   void maxValueThrowsWhenMvIsUnbuilt() {
      MVScriptable scriptable = newScriptableWithUnbuiltMv();

      assertThrows(NullPointerException.class, () -> scriptable.getMember("MaxValue"));
   }

   @Test
   void minValueThrowsWhenMvIsUnbuilt() {
      MVScriptable scriptable = newScriptableWithUnbuiltMv();

      assertThrows(NullPointerException.class, () -> scriptable.getMember("MinValue"));
   }

   @Test
   void maxValueReturnsTheColumnsCachedOriginalMaxWhenMvHasData() throws Exception {
      MVDef mvdef = mock(MVDef.class);
      when(mvdef.getName()).thenReturn("mv1");
      MVColumn mvcol = mock(MVColumn.class);
      when(mvcol.getName()).thenReturn("col1");
      when(mvcol.getOriginalMax()).thenReturn(100.0);
      when(mvcol.isDateTime()).thenReturn(false);

      MVScriptable scriptable = new MVScriptable(mvdef, mvcol);
      setMv(scriptable, builtMvWithNoDictionary());

      assertEquals(100.0, scriptable.getMember("MaxValue"));
   }

   @Test
   void minValueReturnsTheColumnsCachedOriginalMinWhenMvHasData() throws Exception {
      MVDef mvdef = mock(MVDef.class);
      when(mvdef.getName()).thenReturn("mv1");
      MVColumn mvcol = mock(MVColumn.class);
      when(mvcol.getName()).thenReturn("col1");
      when(mvcol.getOriginalMin()).thenReturn(1.0);
      when(mvcol.isDateTime()).thenReturn(false);

      MVScriptable scriptable = new MVScriptable(mvdef, mvcol);
      setMv(scriptable, builtMvWithNoDictionary());

      assertEquals(1.0, scriptable.getMember("MinValue"));
   }

   private static MVScriptable newScriptableWithUnbuiltMv() {
      MVDef mvdef = mock(MVDef.class);
      when(mvdef.getName()).thenReturn("mv1");
      MVColumn mvcol = mock(MVColumn.class);
      return new MVScriptable(mvdef, mvcol);
   }

   /** A mock MV with one block and no dictionary for column 0 (i.e. a non-dimension column). */
   private static MV builtMvWithNoDictionary() {
      MV mv = mock(MV.class);
      when(mv.getBlockSize()).thenReturn(1);
      when(mv.indexOfHeader("col1", 0)).thenReturn(0);
      when(mv.getDictionary(0, 0)).thenReturn(null);
      return mv;
   }

   private static void setMv(MVScriptable scriptable, MV mv) throws Exception {
      java.lang.reflect.Field field = MVScriptable.class.getDeclaredField("mv");
      field.setAccessible(true);
      field.set(scriptable, mv);
   }
}
