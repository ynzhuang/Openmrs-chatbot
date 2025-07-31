OpenMRS Chatbot Module
Overview
This repository contains the openmrschatbot module for OpenMRS, designed to enhance healthcare informatics with chatbot functionality. The module is built for OpenMRS Platform 2.5.9 and uses a MySQL 8.0 database running in a Docker container. This README provides setup instructions for macOS developers to run OpenMRS and the openmrschatbot module locally.
Prerequisites
Ensure the following tools are installed on your Mac:
1. Install Homebrew
Homebrew is a package manager for macOS.
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
echo 'eval "$(/opt/homebrew/bin/brew shellenv)"' >> ~/.zprofile
eval "$(/opt/homebrew/bin/brew shellenv)"
brew --version

2. Install Java 11
OpenMRS 2.5.9 is compatible with Java 11 (preferred over Java 8 for this setup).
brew install --cask temurin@11
echo 'export JAVA_HOME=$(/usr/libexec/java_home -v 11)' >> ~/.zshrc
source ~/.zshrc
java -version

3. Install Docker Desktop
Docker runs the MySQL database for OpenMRS.

Download Docker Desktop: Docker Desktop for Mac
Install the .dmg file and start Docker Desktop.
Verify:docker --version



4. Install Maven
Maven builds the openmrschatbot module.
brew install maven
mvn --version

5. Install Git
Git for version control.
brew install git
git --version

6. Install IntelliJ IDEA (Optional)
Recommended for Java development.
brew install --cask intellij-idea-ce

Setup Instructions
1. Clone the Repository
git clone https://github.com/your-username/openmrschatbot.git
cd openmrschatbot

2. Set Up MySQL Database
Run a MySQL 8.0 container for the chatbot database.
docker run --name openmrs-sdk-mysql-v8-4-1 -p 3308:3306 -e MYSQL_ROOT_PASSWORD=openmrs -e MYSQL_USER=openmrs -e MYSQL_PASSWORD=openmrs -e MYSQL_DATABASE=chatbot -d mysql:8

Grant privileges:
docker exec -it openmrs-sdk-mysql-v8-4-1 mysql -u root -p

CREATE DATABASE chatbot;
GRANT ALL PRIVILEGES ON chatbot.* TO 'openmrs'@'172.17.0.1' IDENTIFIED BY 'openmrs';
GRANT ALL PRIVILEGES ON chatbot.* TO 'openmrs'@'%' IDENTIFIED BY 'openmrs';
FLUSH PRIVILEGES;
EXIT;

Verify connection:
mysql -u openmrs -p -h 127.0.0.1 -P 3308 --protocol=TCP chatbot

3. Set Up OpenMRS Environment
Download and configure OpenMRS 2.5.9:

Create OpenMRS Server Directory:
mkdir -p ~/openmrs/server4
cd ~/openmrs/server4


Download Dependencies:
wget https://repo1.maven.org/maven2/mysql/mysql-connector-java/8.0.30/mysql-connector-java-8.0.30.jar -O ~/openmrs/server4/server/apache-tomcat/lib/mysql-connector-java-8.0.30.jar
chmod u+r ~/openmrs/server4/server/apache-tomcat/lib/mysql-connector-java-8.0.30.jar
mkdir -p ~/openmrs/server4/webapps/openmrs/WEB-INF/lib
cp ~/openmrs/server4/server/apache-tomcat/lib/mysql-connector-java-8.0.30.jar ~/openmrs/server4/webapps/openmrs/WEB-INF/lib/
wget https://repo1.maven.org/maven2/org/yaml/snakeyaml/1.33/snakeyaml-1.33.jar -O ~/openmrs/server4/server/apache-tomcat/lib/snakeyaml-1.33.jar
chmod u+r ~/openmrs/server4/server/apache-tomcat/lib/snakeyaml-1.33.jar
cp ~/openmrs/server4/server/apache-tomcat/lib/snakeyaml-1.33.jar ~/openmrs/server4/webapps/openmrs/WEB-INF/lib/


Configure openmrs-runtime.properties:
cat << EOF > ~/openmrs/server4/openmrs-runtime.properties
connection.url=jdbc:mysql://127.0.0.1:3308/chatbot?autoReconnect=true&useUnicode=true&characterEncoding=UTF-8&sessionVariables=default_storage_engine=InnoDB
connection.username=openmrs
connection.password=openmrs
module.allow_web_admin=true
auto_update_database=true
security.requireBasicAuthentication=false
EOF



4. Build and Deploy the Module
cd ~/openmrs/server2/openmrschatbot
mvn clean install
mkdir -p ~/openmrs/server4/modules
cp omod/target/openmrschatbot*.omod ~/openmrs/server4/modules/

5. Run OpenMRS
export JAVA_HOME=$(/usr/libexec/java_home -v 11)
export MAVEN_OPTS="-Xmx1024m"
export OPENMRS_HOME=~/openmrs/server4
cd ~/openmrs/server4
mvn openmrs-sdk:run -DserverId=server4

6. Complete Setup Wizard

Open http://localhost:8080/openmrs/.
Follow the wizard:
Database: MySQL, URL: jdbc:mysql://127.0.0.1:3308/chatbot?autoReconnect=true&useUnicode=true&characterEncoding=UTF-8
Username: openmrs
Password: openmrs
Admin User: admin/Admin123
Modules: Add openmrschatbot if not auto-detected.



7. Verify

Login: http://localhost:8080/openmrs/ with admin/Admin123.
Check module functionality in OpenMRS Admin → Manage Modules.

Contributing

Fork the repository.
Create a feature branch: git checkout -b feature/your-feature.
Commit changes: git commit -m "Add your feature".
Push to branch: git push origin feature/your-feature.
Open a pull request.

Development Workflow

Build: mvn clean package
Deploy: Copy omod/target/openmrschatbot*.omod to ~/openmrs/server4/modules/.
Format Code: Use Spotless:mvn spotless:apply
mvn spotless:check



Troubleshooting

MySQL Connection Error (ERROR 2002 (HY000)):
Ensure Docker container is running: docker ps -a | grep openmrs-sdk-mysql-v8-4-1.
Use TCP: mysql -u openmrs -p -h 127.0.0.1 -P 3308 --protocol=TCP chatbot.


Liquibase Schema Error:
Reset database:mysql -u root -p -h 127.0.0.1 -P 3308 --protocol=TCP

DROP DATABASE chatbot;
CREATE DATABASE chatbot;
GRANT ALL PRIVILEGES ON chatbot.* TO 'openmrs'@'172.17.0.1' IDENTIFIED BY 'openmrs';
GRANT ALL PRIVILEGES ON chatbot.* TO 'openmrs'@'%' IDENTIFIED BY 'openmrs';
FLUSH PRIVILEGES;




Logs:tail -f ~/openmrs/server4/server/apache-tomcat/logs/catalina.out


Resources:
OpenMRS Documentation
OpenMRS Community
GitHub Issues



License
MIT License
