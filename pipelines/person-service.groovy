#!groovy

node('maven') {

  def ocdevnamespace = "ecu-person-dev"
  def ocqanamespace = "ecu-person-qa"
  def appname = "person-service";

  def mvnCmd = "mvn"

  stage('Checkout Source') {
    echo "Checking out source"
    checkout([$class: 'GitSCM', branches: [[name: '*/master']], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[url: 'https://github.com/InsightDI/GCDJavaReference.git']]])
  }
  
  def version=getBuildVersion()
  def newTag = "v${version}-${BUILD_NUMBER}"
  stage('Build artifact') {
    echo "Building version ${version}"
	sh "mvn clean package -DskipTests"
  }
  
  stage ('Run Unit Tests'){
    sh "mvn test"
  }
  
  stage('Build OpenShift Image') {
    echo "New Tag: ${newTag}"
    sh "oc project ${ocdevnamespace}"
    sh "oc start-build ${appname} --follow --from-file=./target/person-${version}.jar -n ${ocdevnamespace}"
   	sh "oc tag ${ocdevnamespace}/${appname}:latest ${ocdevnamespace}/person-service:${newTag}"	
  }   

  stage ('Deploy to Dev'){
    sh "oc patch dc ${appname} --patch '{\"spec\": { \"triggers\": [ { \"type\": \"ImageChange\", \"imageChangeParams\": { \"containerNames\": [ \"${appname}\" ], \"from\": { \"kind\": \"ImageStreamTag\", \"namespace\": \"${ocdevnamespace}\", \"name\": \"$appname:$newTag\"}}}]}}' -n ${ocdevnamespace}"
	sh "oc rollout latest dc/${appname}"
    verifyDeployment namespace:ocdevnamespace, dc:appname, verbose:true
  }
  
  stage ('Integration Tests'){
    echo 'Run integration tests'
  }

  stage ('Deploy to QA'){
    input "Deploy version ${newTag} to QA?"
    sh "oc project ${ocqanamespace}"
   	sh "oc tag ${ocdevnamespace}/${appname}:${newTag} ${ocqanamespace}/${appname}:${newTag}"	
    sh "oc patch dc ${appname} --patch '{\"spec\": { \"triggers\": [ { \"type\": \"ImageChange\", \"imageChangeParams\": { \"containerNames\": [ \"${appname}\" ], \"from\": { \"kind\": \"ImageStreamTag\", \"namespace\": \"${ocqanamespace}\", \"name\": \"$appname:$newTag\"}}}]}}' -n ${ocqanamespace}"
	sh "oc rollout latest dc/${appname}"
    verifyDeployment namespace:ocqanamespace, dc:appname, verbose:true
  }
}


def getBuildVersion(){
    def pom = readMavenPom  file: "pom.xml"
    return  pom.getVersion()
}

def verifyDeployment(Map config){
	def namespace = config.namespace
    def dcName = config.dc
	def verbose = config.verbose ?: false

	echo "Verifying deployment for ${namespace}"
	sh "oc project ${namespace}"

	// Get the dc and pull the latest version
	def dcStr = sh script:"oc get dc ${dcName} -o yaml", returnStdout:true
	def dcYaml = readYaml text:dcStr
	def latestVersion = dcYaml.status.latestVersion

	// Limit the checking of replica counts to 10 minutes.  Can't wait forever.
	timeout(time: 10, unit: 'MINUTES'){
		waitUntil{
			// Get the replication controller for the last build
			def rcStr = sh script:"oc get rc ${dcName}-${dcYaml.status.latestVersion} -o yaml", returnStdout:true
			def rcYaml = readYaml text:rcStr
			def replicas = rcYaml.status.replicas
			def readyReplicas = rcYaml.status.readyReplicas
			if (verbose){
				echo "Replicas: ${replicas} Ready Replicas: ${readyReplicas}"
			}
			// Check replica counts
			if (replicas != null && readyReplicas != null && replicas.equals(readyReplicas)){
				return true
			}
			return false
		}
	}
}