package software.uncharted.terarium.taskrunner.service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.FileCopyUtils;

import lombok.extern.slf4j.Slf4j;
import software.uncharted.terarium.taskrunner.TaskRunnerApplicationTests;
import software.uncharted.terarium.taskrunner.models.task.TaskRequest;
import software.uncharted.terarium.taskrunner.models.task.TaskStatus;

@Slf4j
public class TaskTests extends TaskRunnerApplicationTests {

	private final int REPEAT_COUNT = 1;
	private final String TEST_INPUT = "{\"research_paper\":\"Test research paper\"}";
	private final String FAILURE_INPUT = "{\"should_fail\":true}";
	private final String SCRIPT_PATH = getClass().getResource("/echo.py").getPath();

	@RepeatedTest(REPEAT_COUNT)
	public void testTaskSuccess() throws Exception {

		TaskRequest req = new TaskRequest();
		req.setId(UUID.randomUUID());
		req.setScript(SCRIPT_PATH);
		req.setInput(new String(TEST_INPUT).getBytes());

		int ONE_MINUTE = 1;

		Task task = new Task(req);
		try {
			Assertions.assertEquals(TaskStatus.QUEUED, task.getStatus());
			task.start();

			Assertions.assertEquals(TaskStatus.RUNNING, task.getStatus());
			task.writeInputWithTimeout(req.getInput(), ONE_MINUTE);

			byte[] output = task.readOutputWithTimeout(ONE_MINUTE);
			Assertions.assertArrayEquals(req.getInput(), output);

			task.waitFor(ONE_MINUTE);
			Assertions.assertEquals(TaskStatus.SUCCESS, task.getStatus());

		} catch (Exception e) {
			throw e;
		} finally {
			task.cleanup();
		}
	}

	@RepeatedTest(REPEAT_COUNT)
	public void testTaskLargeInputOutput() throws Exception {

		ClassPathResource resource = new ClassPathResource("test_input.json");
		String input = new String(FileCopyUtils.copyToByteArray(resource.getInputStream()), StandardCharsets.UTF_8);

		TaskRequest req = new TaskRequest();
		req.setId(UUID.randomUUID());
		req.setScript(SCRIPT_PATH);
		req.setInput(input.getBytes());

		int ONE_MINUTE = 1;

		Task task = new Task(req);
		try {
			Assertions.assertEquals(TaskStatus.QUEUED, task.getStatus());
			task.start();

			Assertions.assertEquals(TaskStatus.RUNNING, task.getStatus());
			task.writeInputWithTimeout(req.getInput(), ONE_MINUTE);

			byte[] output = task.readOutputWithTimeout(ONE_MINUTE);

			Assertions.assertEquals(input, new String(output));
			Assertions.assertArrayEquals(input.getBytes(), output);

			task.waitFor(ONE_MINUTE);
			Assertions.assertEquals(TaskStatus.SUCCESS, task.getStatus());

		} catch (Exception e) {
			throw e;
		} finally {
			task.cleanup();
		}
	}

	@RepeatedTest(REPEAT_COUNT)
	public void testTaskFailure() throws Exception {
		TaskRequest req = new TaskRequest();
		req.setId(UUID.randomUUID());
		req.setScript(SCRIPT_PATH);
		req.setInput(new String(FAILURE_INPUT).getBytes());

		int ONE_MINUTE = 1;

		Task task = new Task(req);
		try {
			Assertions.assertEquals(TaskStatus.QUEUED, task.getStatus());
			task.start();

			Assertions.assertEquals(TaskStatus.RUNNING, task.getStatus());
			task.writeInputWithTimeout(req.getInput(), ONE_MINUTE);

			byte[] output = task.readOutputWithTimeout(ONE_MINUTE);
			Assertions.assertArrayEquals(req.getInput(), output);

			task.waitFor(ONE_MINUTE);

		} catch (InterruptedException e) {
			// this should happen
		} finally {
			task.cleanup();
		}

		Assertions.assertEquals(TaskStatus.FAILED, task.getStatus());
	}

