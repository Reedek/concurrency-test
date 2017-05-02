package ua.nikitos.concurrency;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.IntStream;

import static ua.nikitos.concurrency.ConcurrentUtils.stop;

@RunWith(JUnit4.class)
public class ExecutorsExampleTest {
    @Test
    public void testSimpleExecutor(){
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            try {
                TimeUnit.SECONDS.sleep(5);
                String name = Thread.currentThread().getName();
                System.out.println("task finished: " + name);
            }
            catch (InterruptedException e) {
                System.err.println("task interrupted");
            }
        });

        stop(executor);
    }

    @Test
    public void testCallableFuture()
            throws ExecutionException, InterruptedException {
        Callable<Integer> task = () -> {
            try {
                TimeUnit.SECONDS.sleep(1);
                return 123;
            }
            catch (InterruptedException e) {
                throw new IllegalStateException("task interrupted", e);
            }
        };

        //number of threads
        ExecutorService executor = Executors.newFixedThreadPool(1);
        Future<Integer> future = executor.submit(task);

        System.out.println("future done? " + future.isDone());

        //Calling the method get() blocks the current thread and waits until the callable completes before returning the actual result 123
        Integer result = future.get();

        System.out.println("future done? " + future.isDone());
        System.out.print("result: " + result);
    }

    @Test
    public void testWorkstealingPool() throws InterruptedException {
        //thread pool using all available processors (Runtime#availableProcessors) as its target parallelism level
        //ForkJoinPool type executor
        ExecutorService executor = Executors.newWorkStealingPool();

        List<Callable<String>> callables = Arrays.asList(
                () -> "task1",
                () -> "task2",
                () -> "task3");

        executor.invokeAll(callables)
                .stream()
                .map(future -> {
                    try {
                        return future.get();
                    }
                    catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                })
                .forEach(System.out::println);
    }

    @Test
    public void testInvokeAny()
            throws ExecutionException, InterruptedException {
        ExecutorService executor = Executors.newWorkStealingPool();

        List<Callable<String>> callables = Arrays.asList(
                callable("task1", 3),
                callable("task2", 2),
                callable("task3", 4));

        //Instead of returning future objects this method blocks until the first callable terminates and returns the result of that callable.
        String result = executor.invokeAny(callables);
        System.out.println(result);
    }

    @Test
    public void testScheduledExecutor() throws InterruptedException {
        //A ScheduledExecutorService is capable of scheduling tasks to run either periodically or once after a certain amount of time has elapsed
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);

        Runnable task = () -> System.out.println("Scheduling: " + System.nanoTime());
        ScheduledFuture<?> future = executor.schedule(task, 3, TimeUnit.SECONDS);

        TimeUnit.MILLISECONDS.sleep(1337);

        long remainingDelay = future.getDelay(TimeUnit.MILLISECONDS);
        System.out.printf("Remaining Delay: %sms", remainingDelay);

        Runnable task1 = () -> System.out.println("Scheduling: " + System.nanoTime());

        int initialDelay = 0;
        int period = 1;
        executor.scheduleAtFixedRate(task1, initialDelay, period, TimeUnit.SECONDS);
        //scheduleAtFixedRate - doesn't take into account duration of the task, If any execution of this task takes longer than its period, then subsequent executions
        // may start late, but will not concurrently execute
        //scheduleWithFixedDelay - does tak into account duration of the task (wait time period applies between the end of a task and the start of the next task)
    }

    @Test
    public void testCachedExecutor() throws InterruptedException {
        //Creates a thread pool that creates new threads as needed, but will reuse previously constructed threads when they are available
        ExecutorService executor = Executors.newCachedThreadPool();

        IntStream.range(0, 100)
                .forEach(i -> executor.submit(callablePrintThread()));

        stop(executor);
    }

    @Test
    public void testCachedPool() throws InterruptedException {
        ForkJoinPool.commonPool();
        //Creates a thread pool that creates new threads as needed, but will reuse previously constructed threads when they are available
        ExecutorService executor = Executors.newCachedThreadPool();

        IntStream.range(0, 100)
                .forEach(i -> executor.submit(callablePrintThread()));

        stop(executor);
    }

    @Test
    public void testBlurImageWithoutWorkJoin() throws IOException {
        String srcName = "src/test/resources/1111.png";
        File srcFile = new File(srcName);
        BufferedImage image = ImageIO.read(srcFile);

        System.out.println("Source image: " + srcName);


        int w = image.getWidth();
        int h = image.getHeight();
        int[] src = image.getRGB(0, 0, w, h, null, 0, w);
        int[] dst = new int[src.length];

        ForkBlur fb = new ForkBlur(src, 0, src.length, dst);

        long startTime = System.currentTimeMillis();
        fb.computeDirectly();
        long endTime = System.currentTimeMillis();

        System.out.println("Image blur took " + (endTime - startTime) +
                " milliseconds.");
    }


    @Test
    public void testForkJoinPool() throws IOException, InterruptedException {
        String srcName = "src/test/resources/1111.png";
        File srcFile = new File(srcName);
        BufferedImage image = ImageIO.read(srcFile);

        System.out.println("Source image: " + srcName);

        BufferedImage blurredImage = ForkBlur.blur(image);

        String dstName = "src/test/resources/1111-blurred.png";
        File dstFile = new File(dstName);
        ImageIO.write(blurredImage, "png", dstFile);

        System.out.println("Output image: " + dstName);
    }

    Callable<String> callable(String result, long sleepSeconds) {
        return () -> {
            TimeUnit.SECONDS.sleep(sleepSeconds);
            return result;
        };
    }

    Callable<String> callablePrintThread() {
        return () -> {
            String threadName = Thread.currentThread().getName();
            System.out.println(threadName);
            return threadName;
        };
    }
}
