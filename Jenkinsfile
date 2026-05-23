pipeline {
  agent any

  environment {
    BASE_INSTANCE_ID  = 'i-0b05b4157183ae641'
    SECURITY_GROUP_ID = 'sg-029e6506bc0ed624b'
    SUBNET_ID         = 'subnet-0dc82a8cf1f36e0f3'
    KEY_NAME          = 'jenkins'
    AWS_REGION        = 'ap-south-1'
    APP_NAME          = 'salary-service'
  }

  stages {
    stage('Branch Check') {
      steps {
        script {
          if (env.BRANCH_NAME != 'main') {
            echo "Feature branch detected: ${env.BRANCH_NAME}"
            echo 'Running CI only — no AMI bake, no deployment'
          }
        }
      }
    }

    stage('Checkout') {
      steps {
        checkout scm
      }
    }

    stage('Build Backend') {
      steps {
        sh 'mvn clean package -DskipTests'
        echo 'Backend build complete.'
      }
    }

    stage('Bake AMI') {
      when { branch 'main' }
      steps {
        script {
          echo 'Copying Spring Boot JAR to base-app-server...'

          def baseIp = sh(
            script: "aws ec2 describe-instances --instance-ids ${BASE_INSTANCE_ID} --region ${AWS_REGION} --query 'Reservations[0].Instances[0].PrivateIpAddress' --output text",
            returnStdout: true
          ).trim()

          // Copy Spring Boot JAR to the base server using jenkins.pem
          sh """
            scp -i /var/lib/jenkins/.ssh/jenkins.pem \
              -o StrictHostKeyChecking=no \
              target/*.jar \
              ubuntu@${baseIp}:/opt/attendance-backend/app.jar
          """

          def ts  = sh(script: 'date +%Y%m%d%H%M%S', returnStdout: true).trim()
          def amiName = "${APP_NAME}-${ts}"
          echo "Creating AMI: ${amiName}"

          sh "aws ec2 stop-instances --instance-ids ${BASE_INSTANCE_ID} --region ${AWS_REGION}"
          sh "aws ec2 wait instance-stopped --instance-ids ${BASE_INSTANCE_ID} --region ${AWS_REGION}"

          def amiId = sh(
            script: "aws ec2 create-image --instance-id ${BASE_INSTANCE_ID} --name '${amiName}' --no-reboot --region ${AWS_REGION} --query 'ImageId' --output text",
            returnStdout: true
          ).trim()

          env.AMI_ID = amiId
          echo "Waiting for AMI ${amiId} to become available..."
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
          env.DEV_IP = devIp
          echo "✅ DEV deployed: http://${devIp}/api/v1/salary"
        }
      }
    }

    stage('Approve QA') {
      when { branch 'main' }
      steps {
        input message: "DEV verified at http://${env.DEV_IP}/api/v1/salary. Promote same AMI to QA?", ok: 'Promote to QA'
      }
    }

    stage('Deploy QA') {
      when { branch 'main' }
      steps {
        script {
          def qaId = sh(
            script: "aws ec2 run-instances --image-id ${env.AMI_ID} --instance-type t2.micro --key-name ${KEY_NAME} --security-group-ids ${SECURITY_GROUP_ID} --subnet-id ${SUBNET_ID} --associate-public-ip-address --region ${AWS_REGION} --tag-specifications 'ResourceType=instance,Tags=[{Key=Name,Value=salary-QA},{Key=Environment,Value=QA},{Key=AMI,Value=${env.AMI_ID}}]' --query 'Instances[0].InstanceId' --output text",
            returnStdout: true
          ).trim()

          sh "aws ec2 wait instance-running --instance-ids ${qaId} --region ${AWS_REGION}"
          def qaIp = sh(script: "aws ec2 describe-instances --instance-ids ${qaId} --region ${AWS_REGION} --query 'Reservations[0].Instances[0].PublicIpAddress' --output text", returnStdout: true).trim()
          env.QA_IP = qaIp
          echo "✅ QA deployed: http://${qaIp}/api/v1/salary"
        }
      }
    }
  }
}
