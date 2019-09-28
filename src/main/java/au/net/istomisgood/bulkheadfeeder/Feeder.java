package au.net.istomisgood.bulkheadfeeder;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

@Slf4j
public class Feeder {
    private final ExecutorService executorService;
    private final Bulkhead bulkhead;
    private final List<Job> jobList;
    private final ConcurrentHashMap<String, CompletableFuture<String>> jobFutures;

    private Object stick = new Object();

    public Feeder(ExecutorService executorService, List<Job> jobList) {
        this.executorService = executorService;
        this.jobList = jobList;
        this.bulkhead = getBulkhead();

        this.jobFutures = new ConcurrentHashMap<>();

        jobList.stream().forEach(job -> this.jobFutures.put(job.getName(), new CompletableFuture<String>()));
    }

    public List<String> feed() {

        log.debug("Start feed");

        CompletableFuture<Void> allOf = CompletableFuture.allOf(this.jobFutures.values().stream().toArray(CompletableFuture[]::new));
        allOf.whenComplete((aVoid, throwable) -> {
            log.debug("allOf complete");
        });

        for (int i = 0; i < this.bulkhead.getBulkheadConfig().getMaxConcurrentCalls(); i++) {
            acquireNext();
        }

        try {
            log.debug("Feed before get");
            allOf.get();
            log.debug("Feed after get");
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }

        return null;
    }

    private void acquireNext() {
        bulkhead.acquirePermission();
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

        bulkhead.getEventPublisher().onCallFinished(event -> {
            log.debug("onCallFinished {}", event.toString());
            onFinished();
        });
        bulkhead.getEventPublisher().onCallPermitted(event -> {
            log.debug("onCallPermitted {}", event.toString());
            onPermitted();
        });
        bulkhead.getEventPublisher().onCallRejected(event -> log.debug("onCallRejected {}", event.toString()));
        return bulkhead;
    }

}
