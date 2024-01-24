package software.uncharted.terarium.taskrunner.service;

import java.util.UUID;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import software.uncharted.terarium.taskrunner.TaskRunnerApplicationTests;
import software.uncharted.terarium.taskrunner.models.task.TaskRequest;
import software.uncharted.terarium.taskrunner.models.task.TaskStatus;

public class TaskTests extends TaskRunnerApplicationTests {

	@Test
	public void testTaskSuccess() throws Exception {

		TaskRequest req = new TaskRequest();
		req.setId(UUID.randomUUID());
		req.setTaskKey("ml");
		req.setInput(new String("{\"research_paper\": \"Test research paper\"}").getBytes());

		int ONE_MINUTE = 1;

		Task task = new Task(req.getId(), req.getTaskKey());
		try {
			Assertions.assertEquals(task.getStatus(), TaskStatus.QUEUED);
			task.start();

			Assertions.assertEquals(task.getStatus(), TaskStatus.RUNNING);
			task.writeInputWithTimeout(req.getInput(), ONE_MINUTE);

			byte[] output = task.readOutputWithTimeout(ONE_MINUTE);
			Assertions.assertNotNull(output);
			Assertions.assertTrue(output.length > 0);

			task.waitFor(ONE_MINUTE);
			Assertions.assertEquals(task.getStatus(), TaskStatus.SUCCESS);

		} catch (Exception e) {
			throw e;
		} finally {
			task.cleanup();
		}
	}

	@Test
	public void testTaskFailure() throws Exception {
		TaskRequest req = new TaskRequest();
		req.setId(UUID.randomUUID());
		req.setTaskKey("ml");
		req.setInput(new String("{\"should_fail\": true}").getBytes());

		int ONE_MINUTE = 1;

		Task task = new Task(req.getId(), req.getTaskKey());
		try {
			Assertions.assertEquals(task.getStatus(), TaskStatus.QUEUED);
			task.start();

			Assertions.assertEquals(task.getStatus(), TaskStatus.RUNNING);
			task.writeInputWithTimeout(req.getInput(), ONE_MINUTE);

			byte[] output = task.readOutputWithTimeout(ONE_MINUTE);

			System.out.println(output);

			task.waitFor(ONE_MINUTE);

		} catch (InterruptedException e) {
			// this should happen
		} finally {
			task.cleanup();
		}

		Assertions.assertEquals(task.getStatus(), TaskStatus.FAILED);
	}

	@Test
	public void testTaskCancel() throws Exception {

		TaskRequest req = new TaskRequest();
		req.setId(UUID.randomUUID());
		req.setTaskKey("ml");
		req.setInput(new String("{\"research_paper\": \"Test research paper\"}").getBytes());

		int ONE_MINUTE = 1;

		Task task = new Task(req.getId(), req.getTaskKey());
		try {
			Assertions.assertEquals(task.getStatus(), TaskStatus.QUEUED);
			task.start();

			Assertions.assertEquals(task.getStatus(), TaskStatus.RUNNING);
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

			System.out.println(output);

			task.waitFor(ONE_MINUTE);

		} catch (InterruptedException e) {
			// this should happen
		} finally {
			task.cleanup();
		}

		Assertions.assertEquals(task.getStatus(), TaskStatus.CANCELLED);

	}

	@Test
	public void testTaskCancelMultipleTimes() throws Exception {

		TaskRequest req = new TaskRequest();
		req.setId(UUID.randomUUID());
		req.setTaskKey("ml");
		req.setInput(new String("{\"research_paper\": \"Test research paper\"}").getBytes());

		int ONE_MINUTE = 1;

		Task task = new Task(req.getId(), req.getTaskKey());
		try {
			Assertions.assertEquals(task.getStatus(), TaskStatus.QUEUED);
			task.start();

			Assertions.assertEquals(task.getStatus(), TaskStatus.RUNNING);
			task.writeInputWithTimeout(req.getInput(), ONE_MINUTE);

			new Thread(() -> {
				try {
					Thread.sleep(1000);
					boolean cancelled = false;
					for (int i = 0; i < 10; i++) {
						boolean res = task.cancel();
						if (cancelled) {
							Assertions.assertTrue(false);
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

			System.out.println(output);

			task.waitFor(ONE_MINUTE);

		} catch (InterruptedException e) {
			// this should happen
		} finally {
			task.cleanup();
		}

		Assertions.assertEquals(task.getStatus(), TaskStatus.CANCELLED);

	}

	@Test
	public void testTaskCancelBeforeStart() throws Exception {

		TaskRequest req = new TaskRequest();
		req.setId(UUID.randomUUID());
		req.setTaskKey("ml");
		req.setInput(new String("{\"research_paper\": \"Test research paper\"}").getBytes());

		Task task = new Task(req.getId(), req.getTaskKey());
		try {
			Assertions.assertEquals(task.getStatus(), TaskStatus.QUEUED);
			task.cancel();

			Assertions.assertEquals(task.getStatus(), TaskStatus.CANCELLED);
			task.start();

			// we should not each this code
			Assertions.assertTrue(false);

		} catch (InterruptedException e) {
			// this should happen
		} finally {
			task.cleanup();
		}

		Assertions.assertEquals(task.getStatus(), TaskStatus.CANCELLED);

	}
}
