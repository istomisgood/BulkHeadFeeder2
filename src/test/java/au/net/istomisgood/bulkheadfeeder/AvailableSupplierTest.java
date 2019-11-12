package au.net.istomisgood.bulkheadfeeder;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AvailableSupplierTest {

  @Test
  void whenPartitionSupplier_thenNotAvailable() {
    AvailableSupplier<List<String>> partitionSupplier = new AvailableSupplier<List<String>>() {
      @Override
      public List<String> get() {
        return null;
      }

      @Override
      public List<String> getFallback() {
        return null;
      }
    };

    assertTrue(partitionSupplier.isAvailable());
    partitionSupplier.markPermitted();
    assertFalse(partitionSupplier.isAvailable());
  }
}