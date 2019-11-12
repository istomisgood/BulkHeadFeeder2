package au.net.istomisgood.bulkheadfeeder;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.mockito.Mockito;

class PartitionSupplierCommandTest {

  @Test
  void whenExecuted_thenSuccess(TestInfo testInfo) {
    AvailableSupplier<String> mock = Mockito.mock(AvailableSupplier.class);
    Mockito.when(mock.get()).thenReturn(testInfo.getDisplayName());
    SupplierCommand<AvailableSupplier<String>, String> command = new SupplierCommand(mock, "hck");
    String result = command.execute();
    Assertions.assertSame(result, testInfo.getDisplayName());
  }

  @Test
  void whenException_thenFallback(TestInfo testInfo) {
    AvailableSupplier<String> mock = Mockito.mock(AvailableSupplier.class);
    Mockito.when(mock.get()).thenThrow(new RuntimeException(testInfo.getDisplayName()));
    Mockito.when(mock.getFallback()).thenReturn(testInfo.getDisplayName());

    SupplierCommand<AvailableSupplier<String>, String> command = new SupplierCommand(mock, "hck");
    String result = command.execute();
    Assertions.assertSame(result, testInfo.getDisplayName());
  }
}