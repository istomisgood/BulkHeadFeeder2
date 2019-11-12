package au.net.istomisgood.bulkheadfeeder;

import com.netflix.hystrix.HystrixCommand;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.event.BulkheadEvent;
import io.github.resilience4j.core.EventConsumer;
import io.reactivex.rxjava3.subjects.PublishSubject;
import io.reactivex.rxjava3.subjects.Subject;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import rx.Observable;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Slf4j
public class SupplierRunner<T> {

  private final Map<AvailableSupplier<T>, CompletableFuture<T>> jobFutures;
  private final Subject<BulkheadEvent> eventBus;

  private final Consumer<Runnable> mdcFunc;
  private final String hystrixCommandKey;
  private final Bulkhead bulkhead;

  public SupplierRunner(String hystrixCommandKey, int numberConcurrent, String identifier, List<AvailableSupplier<T>> supplierList) {
    this.hystrixCommandKey = hystrixCommandKey;
    this.eventBus = buildEventBus();
    this.bulkhead = buildBulkhead(identifier, numberConcurrent, this.eventBus::onNext);
    this.jobFutures = supplierList.stream().collect(Collectors.toMap(k -> k, v -> new CompletableFuture<>()));
    this.mdcFunc = buildMdcFunc();
  }

  public List<T> run() {
    log("Start");

    Future<List<T>> result = configureFutures();
    loadBulkhead();

    try {
      log("GetResults");
      return result.get();
    } catch (Exception e) {
      throw new RuntimeException(e);
    } finally {
      log("Finally");
    }
  }

  private Future<List<T>> configureFutures() {
    return CompletableFuture.allOf(
        this.jobFutures.values().stream().map(cf -> cf.whenCompleteAsync((a, t) -> mdcFunc.accept(() -> {
              log("Job Complete");
              this.bulkhead.onComplete();
            })
        )).toArray(CompletableFuture[]::new))
        .whenComplete((aaVoid, throwable) -> mdcFunc.accept(() -> {
          log("All Complete");
          this.eventBus.onComplete();
        }))
        .thenApply(aVoid -> this.jobFutures.values().stream().map(CompletableFuture::join).collect(Collectors.toList()));
  }

  private void loadBulkhead() {
    for (int i = Math.min(bulkhead.getMetrics().getAvailableConcurrentCalls(), this.jobFutures.size()); i > 0; i--) {
      log("Loading");
      bulkhead.tryAcquirePermission();
    }
    log("Loaded");
  }

  private void submitSupplier(AvailableSupplier<T> job) {
    log("Submit " + job.hashCode());
    HystrixCommand hystrixJob = new SupplierCommand(job, hystrixCommandKey);
    Observable<T> observable = hystrixJob.toObservable();
    observable.subscribe(s -> {
      log("onNext " + job.hashCode());
      jobFutures.get(job).complete(s);
    });
  }

  private void onPermitted() {
    log("Permitted");
    final Optional<AvailableSupplier<T>> optionalJob;
    synchronized (jobFutures) {
      optionalJob = jobFutures.keySet().stream().filter(AvailableSupplier::isAvailable).findAny();
      if (optionalJob.isPresent()) {
        optionalJob.get().markPermitted();
      }
    }
    if (optionalJob.isPresent()) {
      submitSupplier(optionalJob.get());
    } else {
      bulkhead.releasePermission();
      log("No Jobs Available");
    }
  }

  private void onFinished() {
    log("Finished");
    bulkhead.tryAcquirePermission();
  }

  //Capture bulkhead event and place on our eventbus
  private Subject<BulkheadEvent> buildEventBus() {
    Subject<BulkheadEvent> eventBus = PublishSubject.create();
    eventBus.subscribe(o -> {
      switch (o.getEventType()) {
        case CALL_FINISHED:
          onFinished();
          break;
        case CALL_PERMITTED:
          onPermitted();
          break;
        case CALL_REJECTED:
        default:
          log.warn(o.toString());
      }
    });
    return eventBus;
  }

  private Bulkhead buildBulkhead(String identifier, int maxCallsPerRequest, EventConsumer<BulkheadEvent> eventConsumer) {
    Bulkhead bulkhead = Bulkhead.of(identifier + "---" + UUID.randomUUID().toString().substring(0, 7),
        BulkheadConfig.custom().maxConcurrentCalls(maxCallsPerRequest).build());
    log.debug("Bulkhead config {} {}", bulkhead.getName(), bulkhead.getBulkheadConfig().getMaxConcurrentCalls());
    bulkhead.getEventPublisher().onEvent(eventConsumer);
    return bulkhead;
  }

  // MDC hacks to deal with ForkJoinPool.commonPool-worker-x
  private Consumer<Runnable> buildMdcFunc() {
    Map<String, String> contextMap = MDC.getCopyOfContextMap();
    return runnable -> {
      MDC.setContextMap(contextMap);
      try {
        runnable.run();
      } finally {
        MDC.clear();
      }
    };
  }

  private void log(String event) {
    log.info("Bulkhead {} perms {} submitted {} completed {} of {} {}",
        bulkhead.getName(),
        bulkhead.getMetrics().getAvailableConcurrentCalls(),
        this.jobFutures.keySet().stream().filter(j -> !j.isAvailable()).count(),
        this.jobFutures.values().stream().filter(CompletableFuture::isDone).count(),
        this.jobFutures.size(),
        event == null ? "" : event);
  }

}
