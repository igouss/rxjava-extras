package com.github.davidmoten.rx.operators;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import rx.Notification;
import rx.Observable;
import rx.Observable.OnSubscribe;
import rx.Producer;
import rx.Scheduler;
import rx.Scheduler.Worker;
import rx.Subscriber;
import rx.Subscription;
import rx.functions.Action0;

import com.github.davidmoten.util.BackpressureUtils;

public class OnSubscribeRetry<T> implements OnSubscribe<T> {

    private final Parameters<T> parameters;

    public OnSubscribeRetry(Observable<T> source, Scheduler scheduler, boolean stopOnComplete,
            boolean stopOnError) {
        this.parameters = new Parameters<T>(source, scheduler, stopOnComplete, stopOnError);
    }

    private static class Parameters<T> {
        final Observable<T> source;
        final Scheduler scheduler;
        final boolean stopOnComplete;
        final boolean stopOnError;

        Parameters(Observable<T> source, Scheduler scheduler, boolean stopOnComplete,
                boolean stopOnError) {
            this.source = source;
            this.scheduler = scheduler;
            this.stopOnComplete = stopOnComplete;
            this.stopOnError = stopOnError;
        }
    }

    @Override
    public void call(Subscriber<? super T> child) {
        child.setProducer(new RetryProducer<T>(parameters, child));
    }

    private static class RetryProducer<T> implements Producer {

        private final AtomicLong expected = new AtomicLong();
        private final AtomicBoolean restart = new AtomicBoolean(true);
        private final Parameters<T> parameters;
        private final Subscriber<? super T> child;
        private final Worker worker;

        public RetryProducer(Parameters<T> parameters, Subscriber<? super T> child) {
            this.parameters = parameters;
            this.child = child;
            this.worker = parameters.scheduler.createWorker();
        }

        @Override
        public void request(long n) {
            if (n > 0) {
                BackpressureUtils.getAndAddRequest(expected, n);
                process();
            }
        }

        private synchronized void process() {
            if (restart.compareAndSet(true, false)) {
                restart();
            }
        }

        private void restart() {
            // TODO Auto-generated method stub

        }

        private void subscribe() {
            Action0 restart = new Action0() {

                @Override
                public void call() {
                    Subscription sub = parameters.source.materialize().unsafeSubscribe(
                            new Subscriber<Notification<T>>() {

                                @Override
                                public void onStart() {

                                }

                                @Override
                                public void onCompleted() {
                                    // do nothing
                                }

                                @Override
                                public void onError(Throwable e) {
                                    child.onError(e);
                                }

                                @Override
                                public void onNext(Notification<T> notification) {
                                    if (notification.hasValue())
                                        child.onNext(notification.getValue());
                                    else if (notification.isOnCompleted())
                                        child.onCompleted();
                                    else {
                                        unsubscribe();
                                        // is error we resubscribe
                                        subscribe();
                                    }
                                }
                            });
                }
            };
            worker.schedule(restart);
        }
    }

}
