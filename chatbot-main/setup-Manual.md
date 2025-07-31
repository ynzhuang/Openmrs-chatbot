# OpenMRS Local Development Setup Manual (Mac)

**Author:** Ajay Panduri  
**Last Updated:** 2024

This manual provides step-by-step instructions for setting up a local development environment for OpenMRS on macOS.

## Prerequisites

Before you begin, ensure you have the following prerequisites installed on your Mac:

### 1. Install Homebrew

Homebrew is a package manager for macOS that we'll use to install other dependencies.

```bash
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
```

After installation, add Homebrew to your PATH (if not already done):

```bash
echo 'eval "$(/opt/homebrew/bin/brew shellenv)"' >> ~/.zprofile
eval "$(/opt/homebrew/bin/brew shellenv)"
```

Verify installation:
```bash
brew --version
```

### 2. Install Java 8

OpenMRS requires Java 8 for development. Install it using Homebrew Cask:

```bash
brew install --cask temurin8
```

Alternatively, you can download it directly from Oracle:
- Visit: https://www.oracle.com/java/technologies/javase/javase8-archive-downloads.html
- Download "Java SE Development Kit 8uXXX" for macOS

Verify Java installation:
```bash
java -version
javac -version
```

Set JAVA_HOME environment variable:
```bash
echo 'export JAVA_HOME=$(/usr/libexec/java_home -v 1.8)' >> ~/.zshrc
source ~/.zshrc
```

### 3. Install Docker Desktop

Docker is required for running OpenMRS in containers and managing dependencies.

1. Visit the official Docker Desktop download page: https://www.docker.com/products/docker-desktop
2. Download Docker Desktop for Mac
3. Install the downloaded `.dmg` file
4. Start Docker Desktop from Applications

Verify Docker installation:
```bash
docker --version
docker-compose --version
```

## Additional Development Tools

### 4. Install Maven

Maven is used for building OpenMRS modules:

```bash
brew install maven
```

Verify installation:
```bash
mvn --version
```

### 5. Install Git

Git for version control:

```bash
brew install git
```

Verify installation:
```bash
git --version
```

### 6. Install IDE (Recommended: IntelliJ IDEA)

For Java development, IntelliJ IDEA is recommended:

```bash
brew install --cask intellij-idea-ce
```

Or download from: https://www.jetbrains.com/idea/download/

## OpenMRS Development Environment Setup

### 7. Clone OpenMRS Core (Optional)

If you want to work with OpenMRS core:

```bash
git clone https://github.com/openmrs/openmrs-core.git
cd openmrs-core
mvn clean install
```

### 8. Setup OpenMRS Module Development

For developing modules like the chatbot module:

1. Clone your module repository:
```bash
git clone <your-module-repo-url>
cd <module-directory>
```

2. Build the module:
```bash
mvn clean package
```

3. The compiled module (.omod file) will be in the `omod/target/` directory.

## Running OpenMRS

### Option 1: Using Docker (Recommended)

1. Create a `docker-compose.yml` file:
```yaml
version: '3.8'
services:
  openmrs:
    image: openmrs/openmrs-reference-application:latest
    ports:
      - "8080:8080"
    environment:
      - DB_HOST=mysql
      - DB_PORT=3306
      - DB_DATABASE=openmrs
      - DB_USERNAME=openmrs
      - DB_PASSWORD=openmrs
    depends_on:
      - mysql
  
  mysql:
    image: mysql:5.7
    environment:
      - MYSQL_ROOT_PASSWORD=root
      - MYSQL_DATABASE=openmrs
      - MYSQL_USER=openmrs
      - MYSQL_PASSWORD=openmrs
    volumes:
      - mysql_data:/var/lib/mysql

volumes:
  mysql_data:
```

2. Start OpenMRS:
```bash
docker-compose up -d
```

3. Access OpenMRS at: http://localhost:8080/openmrs

### Option 2: Manual Setup

1. Download OpenMRS standalone from: https://openmrs.org/download/
2. Extract and run:
```bash
./startup.sh
```

## Module Development Workflow

### Building and Deploying Modules

1. **Build the module:**
```bash
mvn clean package
```

2. **Deploy to running OpenMRS instance:**
```bash
mvn package -P deploy-web -D deploy.path="../../openmrs-1.8.x/webapp/src/main/webapp"
```

3. **Install module:**
   - Go to OpenMRS Admin → Manage Modules
   - Upload the .omod file from `omod/target/`
   - Or copy to `~/.OpenMRS/modules/` directory

### Code Formatting

This project uses Spotless for code formatting:

```bash
# Apply formatting
mvn spotless:apply

# Check formatting without applying
mvn spotless:check
```

## Troubleshooting

### Common Issues

1. **Java version conflicts:**
   - Ensure JAVA_HOME points to Java 8
   - Use `java -version` to verify

2. **Docker issues:**
   - Ensure Docker Desktop is running
   - Check Docker daemon status: `docker info`

3. **Maven build failures:**
   - Clear Maven cache: `mvn clean`
   - Check Java version compatibility

4. **Port conflicts:**
   - Change port in docker-compose.yml if 8080 is in use
   - Check for running services: `lsof -i :8080`

### Getting Help

- OpenMRS Documentation: https://wiki.openmrs.org/
- OpenMRS Community: https://talk.openmrs.org/
- GitHub Issues: https://github.com/openmrs/openmrs-core/issues

## Next Steps

After completing this setup:

1. Familiarize yourself with OpenMRS architecture
2. Review the module development guide
3. Set up your IDE with OpenMRS-specific configurations
4. Join the OpenMRS community for support

---

**Note:** This setup is specifically for macOS. For other operating systems, please refer to the appropriate OpenMRS documentation. 