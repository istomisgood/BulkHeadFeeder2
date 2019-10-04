package au.net.istomisgood.bulkheadfeeder;

import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;

import java.util.function.Supplier;

public class HystrixJob extends HystrixCommand<String> {

    private final Supplier<String> supplier;

    public HystrixJob(Supplier supplier) {
        super(HystrixCommandGroupKey.Factory.asKey("ExampleGroup"));
        this.supplier = supplier;
    }

    @Override
    protected String run() {
        return supplier.get();
    }

    @Override
    protected String getFallback() {
        return "Fallback XXXX";
    }
}