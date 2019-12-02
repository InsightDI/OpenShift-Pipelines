#!groovy

node('maven') {

  def ocdevnamespace = "person-service-dev"
  def ocqanamespace = "person-service-qa"
  def appname = "person-service";

  def mvnCmd = "mvn"

  stage('Checkout Source') {
    echo "Checking out source"
    checkout([$class: 'GitSCM', branches: [[name: '*/master']], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[url: 'https://github.com/InsightDI/GCDJavaReference.git']]])
  }
  
  def version=getBuildVersion()
  stage('Build artifact') {
    echo "Building version ${version}"
	sh "mvn clean package -DskipTests"
  }
  
  stage ('Run Unit Tests'){
    sh "mvn test"
  }
  
  
}


def getBuildVersion(){
    def pom = readMavenPom  file: "pom.xml"
    return  pom.getVersion()
}