	@RepeatedTest(REPEAT_COUNT)
	public void testTaskCancel() throws Exception {

		TaskRequest req = new TaskRequest();
		req.setId(UUID.randomUUID());
		req.setScript(SCRIPT_PATH);
		req.setInput(new String(TEST_INPUT).getBytes());

		int ONE_MINUTE = 1;

		Task task = new Task(req);
		try {
			Assertions.assertEquals(TaskStatus.QUEUED, task.getStatus());
			task.start();

			Assertions.assertEquals(TaskStatus.RUNNING, task.getStatus());
			task.writeInputWithTimeout(req.getInput(), ONE_MINUTE);

			new Thread(() -> {
				try {
					Thread.sleep(1000);
					boolean res = task.cancel();
					Assertions.assertTrue(res);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}).start();

			byte[] output = task.readOutputWithTimeout(ONE_MINUTE);
			Assertions.assertArrayEquals(req.getInput(), output);

			task.waitFor(ONE_MINUTE);

		} catch (InterruptedException e) {
			// this should happen
		} finally {
			task.cleanup();
		}

		Assertions.assertEquals(TaskStatus.CANCELLED, task.getStatus());
	}

	@RepeatedTest(REPEAT_COUNT)
	public void testTaskCancelMultipleTimes() throws Exception {

		TaskRequest req = new TaskRequest();
		req.setId(UUID.randomUUID());
		req.setScript(SCRIPT_PATH);
		req.setInput(new String(TEST_INPUT).getBytes());

		int ONE_MINUTE = 1;

		Task task = new Task(req);
		try {
			Assertions.assertEquals(TaskStatus.QUEUED, task.getStatus());
			task.start();

			Assertions.assertEquals(TaskStatus.RUNNING, task.getStatus());
			task.writeInputWithTimeout(req.getInput(), ONE_MINUTE);

			new Thread(() -> {
				try {
					Thread.sleep(1000);
					boolean cancelled = false;
					for (int i = 0; i < 10; i++) {
						boolean res = task.cancel();
						if (cancelled) {
							Assertions.assertFalse(res);
						} else {
							Assertions.assertTrue(res);
							cancelled = true;
						}
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}).start();

			byte[] output = task.readOutputWithTimeout(ONE_MINUTE);
			Assertions.assertArrayEquals(req.getInput(), output);

			task.waitFor(ONE_MINUTE);

		} catch (InterruptedException e) {
			// this should happen
		} finally {
			task.cleanup();
		}

		Assertions.assertEquals(TaskStatus.CANCELLED, task.getStatus());

	}

	@RepeatedTest(REPEAT_COUNT)
	public void testTaskCancelBeforeStart() throws Exception {

		TaskRequest req = new TaskRequest();
		req.setId(UUID.randomUUID());
		req.setScript(SCRIPT_PATH);
		req.setInput(new String(TEST_INPUT).getBytes());

		Task task = new Task(req);
		try {
			Assertions.assertEquals(TaskStatus.QUEUED, task.getStatus());
			task.cancel();

			Assertions.assertEquals(TaskStatus.CANCELLED, task.getStatus());
			task.start();

			// we should not each this code
			Assertions.assertTrue(false);

		} catch (InterruptedException e) {
			// this should happen
		} finally {
			task.cleanup();
		}

		Assertions.assertEquals(TaskStatus.CANCELLED, task.getStatus());
	}

	@RepeatedTest(REPEAT_COUNT)
	public void testTaskSoakTest() throws Exception {
		int NUM_REQUESTS = 64;
		int NUM_THREADS = 8;
		int ONE_MINUTE = 1;
		int TIMEOUT_SECONDS = 60;

		ExecutorService executor = Executors.newFixedThreadPool(NUM_THREADS);

		ConcurrentHashMap<UUID, TaskStatus> responses = new ConcurrentHashMap<>();
		ConcurrentHashMap<UUID, TaskStatus> expectedResponses = new ConcurrentHashMap<>();
		List<Future<?>> futures = new ArrayList<>();

		Random rand = new Random();

		for (int i = 0; i < NUM_REQUESTS; i++) {
			Thread.sleep(500);
			Future<?> future = executor.submit(() -> {
				try {
					TaskRequest req = new TaskRequest();
					req.setId(UUID.randomUUID());
					req.setScript(SCRIPT_PATH);
					req.setTimeoutMinutes(1);

					boolean shouldCancelBefore = false;
					boolean shouldCancelAfter = false;
					TaskStatus expected = null;

					int randomNumber = rand.nextInt(4);
					switch (randomNumber) {
						case 0:
							// success
							req.setInput(new String(TEST_INPUT).getBytes());
							expected = TaskStatus.SUCCESS;
							break;
						case 1:
							// failure
							req.setInput(new String(FAILURE_INPUT).getBytes());
							expected = TaskStatus.FAILED;
							break;
						case 2:
							// cancellation
							req.setInput(new String(TEST_INPUT).getBytes());
							expected = TaskStatus.CANCELLED;
							shouldCancelBefore = true;
							break;
						case 3:
							// cancellation
							req.setInput(new String(TEST_INPUT).getBytes());
							expected = TaskStatus.CANCELLED;
							shouldCancelAfter = true;
							break;
						default:
							throw new RuntimeException("This shouldnt happen");
					}
					expectedResponses.put(req.getId(), expected);

					Task task = new Task(req);
					Assertions.assertEquals(TaskStatus.QUEUED, task.getStatus());

					if (shouldCancelBefore) {
						// dispatch the cancellation
						new Thread(new Runnable() {
							@Override
							public void run() {
								try {
									boolean res = task.cancel();
									Assertions.assertTrue(res);
								} catch (Exception e) {
									e.printStackTrace();
								}
							}
						}).start();
					}

					task.start();

					if (shouldCancelAfter) {
						// dispatch the cancellation
						new Thread(new Runnable() {
							@Override
							public void run() {
								try {
									Thread.sleep((long) (1000 * Math.random()));
									boolean res = task.cancel();
									Assertions.assertTrue(res);
								} catch (Exception e) {
									e.printStackTrace();
								}
							}
						}).start();
					}

					try {
						task.writeInputWithTimeout(req.getInput(), ONE_MINUTE);

						byte[] output = task.readOutputWithTimeout(ONE_MINUTE);
						Assertions.assertArrayEquals(req.getInput(), output);

						task.waitFor(ONE_MINUTE);

						responses.put(req.getId(), TaskStatus.SUCCESS);

					} catch (TimeoutException | InterruptedException e) {
						if (expected == TaskStatus.CANCELLED) {
							responses.put(req.getId(), TaskStatus.CANCELLED);
						} else if (expected == TaskStatus.FAILED) {
							// this should happen
							responses.put(req.getId(), TaskStatus.FAILED);
						} else {
							throw e;
						}
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			});

			futures.add(future);
		}

		// wait for all the responses to be send
		for (Future<?> future : futures) {
			future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
		}

		// check that the responses are valid
		for (Map.Entry<UUID, TaskStatus> response : responses.entrySet()) {
			UUID id = response.getKey();
			TaskStatus expected = expectedResponses.get(id);

			Assertions.assertEquals(expected, response.getValue());
		}
	}
}
