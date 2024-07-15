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
package com.inetsoft.build.antlr;

import antlr.Tool;
import org.apache.maven.plugin.*;
import org.apache.maven.plugins.annotations.*;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.compiler.util.scan.*;
import org.codehaus.plexus.compiler.util.scan.mapping.SourceMapping;
import org.codehaus.plexus.compiler.util.scan.mapping.SuffixMapping;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Generate Java sources from ANTLR 2 grammar files.
 */
@Mojo(name = "antlr2", defaultPhase = LifecyclePhase.GENERATE_SOURCES, threadSafe = true)
public class Antlr2Mojo extends AbstractMojo {
   /**
    * The current maven project.
    */
   @Parameter(property = "project", required = true, readonly = true)
   private MavenProject project;

   /**
    * The directory where the ANTLR grammar files ({@code *.g}) are located.
    */
   @Parameter(defaultValue = "${basedir}/src/main/antlr2")
   private File sourceDirectory;

   /**
    * The directory where the Java files are generated.
    */
   @Parameter(defaultValue = "${project.build.directory}/generated-sources/antlr2")
   private File outputDirectory;

   /**
    * A set of Ant-like inclusion patterns used to select files from the source directory for
    * processing. By default, the pattern {@code **&#47;*.g} is used to select grammar files.
    */
   @Parameter
   protected Set<String> includes = new HashSet<>();

   /**
    * A set of Ant-like exclusion patterns used to prevent certain files from being processed. By
    * default, this set is empty such that no files are excluded.
    */
   @Parameter
   protected Set<String> excludes = new HashSet<>();

   public File getOutputDirectory() {
      return outputDirectory;
   }

   public Set<String> getIncludesPatterns() {
      if(includes == null || includes.isEmpty()) {
         return Collections.singleton("**/*.g");
      }

      return includes;
   }

   @Override
   public void execute() throws MojoExecutionException, MojoFailureException {
      System.setProperty("ANTLR_DO_NOT_EXIT", "true");

      if(!sourceDirectory.isDirectory()) {
         getLog().info("No ANTLR 2 grammars to compile in " + sourceDirectory.getAbsolutePath());
         return;
      }

      File outputDir = getOutputDirectory();

      if(!outputDir.exists()) {
         outputDir.mkdirs();
      }

      Set<File> grammarFiles;

      try {
         grammarFiles = getGrammarFiles(sourceDirectory);
      }
      catch(Exception e) {
         getLog().error(e);
         throw new MojoExecutionException("Fatal error occurred while evaluating the names of the grammar files to analyze", e);
      }

      for(File grammarFile : grammarFiles) {
         Set<File> javaFiles = getOutputFiles(sourceDirectory, grammarFile, outputDir);
         boolean outOfDate = javaFiles.stream()
            .anyMatch(f -> !f.exists() || grammarFile.lastModified() > f.lastModified());
         File parentDir = javaFiles.stream()
            .findFirst()
            .map(File::getParentFile)
            .orElse(null);

         if(outOfDate && parentDir != null) {
            if(!parentDir.exists()) {
               parentDir.mkdirs();
            }

            String[] args = { "-o", parentDir.getAbsolutePath(), grammarFile.getAbsolutePath() };
            Tool.main(args);
         }
      }

      if(project != null) {
         addSourceRoot(getOutputDirectory());
      }
   }

   void addSourceRoot(File outputDir) {
      project.addCompileSourceRoot(outputDir.getPath());
   }

   private Set<File> getGrammarFiles(File sourceDirectory) throws InclusionScanException {
      SourceMapping mapping = new SuffixMapping("g", Collections.emptySet());
      Set<String> includes = getIncludesPatterns();
      SourceInclusionScanner scan = new SimpleSourceInclusionScanner(includes, excludes);
      scan.addSourceMapping(mapping);
      return scan.getIncludedSources(sourceDirectory, null);
   }

   private Set<File> getOutputFiles(File sourceDirectory, File inputFile, File outputDir)
      throws MojoExecutionException
   {
      Path sourcePath = sourceDirectory.toPath().toAbsolutePath();
      Path inputPath = inputFile.toPath().toAbsolutePath();
      Path outputPath = outputDir.toPath().toAbsolutePath();
      Path outputParent = outputPath.resolve(sourcePath.relativize(inputPath)).getParent();

      List<String> generatedFilenames = new ArrayList<>();

      try(BufferedReader reader = Files.newBufferedReader(inputPath)) {
         String line;

         while((line = reader.readLine()) != null) {
            int extendsIndex = line.indexOf(" extends ");

            if(line.startsWith("class ") && extendsIndex >= 0) {
               generatedFilenames.add(line.substring(6, extendsIndex).trim() + ".java");
            }
         }
      }
      catch(Exception e) {
         throw new MojoExecutionException("Fatal error occurred while evaluating the names of the Java files to generate", e);
      }

      return generatedFilenames.stream()
         .map(outputParent::resolve)
         .map(Path::toFile)
         .collect(Collectors.toSet());
   }
}
