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
package inetsoft.web.wiz.script;

import java.util.Arrays;
import java.util.List;

/**
 * The script target grammar this server speaks.
 *
 * <p>One place, because the controller advertises it and {@link ScriptReadService} emits targets
 * shaped by it: if those two disagreed, a client would negotiate a grammar the responses do not
 * follow, which is worse than no negotiation.
 */
public final class ScriptGrammar {
   /** v1 = the delimited target string; v2 = kinds, ids and structured params. */
   public static final int VERSION = 2;

   /**
    * The kinds this server can serve — those with an internal location behind them. Reserved
    * kinds are deliberately absent: advertising one and then refusing it is the silent capability
    * lie this contract exists to prevent.
    *
    * <p>{@link ScriptTarget.Kind#CALC_FIELD} is included: {@link ScriptReadService},
    * {@code ScriptEditService}, and {@code ScriptContextService} are now wired to it, so its
    * real {@link ScriptTarget.Location} is actually servable rather than merely present.
    *
    * <p>{@link ScriptTarget.Kind#WORKSHEET_EXPRESSION}/{@link ScriptTarget.Kind#WORKSHEET_CONDITION}
    * (G2 Task 8) are included for the same reason, served by {@code WorksheetScriptService} rather
    * than the viewsheet-scoped {@code ScriptReadService}/{@code ScriptEditService} — those two
    * explicitly refuse the worksheet locations (fail loud) rather than serving them.
    *
    * <p>{@link ScriptTarget.Kind#WORKSHEET_CONDITION_VALUE} is included for the same reason and
    * servable by the same {@code WorksheetScriptService}, narrower than
    * {@code WORKSHEET_CONDITION}: see its javadoc for why the two are not interchangeable.
    */
   public static List<String> supportedKinds() {
      return Arrays.stream(ScriptTarget.Kind.values())
         .filter(k -> k.location() != null)
         .map(ScriptTarget.Kind::wireName)
         .toList();
   }

   private ScriptGrammar() {
   }
}
