package com.endlessepoch.core.api.energy.eb;

import com.endlessepoch.core.Config;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Time-window batch buffer. Accumulates events until the configured window elapses,
 * then flushes the batch to the subscriber's background thread.
 * 时间窗口缓冲器，窗口到期后批量推送至订阅者后台线程。
 */
public class WindowBuffer {

    private final Queue<EeEvent> queue = new ConcurrentLinkedQueue<>();
    private final Subscriber subscriber;
    private final TickTimer timer = new TickTimer();
    private long windowStart = System.nanoTime();
    private int dropped;

    public WindowBuffer(Subscriber subscriber) {
        this.subscriber = subscriber;
    }

    /** Push an event into the buffer. / 推入事件。 */
    public void offer(EeEvent event) {
        if (queue.size() >= Config.ebBufferCapacity) {
            queue.poll(); // drop oldest / 丢弃最早
            dropped++;
            if (dropped % 1000 == 1)
                System.err.println("[EB] WindowBuffer overflow, dropped " + dropped + " events");
            return;
        }
        queue.add(event);
    }

    /**
     * Called each server tick. If window elapsed or subscriber unsubscribed,
     * drains all buffered events into a batch and submits to background.
     * 每 tick 调用，窗口到期或取消订阅时排空并提交后台。
     */
    public void flush(long gameTick) {
        if (subscriber.isUnsubscribed()) {
            queue.clear();
            return;
        }
        // Drop stale events after server sleep / 服务器休眠后丢弃过期事件
        if (timer.isExpired(gameTick)) {
            queue.clear();
            windowStart = System.nanoTime();
            return;
        }
        long now = System.nanoTime();
        if (now - windowStart >= Config.ebWindowNanos && !queue.isEmpty()) {
            List<EeEvent> batch = new ArrayList<>();
            EeEvent ev;
            while ((ev = queue.poll()) != null && batch.size() < Config.ebMaxBatch)
                batch.add(ev);
            if (!batch.isEmpty()) {
                Schedulers.BACKGROUND.submit(() -> subscriber.onNext(batch));
            }
            windowStart = now;
        }
    }

    public int size() { return queue.size(); }

    /**
     * Re-sync after a dormant period (e.g. machine re-formed): drop stale leftovers and
     * reset the tick timer so freshly published events aren't discarded as backlog.
     * 休眠后对时（如机器重新成型）：清掉残留旧事件并重置计时器，防止新发布的事件被当积压丢弃。
     */
    public void resync(long gameTick) {
        queue.clear();
        timer.reset(gameTick);
        windowStart = System.nanoTime();
    }
}
