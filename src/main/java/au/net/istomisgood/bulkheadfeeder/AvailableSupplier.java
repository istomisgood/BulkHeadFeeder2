package au.net.istomisgood.bulkheadfeeder;

import java.util.function.Supplier;

public abstract class AvailableSupplier<T> implements Supplier<T> {

  private enum Status {
    AVAILABLE, PERMITTED
  }

  private Status status = Status.AVAILABLE;

  public abstract T getFallback();

  public boolean isAvailable() {
    return this.status == Status.AVAILABLE;
  }

  public void markPermitted() {
    this.status = Status.PERMITTED;
  }
}
