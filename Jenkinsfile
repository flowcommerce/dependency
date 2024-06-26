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
    stage("All in parallel") {
      parallel {
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
        stage('Build and Deploy dependency-api') {
          when { branch 'main'}
          stages {
            stage('Build and push docker image release') {
              steps {
                container('kaniko') {
                  script {
                    semver = VERSION.printable()
                    
                    sh """
                      /kaniko/executor -f `pwd`/api/Dockerfile -c `pwd` \
                      --snapshot-mode=redo --use-new-run  \
                      --destination ${env.ORG}/dependency-api:$semver
                    """
                  }
                }
              }
            }
            stage('deploy dependency-api') {
              steps {
                script {
                  container('helm') {
                    new helmCommonDeploy().deploy('dependency-api', 'production', VERSION.printable())
                  }
                }
              }
            }
          }
        }
        stage('Build and Deploy dependency-www') {
          when { branch 'main'}
          stages {
            stage('Build and push docker image release') {
              agent {
                label 'builder-1'
              }
              steps {
                container('kaniko') {
                  script {
                    semver = VERSION.printable()
                    
                    sh """
                      /kaniko/executor -f `pwd`/www/Dockerfile -c `pwd` \
                      --snapshot-mode=redo --use-new-run  \
                      --destination ${env.ORG}/dependency-www:$semver
                    """
                  }
                
                }
              }
            }
            
            stage('deploy dependency-www') {
              steps {
                script {
                  container('helm') {
                    new helmCommonDeploy().deploy('dependency-www', 'production', VERSION.printable())
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}
