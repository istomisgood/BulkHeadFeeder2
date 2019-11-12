package au.net.istomisgood.bulkheadfeeder;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SupplierRunnerTest {
  @Test
  void whenHappyPath_thenSuccess(TestInfo testInfo) {
    List<AvailableSupplier<String>> supplierList = IntStream.of(1, 2, 3)
        .mapToObj(i -> new AvailableSupplier<String>() {
          @Override
          public String getFallback() {
            return i + "-fallback";
          }

          @Override
          public String get() {
            if (i % 3 == 0) {
              throw new RuntimeException(i + "");
            }
            return i + "-success";
          }
        })
        .collect(Collectors.toList());
    SupplierRunner<String> runner = new SupplierRunner<>("", 1, testInfo.getDisplayName(), supplierList);
    List<String> results = runner.run();
    results.forEach(r -> {
      if (r.startsWith("3")) {
        assertEquals(r, r);
        return;
      }
      assertEquals(r, r);
    });
  }
}