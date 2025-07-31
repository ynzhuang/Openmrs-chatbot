# OpenMRS Chatbot Module

Welcome to the **OpenMRS Chatbot Module** repository! This module enhances OpenMRS with chatbot functionality for healthcare informatics. It’s built for **OpenMRS Platform 2.5.9** and uses **MySQL 8.0** in a Docker container. This guide provides **macOS-specific** instructions to set up a local development environment and contribute to the project.

## Table of Contents
- [Prerequisites](#prerequisites)
- [Setup Instructions](#setup-instructions)
- [Contributing](#contributing)
- [Troubleshooting](#troubleshooting)
- [License](#license)

## Prerequisites

Install the following tools on your Mac:

### 1. Homebrew
The macOS package manager.

```bash
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
echo 'eval "$(/opt/homebrew/bin/brew shellenv)"' >> ~/.zprofile
eval "$(/opt/homebrew/bin/brew shellenv)"
brew --version
```

### 2. Java 11
Required for OpenMRS 2.5.9.

```bash
brew install --cask temurin@11
echo 'export JAVA_HOME=$(/usr/libexec/java_home -v 11)' >> ~/.zshrc
source ~/.zshrc
java -version
```

### 3. Docker Desktop
Runs the MySQL database.

1. Download: [Docker Desktop for Mac](https://www.docker.com/products/docker-desktop)
2. Install the `.dmg` file and start Docker Desktop.
3. Verify:
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

### 6. IntelliJ IDEA (Optional)
Recommended IDE.

```bash
brew install --cask intellij-idea-ce
```

## Setup Instructions

### 1. Clone the Repository
```bash
git clone https://github.com/your-username/openmrschatbot.git
cd openmrschatbot
```

### 2. Set Up MySQL Database
Run a MySQL 8.0 container named `openmrs-sdk-mysql-v8-4-1`.

```bash
docker run --name openmrs-sdk-mysql-v8-4-1 -p 3308:3306 -e MYSQL_ROOT_PASSWORD=openmrs -e MYSQL_USER=openmrs -e MYSQL_PASSWORD=openmrs -e MYSQL_DATABASE=chatbot -d mysql:8
```

Grant privileges:
```bash
docker exec -it openmrs-sdk-mysql-v8-4-1 mysql -u root -p
```
```sql
CREATE DATABASE chatbot;
GRANT ALL PRIVILEGES ON chatbot.* TO 'openmrs'@'172.17.0.1' IDENTIFIED BY 'openmrs';
GRANT ALL PRIVILEGES ON chatbot.* TO 'openmrs'@'%' IDENTIFIED BY 'openmrs';
FLUSH PRIVILEGES;
EXIT;
```

Verify connection:
```bash
mysql -u openmrs -p -h 127.0.0.1 -P 3308 --protocol=TCP chatbot
```

### 3. Configure OpenMRS Environment
Set up OpenMRS 2.5.9 in `~/openmrs/server4`.

1. **Create Directory**:
   ```bash
   mkdir -p ~/openmrs/server4
   cd ~/openmrs/server4
   ```

2. **Download Dependencies**:
   ```bash
   wget https://repo1.maven.org/maven2/mysql/mysql-connector-java/8.0.30/mysql-connector-java-8.0.30.jar -O ~/openmrs/server4/server/apache-tomcat/lib/mysql-connector-java-8.0.30.jar
   chmod u+r ~/openmrs/server4/server/apache-tomcat/lib/mysql-connector-java-8.0.30.jar
   mkdir -p ~/openmrs/server4/webapps/openmrs/WEB-INF/lib
   cp ~/openmrs/server4/server/apache-tomcat/lib/mysql-connector-java-8.0.30.jar ~/openmrs/server4/webapps/openmrs/WEB-INF/lib/
   wget https://repo1.maven.org/maven2/org/yaml/snakeyaml/1.33/snakeyaml-1.33.jar -O ~/openmrs/server4/server/apache-tomcat/lib/snakeyaml-1.33.jar
   chmod u+r ~/openmrs/server4/server/apache-tomcat/lib/snakeyaml-1.33.jar
   cp ~/openmrs/server4/server/apache-tomcat/lib/snakeyaml-1.33.jar ~/openmrs/server4/webapps/openmrs/WEB-INF/lib/
   ```

3. **Configure `openmrs-runtime.properties`**:
   ```bash
   cat << EOF > ~/openmrs/server4/openmrs-runtime.properties
connection.url=jdbc:mysql://127.0.0.1:3308/chatbot?autoReconnect=true&useUnicode=true&characterEncoding=UTF-8&sessionVariables=default_storage_engine=InnoDB
connection.username=openmrs
connection.password=openmrs
module.allow_web_admin=true
auto_update_database=true
security.requireBasicAuthentication=false
EOF
   ```

### 4. Build and Deploy the Module
```bash
cd ~/openmrs/server2/openmrschatbot
mvn clean install
mkdir -p ~/openmrs/server4/modules
cp omod/target/openmrschatbot*.omod ~/openmrs/server4/modules/
```

### 5. Run OpenMRS
```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 11)
export MAVEN_OPTS="-Xmx1024m"
export OPENMRS_HOME=~/openmrs/server4
cd ~/openmrs/server4
mvn openmrs-sdk:run -DserverId=server4
```

### 6. Complete Setup Wizard
- Open: [http://localhost:8080/openmrs/](http://localhost:8080/openmrs/)
- Follow the wizard:
  - **Database**: MySQL, URL: `jdbc:mysql://127.0.0.1:3308/chatbot?autoReconnect=true&useUnicode=true&characterEncoding=UTF-8`
  - **Username**: `openmrs`
  - **Password**: `openmrs`
  - **Admin User**: `admin`/`Admin123`
  - **Modules**: Ensure `openmrschatbot` is loaded.

### 7. Verify
- Login: [http://localhost:8080/openmrs/](http://localhost:8080/openmrs/) with `admin`/`Admin123`
- Check module in **Admin** > **Manage Modules**

## Contributing

We welcome contributions to improve the `openmrschatbot` module!

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
- **Deploy**: Copy `omod/target/openmrschatbot*.omod` to `~/openmrs/server4/modules/`
- **Format Code**: Use Spotless:
  ```bash
  mvn spotless:apply
  mvn spotless
