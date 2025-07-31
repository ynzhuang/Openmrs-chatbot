# 🛠️ OpenMRS Chatbot Module

Welcome to the **OpenMRS Chatbot Module**! This module enhances **OpenMRS Platform 2.7.3** with chatbot functionality for healthcare informatics. This guide provides **macOS-specific** instructions to set up the development environment, run OpenMRS, and contribute to the `openmrschatbot` module, located in the `chatbot-main` subfolder. It uses **Docker** for MySQL and the **OpenMRS SDK** for server management.

## 📋 Table of Contents
- [Prerequisites](#-prerequisites)
- [Setup MySQL Container](#-setup-mysql-container)
- [Setup OpenMRS Server](#-setup-openmrs-server)
- [Launch OpenMRS](#-launch-openmrs)
- [Build and Deploy Module](#-build-and-deploy-module)
- [Restarting in the Future](#-restarting-in-the-future)
- [Contributing](#-contributing)
- [Troubleshooting](#-troubleshooting)
- [License](#-license)

## 📦 Prerequisites

Install these tools on your Mac using **Terminal** (open via Launchpad or Spotlight Search).

### 1. Homebrew
The macOS package manager.

```bash
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
echo 'eval "$(/opt/homebrew/bin/brew shellenv)"' >> ~/.zprofile
eval "$(/opt/homebrew/bin/brew shellenv)"
brew --version
```

### 2. Java 11
Required for OpenMRS 2.7.3.

```bash
brew install --cask temurin@11
echo 'export JAVA_HOME=$(/usr/libexec/java_home -v 11)' >> ~/.zshrc
source ~/.zshrc
java -version
```

### 3. Docker Desktop
Runs the MySQL database.

- Download: [Docker Desktop for Mac](https://www.docker.com/products/docker-desktop)
- Install the `.dmg` file and start Docker Desktop (check for the Docker icon in the top menu bar).
- Verify:
  ```bash
  docker --version
  ```

### 4. Maven
Builds the module.

```bash
brew install maven
mvn --version
```

### 5. Git
Version control.

```bash
brew install git
git --version
```

### 6. OpenMRS SDK
Manages OpenMRS instances.

```bash
mvn org.openmrs.maven.plugins:openmrs-sdk-maven-plugin:setup-sdk
mvn openmrs-sdk:help
```

### 7. IntelliJ IDEA (Optional)
Recommended IDE.

```bash
brew install --cask intellij-idea-ce
```

## 🐬 Setup MySQL Container

**Note**: Use the Mac **Terminal** app, not the Docker Desktop Terminal.

1. **Run MySQL Container**:
   Creates a container named `openmrs-sdk-mysql-v8-4-1` for the `chatbot` database.

   ```bash
   docker run --name openmrs-sdk-mysql-v8-4-1 -p 3308:3306 -e MYSQL_ROOT_PASSWORD=openmrs -e MYSQL_USER=openmrs -e MYSQL_PASSWORD=openmrs -e MYSQL_DATABASE=chatbot -d mysql:8
   ```

2. **Configure Database**:
   ```bash
   docker exec -it openmrs-sdk-mysql-v8-4-1 mysql -u root -p
   ```
   Enter password: `openmrs`
   ```sql
   CREATE DATABASE chatbot;
   GRANT ALL PRIVILEGES ON chatbot.* TO 'openmrs'@'172.17.0.1' IDENTIFIED BY 'openmrs';
   GRANT ALL PRIVILEGES ON chatbot.* TO 'openmrs'@'%' IDENTIFIED BY 'openmrs';
   FLUSH PRIVILEGES;
   EXIT;
   ```

3. **Verify Connection**:
   ```bash
   mysql -u openmrs -p -h 127.0.0.1 -P 3308 --protocol=TCP chatbot
   ```
   Enter password: `openmrs`

## 🚀 Setup OpenMRS Server

1. **Run SDK Setup**:
   ```bash
   mvn openmrs-sdk:setup
   ```

2. **Answer Prompts**:
   - **Server ID**: `chatbot`
   - **Distribution**: `3 (Platform)`
   - **OpenMRS Version**: `2.7.3`
   - **Port**: `8080`
   - **Database Option**: `3 (Existing Docker container)`
   - **Container**: `1 (openmrs-sdk-mysql-v8-4-1)`
   - **Database Credentials**:
     - Username: `openmrs`
     - Password: `openmrs`

3. **Configure `openmrs-runtime.properties`**:
   ```bash
   cat << EOF > ~/openmrs/chatbot/openmrs-runtime.properties
connection.url=jdbc:mysql://127.0.0.1:3308/chatbot?autoReconnect=true&useUnicode=true&characterEncoding=UTF-8&sessionVariables=default_storage_engine=InnoDB
connection.username=openmrs
connection.password=openmrs
module.allow_web_admin=true
auto_update_database=true
security.requireBasicAuthentication=false
EOF
   ```

4. **Add Dependencies**:
   ```bash
   wget https://repo1.maven.org/maven2/mysql/mysql-connector-java/8.0.30/mysql-connector-java-8.0.30.jar -O ~/openmrs/chatbot/server/apache-tomcat/lib/mysql-connector-java-8.0.30.jar
   chmod u+r ~/openmrs/chatbot/server/apache-tomcat/lib/mysql-connector-java-8.0.30.jar
   mkdir -p ~/openmrs/chatbot/webapps/openmrs/WEB-INF/lib
   cp ~/openmrs/chatbot/server/apache-tomcat/lib/mysql-connector-java-8.0.30.jar ~/openmrs/chatbot/webapps/openmrs/WEB-INF/lib/
   wget https://repo1.maven.org/maven2/org/yaml/snakeyaml/1.33/snakeyaml-1.33.jar -O ~/openmrs/chatbot/server/apache-tomcat/lib/snakeyaml-1.33.jar
   chmod u+r ~/openmrs/chatbot/server/apache-tomcat/lib/snakeyaml-1.33.jar
   cp ~/openmrs/chatbot/server/apache-tomcat/lib/snakeyaml-1.33.jar ~/openmrs/chatbot/webapps/openmrs/WEB-INF/lib/
   ```

## 🖥️ Launch OpenMRS

1. **Start OpenMRS**:
   ```bash
   export JAVA_HOME=$(/usr/libexec/java_home -v 11)
   export MAVEN_OPTS="-Xmx1024m"
   export OPENMRS_HOME=~/openmrs/chatbot
   cd ~/openmrs/chatbot
   mvn openmrs-sdk:run -DserverId=chatbot
   ```

2. **Complete Setup Wizard**:
   - Open: [http://localhost:8080/openmrs/](http://localhost:8080/openmrs/)
   - Follow the wizard:
     - **Database**: MySQL
     - **URL**: `jdbc:mysql://127.0.0.1:3308/chatbot?autoReconnect=true&useUnicode=true&characterEncoding=UTF-8`
     - **Username**: `openmrs`
     - **Password**: `openmrs`
     - **Admin User**: `admin`/`Admin123`
     - **Modules**: Ensure `openmrschatbot` is loaded
     - Choose to create a new schema
     - Click **Next** until complete

3. **Verify**:
   - Login: [http://localhost:8080/openmrs/](http://localhost:8080/openmrs/) with `admin`/`Admin123`
   - Check module in **Admin** > **Manage Modules**

## 🧱 Build and Deploy Module

1. **Clone Repository** (if not already done):
   ```bash
   git clone https://github.com/ynzhuang/Openmrs-chatbot.git
   cd Openmrs-chatbot/chatbot-main
   ```

2. **Build Module**:
   ```bash
   mvn clean install
   ```

3. **Deploy Module**:
   ```bash
   mkdir -p ~/openmrs/chatbot/modules
   cp omod/target/openmrschatbot*.omod ~/openmrs/chatbot/modules/
   ```

4. **Restart OpenMRS**:
   ```bash
   cd ~/openmrs/chatbot
   lsof -i :8080
   kill -9 $(lsof -t -i :8080)
   mvn openmrs-sdk:run -DserverId=chatbot
   ```

## 🔁 Restarting in the Future

1. **Start Docker Desktop**:
   Ensure it’s running (check the top menu bar).

2. **Start MySQL Container**:
   ```bash
   docker start openmrs-sdk-mysql-v8-4-1
   ```

3. **Run OpenMRS**:
   ```bash
   export JAVA_HOME=$(/usr/libexec/java_home -v 11)
   export MAVEN_OPTS="-Xmx1024m"
   export OPENMRS_HOME=~/openmrs/chatbot
   cd ~/openmrs/chatbot
   mvn openmrs-sdk:run -DserverId=chatbot
   ```

## 🤝 Contributing

We welcome contributions to enhance the `openmrschatbot` module!

1. Fork the repository.
2. Create a feature branch:
   ```bash
   git checkout -b feature/your-feature
   ```
3. Commit changes:
   ```bash
   git commit -m "Add your feature"
   ```
4. Push to branch:
   ```bash
   git push origin feature/your-feature
   ```
5. Open a pull request on GitHub.

### Development Workflow
- **Build**: `mvn clean package`
- **Deploy**: Copy `chatbot-main/omod/target/openmrschatbot*.omod` to `~/openmrs/chatbot/modules/`
- **Format Code**: Use Spotless:
  ```bash
  mvn spotless:apply
  mvn spotless:check
  ```

## 🛠️ Troubleshooting

### MySQL Connection Error
- **Issue**: `ERROR 2002 (HY000): Can't connect to local MySQL server through socket`
- **Fix**:
  - Ensure container is running:
    ```bash
    docker ps -a | grep openmrs-sdk-mysql-v8-4-1
    docker start openmrs-sdk-mysql-v8-4-1
    ```
  - Use TCP:
    ```bash
    mysql -u openmrs -p -h 127.0.0.1 -P 3308 --protocol=TCP chatbot
    ```

### Liquibase Schema Error
- **Issue**: `Unknown column 'retired_reason' in 'location_tag'`
- **Fix**:
  - Reset database:
    ```bash
    mysql -u root -p -h 127.0.0.1 -P 3308 --protocol=TCP
    ```
    ```sql
    DROP DATABASE chatbot;
    CREATE DATABASE chatbot;
    GRANT ALL PRIVILEGES ON chatbot.* TO 'openmrs'@'172.17.0.1' IDENTIFIED BY 'openmrs';
    GRANT ALL PRIVILEGES ON chatbot.* TO 'openmrs'@'%' IDENTIFIED BY 'openmrs';
    FLUSH PRIVILEGES;
    ```

### General Issues
- **Logs**:
  ```bash
  tail -f ~/openmrs/chatbot/server/apache-tomcat/logs/catalina.out
  ```
- **Port Conflicts**:
  ```bash
  lsof -i :8080
  kill -9 $(lsof -t -i :8080)
  ```
- **Resources**:
  - [OpenMRS Documentation](https://wiki.openmrs.org/)
  - [OpenMRS Community](https://talk.openmrs.org/)
  - [GitHub Issues](https://github.com/ynzhuang/Openmrs-chatbot/issues)

## 📜 License
[MIT License](LICENSE)
