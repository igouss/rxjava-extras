package com.github.davidmoten.rx.operators;

import java.util.concurrent.ConcurrentLinkedQueue;

import rx.Observable.Operator;
import rx.Producer;
import rx.Scheduler;
import rx.Subscriber;

import com.github.davidmoten.rx.util.Drainer;
import com.github.davidmoten.rx.util.DrainerAsyncBiased;
import com.github.davidmoten.rx.util.DrainerSyncBiased;

public final class OperatorBufferEmissions<T> implements Operator<T, T> {

    private final Scheduler observeOnScheduler;

    public OperatorBufferEmissions() {
        this(null);
    }

    public OperatorBufferEmissions(Scheduler observeOnScheduler) {
        this.observeOnScheduler = observeOnScheduler;
    }

    @Override
    public Subscriber<? super T> call(Subscriber<? super T> child) {
        // need to keep an atomic reference to drainer because parent refers to
        // drainer and drainer refers to parent
        final Drainer<T> drainer = createDrainer(child, observeOnScheduler);
        final ParentSubscriber<T> parent = new ParentSubscriber<T>(drainer);
        child.add(parent);
        child.setProducer(new Producer() {

            @Override
            public void request(long n) {
                if (n <= 0)
                    return;
                long t = drainer.surplus();
                // only request what is needed to fulfill total requests (best
                // endeavours). There are race conditons where we request too
                // many but that's ok. The main thing is not to request too
                // little because a stream could stall.
                long r = n - t;
                if (t > 0) {
                    if (r > 0)
                        parent.requestMore(r);
                } else {
                    if (r < 0)
                        r = Long.MAX_VALUE;
                    parent.requestMore(r);
                }
                drainer.request(n);
            }
        });
        return parent;
    }

    private static <T> Drainer<T> createDrainer(Subscriber<? super T> child,
            Scheduler observeOnScheduler) {
        final Drainer<T> drainer;
        if (observeOnScheduler == null || true)
            drainer = DrainerSyncBiased.create(new ConcurrentLinkedQueue<T>(), child);
        else
            drainer = DrainerAsyncBiased.create(new ConcurrentLinkedQueue<Object>(), child,
                    observeOnScheduler.createWorker(), child);
        return drainer;
    }

    private static final class ParentSubscriber<T> extends Subscriber<T> {

        private final Drainer<T> drainer;

        ParentSubscriber(final Drainer<T> drainer) {
            this.drainer = drainer;
        }

        @Override
        public void onStart() {
            // use backpressure
            request(0);
        }

        void requestMore(long n) {
            if (n > 0)
                request(n);
        }

        @Override
        public void onCompleted() {
            drainer.onCompleted();
        }

        @Override
        public void onError(Throwable e) {
            drainer.onError(e);
        }

        @Override
        public void onNext(T t) {
            drainer.onNext(t);
        }

    };

}
