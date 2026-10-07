# 笔记 0001 · Cache line 与 false sharing

- 日期:2026-10-07(Day 1)
- 主题:阶段 0.1
- 源码:aeron `1.53.3` / agrona `2.6.1`
- 本地源码路径:`/Users/mark/GitHub/aeron`、`/Users/mark/GitHub/agrona`

## 走读路径

### 1. cache line 常量

`agrona/agrona/src/main/java/org/agrona/BitUtil.java:67-70`

```java
/**
 * Length of the data blocks used by the CPU cache sub-system in bytes.
 */
public static final int CACHE_LINE_LENGTH = 64;
```

全项目所有"按 cache line"的对齐/间隔都引用它。

### 2. ring buffer trailer:每个共享指针隔 128B

`agrona/.../concurrent/ringbuffer/RingBufferDescriptor.java:57-77`

```java
static
{
    int offset = 0;
    offset += (BitUtil.CACHE_LINE_LENGTH * 2);
    TAIL_POSITION_OFFSET = offset;          // 生产者写

    offset += (BitUtil.CACHE_LINE_LENGTH * 2);
    HEAD_CACHE_POSITION_OFFSET = offset;    // 生产者读的消费者缓存

    offset += (BitUtil.CACHE_LINE_LENGTH * 2);
    HEAD_POSITION_OFFSET = offset;          // 消费者写

    offset += (BitUtil.CACHE_LINE_LENGTH * 2);
    CORRELATION_COUNTER_OFFSET = offset;

    offset += (BitUtil.CACHE_LINE_LENGTH * 2);
    CONSUMER_HEARTBEAT_OFFSET = offset;

    offset += (BitUtil.CACHE_LINE_LENGTH * 2);
    TRAILER_LENGTH = offset;
}
```

要点:
- 间隔是 `64 * 2 = 128`,覆盖 ARM 的 128B line。
- `tail`(生产者)与 `head`(消费者)由**不同线程**更新,必须分处不同 line,否则 ring buffer 每写一条消息都在核心间作废整条 line。
- `HEAD_CACHE` 是消费者对生产者的"已消费位置"的**松弛缓存**,减少生产者去读真正的 `head` 的频率,同样要独立 line。

### 3. counters:每个 counter 预留 128B

`agrona/.../concurrent/status/CountersReader.java:225-228`

```java
/**
 * Length of the space allocated to a counter that includes padding to avoid false sharing.
 */
public static final int COUNTER_LENGTH = BitUtil.CACHE_LINE_LENGTH * 2;
```

Aeron 里所有计数器(发布位置、订阅位置、heartbeat、NAK 计数……)都从 `CountersManager` 分配,天然各自独占 line。

### 4. Aeron 侧:per-thread 网络缓冲隔离

`aeron-driver/src/main/java/io/aeron/driver/media/ReceiveChannelEndpointThreadLocals.java:59-68`

```java
BitUtil.align(smLength, CACHE_LINE_LENGTH) +
BitUtil.align(NakFlyweight.HEADER_LENGTH, CACHE_LINE_LENGTH) +
BitUtil.align(RttMeasurementFlyweight.HEADER_LENGTH, CACHE_LINE_LENGTH) +
BitUtil.align(ResponseSetupFlyweight.HEADER_LENGTH, CACHE_LINE_LENGTH) +
BitUtil.align(ErrorFlyweight.MAX_ERROR_FRAME_LENGTH, CACHE_LINE_LENGTH);
...
final ByteBuffer byteBuffer = BufferUtil.allocateDirectAligned(bufferLength, CACHE_LINE_LENGTH);
```

接收端点每个线程私有的 flyweight 缓冲按 64B 对齐,避免相邻线程的缓冲落在同一 line。

## 实验结论

`labs/jmh-false-sharing/`,`mean slowdown: 11.52x`(4 线程,同 line vs 隔 128B)。
详见 `docs/mechanical-sympathy.md#01-cpu-缓存与-false-sharing`。

## 待解决 / 下一步

- [ ] 用 JMH 重做,消除 JIT/预热干扰,给 p50/p99。
- [ ] 读 `ManyToOneRingBuffer.java` 的 `putIntRelease` / `getIntAcquire`,进入 0.2 内存序。
- [ ] 确认本地 Apple Silicon 实测 cache line 是否 128B(可用 `sysctl hw.cachelinesize`)。
