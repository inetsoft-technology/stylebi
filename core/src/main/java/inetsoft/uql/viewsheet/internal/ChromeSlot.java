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
package inetsoft.uql.viewsheet.internal;

import java.util.function.*;

/**
 * One piece of look-and-feel a component carries: how to read the author's value, the resolved
 * value, and how to clear or reset it. Lambdas address the property through the info they are
 * given, so a slot works on the assembly, a clone or a fresh instance alike.
 */
public final class ChromeSlot {
   private ChromeSlot(String key, boolean objectLevel, boolean residueEligible,
                      Predicate<VSAssemblyInfo> authorSet, Predicate<VSAssemblyInfo> expression,
                      Function<VSAssemblyInfo, Object> authorValue,
                      Function<VSAssemblyInfo, Object> resolvedValue,
                      Consumer<VSAssemblyInfo> clearAuthor,
                      BiConsumer<VSAssemblyInfo, VSAssemblyInfo> reset)
   {
      this.key = key;
      this.objectLevel = objectLevel;
      this.residueEligible = residueEligible;
      this.authorSet = authorSet;
      this.expression = expression;
      this.authorValue = authorValue;
      this.resolvedValue = resolvedValue;
      this.clearAuthor = clearAuthor;
      this.reset = reset;
   }

   /**
    * A slot with an author tier, so it can be residue. A null reset clears the author value.
    */
   public static ChromeSlot tiered(String key, boolean objectLevel, boolean residueEligible,
                                   Predicate<VSAssemblyInfo> authorSet,
                                   Predicate<VSAssemblyInfo> expression,
                                   Function<VSAssemblyInfo, Object> authorValue,
                                   Function<VSAssemblyInfo, Object> resolvedValue,
                                   Consumer<VSAssemblyInfo> clearAuthor,
                                   BiConsumer<VSAssemblyInfo, VSAssemblyInfo> reset)
   {
      return new ChromeSlot(key, objectLevel, residueEligible, authorSet, expression,
                            authorValue, resolvedValue, clearAuthor, reset);
   }

   /**
    * A reset-only slot that copies its value from a fresh instance and is never residue.
    */
   public static ChromeSlot plain(String key,
                                  BiConsumer<VSAssemblyInfo, VSAssemblyInfo> copyFromFresh)
   {
      return new ChromeSlot(key, false, false, info -> false, info -> false, info -> null,
                            info -> null, info -> {}, (info, fresh) -> {
                               if(fresh != null) {
                                  copyFromFresh.accept(info, fresh);
                               }
                            });
   }

   public String key() {
      return key;
   }

   public boolean objectLevel() {
      return objectLevel;
   }

   public boolean residueEligible() {
      return residueEligible;
   }

   public boolean isAuthorSet(VSAssemblyInfo info) {
      return authorSet.test(info);
   }

   public boolean isExpression(VSAssemblyInfo info) {
      return expression.test(info);
   }

   public Object authorValue(VSAssemblyInfo info) {
      return authorValue.apply(info);
   }

   public Object resolvedValue(VSAssemblyInfo info) {
      return resolvedValue.apply(info);
   }

   public void clearAuthor(VSAssemblyInfo info) {
      clearAuthor.accept(info);
   }

   /**
    * Reset this slot on info; fresh may be null, which leaves a plain slot unchanged.
    */
   public void reset(VSAssemblyInfo info, VSAssemblyInfo fresh) {
      if(reset != null) {
         reset.accept(info, fresh);
      }
      else {
         clearAuthor.accept(info);
      }
   }

   private final String key;
   private final boolean objectLevel;
   private final boolean residueEligible;
   private final Predicate<VSAssemblyInfo> authorSet;
   private final Predicate<VSAssemblyInfo> expression;
   private final Function<VSAssemblyInfo, Object> authorValue;
   private final Function<VSAssemblyInfo, Object> resolvedValue;
   private final Consumer<VSAssemblyInfo> clearAuthor;
   private final BiConsumer<VSAssemblyInfo, VSAssemblyInfo> reset;
}
