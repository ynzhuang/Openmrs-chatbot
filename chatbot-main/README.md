# 🛠️ OpenMRS Chatbot Module

Welcome to the **OpenMRS Chatbot Module**! This module enhances **OpenMRS Platform 2.7.3** with chatbot functionality for healthcare informatics. It serves as a starting point for creating new OpenMRS modules. This `README.md` provides instructions to build, deploy, and contribute to the module, located in the `chatbot-main/openmrschatbot` subfolder.

**Note**: This guide assumes you have set up OpenMRS Platform 2.7.3 as per the root [README.md](https://github.com/ynzhuang/Openmrs-chatbot/blob/main/README.md). If you encounter issues with building or deploying, please report them at [GitHub Issues](https://github.com/ynzhuang/Openmrs-chatbot/issues).

## 📋 Table of Contents
- [Description](#-description)
- [Installation](#-installation)

## 📝 Description

This is a basic module that can be used as a starting point for creating new OpenMRS modules. It provides a foundation for adding chatbot functionality to enhance healthcare informatics in OpenMRS.

## 📥 Installation

 **Install the .omod File**:
   - Option 1: Via OpenMRS UI:
     - Open [http://localhost:8080/openmrs/](http://localhost:8080/openmrs/).
     - Go to **Admin** > **Manage Modules**.
     - Upload `chatbot-main/openmrschatbot/omod/target/openmrschatbot*.omod`.
   - Option 2: Manual Copy:
     ```bash
     cp chatbot-main/openmrschatbot/omod/target/openmrschatbot*.omod ~/.OpenMRS/modules/
     ```
     Restart OpenMRS:
     ```bash
     cd ~/openmrs/chatbot
     mvn openmrs-sdk:run -DserverId=chatbot
     ```

2. **Verify**:
   - Login at [http://localhost:8080/openmrs/](http://localhost:8080/openmrs/) with `admin`/`Admin123`.
   - Confirm the module is listed in **Admin** > **Manage Modules**.

2. **Run**:
3.  - [http://localhost:8080/openmrs/module/chatbot/chatbot.form](http://localhost:8080/openmrs/module/chatbot/chatbot.form)
