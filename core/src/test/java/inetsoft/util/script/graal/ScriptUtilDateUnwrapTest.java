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
package inetsoft.util.script.graal;

import inetsoft.util.script.ScriptUtil;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression test for Bug #75633: a JS Date placed in a host data structure
 * (e.g. new DefaultDataSet([["Date","Qty"],[new Date(),200]])) is coerced by
 * GraalJS to a foreign polyglot object rather than a java.util.Date. ScriptUtil
 * .unwrap (called by GTool.unwrap while building the dataset) must recover the
 * date so a TimeScale over that column sees real dates instead of nulls.
 */
@Tag("core")
class ScriptUtilDateUnwrapTest {
   private Context newCtx() {
      Engine engine = Engine.newBuilder().option("engine.WarnInterpreterOnly", "false").build();
      return Context.newBuilder("js")
         .engine(engine)
         .allowHostAccess(ScriptHostAccess.hostAccess())
         .allowHostClassLookup(ScriptHostAccess.classFilter())
         .build();
   }

   @Test
   void jsDateInObjectArrayUnwrapsToJavaDate() {
      try(Context ctx = newCtx()) {
         Value arr = ctx.eval("js",
            "var date1 = new Date(); date1.setFullYear(2008,9,1);\n" +
            "[['Date','Quantity'],[date1,200]];\n");

         // The Object[][] that DefaultDataSet's constructor receives: the JS Date
         // element is coerced to a foreign polyglot object, NOT a java.util.Date.
         Object[][] host = arr.as(Object[][].class);
         Object dateElem = host[1][0];
         Object numElem = host[1][1];
         assertFalse(dateElem instanceof Date,
                     "precondition: raw coerced element is not yet a Date");

         // DefaultDataSet runs its GTool.unwrap (-> ScriptUtil.unwrap) while the
         // script context is current; simulate that by entering the context.
         ctx.enter();

         try {
            Object unwrappedDate = ScriptUtil.unwrap(dateElem);
            Object unwrappedNum = ScriptUtil.unwrap(numElem);

            assertInstanceOf(Date.class, unwrappedDate,
                             "JS Date element must unwrap to java.util.Date (#75633)");
            assertEquals(2008, ((Date) unwrappedDate).getYear() + 1900);
            assertFalse(unwrappedNum instanceof Date,
                        "numeric element must not become a Date");
         }
         finally {
            ctx.leave();
         }
      }
   }

   @Test
   void nonDateValuesUnwrapUnchanged() {
      try(Context ctx = newCtx()) {
         ctx.enter();

         try {
            assertEquals("abc", ScriptUtil.unwrap("abc"));
            assertEquals(5, ScriptUtil.unwrap(5));
            assertNull(ScriptUtil.unwrap(null));
         }
         finally {
            ctx.leave();
         }
      }
   }
}
