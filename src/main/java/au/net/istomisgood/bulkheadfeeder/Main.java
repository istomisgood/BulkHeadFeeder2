package au.net.istomisgood.bulkheadfeeder;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
public class Main {
    public static void main(String[] args) throws Exception {
        List<Job> jobList = IntStream.range(0, 100)
                .mapToObj(i -> new Job("" + i))
                .collect(Collectors.toList());
        log.debug("jobList.size {}", jobList.size());

        Feeder feeder = new Feeder(jobList);
        List<String> stringList = feeder.feed();
        stringList.forEach(log::debug);
    }
}

