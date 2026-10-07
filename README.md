# aeron-study

以「读懂源码」为主线,兼顾面试与文档输出,系统学习 Aeron(低延迟可靠消息传输)源码的学习仓库。面向量化/HFT 场景:行情分发、零拷贝 IPC、订单/状态机复制。

## 目标

1. **读懂源码**:能定位 Aeron 关键机制到具体 `file:line`,讲清一条消息从 `offer()` 到 `poll()` 的完整链路。
2. **面试**:每个考点能落到源码位置 + 可运行实验/基准,而不是背结论。
3. **输出文档**:每份文档必须有 Aeron/Agrona `file:line` 定位和至少一个可复现实验,否则视为未完成。
4. **代码沉淀**:实验代码与源码走读笔记进仓库,可复现。

## 前置约束

- **统一源码版本**:全程 pin 到 Aeron tag `1.53.3`(2026-09,最新稳定),避免代码漂移。
  - 源码:`https://github.com/aeron-io/aeron`(checkout `1.53.3`)
  - 依赖:`org.agrona:agrona`、`uk.co.real-logic:sbe`,版本以 Aeron 的 `version.txt` / Gradle 锁定为准(勿单独升级)。
- **JDK 17+**:Aeron 1.46 起最低 JDK 17;本仓库统一用 **JDK 21(LTS)**。
- **传输实验**:IPC 本机即可;UDP multicast/unicast 建议 Linux(或用本机 loopback + 独立网卡调优),延迟基准需 Linux + `taskset`/`isolcpus`。
- **先依赖后主线**:Aeron 的缓冲区、计数器、IdleStrategy、Agent 框架在 **Agrona** 里,必须先读 Agrona,否则阶段 2+ 会处处断线。

## 源码地图(按模块)

| 模块 | 作用 | 关键包/类 | 阶段 |
| --- | --- | --- | --- |
| `aeron-client` | 客户端 API、ClientConductor、log buffer、控制协议编解码、on-wire 协议 | `io.aeron`、`io.aeron.logbuffer`、`io.aeron.command`、`io.aeron.protocol` | 2–4 |
| `aeron-driver` | Media Driver:conductor 状态机、Sender/Receiver、UDP/IPC 传输、流控/拥塞控制 | `io.aeron.driver`、`io.aeron.driver.media`、`io.aeron.driver.ext` | 5 |
| `aeron-archive` | 消息持久化录制与回放 | `io.aeron.archive` | 6 |
| `aeron-cluster` | 基于 Raft 的容错复制状态机 | `io.aeron.cluster` | 6 |
| `aeron-samples` | 官方示例:Publisher/Subscriber、Ping/Pong、embedded driver | `io.aeron.samples` | 1 |
| `aeron-system-tests` | 端到端行为规格测试(当"可执行文档"读) | `io.aeron.test.*` | 全程 |
| Agrona(外部) | 高性能数据结构与工具 | `org.agrona.concurrent`、`org.agrona` | 0 |

## 学习路线

| 阶段 | 内容 | 周期 | 产出 |
| --- | --- | --- | --- |
| 0 | 机械化同理心 + Agrona 底座 | 2 周 | `docs/mechanical-sympathy.md`、`docs/agrona.md`、`labs/jmh-false-sharing/`、`labs/agrona-ringbuffer/` |
| 1 | 构建、跑起来并观测 | 1–2 周 | `docs/run-and-observe.md`、补齐 `setup.md` |
| 2 | 日志缓冲区:term/frame/position | 2–3 周 | `docs/logbuffer.md`、`labs/frame-layout/` |
| 3 | 协议与编解码(控制协议 + on-wire) | 2 周 | `docs/protocol.md` |
| 4 | 客户端侧:conductor、publication、subscription、流控前压 | 3 周 | `docs/client.md` |
| 5 | Driver 侧:conductor 状态机、sender/receiver、流控与拥塞控制、重传 | 4–5 周 | `docs/driver.md`、`docs/flow-control.md` |
| 6 | Archive 录制回放 + Cluster(Raft) | 3–4 周 | `docs/archive.md`、`docs/cluster.md` |
| 7 | 量化实战 lab + 面试体系化(贯穿) | 持续 | `labs/market-data-ema/`、`interview/index.md` |

### 阶段 0 · 机械化同理心 + Agrona 底座(2 周)

