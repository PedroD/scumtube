package youtube.to.mp3;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
	private static final int MAX_MINUTES_STORING_ABORTED_REQUEST = 120;
	private static final int MAX_MUSIC_DURATION = 10;
	private static final int MAX_RETRIES = 3;
	private static final int MAX_SIMULTANEOUS_DOWNLOADS = 3;

	private static final String TEMP_DIR = "tmp/";

	private static final class VideoRequest {
		private static final Map<String, VideoRequest> abortedRequests = new HashMap<String, VideoRequest>();
		private static final Object lock = new Object();
		private static final Map<String, VideoRequest> requestsQueue = new HashMap<String, VideoRequest>();
		private static final Semaphore simultaneousDownloads = new Semaphore(MAX_SIMULTANEOUS_DOWNLOADS);

		private boolean aborted = false;

		private String abortedMessage;
		private long abortedTimestamp = 0;
		private int duration = -1;
		private boolean ready = false;
		private int retries = 0;
		private String title = "";
		private final String videoId;

		public static final class AbortedRequestsCleaner extends Thread {
			private static AbortedRequestsCleaner r;

			public static void startCleaner() {
				if (r == null) {
					r = new AbortedRequestsCleaner();
					new Logger().log(Logger.LOG_INFO, "Aborted Requests Cleaner started.");
					r.start();
				}
			}

			private AbortedRequestsCleaner() {
				super("Aborted Requests Remover");
			}

			@Override
			public void run() {
				try {
					while (true) {
						/* 20 minutes */
						Thread.sleep(20 * 60000);
						final List<String> toRemove = new ArrayList<String>();
						for (Map.Entry<String, VideoRequest> vr : abortedRequests.entrySet()) {
							final long delta = TimeUnit.MILLISECONDS
									.toMinutes(System.currentTimeMillis() - vr.getValue().getAbortedTimestamp());
							if (delta >= MAX_MINUTES_STORING_ABORTED_REQUEST)
								toRemove.add(vr.getKey());
						}
						for (String v : toRemove) {
							abortedRequests.remove(v);
						}
					}
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				new Logger().log(Logger.LOG_INFO, "Aborted Requests Cleaner stopped.");
			}
		}

		{
			AbortedRequestsCleaner.startCleaner();
		}

		public VideoRequest(final String videoId) {
			this.videoId = videoId;
			try {
				synchronized (lock) {
					if (wasAbortedAlready()) {
						ready = true;
						aborted = true;
						abortedMessage = abortedRequests.get(videoId).getAbortedMessage();
					} else if (this.isMP3FileAlreadyCreated() && !isDownloadingAlready()) {
						ready = true;
						this.title = new BufferedReader(new FileReader(FINAL_DIR + videoId + ".txt")).readLine();
						touchFile();
					} else if (!isDownloadingAlready()) {
						requestsQueue.put(videoId, this);
						new Thread() {
							@Override
							public void run() {
								try {
									simultaneousDownloads.acquire();
									new Logger().log(Logger.LOG_INFO, "Preparing video " + videoId);
									VideoRequest.this.download();
									simultaneousDownloads.release();
									new Logger().log(Logger.LOG_INFO, "Finished preparing " + videoId);
								} catch (InterruptedException e) {
									e.printStackTrace();
								}
							}
						}.start();
					}
				}
			} catch (Exception e) {
				ready = true;
				aborted = true;
				abortedMessage = e.getMessage();
				e.printStackTrace();
			}
		}

		private void abortRequest(String errorMsg) {
			aborted = true;
			abortedTimestamp = System.currentTimeMillis();
			abortedMessage = errorMsg;
			new Logger().log(Logger.LOG_ERROR, abortedMessage);
		}

		@SuppressWarnings("deprecation")
		private void download() {
			/*
			 * Semaphore released only when the download finishes (with error or not).
			 */
			final Semaphore sem = new Semaphore(0);
			if (getTemporaryFile().exists()) {
				getTemporaryFile().delete();
			}
			final Thread t = new Thread() {
				@Override
				public void run() {
					if (retries > 0) {
						new Logger().log(Logger.LOG_WARNING, "Retrying... " + videoId);
					}
					boolean downloadSuccessfull = false;
					if (getMetadata()) {
						if (duration >= MAX_MUSIC_DURATION) {
							abortRequest("Could not download " + videoId + ", it exceeds " + MAX_MUSIC_DURATION + " minutes.");
							sem.release();
							return;
						}
						final String downloadCmd = "youtube-dl "
								+ "-o " + TEMP_DIR + "%(id)s.%(ext)s "
								+ "--extract-audio "
								+ "--write-thumbnail "
								+ "--audio-format mp3 "
								+ "--audio-quality 160K "
								+ "https://www.youtube.com/watch?v="
								+ videoId;
						final String downloadResult = executeCommand(downloadCmd);
						downloadSuccessfull = downloadResult.contains("Deleting original file");
						if (!downloadSuccessfull) {
							if (retries < MAX_RETRIES) {
								retries++;
								download();
								return;
							} else {
								new Logger().log(Logger.LOG_ERROR, downloadResult);
								abortRequest("Could not download " + videoId);
								sem.release();
								return;
							}
						}
						/*
						 * Move files to final folder
						 */
						final File thumbnailFile = new File(TEMP_DIR + videoId + ".jpg");
						if (thumbnailFile.exists() && getTemporaryFile().exists()) {
							getTemporaryFile().renameTo(new File(FINAL_DIR + getTemporaryFile().getName()));
							thumbnailFile.renameTo(new File(FINAL_DIR + thumbnailFile.getName()));
							try {
								writeTitleToFile();
							} catch (Exception e) {
								e.printStackTrace();
							}
						}
					}
					sem.release();
					return;
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
				if (retries < MAX_RETRIES) {
					retries++;
					download();
				} else {
					abortRequest("Could not download " + videoId + " because timeout.");
					removeRequestFromQueue();
				}
			}
			if (aborted) {
				moveToAbortedRequestsList(this);
			} else {
				removeRequestFromQueue();
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

		private boolean getMetadata() {
			try {
				final String metadataCmd = "youtube-dl "
						+ "--get-title --get-duration "
						+ "https://www.youtube.com/watch?v="
						+ videoId;
				final String metadataResult = executeCommand(metadataCmd);
				final String[] tokens = metadataResult.split("\n");
				this.title = tokens[0];
				this.duration = Integer.parseInt(tokens[1].split(":")[0]);
				return true;
			} catch (Exception e) {
				abortRequest(e.getMessage() + " Is the video id correct?");
				e.printStackTrace();
				return false;
			}
		}

		private File getTemporaryFile() {
			return new File(TEMP_DIR + videoId + ".mp3");
		}

		private boolean isMP3FileAlreadyCreated() {
			return this.getMP3File().exists();
		}

		private void moveToAbortedRequestsList(VideoRequest vr) {
			synchronized (lock) {
				requestsQueue.remove(videoId);
				abortedRequests.put(videoId, vr);
			}
		}

		private void removeRequestFromQueue() {
			synchronized (lock) {
				requestsQueue.remove(videoId);
			}
		}

		private void touchFile() {
			this.getMP3File().setLastModified(System.currentTimeMillis());
		}

		private void writeTitleToFile() throws Exception {
			PrintWriter writer = new PrintWriter(FINAL_DIR + videoId + ".txt", "UTF-8");
			writer.println(title);
			writer.close();
		}

		public String getAbortedMessage() {
			return this.abortedMessage;
		}

		public long getAbortedTimestamp() {
			return abortedTimestamp;
		}

		public File getMP3File() {
			return new File(FINAL_DIR + videoId + ".mp3");
		}

		public String getTitle() {
			return title;
		}

		public boolean isDownloadingAlready() {
			return requestsQueue.get(this.videoId) != null;
		}

		public boolean isMP3FileReady() {
			return this.ready;
		}

		public boolean wasAborted() {
			return this.aborted;
		}

		public boolean wasAbortedAlready() {
			return abortedRequests.get(this.videoId) != null;
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
				if (vr.isMP3FileReady() && !vr.wasAborted()) {
					response.put("ready", "The file can be downloaded.");
					response.put("url", "http://wavedomotics.com/" + videoId + ".mp3");
					response.put("cover", "http://wavedomotics.com/" + videoId + ".jpg");
					response.put("title", vr.getTitle());
					return response.toString(4);
				} else if (vr.isMP3FileReady() && vr.wasAborted()) {
					response.put("error", vr.getAbortedMessage());
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
