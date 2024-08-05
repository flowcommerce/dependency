properties([pipelineTriggers([githubPush()])])

pipeline {
  options {
    disableConcurrentBuilds()
    buildDiscarder(logRotator(numToKeepStr: '10'))
    timeout(time: 30, unit: 'MINUTES')
  }

  agent {
    kubernetes {
      inheritFrom 'kaniko-slim'

      containerTemplates([
        containerTemplate(name: 'postgres', image: "flowcommerce/dependency-postgresql:latest-pg15", alwaysPullImage: true, resourceRequestMemory: '1Gi'),
        containerTemplate(name: 'play', image: "flowdocker/play_builder:latest-java17", alwaysPullImage: true, resourceRequestMemory: '2Gi', command: 'cat', ttyEnabled: true)
      ])
    }
  }

  environment {
    ORG      = 'flowcommerce'
  }

  stages {
    stage('Checkout') {
      steps {
        checkoutWithTags scm

        script {
          VERSION = new flowSemver().calculateSemver() //requires checkout
        }
      }
    }

    stage('Commit SemVer tag') {
      when { branch 'main' }
      steps {
        script {
          new flowSemver().commitSemver(VERSION)
        }
      }
    }

    stage('Display Helm Diff') {
      when {
        allOf {
          not {branch 'main'}
          changeRequest()
        }
      }
      steps {
        script {
          container('helm') {
            helmCommonDiff(['dependency-api', 'dependency-www'])
          }
        }
      }
    }
    stage("Build, deploy, SBT test") {
      stages {
        stage('Build dependency-api and depencdency-www services') {
          when { branch 'main'}
          stages {
            stage('Build and push docker images') {
              stages {
                stage('Parallel image builds') {
                  parallel {
                    stage("Build x86_64/amd64 dependency-api") {
                      steps {
                        container('kaniko') {
                          script {
                            String semversion = VERSION.printable()
                            imageBuild(
                              orgName: 'flowcommerce',
                              serviceName: 'dependency-api',
                              platform: 'amd64',
                              dockerfilePath: '/api/Dockerfile',
                              semver: semversion
                            )
                          }
                        }
                      }
                    } 
                    // create new agent to avoid conflicts with the main pipeline agent
                    stage("Build x86_64/amd64 dependency-www") {
                      agent {
                          kubernetes {
                              label 'dependency-www-amd64'
                              inheritFrom 'kaniko-slim'
                          }
                      }
                      steps {
                        container('kaniko') {
                          script {
                            String semversion = VERSION.printable()
                            imageBuild(
                              orgName: 'flowcommerce',
                              serviceName: 'dependency-www',
                              platform: 'amd64',
                              dockerfilePath: '/www/Dockerfile',
                              semver: semversion
                            )
                          }
                        }
                      }
                    }
                    stage("Build arm64 dependency-api") {
                      agent {
                        kubernetes {
                          label 'dependency-api-arm64'
                          inheritFrom 'kaniko-slim-arm64'
                        }
                      }
                      steps {
                        container('kaniko') {
                          script {
                            String semversion = VERSION.printable()
                            imageBuild(
                              orgName: 'flowcommerce',
                              serviceName: 'dependency-api',
                              platform: 'arm64',
                              dockerfilePath: '/api/Dockerfile',
                              semver: semversion
                            )
                          }
                        }
                      }
                    }
                    stage("Build arm64 dependency-www") {
                      agent {
                        kubernetes {
                          label 'dependency-www-arm64'
                          inheritFrom 'kaniko-slim-arm64'
                        }
                      }
                      steps {
                        container('kaniko') {
                          script {
                            String semversion = VERSION.printable()
                            imageBuild(
                              orgName: 'flowcommerce',
                              serviceName: 'dependency-www',
                              platform: 'arm64',
                              dockerfilePath: '/www/Dockerfile',
                              semver: semversion
                            )
                          }
                        }
                      }
                    }  
                  }
                }
              }
            }
            stage('run manifest tool for dependency-api') {
              steps {
                container('kaniko') {
                  script {
                    semver = VERSION.printable()
                    String templateName = "dependency-api-ARCH:${semver}"
                    String targetName = "dependency-api:${semver}"
                    String orgName = "flowcommerce"
                    String jenkinsAgentArch = "amd64"
                    manifestTool(templateName, targetName, orgName, jenkinsAgentArch)
                  }
                }
              }
            }
        
            stage('run manifest tool for dependency-www') {
              steps {
                container('kaniko') {
                  script {
                    semver = VERSION.printable()
                    String templateName = "dependency-www-ARCH:${semver}"
                    String targetName = "dependency-www:${semver}"
                    String orgName = "flowcommerce"
                    String jenkinsAgentArch = "amd64"
                    manifestTool(templateName, targetName, orgName, jenkinsAgentArch)
                  }
                }
              }
            }
          }
        }
        
        stage('Deploy dependency servcies') {
          when { branch 'main' }
          stages {
            stage('Deploy dependency-api and dependency-www services') {
              parallel {
                stage('Deploy dependency-api service') {
                  steps {
                    script {
                      container('helm') {
                        new helmCommonDeploy().deploy('dependency-api', 'production', VERSION.printable(), 600)
                      }
                    }
                  }
                }
                stage('Deploy dependency-www service') {
                  steps {
                    script {
                      container('helm') {
                        new helmCommonDeploy().deploy('dependency-www', 'production', VERSION.printable(), 600)
                      }
                    }
                  }
                }
              }
            }
          }
        }

        stage('SBT Test') {
          steps {
            container('play') {
              script {
                try {
                  sh '''
                    echo "$(date) - waiting for database to start"
                    until pg_isready -h localhost
                    do
                      sleep 10
                    done
                  '''
                  sh 'sbt clean flowLint coverage test scalafmtSbtCheck scalafmtCheck doc'
                  sh 'sbt coverageAggregate'
                }
                finally {
                  postSbtReport()
                }
              }
            }
          }
        }
      }
    }
  }
}