Aeron 的性能不来自"用了 Aeron",而来自对 CPU/内存/OS 的利用。先补底座,再读源码。

- 0.1 CPU 与内存:cache line(64B)、false sharing、MESI、预取;`@Contended` / 手工 padding
- 0.2 内存模型:JMM happens-before、`volatile`、`VarHandle` 的 acquire/release/opaque、`Unsafe`(Aeron/Agrona 大量使用)
- 0.3 off-heap 与 mmap:直接内存、`MappedByteBuffer` vs Agrona `UnsafeBuffer`、页对齐、`mlock`/hugepage
- 0.4 Agrona(重点):`UnsafeBuffer`/`MutableDirectBuffer`、`ManyToOneRingBuffer`、`BroadcastBuffer`(`BroadcastTransmitter`/`BroadcastReceiver`)、`CountersManager`/`AtomicCounter`、`IdleStrategy`(BusySpin/Sleeping/Backoff/Yielding)、`Agent`/`AgentRunner`、`ManyToOneConcurrentArrayQueue`
- 实验:① JMH 量化 false sharing;② 用 `ManyToOneRingBuffer` 做进程内/跨进程 IPC;③ 各 IdleStrategy 的延迟-功耗对比
- 产出 `docs/mechanical-sympathy.md`、`docs/agrona.md`

### 阶段 1 · 构建、跑起来并观测(1–2 周)

- 构建 `./gradlew build`;跑 `aeron-samples`:BasicPublisher/Subscriber、Ping/Pong、EmbeddedPingPong
- Media Driver 两种形态:Embedded vs Standalone;观察 `aeron.dir` 下的 `cnc.dat`、`counters`、`publications/`、`subscriptions/`、`loss-report/`
- 用 `CountersReader` / `aeron-stat`(见 wiki *Monitoring and Debugging*)观测 position、back-pressure、NAK 计数
- 产出 `docs/run-and-observe.md`、补齐 `setup.md`

### 阶段 2 · 日志缓冲区(2–3 周)

源码入口:`aeron-client/src/main/java/io/aeron/logbuffer/`(`LogBufferDescriptor`、`TermBuffer`、`FrameDescriptor`、`LogBufferUnblocker`、`TermRebuilder`)

- 术语体系:term buffer(默认 1MB × 3)、frame、`position` / `termId` / `termOffset`、`sessionId` / `streamId` / `channel`
- 为什么是 3 个 term(写 / 清 / 读)、term 旋转(rotation)与 tail 推进
- frame 头字段、padding frame、`reserved value`
- 产出 `docs/logbuffer.md` + 手工拼/解析一个 data frame 的实验

### 阶段 3 · 协议与编解码(2 周)

- 控制协议(客户端 ↔ driver):`io.aeron.command`(SBE flyweights):`AddPublicationCmd`、`AddSubscriptionCmd`、`PublicationReadyFlyweight`、`ImageReadyFlyweight` 等
- on-wire 协议:`io.aeron.protocol`:`DataFrameHeader`、`SetupHeader`、`StatusMessageHeader`、`NakHeader`、`Heartbeat`、`RttMeasurement`
- 产出 `docs/protocol.md`,附各头部位布局图,并对照 wiki *Transport Protocol Specification*

### 阶段 4 · 客户端侧(3 周)

源码入口:`io.aeron`(`Aeron`、`Aeron.Context`、`ClientConductor`、`Publication`、`ExclusivePublication`、`ConcurrentPublication`、`Subscription`、`Image`)

- ClientConductor 如何通过 `toDriverCommands`(ring buffer)下发命令,通过 driver 广播 buffer 收回执
- `offer()` 的返回码语义:`OK`/`BACK_PRESSURED`/`NOT_CONNECTED`/`ADMIN_ACTION`;何时被卡
- `poll()`/`ControlledPollAction`、`FragmentHandler`、`Image` 生命周期
- 产出 `docs/client.md`

### 阶段 5 · Driver 侧(4–5 周,核心)

源码入口:`aeron-driver/src/main/java/io/aeron/driver/`(`DriverConductor`、`Sender`、`Receiver`、`SenderProxy`、`ReceiverProxy`、`ClientProxy`、`DriverProxy`、`AeronClientInvoker`)+ `driver/media/`(`UdpChannelTransport`、`SendChannelEndpoint`、`ReceiveChannelEndpoint`)+ IPC link

