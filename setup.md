# 环境与调试步骤

## 源码

```bash
git clone https://github.com/aeron-io/aeron.git
cd aeron
git checkout 1.53.3   # 全程 pin 此 tag
```

依赖版本由 Aeron 的 `gradle/libs.versions.toml` 锁定,勿单独升级:

| 组件 | 版本 | 锁定位置 |
| --- | --- | --- |
| aeron | 1.53.3 | `version.txt` |
| agrona | 2.6.1 | `gradle/libs.versions.toml:2` |
| sbe | 1.40.2 | `gradle/libs.versions.toml:18` |

## 本地已就绪(2026-10-07)

```bash
# 已 clone 到仓库同级目录(shallow,已 pin)
/Users/mark/GitHub/aeron    # tag 1.53.3
/Users/mark/GitHub/agrona   # tag 2.6.1
```

阶段 0 实验为独立 `javac/java`,不依赖 Gradle,可直接跑(见 `labs/`)。

## JDK

Aeron 1.46 起最低 JDK 17;本仓库统一 **JDK 21(LTS)**。本机当前为 JDK 25,阶段 0 实验可正常运行。

```bash
java -version   # 本机 25.x;构建/生产建议 21.x
```

## 构建

```bash
./gradlew clean build          # 编译 + 单测
./gradlew :aeron-samples:build # 只编示例
./gradlew tasks                # 查看可用任务
```

> C++ 客户端为可选(阶段 6 若需跨语言验证再看 `cppbuild/`)。

## 运行与观测

> TODO(阶段 1):补齐 Media Driver(embedded / standalone)启动命令、aeron-samples 运行方式、`aeron.dir` 目录说明。

- Media Driver `aeron.dir` 关键文件/目录:`cnc.dat`(command & control 映射)、`counters`、`publications/`、`subscriptions/`、`loss-report/`。
- 观测工具:`CountersReader`,以及 `aeron-stat`(见 wiki *Monitoring and Debugging*)。

## 调试

> TODO(阶段 1):补齐断点(ClientConductor / DriverConductor / Sender / Receiver)、常用变量打印、调用栈查看。

## 网络与延迟实验环境

- IPC:本机即可。
- UDP multicast/unicast:优先 Linux;需配置多播路由与网卡参数。
- 延迟基准:Linux + `taskset` / `isolcpus`,参考 `aeron-io/benchmarks`。

## 改-编-跑-调 闭环记录

> 在阶段 1 完成后,把一次完整闭环(改哪、怎么编、怎么验证)记在这里。
