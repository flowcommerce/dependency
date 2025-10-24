@Library('lib-jenkins-pipeline') _


def newTagEveryRunMainBranch = "yes" // Force a new version and deploy clicking on Build Now in Jenkins
def sbtOnMain = "yes"
def sbtCommand = "sbt clean flowLint coverage test scalafmtSbtCheck scalafmtCheck doc && sbt coverageAggregate"


// we can remove the pod_template block if we end up having only one template
// in jenkins config
//
String podLabel = "Jenkinsfile-dependency"
podTemplate(
  label: "${podLabel}",
  inheritFrom : 'generic'
){
  node(podLabel) {
    withEnv([
        'ORG=flowcommerce'
    ]) {
        try {
        checkoutWithTags scm
        //Checkout the code from the repository
        stage('Checkout') {
            echo "Checking out branch: ${env.BRANCH_NAME}"
            checkout scm
        }

        // => tagging function to identify what actions to take depending on the nature of the changes
        stage ('tagging') {
            semversion = taggingv2(newTagEveryMainRun: "${newTagEveryRunMainBranch}")
            println(semversion)
        }

        // => Running the actions for each component in parallel
        checkoutWithTags scm

        String jsondata = '''
        [{"serviceName": "dependency-api",
        "dockerImageName": "dependency-api",
        "dockerFilePath" : "/api/Dockerfile"},
        {"serviceName": "dependency-www",
        "dockerImageName": "dependency-www",
        "dockerFilePath" : "/www/Dockerfile"}]
        '''
        withCredentials([string(credentialsId: "jenkins-argocd-token", variable: 'ARGOCD_AUTH_TOKEN')]) {
            mainJenkinsBuildArgo(
            semversion: "${semversion}",
            pgImage: "flowcommerce/dependency-postgresql:latest",
            componentargs: "${jsondata}",
            sbtOnMain: "${sbtOnMain}",
            sbtCommand: "${sbtCommand}"
            )
        }

        } catch (Exception e) {
            // In case of an error, mark the build as failure
            currentBuild.result = 'FAILURE'
            throw e
        } finally {
            // Always clean up workspace and notify if needed
            cleanWs()
            echo "Pipeline execution finished"
        }
    }
  }
}