- 线程模型:1 个 DriverConductor + N 个 Sender + 1 个 Receiver(默认),无锁 ring buffer 协作
- 生命周期状态机:`PublicationLink` / `SubscriptionLink` / `NetworkPublication` / `IpcPublication` / `NetworkSubscription` / `IpcSubscriptionLink`
- 可靠传输:loss detection、`NakMessage`、retransmit、`TermRebuilder`;Aeron 为何用 NAK 而非 ACK
- 流控(`FlowControl`):`MinMulticastFlowControl` / `MaxMulticastFlowControl` / `UnicastFlowControl`;与拥塞控制(`CongestionControl`、`StaticWindowCongestionControl`)的区别
- 实验:抓包 + counters 观测 NAK/重传;调 window 观察吞吐-延迟 trade-off
- 产出 `docs/driver.md`、`docs/flow-control.md`

### 阶段 6 · Archive + Cluster(3–4 周)

- Archive:`RecordingSession`、`ReplaySession`、`Catalog`(segment 存储布局)、`AeronArchive` 客户端
- Cluster:Raft —— `ConsensusModule`、`AeronCluster`、`ClusterSession`、`Election`、`LogAdapter`、snapshot;复制状态机语义
- 量化相关性:行情录制/实时回放(恢复、回补)、OMS/风控状态机跨节点复制
- 产出 `docs/archive.md`、`docs/cluster.md`

### 阶段 7 · 量化实战 + 面试体系化(持续)

- 实战 lab:`labs/market-data-ema/` —— UDP multicast 行情发布 + 订阅端算 EMA/信号(与 `defi`/`spcx-ema-alert` 类需求对接);可选 `labs/oms-cluster/` 用 Cluster 做订单状态机
- 延迟基准:用 `aeron-io/benchmarks` 或自写 JMH/HdrHistogram,记录 p50/p99/p99.9
- 面试:每题按 **问题 → 结论 → 源码位置(file:line) → 实验/基准佐证** 四段式归档到 `interview/`

## 面试考点框架(初稿,阶段 7 展开)

- 架构:为什么 driver 与 client 分离?为什么用共享内存 ring buffer 而非 socket/队列?
- 缓冲区:term 为什么是 3 个?frame 结构?padding 如何避免？
- 流控:window / back-pressure / congestion control 三者区别?pub 侧如何感知订阅者慢?
- 可靠性:Aeron 为什么用 NAK 而非 ACK?丢包、乱序、重复如何检测与修复?
- 性能:false sharing、IdleStrategy、cache line、内存序、off-heap 零拷贝
- 量化:行情分发为何用 multicast?IPC 零拷贝怎么实现?Cluster 复制状态机怎么做 OMS?

## 学习原则

- 每份文档必须带 Aeron/Agrona `file:line` 定位和至少一个可复现实验/基准,防止退化成资料搬运。
- 源码走读笔记放 `notes/`,标题带路径索引,方便检索。
- 进度与勾选维护在 [`progress.md`](./progress.md)。
- 遇不确定的版本/工具命令,先查仓库 README 与 wiki,勿凭记忆写死。

## 目录说明

```
aeron-study/
├── README.md                    # 本文件:路线图 + 原则 + 源码地图
├── progress.md                  # 进度看板
├── setup.md                     # 环境、构建、运行与观测步骤
├── docs/                        # 各阶段学习文档
│   ├── mechanical-sympathy.md   # 阶段0
│   ├── agrona.md                # 阶段0
│   ├── run-and-observe.md       # 阶段1
│   ├── logbuffer.md             # 阶段2
│   ├── protocol.md              # 阶段3
│   ├── client.md                # 阶段4
│   ├── driver.md                # 阶段5
│   ├── flow-control.md          # 阶段5
│   ├── archive.md               # 阶段6
│   └── cluster.md               # 阶段6
├── notes/                       # 源码走读笔记(带 aeron/agrona 路径索引)
├── labs/                        # 可运行实验与基准
│   ├── jmh-false-sharing/       # 阶段0
│   ├── agrona-ringbuffer/       # 阶段0
│   ├── frame-layout/            # 阶段2
│   ├── market-data-ema/         # 阶段7(量化)
│   └── latency-bench/           # 阶段7
└── interview/                   # 四段式面试题库
    └── index.md
```
