properties([pipelineTriggers([githubPush()])])

pipeline {
  options {
    disableConcurrentBuilds()
    buildDiscarder(logRotator(numToKeepStr: '10'))
    timeout(time: 30, unit: 'MINUTES')
  }

  agent {
    kubernetes {
      label 'worker-dependency'
      inheritFrom 'generic'

      containerTemplates([
        containerTemplate(name: 'postgres', image: "flowcommerce/dependency-postgresql:latest", alwaysPullImage: true, resourceRequestMemory: '1Gi'),
        containerTemplate(name: 'play', image: "flowdocker/play_builder:latest-java13", alwaysPullImage: true, resourceRequestMemory: '2Gi', command: 'cat', ttyEnabled: true)
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
          expression {
            return changesCheck.hasChangesInDir('deploy')
          }          
        }
      }
      steps {
        script {
          container('helm') {
            if(changesCheck.hasChangesInDir('deploy/dependency-api')){
              new helmDiff().diff('dependency-api')
            }
            if(changesCheck.hasChangesInDir('deploy/dependency-www')){
              new helmDiff().diff('dependency-www')
            }
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
                sh '''
                  echo "$(date) - waiting for database to start"
                  until pg_isready -h localhost
                  do
                    sleep 10
                  done
                  sbt clean flowLint test doc
                '''
                junit allowEmptyResults: true, testResults: '**/target/test-reports/*.xml'
              }
            }   
          }
        }
        stage('Build and Deploy dependency-api') {
          when { branch 'main'}
          stages {
            stage('Build and push docker image release') {
              steps {
                container('docker') {
                  script {
                    semver = VERSION.printable()
                    
                    docker.withRegistry('https://index.docker.io/v1/', 'jenkins-dockerhub') {
                      db = docker.build("$ORG/dependency-api:$semver", '--network=host -f api/Dockerfile .')
                      db.push()
                    }

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
              steps {
                container('docker') {
                  script {
                    semver = VERSION.printable()
                    
                    docker.withRegistry('https://index.docker.io/v1/', 'jenkins-dockerhub') {
                      db = docker.build("$ORG/dependency-www:$semver", '--network=host -f www/Dockerfile .')
                      db.push()
                    }
                
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
