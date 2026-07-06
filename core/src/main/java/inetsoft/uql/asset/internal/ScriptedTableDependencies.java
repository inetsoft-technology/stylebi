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
package inetsoft.uql.asset.internal;

import inetsoft.uql.asset.*;
import inetsoft.uql.schema.UserVariable;
import inetsoft.util.DataCache;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@code ScriptedTableDependencies} handles extracting references to other table assemblies and
 * variables from the scripts of a {@link ScriptedTableAssembly}.
 */
public class ScriptedTableDependencies {
   /**
    * Gets the references from the input script of an assembly.
    *
    * @param worksheet the worksheet containing the assembly.
    * @param assembly  the assembly.
    *
    * @return the set of referenced assemblies.
    */
   public static Set<AssemblyRef> getInputReferences(Worksheet worksheet, Assembly assembly) {
      String script = getInputScript(assembly);

      if(script == null) {
         return Collections.emptySet();
      }
      else {
         // Don't need to cache the input references. This is only called for the scripted table
         // assembly itself, which will cache the result internally.
         return getReferences(worksheet, assembly, script);
      }
   }

   /**
    * Gets the references from the output script of an assembly.
    *
    * @param worksheet the worksheet containing the assembly.
    * @param assembly  the assembly.
    *
    * @return the set of referenced assemblies.
    */
   public static Set<AssemblyRef> getOutputReferences(Worksheet worksheet, Assembly assembly) {
      String script = getOutputScript(assembly);

      if(script == null) {
         return Collections.emptySet();
      }
      else {
         // Cache the output references, this will be called for every other table assembly in the
         // worksheet.
         String cacheKey = worksheet.addr() + "::" + script;
         Set<AssemblyRef> references = outputCache.get(cacheKey);

         if(references == null) {
            references = getReferences(worksheet, assembly, script);
            outputCache.put(cacheKey, references);
         }

         return references;
      }
   }

   /**
    * Gets the input script for an assembly.
    *
    * @param assembly the assembly.
    *
    * @return the input script or {@code null} if not defined or the assembly is not an instance of
    *         {@link ScriptedTableAssembly}.
    */
   private static String getInputScript(Assembly assembly) {
      return (assembly instanceof ScriptedTableAssembly) ?
         ((ScriptedTableAssembly) assembly).getInputScript() : null;
   }

   /**
    * Gets the output script for an assembly.
    *
    * @param assembly the assembly.
    *
    * @return the output script or {@code null} if not defined or the assembly is not an instance of
    *         {@link ScriptedTableAssembly}.
    */
   private static String getOutputScript(Assembly assembly) {
      return (assembly instanceof ScriptedTableAssembly) ?
         ((ScriptedTableAssembly) assembly).getOutputScript() : null;
   }

   /**
    * Gets the assembly references from a script.
    *
    * @param worksheet the worksheet containing the assemblies.
    * @param source    the assembly containing the script.
    * @param script    the JavaScript source of the script.
    *
    * @return the set of referenced assemblies.
    */
   private static Set<AssemblyRef> getReferences(Worksheet worksheet, Assembly source,
                                                 String script)
   {
      Set<AssemblyRef> references = new HashSet<>();

      // TODO(cutover): GraalJS does not expose a public Java AST/parser like
      // the old Rhino Parser, so identifier references are
      // extracted with a lexical (regex) scan rather than a true AST walk. This
      // matches identifier tokens and "object.property" accesses, which covers
      // the original two cases (bare assembly-name references and
      // parameter.<name> references). It may over-match identifiers that appear
      // inside string literals; over-matching only adds spurious dependency
      // edges, which is conservative and safe.
      Matcher matcher = IDENTIFIER.matcher(script);

      while(matcher.find()) {
         String objectName = matcher.group(1); // optional "object." prefix name
         String name = matcher.group(2);        // the identifier / property name

         if(!Assembly.FIELD.equals(name) && !Assembly.FIELD.equals(objectName)) {
            Assembly assembly = worksheet.getAssembly(name);

            if((assembly instanceof TableAssembly) && !assembly.equals(source)) {
               references.add(new AssemblyRef(assembly.getAssemblyEntry()));
               continue;
            }
         }

         if("parameter".equals(objectName)) {
            for(Assembly assembly : worksheet.getAssemblies()) {
               if(assembly instanceof VariableAssembly) {
                  UserVariable variable = ((VariableAssembly) assembly).getVariable();

                  if(variable != null && variable.getName().equals(name)) {
                     references.add(new AssemblyRef(assembly.getAssemblyEntry()));
                  }
               }
            }
         }
      }

      return references;
   }

   // Matches an optional "object." qualifier followed by an identifier; both
   // captured (group 1 = object name or null, group 2 = identifier/property).
   private static final Pattern IDENTIFIER = Pattern.compile(
      "(?:([A-Za-z_$][A-Za-z0-9_$]*)\\s*\\.\\s*)?([A-Za-z_$][A-Za-z0-9_$]*)");

   private static final DataCache<String, Set<AssemblyRef>> outputCache =
      new DataCache<>(1000, 5000);
}
