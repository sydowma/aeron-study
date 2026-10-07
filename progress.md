# 进度看板

> 完成标准:文档带 Aeron/Agrona `file:line` 定位 + 至少一个可复现实验/基准。勾选前请自检。

## 阶段 0 · 机械化同理心 + Agrona 底座(2 周)

- [ ] 0.1 CPU 与内存:cache line、false sharing、MESI、padding/@Contended
- [ ] 0.2 内存模型:JMM、volatile、VarHandle acquire/release/opaque、Unsafe
- [ ] 0.3 off-heap 与 mmap:直接内存、UnsafeBuffer、页对齐、hugepage
- [ ] 0.4 Agrona:UnsafeBuffer、ManyToOneRingBuffer、BroadcastBuffer
- [ ] 0.5 Agrona:CountersManager/AtomicCounter、IdleStrategy、Agent/AgentRunner
- [ ] 实验 labs/jmh-false-sharing/
- [ ] 实验 labs/agrona-ringbuffer/
- [ ] 产出 docs/mechanical-sympathy.md、docs/agrona.md

## 阶段 1 · 构建、跑起来并观测(1–2 周)

- [ ] 构建 `./gradlew build`
- [ ] 跑 aeron-samples:BasicPublisher/Subscriber
- [ ] 跑 aeron-samples:Ping/Pong、EmbeddedPingPong
- [ ] 解析 aeron.dir 结构(cnc.dat / counters / publications / subscriptions)
- [ ] CountersReader / aeron-stat 观测 position 与 back-pressure
- [ ] 产出 docs/run-and-observe.md、补齐 setup.md

## 阶段 2 · 日志缓冲区(2–3 周)

- [ ] 术语体系:term/frame/position/termId/termOffset/sessionId/streamId/channel
- [ ] 为什么 3 个 term、term rotation 与 tail 推进
- [ ] frame 头字段 + padding frame + reserved value
- [ ] LogBufferDescriptor / TermBuffer / FrameDescriptor 走读
- [ ] 实验 labs/frame-layout/
- [ ] 产出 docs/logbuffer.md

## 阶段 3 · 协议与编解码(2 周)

- [ ] 控制协议 io.aeron.command(SBE flyweights)走读
- [ ] on-wire 协议 io.aeron.protocol 各族头部走读
- [ ] 对照 wiki Transport Protocol Specification
- [ ] 产出 docs/protocol.md(含位布局图)

## 阶段 4 · 客户端侧(3 周)

- [ ] Aeron / Aeron.Context / ClientConductor
- [ ] Publication / ExclusivePublication / ConcurrentPublication
- [ ] Subscription / Image / FragmentHandler
- [ ] toDriverCommands 与 driver 广播 buffer 通信路径
- [ ] offer() 返回码与 back-pressure 触发点
- [ ] 产出 docs/client.md

## 阶段 5 · Driver 侧(4–5 周)

- [ ] 线程模型:DriverConductor + Sender 组 + Receiver
- [ ] ClientProxy / DriverProxy / AeronClientInvoker
- [ ] PublicationLink / SubscriptionLink / NetworkPublication / IpcPublication
- [ ] UDP 传输(UdpChannelTransport / Send+ReceiveChannelEndpoint)
- [ ] IPC 传输(IpcPublication / IpcSubscriptionLink)
- [ ] 可靠传输:loss detection、NAK、retransmit、TermRebuilder
- [ ] 流控:Min/MaxMulticast / Unicast FlowControl
- [ ] 拥塞控制:CongestionControl / StaticWindowCongestionControl
- [ ] 实验:抓包 + counters 观测 NAK/重传;window 调参对比
- [ ] 产出 docs/driver.md、docs/flow-control.md

## 阶段 6 · Archive + Cluster(3–4 周)

- [ ] Archive:RecordingSession / ReplaySession
- [ ] Archive:Catalog 与 segment 存储布局
- [ ] AeronArchive 客户端
- [ ] Cluster:Raft(ConsensusModule / Election / LogAdapter)
- [ ] Cluster:AeronCluster / ClusterSession / snapshot
- [ ] 量化应用:行情录制回放、OMS 状态机复制
- [ ] 产出 docs/archive.md、docs/cluster.md

## 阶段 7 · 量化实战 + 面试体系化(持续)

- [ ] 实验 labs/market-data-ema/(multicast 行情 + EMA)
- [ ] (可选)实验 labs/oms-cluster/
- [ ] 延迟基准 labs/latency-bench/(p50/p99/p99.9)
- [ ] interview/index.md 建立分类题库
- [ ] 每题补齐:问题 → 结论 → 源码位置 → 实验/基准佐证
