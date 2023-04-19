# java_log_insertion

## Dependencies

|       | Version | Note |
|:-----:|:-------:|:----:|
|  Java |   1.8   |      |
| Maven |    ^3   |      |

## Run

```bash
make package
```

```bash
scp -P 22345 $SOURCE_DIR/target/EQMDataCollector-v1.0.1.jar 39_honeypot:/home/isslab/Developer/log_metric_analyze_framework/target/eqms-v220603/eqms-data-collector/target
```
