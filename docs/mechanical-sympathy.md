# 机械化同理心(Mechanical Sympathy)

> 阶段 0 文档。本章目标:理解 Aeron 的性能来源,并为阅读 Agrona/Aeron 源码建立"为什么会这么写"的直觉。
> 完成标准:每个结论都有 `file:line` 定位 + 可运行实验。当前进度:0.1 已完成,0.2 / 0.3 待补。

## 0.1 CPU 缓存与 false sharing

### 概念

- **Cache line**:CPU 在 cache 之间搬运数据的最小单位。x86 为 **64 字节**;Apple Silicon(M 系列)为 **128 字节**。
- **False sharing(伪共享)**:两个**互不相关的变量**恰好落在同一条 cache line 上。当不同核心各写其中一个变量时,缓存一致性协议(MESI)会反复把整条 line 在核心间作废/独占(即所谓 cache line ping-pong),程序在逻辑上无竞争,却在硬件上付出了跨核同步代价。
- 关键点:**这是"空间"问题,不是"逻辑"问题**。即使两个变量被不同线程各写各的,只要在同一 line,就会互相拖慢。

### 如何规避

1. **Padding**:把热点字段各自撑到独立 cache line(见下 Agrona 的 128 字节间隔)。
2. **`@sun.misc.Contended` / `jdk.internal.vm.annotation.Contended`**:JVM 自动插入 padding,用户类需 `-XX:-RestrictContended`。
3. **数据分片(per-thread / per-sequence):** 每个线程写自己的槽位,天然不共享。
4. **按 cache line 对齐分配**:让不同对象/缓冲区起始地址对齐到 line,避免跨界。

> 为什么 Agrona 的间隔是 `CACHE_LINE_LENGTH * 2`(=128B)而不是 64B?因为要同时覆盖 x86 的 64B 与 Apple/ARM 的 128B cache line,并给硬件相邻行预取留出余量。

### Agrona / Aeron 中的实证

| 位置 | 说明 |
| --- | --- |
| `agrona/agrona/src/main/java/org/agrona/BitUtil.java:70` | `CACHE_LINE_LENGTH = 64`,全项目 cache line 基准常量 |
| `agrona/.../concurrent/ringbuffer/RingBufferDescriptor.java:57-77` | ring buffer trailer 里 tail / headCache / head / correlation / heartbeat 各占 `CACHE_LINE_LENGTH * 2` = 128B,分别归生产者与消费者所有,避免伪共享 |
| `agrona/.../concurrent/status/CountersReader.java:228` | `COUNTER_LENGTH = CACHE_LINE_LENGTH * 2`,每个 counter 预留 128B padding |
| `agrona/.../concurrent/broadcast/BroadcastBufferDescriptor.java:57` | `TRAILER_LENGTH = CACHE_LINE_LENGTH * 2` |
| `aeron-driver/src/main/java/io/aeron/driver/media/ReceiveChannelEndpointThreadLocals.java:59-68` | 每个线程私有的 SM / NAK / RTT / Setup / Error flyweight 缓冲区按 `CACHE_LINE_LENGTH` 对齐,隔离线程间伪共享 |
| `aeron-driver/src/main/java/io/aeron/driver/media/DataTransportPoller.java:51` | UDP 收发缓冲用 `allocateDirectAligned(..., CACHE_LINE_LENGTH)` 对齐分配 |

一句话:**Aeron 的计数器、ring buffer 指针、per-thread 网络缓冲,全部按 cache line 隔离**——这正是它高吞吐低抖动的底层原因之一。

### 实验:`labs/jmh-false-sharing/`

同一段"4 线程各自自增计数器"的代码,只改 stride:

- `stride = 1`(相邻 8B,同一条 cache line)→ 伪共享
- `stride = 16`(相隔 128B,各占一条 line)→ 无伪共享

运行与实测输出(JDK 25 / Apple Silicon):

```bash
cd labs/jmh-false-sharing
javac FalseSharingDemo.java && java FalseSharingDemo
```

```
threads=4 iterations=20,000,000
cache line = 64B (x86) / 128B (Apple Silicon)

round 1 | shared(stride=1):   0.414 s | padded(stride=16):   0.092 s | slowdown:  4.50x
round 2 | shared(stride=1):   1.167 s | padded(stride=16):   0.092 s | slowdown: 12.74x
round 3 | shared(stride=1):   1.612 s | padded(stride=16):   0.094 s | slowdown: 17.22x

mean slowdown: 11.52x
```

**结论**:逻辑上四个线程各写自己的变量,零竞争;但同 line 版本慢一个数量级。这就是 `RingBufferDescriptor` 要把每个指针隔开 128B 的原因。

> 注:本实验用 `AtomicLongArray` 是为了防止 JIT 把自增优化掉;真实生产中都是普通字段 + padding。后续可升级为 JMH 版本(`@Benchmark` + `@State`,`labs/` 里保留依赖说明)。

### 自测

1. 为什么 `CACHE_LINE_LENGTH=64` 但 padding 用 128?
2. `RingBufferDescriptor` 里哪几个字段会被生产者写、哪几个会被消费者写?它们为何要分开?
3. `@Contended` 与手工 padding 各自的代价是什么?

## 0.2 内存模型与内存序

> TODO:JMM happens-before、`volatile`、`VarHandle` 的 acquire/release/opaque、`Unsafe`。结合 `ManyToOneRingBuffer` 的 `putIntRelease` / `getIntAcquire` 走读。

## 0.3 off-heap 与 mmap

> TODO:直接内存、`UnsafeBuffer` vs `ByteBuffer`、页对齐、hugepage,结合 `CncFileDescriptor` 走读。
