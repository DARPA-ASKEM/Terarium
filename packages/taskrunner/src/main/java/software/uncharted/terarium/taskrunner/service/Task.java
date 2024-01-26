package software.uncharted.terarium.taskrunner.service;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import software.uncharted.terarium.taskrunner.models.task.TaskStatus;
import software.uncharted.terarium.taskrunner.util.ScopedLock;
import software.uncharted.terarium.taskrunner.util.TimeFormatter;

@Data
@Slf4j
public class Task {

	private UUID id;
	private String taskKey;
	private ObjectMapper mapper;
	private ProcessBuilder processBuilder;
	private Process process;
	private CompletableFuture<Integer> processFuture;
	private String inputPipeName;
	private String outputPipeName;
	private TaskStatus status = TaskStatus.QUEUED;
	private ScopedLock lock = new ScopedLock();
	private String script;
	private int NUM_THREADS = 8;
	ExecutorService executor = Executors.newFixedThreadPool(NUM_THREADS);

	private int PROCESS_KILL_TIMEOUT_SECONDS = 10;

	public Task(UUID id, String taskKey) throws IOException, InterruptedException {
		mapper = new ObjectMapper();

		this.id = id;
		this.taskKey = taskKey;
		inputPipeName = "/tmp/input-" + id;
		outputPipeName = "/tmp/output-" + id;

		try {
			setup();
		} catch (Exception e) {
			cleanup();
			throw e;
		}
	}

	private void setup() throws IOException, InterruptedException {
		script = getClass().getResource("/" + taskKey + ".py").getPath();

		log.debug("Creating input and output pipes: {} {} for task {}", inputPipeName, outputPipeName, id);

		// Create the named pipes
		Process inputPipe = new ProcessBuilder("mkfifo", inputPipeName).start();
		int exitCode = inputPipe.waitFor();
		if (exitCode != 0) {
			throw new RuntimeException("Error creating input pipe");
		}

		Process outputPipe = new ProcessBuilder("mkfifo", outputPipeName).start();
		exitCode = outputPipe.waitFor();
		if (exitCode != 0) {
			throw new RuntimeException("Error creating input pipe");
		}

		processBuilder = new ProcessBuilder("python", script, "--id", id.toString(), "--input_pipe", inputPipeName,
				"--output_pipe", outputPipeName);
	}

	public void writeInputWithTimeout(byte[] bytes, int timeoutMinutes)
			throws IOException, InterruptedException, TimeoutException {
		log.debug("Dispatching write thread for input pipe: {} for task: {}", inputPipeName, id);

		CompletableFuture<Void> future = new CompletableFuture<>();
		new Thread(() -> {
			try {
				// Write to the named pipe in a separate thread
				log.debug("Opening input pipe: {} for task: {}", inputPipeName, id);
				try (FileOutputStream fos = new FileOutputStream(inputPipeName)) {
					log.debug("Writing to input pipe: {} for task: {}", inputPipeName, id);
					fos.write(appendNewline(bytes));
				}
				future.complete(null);
			} catch (IOException e) {
				future.completeExceptionally(e);
			}
		}).start();

		Object result;
		try {
			result = CompletableFuture.anyOf(future, processFuture).get(timeoutMinutes, TimeUnit.MINUTES);
		} catch (ExecutionException | InterruptedException e) {
			throw new RuntimeException("Error while writing to pipe", e);
		} catch (TimeoutException e) {
			future.cancel(true);
			throw new TimeoutException("Writing to pipe took too long for task " + id);
		}

		if (result == null) {
			// successful write
			return;
		}
		if (result instanceof Integer) {
			// process has exited early
			if (getStatus() == TaskStatus.CANCELLED) {
				throw new InterruptedException("Process for task " + id + " has been cancelled");
			}
			throw new InterruptedException("Process for task " + id + " exited early with code " + result);
		}
		throw new RuntimeException("Unexpected result type: " + result.getClass());
	}

	public byte[] readOutputWithTimeout(int timeoutMinutes)
			throws IOException, InterruptedException, ExecutionException, TimeoutException {
		log.debug("Dispatching read thread for input pipe: {} for task: {}", outputPipeName, id);

		CompletableFuture<byte[]> future = new CompletableFuture<>();
		new Thread(() -> {
			log.debug("Opening output pipe: {} for task: {}", outputPipeName, id);
			try (BufferedReader reader = new BufferedReader(
					new InputStreamReader(new FileInputStream(outputPipeName)))) {
				log.debug("Reading on output pipe: {} for task {}", outputPipeName, id);
				future.complete(reader.readLine().getBytes());
			} catch (IOException e) {
				future.completeExceptionally(e);
			}
		}).start();

		Object result;
		try {
			result = CompletableFuture.anyOf(future, processFuture).get(timeoutMinutes, TimeUnit.MINUTES);
		} catch (TimeoutException e) {
			future.cancel(true);
			throw new TimeoutException("Reading from pipe took too long for task " + id);
		}

		if (result == null) {
			throw new RuntimeException("Unexpected null result for task " + id);
		}

		if (result instanceof byte[]) {
			// we got our response
			return (byte[]) result;
		}
		if (result instanceof Integer) {
			// process has exited early
			if (getStatus() == TaskStatus.CANCELLED) {
				throw new InterruptedException("Process for task " + id + " has been cancelled");
			}
			throw new InterruptedException("Process for task " + id + " exited early with code " + result);
		}

		throw new RuntimeException("Unexpected result type: " + result.getClass());
	}

