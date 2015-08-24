package youtube.to.mp3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import org.apache.commons.logging.LogFactory;
import org.json.JSONException;
import org.json.JSONObject;
import org.restlet.data.Header;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;
import org.restlet.util.Series;

import youtube.to.mp3.converters.TheYouMp3Task;
import youtube.to.mp3.converters.Youtube2Mp3Task;

public final class ServicesProvider {

	private static final int CONVERSION_TIMEOUT_MINS = 5;

	private static final String CURRENT_VERSION = "0.7";
	private static final int MAX_MINUTES_STORING_RESOLVED_REQUEST = 1;
	private static final int MAX_RETRIES = 3;
	private static final int MAX_SIMULTANEOUS_DOWNLOADS = 10;

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
			response.put("version", CURRENT_VERSION);
			final String videoId = getRequest().getAttributes().get("video_id").toString();
			if (videoId == null || videoId.equals("")) {
				response.put("error", "Invalid video id!");
				return response.toString(4);
			} else {
				final VideoRequest vr = new VideoRequest(videoId);
				if (vr.isReady() && !vr.wasAborted()) {
					response.put("ready", "The request was fulfilled.");
					response.put("url", vr.getMp3Url());
					response.put("cover", vr.getCoverUrl());
					response.put("title", vr.getTitle());
					return response.toString(4);
				} else if (vr.isReady() && vr.wasAborted()) {
					response.put("error", vr.getAbortedMessage());
					return response.toString(4);
				} else {
					response.put("scheduled", "Wait until the request is fulfilled.");
					return response.toString(4);
				}
			}
		}

	}

	public static final class VideoRequest {
		private static final Map<String, VideoRequest> completedRequests = new HashMap<String, VideoRequest>();
		private static final Object lock = new Object();
		private static final Map<String, VideoRequest> requestsQueue = new HashMap<String, VideoRequest>();
		private static final Semaphore simultaneousDownloads = new Semaphore(MAX_SIMULTANEOUS_DOWNLOADS);

		private boolean aborted = false;

		private String abortedMessage;
		private long abortedTimestamp = 0;
		private String coverUrl = "";
		private String mp3Url = "";
		private boolean ready = false;
		private int retries = 0;
		private String title = "";
		private final String videoId;

		private final class DownloadThread extends Thread {

			final Semaphore downloadFinishSem;

			private final class ServerFetcher extends Thread {
				private boolean hasError = false;
				private boolean hasFinished = false;
				final private Semaphore sem;

				final private Task task;

				public ServerFetcher(Semaphore sem, Task task) {
					super("Server Fetcher for video " + videoId + " - " + task);
					this.sem = sem;
					this.task = task;
				}

				public boolean hasError() {
					return this.hasError;
				}

				public boolean hasFinished() {
					return this.hasFinished;
				}

				@Override
				public void run() {
					hasError = !task.run();
					hasFinished = true;
					// Finally release the lock
					this.sem.release();
				}
			}

			public DownloadThread(Semaphore sem) {
				super("Download Thread for video " + videoId);
				this.downloadFinishSem = sem;
			}

			private boolean fetchVideoInfoFromServer() {
				/*
				 * Initializing HTMLUnit
				 */
				LogFactory.getFactory().setAttribute("org.apache.commons.logging.Log",
						"org.apache.commons.logging.impl.NoOpLog");
				java.util.logging.Logger.getLogger("com.gargoylesoftware.htmlunit").setLevel(Level.OFF);
				java.util.logging.Logger.getLogger("org.apache.commons.httpclient").setLevel(Level.OFF);

				final Semaphore sem = new Semaphore(0);
				final TheYouMp3Task task1 = new TheYouMp3Task(VideoRequest.this);
				final Youtube2Mp3Task task2 = new Youtube2Mp3Task(VideoRequest.this);
				final ServerFetcher t1 = new ServerFetcher(sem, task1);
				final ServerFetcher t2 = new ServerFetcher(sem, task2);

				t1.start();
				t2.start();
				try {
					do {
						sem.acquire();
						if (t1.hasFinished() && !t1.hasError()) {
							t2.interrupt();
							t2.stop();
							break;
						} else if (t2.hasFinished() && !t2.hasError()) {
							t1.interrupt();
							t1.stop();
							break;
						}
					} while (!t1.hasFinished() || !t2.hasFinished());
				} catch (InterruptedException e) {
					t1.interrupt();
					t1.stop();
					t2.interrupt();
					t2.stop();
					return false;

				}
				return !(t1.hasError() && t2.hasError());

			}

			@Override
			public void run() {
				boolean success = false;
				while (retries < MAX_RETRIES) {
					if (retries > 0) {
						new Logger().log(Logger.LOG_WARNING, "Retrying... " + videoId);
					}
					retries++;
					try {
						success = fetchVideoInfoFromServer();
						break;
					} catch (Exception e) {
						e.printStackTrace();
						continue;
					}
				}
				if (!success) {
					VideoRequest.this.abortRequest("There was an error connecting to Youtube.");
				}
				downloadFinishSem.release();
				return;
			}
		}

		public static final class OldRequestsCleaner extends Thread {
			private static OldRequestsCleaner r;

			public static void startCleaner() {
				if (r == null) {
					r = new OldRequestsCleaner();
					new Logger().log(Logger.LOG_INFO, "Old Requests Cleaner started.");
					r.start();
				}
			}

			private OldRequestsCleaner() {
				super("Old Requests Remover");
			}

			@Override
			public void run() {
				try {
					while (true) {
						Thread.sleep(Math.round(MAX_MINUTES_STORING_RESOLVED_REQUEST / 2.0) * 60000);
						synchronized (lock) {
							final List<String> toRemove = new ArrayList<String>();

							for (Map.Entry<String, VideoRequest> vr : completedRequests.entrySet()) {
								final long delta = TimeUnit.MILLISECONDS
										.toMinutes(System.currentTimeMillis() - vr.getValue().getAbortedTimestamp());
								if (delta >= MAX_MINUTES_STORING_RESOLVED_REQUEST) {
									toRemove.add(vr.getKey());
									new Logger().log(Logger.LOG_INFO, "Removed old request " + vr.getKey() + ".");
								}
							}
							for (String v : toRemove) {
								completedRequests.remove(v);
							}
						}
					}
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				new Logger().log(Logger.LOG_INFO, "Old Requests Cleaner stopped.");
			}
		}

		{
			OldRequestsCleaner.startCleaner();
		}

		public VideoRequest(final String videoId) {
			this.videoId = videoId;
			final VideoRequest tmpVr = wasPreviouslyHandled();
			if (tmpVr != null) {
				this.aborted = tmpVr.aborted;
				this.ready = tmpVr.ready;
				this.abortedMessage = tmpVr.abortedMessage;
				this.coverUrl = tmpVr.coverUrl;
				this.title = tmpVr.title;
				this.mp3Url = tmpVr.mp3Url;
			} else if (!isDownloadingAlready()) {
				synchronized (lock) {
					requestsQueue.put(videoId, this);
				}
				new Thread() {
					@Override
					public void run() {
						try {
							simultaneousDownloads.acquire();
							new Logger().log(Logger.LOG_INFO, "Preparing video " + videoId);
							VideoRequest.this.download();
							simultaneousDownloads.release();
							ready = true;
							new Logger().log(Logger.LOG_INFO, "Finished preparing " + videoId);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}
				}.start();
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
			while (retries < MAX_RETRIES) {
				/*
				 * Semaphore released only when the download finishes (with
				 * error or not).
				 */
				final Semaphore sem = new Semaphore(0);
				final Thread t = new DownloadThread(sem);
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
					if (retries >= MAX_RETRIES) {
						abortRequest("Could not download " + videoId + " because it timed out.");
					} else {
						retries++;
					}
				} else {
					break;
				}
			}
			moveToCompletedRequestsList(this);
		}

		private void moveToCompletedRequestsList(VideoRequest vr) {
			synchronized (lock) {
				requestsQueue.remove(videoId);
				completedRequests.put(videoId, vr);
			}
		}

		public String getAbortedMessage() {
			return this.abortedMessage;
		}

		public long getAbortedTimestamp() {
			return abortedTimestamp;
		}

		public String getCoverUrl() {
			return coverUrl;
		}

		public String getMp3Url() {
			return mp3Url;
		}

		public String getTitle() {
			return title;
		}

		public String getVideoId() {
			return videoId;
		}

		public boolean isDownloadingAlready() {
			synchronized (lock) {
				return requestsQueue.get(this.videoId) != null;
			}
		}

		public boolean isReady() {
			return this.ready;
		}

		public void setCoverUrl(String coverUrl) {
			this.coverUrl = coverUrl;
		}

		public void setMp3Url(String mp3Url) {
			this.mp3Url = mp3Url;
		}

		public void setTitle(String title) {
			this.title = title;
		}

		public boolean wasAborted() {
			return this.aborted;
		}

		public VideoRequest wasPreviouslyHandled() {
			synchronized (lock) {
				return completedRequests.get(this.videoId);
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
