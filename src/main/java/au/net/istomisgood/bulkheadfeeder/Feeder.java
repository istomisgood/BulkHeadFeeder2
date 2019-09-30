package au.net.istomisgood.bulkheadfeeder;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.bulkhead.event.BulkheadEvent;
import io.reactivex.rxjava3.subjects.Subject;
import io.reactivex.rxjava3.subjects.UnicastSubject;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

@Slf4j
public class Feeder {
    private final ExecutorService executorService;
    private final Bulkhead bulkhead;
    private final List<Job> jobList;
    private final ConcurrentHashMap<String, CompletableFuture<String>> jobFutures;

    private Object stick = new Object();

    private Subject<BulkheadEvent> bus = UnicastSubject.create();

    public Feeder(ExecutorService executorService, List<Job> jobList) {
        this.executorService = executorService;
        this.jobList = jobList;
        this.bulkhead = getBulkhead();

        this.jobFutures = new ConcurrentHashMap<>();

        jobList.stream().forEach(job -> this.jobFutures.put(job.getName(), new CompletableFuture<String>()));

        bus.subscribe(o -> {
            switch (o.getEventType()) {
                case CALL_FINISHED:
                    onFinished();
                    break;
                case CALL_REJECTED:
                    log.warn(o.toString());
                case CALL_PERMITTED:
                    onPermitted();
                    break;
            }
        });
    }

    public List<String> feed() {
        log.debug("Start feed");

        CompletableFuture<Void> allOf = CompletableFuture.allOf(this.jobFutures.values().stream().toArray(CompletableFuture[]::new));
        allOf.whenComplete((aVoid, throwable) -> {
            log.debug("allOf complete");
            this.bus.onComplete();
        });
        CompletableFuture<List<String>> futureResults = allOf.thenApply(aVoid -> this.jobFutures.values().stream().map(CompletableFuture::join).collect(Collectors.toList()));

        while (bulkhead.tryAcquirePermission()) ;

        try {
            return futureResults.get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void acquireNext() {
        bulkhead.tryAcquirePermission();
    }

    private void performJob(Job job) {
        log.debug("Before submit job {}", job.getName());
        executorService.submit(() -> {
            log.debug("submitted to executor job: {}", job.getName());
            jobFutures.get(job.getName()).complete(job.get());
            bulkhead.onComplete();
        });
        log.debug("After submit job {}", job.getName());
    }

    private void onPermitted() {
        final Optional<Job> optionalJob;
        synchronized (stick) {
            optionalJob = jobList.stream().filter(job -> job.getStatus() == Job.Status.AVAILABLE).findAny();
            if (optionalJob.isPresent()) {
                Job job = optionalJob.get();
                job.setStatus(Job.Status.PERMITTED);
            }
        }
        if (optionalJob.isPresent()) {
            performJob(optionalJob.get());
        }
    }

    private void onFinished() {
        acquireNext();
    }

    private Bulkhead getBulkhead() {
        BulkheadConfig config = BulkheadConfig.custom()
                .maxConcurrentCalls(3)
                .build();
        BulkheadRegistry registry = BulkheadRegistry.of(config);

        Bulkhead bulkhead = registry.bulkhead("TomsBulkHead");

        bulkhead.getEventPublisher().onEvent(this.bus::onNext);
        return bulkhead;
    }

}
