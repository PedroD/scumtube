package youtube.to.mp3;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONException;
import org.json.JSONObject;
import org.restlet.data.Header;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;
import org.restlet.util.Series;

import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.ElementNotFoundException;
import com.gargoylesoftware.htmlunit.NicelyResynchronizingAjaxController;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlPage;

public final class ServicesProvider {

	private static final int CONVERSION_TIMEOUT_MINS = 5;
	private static final int MAX_MINUTES_STORING_RESOLVED_REQUEST = 3;
	private static final int MAX_RETRIES = 3;
	private static final int MAX_SIMULTANEOUS_DOWNLOADS = 10;

	private static final class VideoRequest {
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

			final Semaphore sem;

			public DownloadThread(Semaphore sem) {
				this.sem = sem;
			}


			private boolean fetchVideoInfoFromServer() throws Exception {
				String requestUrl = "http://www.theyoump3.com/a/pushItem/?item=https://www.youtube.com/watch?v="
						+ VideoRequest.this.videoId;
				String httpResponse = getHttpResponse(requestUrl);
				if (httpResponse == null || httpResponse.contains("ERROR")) {
					return false;
				}
				
				String infoUrl = "http://www.theyoump3.com/a/itemInfo/?video_id=" + VideoRequest.this.videoId;				
				JSONObject jsonObject;
				while (true) {
					httpResponse = getHttpResponse(infoUrl);
					if (httpResponse == null || httpResponse.contains("ERROR")) {
						return false;
					}					
					httpResponse = httpResponse.substring(7);					
					jsonObject = new JSONObject(httpResponse);
					if (jsonObject.has("status")) {
						if (jsonObject.get("status").equals("serving")) {
							VideoRequest.this.coverUrl = "http://i.ytimg.com/vi/" + VideoRequest.this.videoId
									+ "/default.jpg";
							VideoRequest.this.title = jsonObject.getString("title");
							VideoRequest.this.mp3Url = "www.theyoump3.com/get?ab=128&video_id="
									+ VideoRequest.this.videoId + "&h=" + jsonObject.getString("h");
							return true;
						}
					} else {
						return false;
					}
					Thread.sleep(2000);
				}
			}
			
			public String getHttpResponse(String url) throws IOException {
				LogFactory.getFactory().setAttribute("org.apache.commons.logging.Log",
						"org.apache.commons.logging.impl.NoOpLog");
				java.util.logging.Logger.getLogger("com.gargoylesoftware.htmlunit").setLevel(Level.OFF);
				java.util.logging.Logger.getLogger("org.apache.commons.httpclient").setLevel(Level.OFF);

				WebClient webClient = null;
				try {
					webClient = new WebClient(BrowserVersion.FIREFOX_38);
					webClient.setAjaxController(new NicelyResynchronizingAjaxController());
					webClient.getOptions().setJavaScriptEnabled(true);
					webClient.getOptions().setRedirectEnabled(true);
					webClient.getOptions().setThrowExceptionOnScriptError(false);

					final HtmlPage page = webClient.getPage(url);					
					
					webClient.close();
					return page.getBody().asText();
					
				} catch (ElementNotFoundException e) {
					new Logger().log(Logger.LOG_ERROR, "Error scraping the website. " + e.getMessage());
					e.printStackTrace();
					if (webClient != null) {
						webClient.close();
					}
					return null;
				} catch (Exception e) {
					e.printStackTrace();
					if (webClient != null) {
						webClient.close();
					}
					throw e;
				}

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
					VideoRequest.this.abortRequest("There was an error connecting Youtube.");
				}
				sem.release();
				return;
			}
		}

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
						Thread.sleep(Math.round(MAX_MINUTES_STORING_RESOLVED_REQUEST / 2.0) * 60000);
						synchronized (lock) {
							final List<String> toRemove = new ArrayList<String>();

							for (Map.Entry<String, VideoRequest> vr : completedRequests.entrySet()) {
								final long delta = TimeUnit.MILLISECONDS
										.toMinutes(System.currentTimeMillis() - vr.getValue().getAbortedTimestamp());
								if (delta >= MAX_MINUTES_STORING_RESOLVED_REQUEST) {
									toRemove.add(vr.getKey());
									new Logger().log(Logger.LOG_INFO,
											"Removed old aborted request " + vr.getKey() + ".");
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
				new Logger().log(Logger.LOG_INFO, "Aborted Requests Cleaner stopped.");
			}
		}

		{
			AbortedRequestsCleaner.startCleaner();
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

		public boolean isDownloadingAlready() {
			synchronized (lock) {
				return requestsQueue.get(this.videoId) != null;
			}
		}

		public boolean isReady() {
			return this.ready;
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
