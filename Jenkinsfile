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
      inheritFrom 'default'

      containerTemplates([
        containerTemplate(name: 'helm', image: "flowcommerce/k8s-build-helm2:0.0.50", command: 'cat', ttyEnabled: true),
        containerTemplate(name: 'docker', image: 'docker:18', resourceRequestCpu: '1', resourceRequestMemory: '2Gi', command: 'cat', ttyEnabled: true)
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

    stage('Build and push docker image release') {
      when { branch 'main' }
      steps {
        container('docker') {
          script {
            semver = VERSION.printable()
            
            docker.withRegistry('https://index.docker.io/v1/', 'jenkins-dockerhub') {
              db = docker.build("$ORG/dependency-api:$semver", '--network=host -f api/Dockerfile .')
              db.push()
            }
            
            docker.withRegistry('https://index.docker.io/v1/', 'jenkins-dockerhub') {
              db = docker.build("$ORG/dependency-www:$semver", '--network=host -f www/Dockerfile .')
              db.push()
            }
            
          }
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

    stage('Deploy Helm chart') {
      when { branch 'main' }
      parallel {
        
        stage('deploy dependency-api') {
          steps {
            script {
              container('helm') {
                new helmCommonDeploy().deploy('dependency-api', 'production', VERSION.printable())
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
