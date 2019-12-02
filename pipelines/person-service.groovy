#!groovy

node('maven') {

  def ocdevnamespace = "ecu-person-dev"
  def ocqanamespace = "person-service-qa"
  def appname = "person-service";

  def mvnCmd = "mvn"

  stage('Checkout Source') {
    echo "Checking out source"
    checkout([$class: 'GitSCM', branches: [[name: '*/master']], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[url: 'https://github.com/InsightDI/GCDJavaReference.git']]])
  }
  
  def version=getBuildVersion()
  def newTag = "PersonService-${version}:${BUILD_NUMBER}"
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
    input 'stop'
    sh "oc start-build ${appname} --follow --from-file=./target/ROOT.war -n ${ocdevnamespace}"
    openshiftTag alias: 'false', destStream: appname, destTag: newTag, destinationNamespace: ocdevnamespace, namespace: ocdevnamespace, srcStream: appname, srcTag: 'latest', verbose: 'false'
  }   
  
}


def getBuildVersion(){
    def pom = readMavenPom  file: "pom.xml"
    return  pom.getVersion()
}