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

    /** Cancel subscription and clean up. / 取消订阅并清理。 */
    public void dispose() {
        subscriber.onComplete();
    }

    public boolean isActive() { return !subscriber.isUnsubscribed(); }
    public WindowBuffer buffer() { return buffer; }
}
