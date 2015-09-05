package youtube.to.mp3;

import java.io.BufferedReader;
import java.io.InputStreamReader;
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

import youtube.to.mp3.converters.ListenToYouTubeTask;
import youtube.to.mp3.converters.TheYouMp3Task;
import youtube.to.mp3.converters.VidToMp3Task;

public final class ServicesProvider {

	private static final int CONVERSION_TIMEOUT_MINS = 5;

	private static final String CURRENT_VERSION = "0.8";
	private static final int MAX_MINUTES_STORING_RESOLVED_REQUEST = 1;
	private static final int MAX_RETRIES = 3;
	private static final int MAX_SIMULTANEOUS_DOWNLOADS = 10;
	
	private static final Map<String, Request> completedRequests = new HashMap<String, Request>();
	private static final Map<String, Request> requestsQueue = new HashMap<String, Request>();
	private static final Object lock = new Object();
	private static final Semaphore simultaneousDownloads = new Semaphore(MAX_SIMULTANEOUS_DOWNLOADS);
	
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
	
	public static final class Playlist extends ServerResource {

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
			final String playlistId = getRequest().getAttributes().get("playlist_id").toString();
			if (playlistId == null || playlistId.equals("")) {
				response.put("error", "Invalid video id!");
				return response.toString(4);
			} else {
				final PlaylistIdsRequest pip = new PlaylistIdsRequest(playlistId);
				if (pip.isReady() && !pip.wasAborted()) {
					response.put("ready", "The request was fulfilled.");
					response.put("ids", pip.getIds());
					return response.toString(4);
				} else if (pip.isReady() && pip.wasAborted()) {
					response.put("error", pip.getAbortedMessage());
					return response.toString(4);
				} else {
					response.put("scheduled", "Wait until the request is fulfilled.");
					return response.toString(4);
				}
			}
		}
	}
	
	public static abstract class Request {
		private boolean aborted = false;
		private String abortedMessage;
		private long abortedTimestamp = 0;
		private boolean ready = false;
		private int retries = 0;
		
		public Request wasPreviouslyHandled(String id) {
			synchronized (lock) {
				return completedRequests.get(id);
			}
		}
		
		public boolean isDownloadingAlready(String id) {
			synchronized (lock) {
				return requestsQueue.get(id) != null;
			}
		}
		
		public void abortRequest(String errorMsg) {
			setAborted(true);
			setAbortedTimestamp(System.currentTimeMillis());
			setAbortedMessage(errorMsg);
			new Logger().log(Logger.LOG_ERROR, getAbortedMessage());
		}
		
		public void moveToCompletedRequestsList(Request r, String id) {
			synchronized (lock) {
				requestsQueue.remove(id);
				completedRequests.put(id, r);
			}
		}
		
		public boolean wasAborted() {
			return aborted;
		}
		public void setAborted(boolean aborted) {
			this.aborted = aborted;
		}
		public String getAbortedMessage() {
			return abortedMessage;
		}
		public void setAbortedMessage(String abortedMessage) {
			this.abortedMessage = abortedMessage;
		}
		public long getAbortedTimestamp() {
			return abortedTimestamp;
		}
		public void setAbortedTimestamp(long abortedTimestamp) {
			this.abortedTimestamp = abortedTimestamp;
		}
		public boolean isReady() {
			return ready;
		}
		public void setReady(boolean ready) {
			this.ready = ready;
		}
		public int getRetries() {
			return retries;
		}
		public void setRetries(int retries) {
			this.retries = retries;
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

							for (Map.Entry<String, Request> vr : completedRequests.entrySet()) {
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
	}
	
	public static final class PlaylistIdsRequest extends Request {		
		
		private ArrayList<String> ids;		
		private final String playlistId;

		private final class GetPlaylistIdsThread extends Thread {
			final Semaphore finishSem;
			
			public GetPlaylistIdsThread(Semaphore sem) {
				super("Get Playlist Ids Thread for playlist " + playlistId);
				this.finishSem = sem;
			}
			@Override
			public void run() {
				boolean success = false;
				while (getRetries() < MAX_RETRIES) {
					if (getRetries() > 0) {
						new Logger().log(Logger.LOG_WARNING, "Retrying playlist... " + playlistId);
					}
					setRetries(getRetries() + 1);
					try {
						final String getPlaylistIdsCmd = "youtube-dl "
								+ "-j " 
								+ "--flat-playlist " 
								+ "\'http://www.youtube.com/playlist?list="
								+ playlistId + "\' "
								+ "| jq -r \'.id\' | sed \'s_^_https://youtu.be/_\'";
						new Logger().log(Logger.LOG_INFO, "COMMAND: " + getPlaylistIdsCmd);
						final ArrayList<String> getPlaylistIdsResult = executeCommand(getPlaylistIdsCmd);
						boolean getPlaylistIdsSuccessfull = getPlaylistIdsResult.size() != 0 && !getPlaylistIdsResult.contains("error") ;
						if (!getPlaylistIdsSuccessfull) {
							if (getRetries() < MAX_RETRIES) {
								continue;
							} else {
								new Logger().log(Logger.LOG_ERROR, getPlaylistIdsResult.toString());
								abortRequest("Could not get ids from playlist " + playlistId);
								finishSem.release();
								return;
							}
						}
						ids = getPlaylistIdsResult;
						success = true;
						break;
					} catch (Exception e) {
						e.printStackTrace();
						continue;
					}
				}
				if (!success) {
					abortRequest("There was an error connecting to Youtube.");
				}
				finishSem.release();
				return;
			}
			private ArrayList<String> executeCommand(String command) {
				ArrayList<String> output = new ArrayList<String>();
				try {
					String[] cmd = {
							"/bin/sh",
							"-c",
							command
							};
					Process p = Runtime.getRuntime().exec(cmd);
					BufferedReader reader1 = new BufferedReader(new InputStreamReader(p.getInputStream()));
					BufferedReader reader2 = new BufferedReader(new InputStreamReader(p.getErrorStream()));
					String line = "";
					while ((line = reader1.readLine()) != null) {
						output.add(line);
					}
					while ((line = reader2.readLine()) != null) {
						output.add("error");
						output.add(line);
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
				return output;
			}
		}
	
		public PlaylistIdsRequest(final String playlistId){
			this.playlistId = playlistId;
			final Request tmpPip = wasPreviouslyHandled(this.playlistId);
			if (tmpPip != null && tmpPip instanceof PlaylistIdsRequest) {
				setAborted(tmpPip.wasAborted());
				setReady(tmpPip.isReady());
				setAbortedMessage(tmpPip.getAbortedMessage());
				this.ids = ((PlaylistIdsRequest)tmpPip).ids;
			} else if (!isDownloadingAlready(this.playlistId)) {
				synchronized (lock) {
					requestsQueue.put(playlistId, this);
				}
				new Thread() {
					@Override
					public void run() {
						try {
							simultaneousDownloads.acquire();
							new Logger().log(Logger.LOG_INFO, "Preparing playlist " + playlistId);
							PlaylistIdsRequest.this.getPlaylistIds();
							simultaneousDownloads.release();
							setReady(true);
							new Logger().log(Logger.LOG_INFO, "Finished preparing " + playlistId);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}
				}.start();
			}
		}

		private void getPlaylistIds() {
			new Logger().log(Logger.LOG_INFO, "Starting getting playlist ids" + playlistId);
			while (getRetries() < MAX_RETRIES) {
				if (getRetries() > 0)
					new Logger().log(Logger.LOG_WARNING, "Retrying again... (" + getRetries() + ")");
				/*
				 * Semaphore released only when the download finishes (with error or not).
				 */
				final Semaphore sem = new Semaphore(0);
				final Thread t = new GetPlaylistIdsThread(sem);
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
					new Logger().log(Logger.LOG_WARNING, "Getting playlist ids " + playlistId + " timed out!");
					if (getRetries() >= MAX_RETRIES) {
						abortRequest("Could not get playlist ids " + playlistId + " because it timed out.");
					} else {
						setRetries(getRetries() + 1);
					}
				} else {
					break;
				}
			}
			new Logger().log(Logger.LOG_INFO, "Getting playlist ids " + playlistId + " completed!");
			moveToCompletedRequestsList(this, this.playlistId);
		}
							
		public ArrayList<String> getIds() {
			return ids;
		}
	}

	public static final class VideoRequest extends Request {

		private String coverUrl = "";
		private String mp3Url = "";
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
				final ArrayList<ServerFetcher> serversTasks = new ArrayList<ServerFetcher>();
				/*
				 * Initializing HTMLUnit
				 */
				LogFactory.getFactory().setAttribute("org.apache.commons.logging.Log",
						"org.apache.commons.logging.impl.NoOpLog");
				java.util.logging.Logger.getLogger("com.gargoylesoftware.htmlunit").setLevel(Level.OFF);
				java.util.logging.Logger.getLogger("org.apache.commons.httpclient").setLevel(Level.OFF);

				final Semaphore sem = new Semaphore(0);
				
//				serversTasks.add(new ServerFetcher(sem, new Youtube2Mp3Task(VideoRequest.this)));
				serversTasks.add(new ServerFetcher(sem, new TheYouMp3Task(VideoRequest.this)));
//				serversTasks.add(new ServerFetcher(sem, new Mp3FiberTask(VideoRequest.this)));
				serversTasks.add(new ServerFetcher(sem, new ListenToYouTubeTask(VideoRequest.this)));
				serversTasks.add(new ServerFetcher(sem, new VidToMp3Task(VideoRequest.this)));
				
				for(ServerFetcher t : serversTasks){
					t.start();
				}
				try {
					boolean allTasksFinished;
					boolean oneTaskFinished = false;
					do {
						sem.acquire();
						for(ServerFetcher t1 : serversTasks){
							if(t1.hasFinished() && !t1.hasError()){
								for(ServerFetcher t2 : serversTasks){
									if(!t1.equals(t2)){
										t2.interrupt();
										t2.stop();
									}									
								}
								oneTaskFinished = true;
								break;
							}
						}
						if(oneTaskFinished){
							break;
						}
						allTasksFinished = true;
						for(ServerFetcher t : serversTasks){
							allTasksFinished = allTasksFinished && t.hasFinished();
						}
					} while (!allTasksFinished);
				} catch (InterruptedException e) {
					for(ServerFetcher t : serversTasks){
						t.interrupt();
						t.stop();
					}
					return false;
				}
				boolean allTasksHadError = true;
				for(ServerFetcher t : serversTasks){
					allTasksHadError = allTasksHadError && t.hasError();
				}
				return !allTasksHadError;

			}

			@Override
			public void run() {
				boolean success = false;
				while (getRetries() < MAX_RETRIES) {
					if (getRetries() > 0) {
						new Logger().log(Logger.LOG_WARNING, "Retrying... " + videoId);
					}
					setRetries(getRetries() + 1);
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


		public VideoRequest(final String videoId) {
			this.videoId = videoId;
			final Request tmpVr = wasPreviouslyHandled(this.videoId);
			if (tmpVr != null && tmpVr instanceof VideoRequest) {
				setAborted(tmpVr.wasAborted());
				setReady(tmpVr.isReady());
				setAbortedMessage(tmpVr.getAbortedMessage());
				this.coverUrl = ((VideoRequest)tmpVr).coverUrl;
				this.title = ((VideoRequest)tmpVr).title;
				this.mp3Url = ((VideoRequest)tmpVr).mp3Url;
			} else if (!isDownloadingAlready(this.videoId)) {
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
							setReady(true);
							new Logger().log(Logger.LOG_INFO, "Finished preparing " + videoId);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}
				}.start();
			}
		}

		@SuppressWarnings("deprecation")
		private void download() {
			new Logger().log(Logger.LOG_INFO, "Starting download of " + videoId);
			while (getRetries() < MAX_RETRIES) {
				if (getRetries() > 0)
					new Logger().log(Logger.LOG_WARNING, "Retrying again... (" + getRetries() + ")");
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
					new Logger().log(Logger.LOG_WARNING, "Download of " + videoId + " timed out!");
					if (getRetries() >= MAX_RETRIES) {
						abortRequest("Could not download " + videoId + " because it timed out.");
					} else {
						setRetries(getRetries() + 1);
					}
				} else {
					break;
				}
			}
			new Logger().log(Logger.LOG_INFO, "Download of " + videoId + " completed!");
			moveToCompletedRequestsList(this, this.videoId);
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

		public void setCoverUrl(String coverUrl) {
			this.coverUrl = coverUrl;
		}

		public void setMp3Url(String mp3Url) {
			new Logger().log(Logger.LOG_INFO, "(" + videoId + ") MP3 URL: " + mp3Url);
			this.mp3Url = mp3Url;
		}

		public void setTitle(String title) {
			this.title = title;
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
