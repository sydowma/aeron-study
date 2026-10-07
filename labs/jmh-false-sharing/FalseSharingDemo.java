import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLongArray;

/**
 * Day 1 实验:false sharing 对照。
 *
 * 同一份递增代码,只改变每个线程写的内存间距(stride):
 *   - STRIDE=1  (8 字节)  -> 多个线程的计数器落在同一 cache line -> false sharing
 *   - STRIDE=16 (128 字节)-> 每个计数器独占一条 cache line       -> 无 false sharing
 *
 * 用 AtomicLongArray(底层是一段连续 long[])保证写入不会被 JIT 消除,
 * 同时让"是否同一 cache line"只由 stride 决定。
 *
 * 运行:
 *   javac FalseSharingDemo.java
 *   java FalseSharingDemo
 */
public final class FalseSharingDemo
{
    private static final int THREADS = 4;
    private static final long ITERATIONS = 20_000_000L;

    private static long run(final int stride, final long iterations) throws InterruptedException
    {
        final AtomicLongArray counters = new AtomicLongArray(THREADS * stride);
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(THREADS);

        for (int t = 0; t < THREADS; t++)
        {
            final int index = t * stride;
            final Thread thread = new Thread(() ->
            {
                try
                {
                    start.await();
                }
                catch (final InterruptedException ex)
                {
                    Thread.currentThread().interrupt();
                    return;
                }

                for (long i = 0; i < iterations; i++)
                {
                    counters.incrementAndGet(index);
                }
                done.countDown();
            });
            thread.start();
        }

        final long begin = System.nanoTime();
        start.countDown();
        done.await();
        return System.nanoTime() - begin;
    }

    public static void main(final String[] args) throws InterruptedException
    {
        System.out.printf("threads=%d iterations=%,d%n", THREADS, ITERATIONS);
        System.out.printf("cache line = 64B (x86) / 128B (Apple Silicon)%n%n");

        run(1, ITERATIONS / 10);
        run(16, ITERATIONS / 10);

        double sharedTotal = 0;
        double paddedTotal = 0;
        for (int round = 1; round <= 3; round++)
        {
            final long shared = run(1, ITERATIONS);
            final long padded = run(16, ITERATIONS);
            sharedTotal += shared;
            paddedTotal += padded;
            System.out.printf(
                "round %d | shared(stride=1): %7.3f s | padded(stride=16): %7.3f s | slowdown: %.2fx%n",
                round, shared / 1e9, padded / 1e9, (double)shared / padded);
        }
        System.out.printf("%nmean slowdown: %.2fx%n", sharedTotal / paddedTotal);
    }

    private FalseSharingDemo()
    {
    }
}
