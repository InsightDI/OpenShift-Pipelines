#!groovy

node('maven') {


  echo 'payload=' + payload
  def pld  = readJSON text: payload
  def ref = pld.ref
  def branchName = pld.ref
  def afterCommit = pld.after
  def sourceURL = pld.repository.clone_url

  def gitAuthorName = pld.pusher.name
  def gitAuthorEmail = pld.pusher.email

  
  def ocdevnamespace = "ecu-person-dev"
  def ocqanamespace = "ecu-person-qa"
  def ocprodnamespace = "ecu-person-prod"
  def appname = "person-service";

  def mvnCmd = "mvn"

  stage('Checkout Source') {
    echo "Checking out source"
    checkout([$class: 'GitSCM', branches: [[name: branchName]], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[url: sourceURL]]])
  }
  
  def version=getBuildVersion()
  def newTag = "v${version}-${BUILD_NUMBER}"
  stage('Build Artifact') {
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

  // Blue/Green Deployment into Production
  // -------------------------------------
  def dest   = "${appname}-green"
  def active = ""

  stage('Prepare Blue/Green Switch') {
    sh "oc project ${ocprodnamespace}"
  	active = sh(returnStdout: true, script: "oc get route ${appname} -n ${ocprodnamespace} -o jsonpath='{ .spec.to.name }'")

    if (active == "${appname}-green") {
      dest = "${appname}-blue"
    }

    echo "Active svc: " + active
    echo "Dest svc:   " + dest
  }  

  stage ('Deploy to Prod'){
    input "Deploy version ${newTag} to ${dest}?"
    sh "oc project ${ocprodnamespace}"
   	sh "oc tag ${ocdevnamespace}/${appname}:${newTag} ${ocprodnamespace}/${appname}:${newTag}"	
    sh "oc patch dc ${dest} --patch '{\"spec\": { \"triggers\": [ { \"type\": \"ImageChange\", \"imageChangeParams\": { \"containerNames\": [ \"${dest}\" ], \"from\": { \"kind\": \"ImageStreamTag\", \"namespace\": \"${ocprodnamespace}\", \"name\": \"$appname:$newTag\"}}}]}}' -n ${ocprodnamespace}"
	sh "oc rollout latest dc/${dest}"
    verifyDeployment namespace:ocprodnamespace, dc:dest, verbose:true
  }  

  stage('Switch over to new Version') {
    input "Switch Route to Production (${dest})?"
	sh "oc patch route ${appname} --patch '{\"spec\": {\"port\": {\"targetPort\": \"${dest}\"}, \"to\":{\"name\": \"${dest}\"}}}'"
    newRoute = sh (returnStdout: true, script:"oc get route ${appname} -n ${ocprodnamespace}")
    echo "Current route configuration: " + newRoute
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