package au.net.istomisgood.bulkheadfeeder;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class SomeJob extends AvailableSupplier<String> {

  private final String name;

  @Override
  public String getFallback() {
    return name + "-fallback";
  }

  @Override
  public String get() {
    return name;
  }
}
