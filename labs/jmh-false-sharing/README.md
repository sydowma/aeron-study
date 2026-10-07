# labs/jmh-false-sharing

Day 1(阶段 0.1)实验:证明 cache line 上的 false sharing 会带来数量级性能损失。

## 运行

```bash
javac FalseSharingDemo.java
java FalseSharingDemo
```

无需 Gradle,JDK 17+ 即可(本机 JDK 25 实测通过)。

## 原理

4 个线程各自 `incrementAndGet` 一个原子计数器,唯一变量是相邻计数器的间距:

| 变体 | stride | 字节间距 | 预期 |
| --- | --- | --- | --- |
| shared | 1 | 8B,同一条 cache line | 伪共享,慢 |
| padded | 16 | 128B,各占一条 line | 无伪共享,快 |

用 `AtomicLongArray`(底层连续 `long[]`)保证写入不被 JIT 消除。

## 实测(Apple Silicon / JDK 25)

```
threads=4 iterations=20,000,000
cache line = 64B (x86) / 128B (Apple Silicon)

round 1 | shared(stride=1):   0.414 s | padded(stride=16):   0.092 s | slowdown:  4.50x
round 2 | shared(stride=1):   1.167 s | padded(stride=16):   0.092 s | slowdown: 12.74x
round 3 | shared(stride=1):   1.612 s | padded(stride=16):   0.094 s | slowdown: 17.22x

mean slowdown: 11.52x
```

## 进阶

- JMH 版本:加 `org.openjdk.jmh:jmh-core` + `jmh-generator-annprocess`,`@State(Scope.Group)`、`@Benchmark`,`@CompilerControl(DONT_INLINE)`。
- 对照 Agrona `RingBufferDescriptor` 的 128B padding 设计:`docs/mechanical-sympathy.md`。
