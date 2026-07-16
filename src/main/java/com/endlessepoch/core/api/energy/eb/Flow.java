package com.endlessepoch.core.api.energy.eb;

/**
 * Reactive event stream — one instance per machine.
 * publish → WindowBuffer → subscriber.onNext (background thread).
 * 响应式事件流——每台机器一个实例。
 * publish→WindowBuffer→subscriber.onNext（后台线程）。
 */
public class Flow {

    private final WindowBuffer buffer;
    private final Subscriber subscriber;

    private Flow(Subscriber subscriber) {
        this.subscriber = subscriber;
        this.buffer = new WindowBuffer(subscriber);
    }

    /** Create a Flow with the given subscriber. / 创建流并绑定订阅者。 */
    public static Flow create(Subscriber subscriber) {
        return new Flow(subscriber);
    }

    /** Publish an event into the pipeline. / 发布事件。 */
    public void publish(EeEvent event) {
        if (!subscriber.isUnsubscribed())
            buffer.offer(event);
    }

    /** Tick the internal buffer — call from serverTick. / 每 tick 推进缓冲。 */
    public void flush(long gameTick) {
        buffer.flush(gameTick);
    }

    /**
     * Re-sync the buffer clock after dormancy (machine re-formed etc.) — drops leftovers
     * and resets the stale timer. Call before publishing kick-off events.
     * 休眠后对时（机器重新成型等）——清残留、重置过期计时器。发布启动事件前调用。
     */
    public void resync(long gameTick) {
        buffer.resync(gameTick);
    }

    /** Cancel subscription and clean up. / 取消订阅并清理。 */
    public void dispose() {
        subscriber.onComplete();
    }

    public boolean isActive() { return !subscriber.isUnsubscribed(); }
    public WindowBuffer buffer() { return buffer; }
}
