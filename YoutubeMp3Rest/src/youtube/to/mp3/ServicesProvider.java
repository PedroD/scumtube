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

import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.NicelyResynchronizingAjaxController;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlAnchor;
import com.gargoylesoftware.htmlunit.html.HtmlButton;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlTextInput;

public final class ServicesProvider {

	private static final String CURRENT_VERSION = "0.7";

	private static final int CONVERSION_TIMEOUT_MINS = 5;
	private static final int MAX_MINUTES_STORING_RESOLVED_REQUEST = 1;
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

			private boolean theYouMp3Error = false;
			private boolean youtube2Mp3Error = false;

			private boolean getTheYouMp3Error() {
				return this.theYouMp3Error;
			}

			private boolean getYoutube2Mp3Error() {
				return this.youtube2Mp3Error;
			}

			private void flagTheYouMp3Error() {
				this.theYouMp3Error = true;
				new Logger().log(Logger.LOG_ERROR, "Error scraping the TheYouMp3 website.");
			}

			private void flagYoutube2Mp3Error() {
				this.youtube2Mp3Error = true;
				new Logger().log(Logger.LOG_ERROR, "Error scraping the Youtube2Mp3 website.");
			}

			private void flagTheYouMp3Error(String msg) {
				this.theYouMp3Error = true;
				new Logger().log(Logger.LOG_ERROR, "Error scraping the TheYouMp3 website. " + msg);
			}

			private void flagYoutube2Mp3Error(String msg) {
				this.youtube2Mp3Error = true;
				new Logger().log(Logger.LOG_ERROR, "Error scraping the Youtube2Mp3 website. " + msg);
			}

			private boolean fetchVideoInfoFromServer() throws Exception {
				/*
				 * Initializing HTMLUnit
				 */
				LogFactory.getFactory().setAttribute("org.apache.commons.logging.Log",
						"org.apache.commons.logging.impl.NoOpLog");
				java.util.logging.Logger.getLogger("com.gargoylesoftware.htmlunit").setLevel(Level.OFF);
				java.util.logging.Logger.getLogger("org.apache.commons.httpclient").setLevel(Level.OFF);

				WebClient webClient = null;
				try {
					/*
					 * Initializing WebClient
					 */
					webClient = new WebClient(BrowserVersion.FIREFOX_38);
					webClient.setAjaxController(new NicelyResynchronizingAjaxController());
					webClient.getOptions().setJavaScriptEnabled(true);
					webClient.getOptions().setRedirectEnabled(true);

					// TheYouMp3
					HtmlPage theYouMp3Page = null;
					String theYouMp3HttpResponse = null;
					try {
						theYouMp3Page = webClient
								.getPage("http://www.theyoump3.com/a/pushItem/?item=https://www.youtube.com/watch?v="
										+ VideoRequest.this.videoId);
						theYouMp3HttpResponse = theYouMp3Page.getBody().asText();
						if (theYouMp3HttpResponse == null || theYouMp3HttpResponse.contains("ERROR")) {
							flagTheYouMp3Error();
						}
					} catch (Exception e) {
						flagTheYouMp3Error(e.getMessage());
					}

					// Youtube2Mp3
					HtmlPage youtube2Mp3Page = null;
					HtmlAnchor youtube2Mp3An = null;
					try {
						youtube2Mp3Page = webClient.getPage("http://www.youtube2mp3.cc");
						final HtmlTextInput youtube2Mp3TextField = (HtmlTextInput) youtube2Mp3Page
								.getElementById("video");
						final HtmlButton youtube2Mp3Button = (HtmlButton) youtube2Mp3Page.getElementById("button");
						youtube2Mp3An = (HtmlAnchor) youtube2Mp3Page.getElementById("download");
						youtube2Mp3TextField
								.setValueAttribute("https://www.youtube.com/watch?v=" + VideoRequest.this.videoId);
						youtube2Mp3Button.click();
					} catch (Exception e) {
						flagYoutube2Mp3Error(e.getMessage());
					}

					/*
					 * While waiting for one response
					 */
					JSONObject theYouMp3JsonObject;
					while (true) {
						if (getTheYouMp3Error() && getYoutube2Mp3Error()) {
							webClient.close();
							return false;
						}

						// TheYouMp3
						if (!theYouMp3Error) {
							try {
								theYouMp3Page = webClient.getPage(
										"http://www.theyoump3.com/a/itemInfo/?video_id=" + VideoRequest.this.videoId);
								theYouMp3HttpResponse = theYouMp3Page.getBody().asText();
								if (theYouMp3HttpResponse == null || theYouMp3HttpResponse.contains("ERROR")) {
									flagTheYouMp3Error();
								}
								theYouMp3HttpResponse = theYouMp3HttpResponse.substring(7);
								theYouMp3JsonObject = new JSONObject(theYouMp3HttpResponse);
								if (theYouMp3JsonObject.has("status")) {
									if (theYouMp3JsonObject.get("status").equals("serving")) {
										VideoRequest.this.coverUrl = "http://i.ytimg.com/vi/"
												+ VideoRequest.this.videoId + "/default.jpg";
										VideoRequest.this.title = theYouMp3JsonObject.getString("title");
										VideoRequest.this.mp3Url = "http://www.theyoump3.com/get?ab=128&video_id="
												+ VideoRequest.this.videoId + "&h="
												+ theYouMp3JsonObject.getString("h");
										webClient.close();
										return true;
									}
								} else {
									flagTheYouMp3Error();
								}
							} catch (Exception e) {
								flagTheYouMp3Error(e.getMessage());
							}
						}

						// Youtube2Mp3
						if (!youtube2Mp3Error) {
							try {
								if (youtube2Mp3An.getAttribute("href").toString().contains("http")) {
									VideoRequest.this.mp3Url = youtube2Mp3An.getAttribute("href").toString();
									VideoRequest.this.coverUrl = "http://i.ytimg.com/vi/" + videoId + "/default.jpg";
									VideoRequest.this.title = youtube2Mp3Page.getElementById("title").asText();
									webClient.close();
									return true;
								} else if (youtube2Mp3Page.getElementById("error") != null) {
									flagYoutube2Mp3Error();
								}
							} catch (Exception e) {
								flagYoutube2Mp3Error(e.getMessage());
							}
						}
						Thread.sleep(1000);
					}

				} catch (Exception e) {
					new Logger().log(Logger.LOG_ERROR, "Error scraping the website. " + e.getMessage());
					e.printStackTrace();
					if (webClient != null) {
						webClient.close();
					}
					return false;
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
					VideoRequest.this.abortRequest("There was an error connecting to Youtube.");
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
				 * Semaphore released only when the download finishes (with error or not).
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
