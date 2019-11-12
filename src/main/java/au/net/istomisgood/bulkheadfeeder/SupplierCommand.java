package au.net.istomisgood.bulkheadfeeder;

import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandKey;

public class SupplierCommand<T extends AvailableSupplier<R>, R> extends HystrixCommand<R> {

  private final T supplier;

  public SupplierCommand(T supplier, String hystrixCommandKey) {
    super(Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey(supplier.getClass().getSimpleName())).andCommandKey(HystrixCommandKey.Factory.asKey(hystrixCommandKey)));
    this.supplier = supplier;
  }

  @Override
  protected R run() {
    return supplier.get();
  }

  @Override
  protected R getFallback() {
    return supplier.getFallback();
  }
}
