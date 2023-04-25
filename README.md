# java_log_insertion

[![Java CI with Maven](https://github.com/cheesecat47/java_log_insertion/actions/workflows/maven.yml/badge.svg)](https://github.com/cheesecat47/java_log_insertion/actions/workflows/maven.yml)

## Dependencies

|       | Version | Note |
|:-----:|:-------:|:----:|
| Java  |   1.8   |      |
| Maven |   ^3    |      |

## Environments

```bash
cp .env.template .env
vi .env

SOURCE_DIR=./some_path/where_pom.xml/exist/
PACKAGE_NAME=com.my.package
```

- SOURCE_DIR: Directory of maven project that you want to insert logs.
- PACKAGE_NAME: package name of the source project.

## Run

```bash
make package
```

Use `log_insertion-1.0-jar-with-dependencies.jar` file under `target` directory.