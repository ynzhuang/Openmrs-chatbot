# Daily OpenMRS Development Commands

## Start Services
```bash
# Start the services
docker-compose up -d
```

## Build Changes
```bash
# Build changes
mvn clean package
```

## Hot Deploy (if needed)
```bash
# Hot deploy (if needed)
mvn package -P deploy-web -D deploy.path="../../openmrs-1.8.x/webapp/src/main/webapp"
``` 