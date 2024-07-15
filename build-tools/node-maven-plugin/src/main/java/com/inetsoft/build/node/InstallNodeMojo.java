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

package com.inetsoft.build.node;

import com.github.eirslett.maven.plugins.frontend.lib.*;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.*;

import java.io.File;
import java.util.Collections;

@Mojo(name = "install-node", defaultPhase = LifecyclePhase.GENERATE_RESOURCES, threadSafe = true)
public class InstallNodeMojo extends AbstractMojo {

   /**
    * The version of Node.js to install. IMPORTANT! Most Node.js version names start with 'v', for
    * example 'v0.10.18'
    */
   @Parameter(property="nodeVersion", required = true)
   private String nodeVersion;

   /**
    * The version of NPM to install.
    */
   @Parameter(property = "npmVersion", defaultValue = "provided")
   private String npmVersion;

   /**
    * The base directory for running all Node commands. (Usually the directory that contains
    * package.json)
    */
   @Parameter(defaultValue = "${basedir}", property = "workingDirectory")
   private File workingDirectory;

   /**
    * The base directory for installing node and npm.
    */
   @Parameter(property = "installDirectory")
   private File installDirectory;

   @Override
   public void execute() throws MojoFailureException {
      if(installDirectory == null) {
         installDirectory = workingDirectory;
      }

      FrontendPluginFactory factory = new FrontendPluginFactory(workingDirectory, installDirectory);
      ProxyConfig proxyConfig = new ProxyConfig(Collections.emptyList());

      try {
         factory.getNodeInstaller(proxyConfig)
            .setNodeVersion(nodeVersion)
            .setNpmVersion(npmVersion)
            .install();
         factory.getNPMInstaller(proxyConfig)
            .setNodeVersion(nodeVersion)
            .setNpmVersion(npmVersion)
            .install();
      }
      catch(InstallationException e) {
         throw new MojoFailureException("Failed to install Node " + nodeVersion, e);
      }
   }
}
