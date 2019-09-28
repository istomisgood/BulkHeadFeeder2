package au.net.istomisgood.bulkheadfeeder;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Data
@Slf4j
@RequiredArgsConstructor
public class Job implements Supplier<String> {
    public enum Status {AVAILABLE, PERMITTED}

    private final String name;
    private Status status = Status.AVAILABLE;

    @Override
    public String get() {
        log.debug("Inside Get Job {}", name);
        int timeout = new Random().nextInt(1000);
        log.debug("Job {} sleeping for {}", name, timeout);
        try {
            TimeUnit.MILLISECONDS.sleep(timeout);
        } catch (InterruptedException e) {
            log.error(e.getMessage(), e);
        }
        return name;
    }
}