	private byte[] appendNewline(byte[] original) {
		byte[] newline = System.lineSeparator().getBytes();
		byte[] combined = new byte[original.length + newline.length];

		System.arraycopy(original, 0, combined, 0, original.length);
		System.arraycopy(newline, 0, combined, original.length, newline.length);

		return combined;
	}

	public void cleanup() {
		try {
			Files.deleteIfExists(Paths.get(inputPipeName));
		} catch (Exception e) {
			log.warn("Exception occurred while cleaning up the task input pipe:" + e);
		}

		try {
			Files.deleteIfExists(Paths.get(outputPipeName));
		} catch (Exception e) {
			log.warn("Exception occurred while cleaning up the task output pipe:" + e);
		}

		try {
			cancel();
		} catch (Exception e) {
			log.warn("Exception occurred while killing any residual process:" + e);
		}
	}

	public void start() throws IOException, InterruptedException {

		lock.lock();
		try {
			if (status == TaskStatus.CANCELLED) {
				// don't run if we already cancelled
				throw new InterruptedException("Task " + id + "has already been cancelled");
			}

			if (status != TaskStatus.QUEUED) {
				// has to be in a queued state to be valid to run
				throw new RuntimeException("Task " + id + " has already been started");
			}

			status = TaskStatus.RUNNING;

			log.info("Starting task {}", id);
			process = processBuilder.start();

			// Create a future to signal when the process has exited
			processFuture = new CompletableFuture<>();
			new Thread(() -> {
				try {
					log.debug("Begin waiting for process to exit for task {}");
					int exitCode = process.waitFor();
					log.info("Process exited with code {} for task {}", exitCode, id);
					lock.lock(() -> {
						if (exitCode != 0) {
							if (status == TaskStatus.CANCELLING) {
								status = TaskStatus.CANCELLED;
							} else {
								status = TaskStatus.FAILED;
							}
						} else {
							status = TaskStatus.SUCCESS;
						}
					});
					log.debug("Finalized process status for task {}", exitCode, id);
					processFuture.complete(exitCode);
				} catch (InterruptedException e) {
					log.warn("Process failed to exit cleanly for task {}: {}", id, e);
					lock.lock(() -> {
						status = TaskStatus.FAILED;
					});
					processFuture.completeExceptionally(e);
				}
			}).start();

			InputStream inputStream = process.getInputStream();
			InputStream errorStream = process.getErrorStream();

			new Thread(() -> {
				try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
					String line;
					while ((line = reader.readLine()) != null) {
						log.info("[{}] stdout: {}", id, line);
					}
				} catch (IOException e) {
					log.warn("Error occured while logging stdout for task {}: {}", id,
							getStatus());
				}
			}).start();

			new Thread(() -> {
				try (BufferedReader reader = new BufferedReader(new InputStreamReader(errorStream))) {
					String line;
					while ((line = reader.readLine()) != null) {
						log.warn("[{}] stderr: {}", id, line);
					}
				} catch (IOException e) {
					log.warn("Error occured while logging stderr for task {}: {}", id, getStatus());
				}
			}).start();

		} finally {
			lock.unlock();
		}
	}

	public void waitFor(int timeoutMinutes) throws InterruptedException, TimeoutException, ExecutionException {
		boolean hasExited = process.waitFor((long) timeoutMinutes, TimeUnit.MINUTES);
		if (hasExited) {
			// if we have exited, lets wait on the future to resolve and the status to be
			// correctly set
			processFuture.get();
			int exitCode = process.exitValue();
			if (exitCode != 0) {
				throw new RuntimeException("Python script exited with non-zero exit code: " + exitCode);
			}
		} else {
			throw new TimeoutException("Process did not exit within the timeout");
		}
	}

	public boolean flagAsCancelling() {

		// Splitting this off as separate method allows us to accept a cancel
		// request, response that we are cancelling, and then process it.

		return lock.lock(() -> {
			if (status == TaskStatus.QUEUED) {
				// if we havaen't started yet, flag it as cancelled
				log.debug("Cancelled task {} before starting it", id);
				status = TaskStatus.CANCELLED;
				return false;
			}
			if (status != TaskStatus.RUNNING) {
				// can't cancel a process if it isn't in a running state
				return false;
			}

			status = TaskStatus.CANCELLING;
			return true;
		});
	}

	public boolean cancel() {
		flagAsCancelling();

		if (getStatus() != TaskStatus.CANCELLING) {
			return false;
		}

		long start = System.currentTimeMillis();

		if (process == null) {
			throw new RuntimeException("Process is null for task: " + id);
		}

		// try to kill cleanly
		log.info("Cancelling task {}", id);
		process.destroy();

		try {
			processFuture.get(PROCESS_KILL_TIMEOUT_SECONDS, TimeUnit.SECONDS);

			log.info("Process successfully cancelled in: {} for task {}", id,
					TimeFormatter.format(System.currentTimeMillis() - start), id);
		} catch (InterruptedException | ExecutionException | TimeoutException e) {
			log.warn("Error while waiting for task {} process to exit cleanly in {}, sending SIGKILL", id,
					TimeFormatter.format(System.currentTimeMillis() - start));
			// kill the process forcibly (SIGKILL)
			process.destroyForcibly();
		}

		return true;
	}

	TaskStatus getStatus() {
		return lock.lock(() -> {
			return status;
		});
	}

}
