package au.net.istomisgood.bulkheadfeeder;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
public class Main {
  public static void main(String[] args) throws Exception {
    List<AvailableSupplier> jobList = IntStream.range(0, 100)
        .mapToObj(i -> new SomeJob("" + i))
        .collect(Collectors.toList());
    log.debug("jobList.size {}", jobList.size());

    MDC.put("CorrId", "asdf");

    SupplierRunner supplierRunner = new SupplierRunner("hck", 13, "someCorrId", jobList);
    List<String> stringList = supplierRunner.run();
    stringList.forEach(log::debug);
  }
}

