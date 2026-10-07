# 面试题库索引

> 四段式:每题按 **问题 → 结论 → 源码位置(file:line) → 实验/基准佐证** 归档,缺一项视为未完成。

## 分类

### A. 架构与设计
- 为什么 Aeron 把 I/O 放在独立 Media Driver 进程,客户端只做 API?
- 客户端与 driver 之间如何通信?为什么用共享内存 ring buffer 而不是 socket/队列?
- driver 的线程模型(1 conductor + N sender + 1 receiver)为什么这样切分?

### B. 缓冲区与日志结构
- term buffer 为什么是 3 个?term rotation 如何推进?
- frame 头有哪些字段?padding frame 和 reserved value 解决什么问题?
- position / termId / termOffset 的关系?

### C. 流控与可靠性
- 流控(window)、back-pressure、拥塞控制三者区别?
- 发布端如何感知订阅端变慢?`offer()` 的返回码怎么用?
- Aeron 为什么用 NAK 而不是 ACK?丢包/乱序/重复怎么修复?

### D. 性能
- false sharing 在 Aeron/Agrona 里怎么规避?
- IdleStrategy 各档如何选?对延迟/功耗的影响?
- 内存序(acquire/release)在无锁 ring buffer 里的作用?
- off-heap 零拷贝在 IPC 路径上如何实现?

### E. 量化场景
- 行情分发为什么常用 multicast?Aeron 的 multicast 可靠性如何保证?
- 用 Archive 做行情录制 + 实时回放的典型架构?
- 用 Cluster 复制状态机做 OMS/风控状态复制的关键点?

---

## 模板

```markdown
### Q: <问题>

**结论**:
<3–5 句能背下来的结论>

**源码位置**:
- `aeron-.../src/main/java/.../Xxx.java:NN` —— <说明>

**实验/基准佐证**:
- `labs/...` 或命令 + 观测结果
```

## 题目清单

> 待补充(阶段 7 逐题填写)
