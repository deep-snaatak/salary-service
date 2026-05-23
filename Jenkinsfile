pipeline {
  agent any

  tools {
    jdk 'jdk17'
    maven 'maven3'
  }

  environment {
    JAVA_HOME = "${tool 'jdk17'}"
    BASE_INSTANCE_ID  = 'i-0b05b4157183ae641'
    SECURITY_GROUP_ID = 'sg-029e6506bc0ed624b'
    SUBNET_ID         = 'subnet-0dc82a8cf1f36e0f3'
    KEY_NAME          = 'jenkins'
    AWS_REGION        = 'ap-south-1'
    APP_NAME          = 'salary-service'
  }

  stages {
    stage('Checkout') {
      steps {
        checkout scm
      }
    }

    stage('Build Backend') {
      steps {
        // Force the use of the JDK 17 binary explicitly
        sh '''
          export PATH=$JAVA_HOME/bin:$PATH
          mvn clean package -DskipTests
        '''
        echo 'Backend build complete.'
      }
    }

    stage('Bake AMI') {
      when { branch 'main' }
      steps {
        script {
          echo 'Copying Spring Boot JAR to base-server...'

          def baseIp = sh(
            script: "aws ec2 describe-instances --instance-ids ${BASE_INSTANCE_ID} --region ${AWS_REGION} --query 'Reservations[0].Instances[0].PrivateIpAddress' --output text",
            returnStdout: true
          ).trim()

          // Strict identity and host-key bypassing for automated environments
          sh """
            scp -i /var/lib/jenkins/.ssh/jenkins.pem \
              -o UserKnownHostsFile=/dev/null \
              -o StrictHostKeyChecking=no \
              target/*.jar \
              ubuntu@${baseIp}:/opt/attendance-backend/app.jar
          """

          def ts  = sh(script: 'date +%Y%m%d%H%M%S', returnStdout: true).trim()
          sh "aws ec2 stop-instances --instance-ids ${BASE_INSTANCE_ID} --region ${AWS_REGION}"
          sh "aws ec2 wait instance-stopped --instance-ids ${BASE_INSTANCE_ID} --region ${AWS_REGION}"

          def amiId = sh(
            script: "aws ec2 create-image --instance-id ${BASE_INSTANCE_ID} --name '${APP_NAME}-${ts}' --no-reboot --region ${AWS_REGION} --query 'ImageId' --output text",
            returnStdout: true
          ).trim()

          env.AMI_ID = amiId
          sh "aws ec2 wait image-available --image-ids ${amiId} --region ${AWS_REGION}"
          sh "aws ec2 start-instances --instance-ids ${BASE_INSTANCE_ID} --region ${AWS_REGION}"
        }
      }
    }

    stage('Deploy DEV') {
      when { branch 'main' }
      steps {
        script {
          def devId = sh(
            script: "aws ec2 run-instances --image-id ${env.AMI_ID} --instance-type t2.micro --key-name ${KEY_NAME} --security-group-ids ${SECURITY_GROUP_ID} --subnet-id ${SUBNET_ID} --associate-public-ip-address --region ${AWS_REGION} --tag-specifications 'ResourceType=instance,Tags=[{Key=Name,Value=salary-DEV},{Key=Environment,Value=DEV},{Key=AMI,Value=${env.AMI_ID}}]' --query 'Instances[0].InstanceId' --output text",
            returnStdout: true
          ).trim()

          env.DEV_INSTANCE_ID = devId
          sh "aws ec2 wait instance-running --instance-ids ${devId} --region ${AWS_REGION}"
          
          def devIp = sh(script: "aws ec2 describe-instances --instance-ids ${devId} --region ${AWS_REGION} --query 'Reservations[0].Instances[0].PublicIpAddress' --output text", returnStdout: true).trim()
          echo "✅ DEV deployed: http://${devIp}/api/v1/salary"
        }
      }
    }
  }
}
