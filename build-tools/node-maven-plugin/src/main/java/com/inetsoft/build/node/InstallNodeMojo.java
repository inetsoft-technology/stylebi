/*
 * This file is part of StyleBI.
 *
 * Copyright (c) 2024, InetSoft Technology Corp, All Rights Reserved.
 *
 * The software and information contained herein are copyrighted and
 * proprietary to InetSoft Technology Corp. This software is furnished
 * pursuant to a written license agreement and may be used, copied,
 * transmitted, and stored only in accordance with the terms of such
 * license and with the inclusion of the above copyright notice. Please
 * refer to the file "COPYRIGHT" for further copyright and licensing
 * information. This software and information or any other copies
 * thereof may not be provided or otherwise made available to any other
 * person.
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
