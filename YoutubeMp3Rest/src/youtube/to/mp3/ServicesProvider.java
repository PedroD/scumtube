package youtube.to.mp3;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.json.JSONException;
import org.json.JSONObject;
import org.restlet.data.Header;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;
import org.restlet.util.Series;

public final class ServicesProvider {

	private static final int CONVERSION_TIMEOUT_MINS = 5;
	private static final String FINAL_DIR = "www/";
	private static final Object lock = new Object();
	private static final int MAX_RETRIES = 3;

	private static final String TEMP_DIR = "tmp/";

	private static final class VideoRequest {
		private static final Map<String, VideoRequest> requestsQueue = new HashMap<String, VideoRequest>();

		private boolean ready = false;
		private int retries = 0;
		private final String videoId;

		public VideoRequest(final String videoId) {
			this.videoId = videoId;
			if (this.isMP3FileAlreadyCreated() && !isDownloadingAlready()) {
				ready = true;
				touchFile();
			} else if (!isDownloadingAlready()) {
				synchronized (lock) {
					requestsQueue.put(videoId, this);
					new Thread() {
						@Override
						public void run() {
							VideoRequest.this.download();
							new Logger().log(Logger.LOG_INFO, "Finished preparing " + videoId);
						}
					}.start();
				}
			}
		}

		@SuppressWarnings("deprecation")
		private void download() {
			final Semaphore sem = new Semaphore(0);
			if (getTemporaryFile().exists())
				getTemporaryFile().delete();
			final Thread t = new Thread() {
				@Override
				public void run() {
					final String cmd = "youtube-dl "
							+ "-o " + TEMP_DIR + "%(id)s.%(ext)s "
							+ "--extract-audio "
							+ "--audio-format mp3 "
							+ "--audio-quality 160K "
							+ "https://www.youtube.com/watch?v="
							+ videoId;
					final String result = executeCommand(cmd);
					if (!result.contains("Deleting original file") && retries < MAX_RETRIES) {
						retries++;
						download();
					} else {
						if (retries >= MAX_RETRIES) {
							new Logger().log(Logger.LOG_ERROR, "Could not download " + videoId);
							new Logger().log(Logger.LOG_ERROR, result);
						} else {
							getTemporaryFile().renameTo(new File(FINAL_DIR + getTemporaryFile().getName()));
						}
						synchronized (lock) {
							if (null == requestsQueue.remove(videoId)) {
								new Logger().log(Logger.LOG_ERROR, "Could not remove thread for " + videoId);
							}
						}
					}
					sem.release();
				}
			};
			t.start();
			boolean timedOut = true;
			try {
				timedOut = !sem.tryAcquire(CONVERSION_TIMEOUT_MINS, TimeUnit.MINUTES);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			if (timedOut) {
				t.interrupt();
				t.stop();
				new Logger().log(Logger.LOG_ERROR, "Could not download " + videoId + " because timeout.");
				return;
			}
		}

		private String executeCommand(String command) {
			StringBuffer output = new StringBuffer();
			try {
				Process p = Runtime.getRuntime().exec(command);
				BufferedReader reader1 = new BufferedReader(new InputStreamReader(p.getInputStream()));
				BufferedReader reader2 = new BufferedReader(new InputStreamReader(p.getErrorStream()));
				String line = "";
				while ((line = reader1.readLine()) != null) {
					output.append(line + "\n");
				}
				while ((line = reader2.readLine()) != null) {
					output.append("Error output: " + line + "\n");
				}
				output.append("exit code: " + p.waitFor() + "\n");
			} catch (Exception e) {
				e.printStackTrace();
			}
			return output.toString();
		}

		private File getTemporaryFile() {
			return new File(TEMP_DIR + videoId + ".mp3");
		}

		private boolean isMP3FileAlreadyCreated() {
			return this.getMP3File().exists();
		}

		private void touchFile() {
			this.getMP3File().setLastModified(System.currentTimeMillis());
		}

		public File getMP3File() {
			return new File(FINAL_DIR + videoId + ".mp3");
		}

		public boolean isDownloadingAlready() {
			synchronized (lock) {
				return requestsQueue.get(this.videoId) != null;
			}
		}

		public boolean isMP3FileReady() {
			return this.ready;
		}
	}

	public static final class DownloadMP3 extends ServerResource {

		/**
		 * Do get.
		 *
		 * @return the JSON object
		 * @throws InterruptedException
		 *             the interrupted exception
		 * @throws ConstraintViolationException
		 *             the constraint violation exception
		 * @throws InvalidChoiceException
		 *             the invalid choice exception
		 * @throws JSONException
		 *             the JSON exception
		 */
		@Get("application/json")
		public String doGet() throws Exception {
			configureRestForm(this);
			JSONObject response = new JSONObject();
			final String videoId = getRequest().getAttributes().get("video_id").toString();
			if (videoId == null || videoId == "") {
				response.put("error", "Invalid video id!");
				return response.toString(4);
			} else {
				final VideoRequest vr = new VideoRequest(videoId);
				if (vr.isMP3FileReady()) {
					response.put("ready", "The file can be downloaded.");
					response.put("url", "http://wavedomotics.com/" + videoId + ".mp3");
					return response.toString(4);
				} else {
					response.put("scheduled", "Wait until the mp3 is cached.");
					return response.toString(4);
				}
			}
		}
	}

	/**
	 * Enables incoming connections from different servers.
	 * 
	 * @param serverResource
	 * @return
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private static Series<Header> configureRestForm(ServerResource serverResource) {
		Series<Header> responseHeaders = (Series<Header>) serverResource.getResponse().getAttributes()
				.get("org.restlet.http.headers");
		if (responseHeaders == null) {
			responseHeaders = new Series(Header.class);
			serverResource.getResponse().getAttributes().put("org.restlet.http.headers", responseHeaders);
		}
		responseHeaders.add("Access-Control-Allow-Origin", "*");
		responseHeaders.add("Access-Control-Allow-Methods", "GET, POST, PUT, OPTIONS");
		responseHeaders.add("Access-Control-Allow-Headers", "Content-Type");
		responseHeaders.add("Access-Control-Allow-Credentials", "false");
		responseHeaders.add("Access-Control-Max-Age", "60");
		return responseHeaders;
	}
}
