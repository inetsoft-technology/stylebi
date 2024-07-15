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

def dockerBuildVersion = new Date().format('YYYYMMddHHmmss')

pipeline {
  agent {
    ecs {
      inheritFrom 'product-build-java21'
      cpu 4096
      memory 10240
    }
  }

  tools {
    jdk 'java21'
  }

  stages {
    stage('Checkout') {
      steps {
        checkout poll: false, scm: scmGit(branches: [[name: '*/master']], extensions: [submodule(parentCredentials: true, recursiveSubmodules: true, reference: '')], userRemoteConfigs: [[credentialsId: 'buildserver-subversion', url: 'https://repo.inetsoft.com/stylebi/stylebi']])

        withCredentials([usernamePassword(credentialsId: 'github-maven', usernameVariable: 'GH_USERNAME', passwordVariable: 'GH_PASSWORD'), aws(credentialsId: 'build-tasks-aws', accessKeyVariable: 'AWS_ACCESS_KEY_ID', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY')]) {
          script {
            sh 'dependency-cache.sh load "/home/jenkins/workspace/local_cache" "s3://inetsoft-build-cache/Product/Version 14.0/Style BI/cache/deps_cache.tar.gz"'
          }
        }
      }
    }

    stage('Root POM') {
      steps {
        withCredentials([usernamePassword(credentialsId: 'github-maven', usernameVariable: 'GH_USERNAME', passwordVariable: 'GH_PASSWORD'), aws(credentialsId: 'build-tasks-aws', accessKeyVariable: 'AWS_ACCESS_KEY_ID', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY')]) {
          script {
            env.AWS_CODEARTIFACT_AUTH_TOKEN = sh(returnStdout: true, script: 'aws codeartifact get-authorization-token --region us-east-2 --domain maven --domain-owner 636869400126 --query authorizationToken --output text').trim()
            sh './mvnw -B clean deploy -Pcommunity,enterprise -pl . -s /home/jenkins/.m2/settings.xml -DskipTests -DaltReleaseDeploymentRepository=inetsoft::https://maven-636869400126.d.codeartifact.us-east-2.amazonaws.com/maven/releases/ -DaltSnapshotDeploymentRepository=inetsoft::https://maven-636869400126.d.codeartifact.us-east-2.amazonaws.com/maven/snapshots/'
          }
        }
      }
    }

    stage('Build Tools') {
      steps {
        withCredentials([usernamePassword(credentialsId: 'github-maven', usernameVariable: 'GH_USERNAME', passwordVariable: 'GH_PASSWORD'), aws(credentialsId: 'build-tasks-aws', accessKeyVariable: 'AWS_ACCESS_KEY_ID', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY')]) {
          script {
            env.AWS_CODEARTIFACT_AUTH_TOKEN = sh(returnStdout: true, script: 'aws codeartifact get-authorization-token --region us-east-2 --domain maven --domain-owner 636869400126 --query authorizationToken --output text').trim()
            sh './mvnw -B clean deploy -Pcommunity,enterprise -DskipTests -f build-tools -s /home/jenkins/.m2/settings.xml -DaltReleaseDeploymentRepository=inetsoft::https://maven-636869400126.d.codeartifact.us-east-2.amazonaws.com/maven/releases/ -DaltSnapshotDeploymentRepository=inetsoft::https://maven-636869400126.d.codeartifact.us-east-2.amazonaws.com/maven/snapshots/'
          }
        }
      }
    }

    stage('Core') {
//      when {
//        anyOf {
//          changeset 'pom.xml'
//          changeset 'core/**'
//          expression { return currentBuild.previousBuild == null || currentBuild.previousBuild.result != 'SUCCESS' }
//        }
//      }
      steps {
        withCredentials([usernamePassword(credentialsId: 'github-maven', usernameVariable: 'GH_USERNAME', passwordVariable: 'GH_PASSWORD'), aws(credentialsId: 'build-tasks-aws', accessKeyVariable: 'AWS_ACCESS_KEY_ID', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY')]) {
          script {
            env.AWS_CODEARTIFACT_AUTH_TOKEN = sh(returnStdout: true, script: 'aws codeartifact get-authorization-token --region us-east-2 --domain maven --domain-owner 636869400126 --query authorizationToken --output text').trim()
            sh './mvnw -B clean deploy -Pcommunity,enterprise -pl core -s /home/jenkins/.m2/settings.xml -DaltReleaseDeploymentRepository=inetsoft::https://maven-636869400126.d.codeartifact.us-east-2.amazonaws.com/maven/releases/ -DaltSnapshotDeploymentRepository=inetsoft::https://maven-636869400126.d.codeartifact.us-east-2.amazonaws.com/maven/snapshots/'
          }
        }
      }
      post {
        always {
          junit allowEmptyResults: true, keepLongStdio: true, testResults: 'core/target/surefire-reports/*.xml'
        }
      }
    }

    stage('Utils') {
//      when {
//        anyOf {
//          changeset 'pom.xml'
//          changeset 'core/**'
//          changeset 'utils/**'
//          expression { return currentBuild.previousBuild == null || currentBuild.previousBuild.result != 'SUCCESS' }
//        }
//      }
      steps {
        withCredentials([usernamePassword(credentialsId: 'github-maven', usernameVariable: 'GH_USERNAME', passwordVariable: 'GH_PASSWORD'), aws(credentialsId: 'build-tasks-aws', accessKeyVariable: 'AWS_ACCESS_KEY_ID', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY')]) {
          script {
            env.AWS_CODEARTIFACT_AUTH_TOKEN = sh(returnStdout: true, script: 'aws codeartifact get-authorization-token --region us-east-2 --domain maven --domain-owner 636869400126 --query authorizationToken --output text').trim()
            sh './mvnw -B clean deploy -Pcommunity,enterprise -f utils -s /home/jenkins/.m2/settings.xml -DaltReleaseDeploymentRepository=inetsoft::https://maven-636869400126.d.codeartifact.us-east-2.amazonaws.com/maven/releases/ -DaltSnapshotDeploymentRepository=inetsoft::https://maven-636869400126.d.codeartifact.us-east-2.amazonaws.com/maven/snapshots/'
          }
        }
      }
      post {
        always {
          junit allowEmptyResults: true, keepLongStdio: true, testResults: 'utils/**/target/surefire-reports/*.xml'
        }
      }
    }

    stage('Enterprise') {
//      when {
//        anyOf {
//          changeset 'pom.xml'
//          changeset 'core/**'
//          changeset 'enterprise/**'
//          expression { return currentBuild.previousBuild == null || currentBuild.previousBuild.result != 'SUCCESS' }
//        }
//      }
      steps {
        withCredentials([usernamePassword(credentialsId: 'github-maven', usernameVariable: 'GH_USERNAME', passwordVariable: 'GH_PASSWORD'), aws(credentialsId: 'build-tasks-aws', accessKeyVariable: 'AWS_ACCESS_KEY_ID', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY')]) {
          script {
            env.AWS_CODEARTIFACT_AUTH_TOKEN = sh(returnStdout: true, script: 'aws codeartifact get-authorization-token --region us-east-2 --domain maven --domain-owner 636869400126 --query authorizationToken --output text').trim()
            sh './mvnw -B clean deploy -Pcommunity,enterprise -pl enterprise -s /home/jenkins/.m2/settings.xml -DaltReleaseDeploymentRepository=inetsoft::https://maven-636869400126.d.codeartifact.us-east-2.amazonaws.com/maven/releases/ -DaltSnapshotDeploymentRepository=inetsoft::https://maven-636869400126.d.codeartifact.us-east-2.amazonaws.com/maven/snapshots/'
          }
        }
      }
      post {
        always {
          junit allowEmptyResults: true, keepLongStdio: true, testResults: 'enterprise/target/surefire-reports/*.xml'
        }
      }
    }

    stage('Integration') {
//      when {
//        anyOf {
//          changeset 'pom.xml'
//          changeset 'core/**'
//          changeset 'integration/**'
//          expression { return currentBuild.previousBuild == null || currentBuild.previousBuild.result != 'SUCCESS' }
//        }
//      }
      steps {
        withCredentials([usernamePassword(credentialsId: 'github-maven', usernameVariable: 'GH_USERNAME', passwordVariable: 'GH_PASSWORD'), aws(credentialsId: 'build-tasks-aws', accessKeyVariable: 'AWS_ACCESS_KEY_ID', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY')]) {
          script {
            env.AWS_CODEARTIFACT_AUTH_TOKEN = sh(returnStdout: true, script: 'aws codeartifact get-authorization-token --region us-east-2 --domain maven --domain-owner 636869400126 --query authorizationToken --output text').trim()
            sh './mvnw -B clean deploy -Pcommunity,enterprise -f integration -s /home/jenkins/.m2/settings.xml -DaltReleaseDeploymentRepository=inetsoft::https://maven-636869400126.d.codeartifact.us-east-2.amazonaws.com/maven/releases/ -DaltSnapshotDeploymentRepository=inetsoft::https://maven-636869400126.d.codeartifact.us-east-2.amazonaws.com/maven/snapshots/'
          }
        }
      }
      post {
        always {
          junit allowEmptyResults: true, keepLongStdio: true, testResults: 'integration/**/target/surefire-reports/*.xml'
        }
      }
    }

    stage('Shell') {
//      when {
//        anyOf {
//          changeset 'pom.xml'
//          changeset 'core/**'
//          changeset 'shell/**'
//          expression { return currentBuild.previousBuild == null || currentBuild.previousBuild.result != 'SUCCESS' }
//        }
//      }
      steps {
        withCredentials([usernamePassword(credentialsId: 'github-maven', usernameVariable: 'GH_USERNAME', passwordVariable: 'GH_PASSWORD'), aws(credentialsId: 'build-tasks-aws', accessKeyVariable: 'AWS_ACCESS_KEY_ID', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY')]) {
          script {
            env.AWS_CODEARTIFACT_AUTH_TOKEN = sh(returnStdout: true, script: 'aws codeartifact get-authorization-token --region us-east-2 --domain maven --domain-owner 636869400126 --query authorizationToken --output text').trim()
            sh './mvnw -B clean deploy -Pcommunity,enterprise -pl shell -s /home/jenkins/.m2/settings.xml -DaltReleaseDeploymentRepository=inetsoft::https://maven-636869400126.d.codeartifact.us-east-2.amazonaws.com/maven/releases/ -DaltSnapshotDeploymentRepository=inetsoft::https://maven-636869400126.d.codeartifact.us-east-2.amazonaws.com/maven/snapshots/'
          }
        }
      }
      post {
        always {
          junit allowEmptyResults: true, keepLongStdio: true, testResults: 'shell/target/surefire-reports/*.xml'
        }
      }
    }

    stage('Connectors') {
//      when {
//        anyOf {
//          changeset 'pom.xml'
//          changeset 'core/**'
//          changeset 'connectors/**'
//          expression { return currentBuild.previousBuild == null || currentBuild.previousBuild.result != 'SUCCESS' }
//        }
//      }
      steps {
        withCredentials([usernamePassword(credentialsId: 'github-maven', usernameVariable: 'GH_USERNAME', passwordVariable: 'GH_PASSWORD'), aws(credentialsId: 'build-tasks-aws', accessKeyVariable: 'AWS_ACCESS_KEY_ID', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY')]) {
          script {
            env.AWS_CODEARTIFACT_AUTH_TOKEN = sh(returnStdout: true, script: 'aws codeartifact get-authorization-token --region us-east-2 --domain maven --domain-owner 636869400126 --query authorizationToken --output text').trim()
            sh './mvnw -B clean deploy -Pcommunity,enterprise -f connectors -s /home/jenkins/.m2/settings.xml -DaltReleaseDeploymentRepository=inetsoft::https://maven-636869400126.d.codeartifact.us-east-2.amazonaws.com/maven/releases/ -DaltSnapshotDeploymentRepository=inetsoft::https://maven-636869400126.d.codeartifact.us-east-2.amazonaws.com/maven/snapshots/'
          }
        }
      }
      post {
        always {
          junit allowEmptyResults: true, keepLongStdio: true, testResults: 'connectors/**/target/surefire-reports/*.xml'
        }
      }
    }

    stage('Web') {
//      when {
//        anyOf {
//          changeset 'web/**'
//          expression { return currentBuild.previousBuild == null || currentBuild.previousBuild.result != 'SUCCESS' }
//        }
//      }
      environment {
        NODE_OPTIONS = '--max-old-space-size=4096'
      }
      steps {
        withCredentials([usernamePassword(credentialsId: 'github-maven', usernameVariable: 'GH_USERNAME', passwordVariable: 'GH_PASSWORD'), aws(credentialsId: 'build-tasks-aws', accessKeyVariable: 'AWS_ACCESS_KEY_ID', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY')]) {
          script {
            env.AWS_CODEARTIFACT_AUTH_TOKEN = sh(returnStdout: true, script: 'aws codeartifact get-authorization-token --region us-east-2 --domain maven --domain-owner 636869400126 --query authorizationToken --output text').trim()
            sh './mvnw -B clean deploy -Pcommunity,enterprise,production -pl web -DskipTests -s /home/jenkins/.m2/settings.xml -DaltReleaseDeploymentRepository=inetsoft::https://maven-636869400126.d.codeartifact.us-east-2.amazonaws.com/maven/releases/ -DaltSnapshotDeploymentRepository=inetsoft::https://maven-636869400126.d.codeartifact.us-east-2.amazonaws.com/maven/snapshots/'
          }
        }
      }
//       post {
//         always {
//           junit allowEmptyResults: true, keepLongStdio: true, testResults: 'web/junit.xml'
//           recordIssues enabledForFailure: true, sourceCodeRetention: 'NEVER', tools: [tsLint(pattern: 'web/*-lint-results.xml')]
//         }
//       }
    }

    stage('Server') {
//      when {
//        anyOf {
//          changeset 'pom.xml'
//          changeset 'core/**'
//          changeset 'server/**'
//          expression { return currentBuild.previousBuild == null || currentBuild.previousBuild.result != 'SUCCESS' }
//        }
//      }
      steps {
        withCredentials([usernamePassword(credentialsId: 'github-maven', usernameVariable: 'GH_USERNAME', passwordVariable: 'GH_PASSWORD'), aws(credentialsId: 'build-tasks-aws', accessKeyVariable: 'AWS_ACCESS_KEY_ID', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY')]) {
          script {
            env.AWS_CODEARTIFACT_AUTH_TOKEN = sh(returnStdout: true, script: 'aws codeartifact get-authorization-token --region us-east-2 --domain maven --domain-owner 636869400126 --query authorizationToken --output text').trim()
            sh './mvnw -B clean deploy -Pcommunity -pl server -s /home/jenkins/.m2/settings.xml -DaltReleaseDeploymentRepository=inetsoft::https://maven-636869400126.d.codeartifact.us-east-2.amazonaws.com/maven/releases/ -DaltSnapshotDeploymentRepository=inetsoft::https://maven-636869400126.d.codeartifact.us-east-2.amazonaws.com/maven/snapshots/'
          }
        }
      }
      post {
        always {
          junit allowEmptyResults: true, keepLongStdio: true, testResults: 'server/target/surefire-reports/*.xml'
        }
      }
    }

    stage("Docker Images") {
      steps {
        withCredentials([usernamePassword(credentialsId: 'github-maven', usernameVariable: 'GH_USERNAME', passwordVariable: 'GH_PASSWORD'), aws(credentialsId: 'build-tasks-aws', accessKeyVariable: 'AWS_ACCESS_KEY_ID', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY')]) {
          script {
            env.AWS_CODEARTIFACT_AUTH_TOKEN = sh(returnStdout: true, script: 'aws codeartifact get-authorization-token --region us-east-2 --domain maven --domain-owner 636869400126 --query authorizationToken --output text').trim()
            sh "./mvnw -B clean package -DskipTests -Ddocker.build.version=$dockerBuildVersion -Pcommunity -pl docker/community -s /home/jenkins/.m2/settings.xml"
            sh """/kaniko/executor \
              --dockerfile=Dockerfile \
              --context="dir://\$WORKSPACE/docker/community/target/docker" \
              --destination=636869400126.dkr.ecr.us-east-2.amazonaws.com/inetsoft/stylebi-community:14.0.$dockerBuildVersion \
              --destination=636869400126.dkr.ecr.us-east-2.amazonaws.com/inetsoft/stylebi-community:14.0 \
              --cache=true --compressed-caching=false --use-new-run --snapshot-mode=redo"""
            sh "./mvnw -B clean package -DskipTests -Ddocker.build.version=$dockerBuildVersion -Dinetsoft.community.image=636869400126.dkr.ecr.us-east-2.amazonaws.com/inetsoft/stylebi-community:14.0.$dockerBuildVersion -Pcommunity,enterprise -pl docker/enterprise -s /home/jenkins/.m2/settings.xml"
            sh """/kaniko/executor \
              --dockerfile=Dockerfile \
              --context="dir://\$WORKSPACE/docker/enterprise/target/docker" \
              --destination=636869400126.dkr.ecr.us-east-2.amazonaws.com/inetsoft/stylebi-enterprise:14.0.$dockerBuildVersion \
              --destination=636869400126.dkr.ecr.us-east-2.amazonaws.com/inetsoft/stylebi-enterprise:14.0 \
              --cache=true --compressed-caching=false --use-new-run --snapshot-mode=redo"""
          }
        }
      }
    }

    stage("Save Build Cache") {
      steps {
        withCredentials([aws(credentialsId: 'build-tasks-aws', accessKeyVariable: 'AWS_ACCESS_KEY_ID', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY')]) {
          script {
            sh 'dependency-cache.sh save "/home/jenkins/workspace/local_cache" "s3://inetsoft-build-cache/Product/Version 14.0/Style BI/cache/deps_cache.tar.gz" 2684354560'
          }
        }
      }
    }
  }

  post {
    always {
      script {
        if(currentBuild.result == null || currentBuild.result == 'SUCCESS') {
          if(currentBuild.previousBuild != null && currentBuild.previousBuild.result != 'SUCCESS') {
            slackSend(
                    color: 'good',
                    message: "The ${currentBuild.fullDisplayName} build is back to normal. (<${env.BUILD_URL}|Open>)"
            )
            emailext(
                    subject: "Build is back to normal: ${currentBuild.fullDisplayName} - #${env.BUILD_NUMBER}",
                    body: "Check console output at ${env.BUILD_URL} to view the results.",
                    recipientProviders: [
                            [$class: 'CulpritsRecipientProvider'],
                            [$class: 'RequesterRecipientProvider']
                    ],
                    to: 'jason.shobe@inetsoft.com fredaan@inetsoft.com'
            )
          }
        }
        else if(currentBuild.previousBuild == null || currentBuild.previousBuild.result == 'SUCCESS') {
          slackSend(
                  color: 'danger',
                  message: "The ${currentBuild.fullDisplayName} build failed. (<${env.BUILD_URL}|Open>)"
          )
          emailext(
                  subject: "Build failed: ${currentBuild.fullDisplayName} - #${env.BUILD_NUMBER}",
                  body: "Check console output at ${env.BUILD_URL} to view the results.",
                  recipientProviders: [
                          [$class: 'CulpritsRecipientProvider'],
                          [$class: 'RequesterRecipientProvider']
                  ],
                  to: 'jason.shobe@inetsoft.com fredaan@inetsoft.com'
          )
        }
      }
    }
  }
}
