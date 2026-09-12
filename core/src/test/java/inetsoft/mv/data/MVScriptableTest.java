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
 * <p>When the backing MV has not been built yet (storage lookup fails, e.g. a fresh/
 * never-materialized def, or a concurrent MV rebuild races the storage read), {@code
 * getMember("MaxValue")}/{@code getMember("MinValue")} return {@code null} instead of throwing --
 * see Bug: MVScriptable.getMember() throws uncaught NullPointerException when MV storage read
 * races with concurrent MV rebuild.</p>
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

   @Test
   void maxValueReturnsNullWhenMvIsUnbuilt() {
      MVScriptable scriptable = newScriptableWithUnbuiltMv();

      assertNull(scriptable.getMember("MaxValue"));
   }

   @Test
   void minValueReturnsNullWhenMvIsUnbuilt() {
      MVScriptable scriptable = newScriptableWithUnbuiltMv();

      assertNull(scriptable.getMember("MinValue"));
   }

   @Test
   void maxValueReturnsTheColumnsCachedOriginalMaxWhenMvHasData() {
      MVDef mvdef = mock(MVDef.class);
      when(mvdef.getName()).thenReturn("mv1");
      MVColumn mvcol = mock(MVColumn.class);
      when(mvcol.getName()).thenReturn("col1");
      when(mvcol.getOriginalMax()).thenReturn(100.0);
      when(mvcol.isDateTime()).thenReturn(false);

      MVScriptable scriptable = new MVScriptable(mvdef, mvcol, builtMvWithNoDictionary());

      assertEquals(100.0, scriptable.getMember("MaxValue"));
   }

   @Test
   void minValueReturnsTheColumnsCachedOriginalMinWhenMvHasData() {
      MVDef mvdef = mock(MVDef.class);
      when(mvdef.getName()).thenReturn("mv1");
      MVColumn mvcol = mock(MVColumn.class);
      when(mvcol.getName()).thenReturn("col1");
      when(mvcol.getOriginalMin()).thenReturn(1.0);
      when(mvcol.isDateTime()).thenReturn(false);

      MVScriptable scriptable = new MVScriptable(mvdef, mvcol, builtMvWithNoDictionary());

      assertEquals(1.0, scriptable.getMember("MinValue"));
   }

   @Test
   void threeArgConstructorUsesTheGivenMvWithoutTouchingStorage() {
      MVDef mvdef = mock(MVDef.class);
      when(mvdef.getLastUpdateTime()).thenReturn(1700000000000L);
      MVColumn mvcol = mock(MVColumn.class);
      when(mvcol.getName()).thenReturn("col1");
      when(mvcol.getOriginalMax()).thenReturn(100.0);
      when(mvcol.getOriginalMin()).thenReturn(1.0);
      when(mvcol.isDateTime()).thenReturn(false);

      // mvdef.getName() is intentionally left unstubbed (returns null): the 3-arg
      // constructor must not call MVStorage.getFile()/get() at all, since its whole
      // point is to skip the independent storage lookup the 2-arg constructor does.
      MVScriptable scriptable = new MVScriptable(mvdef, mvcol, builtMvWithNoDictionary());

      assertEquals(new Timestamp(1700000000000L), scriptable.getMember("LastUpdateTime"));
      assertEquals(100.0, scriptable.getMember("MaxValue"));
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
}
